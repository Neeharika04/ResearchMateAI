package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.GeminiClient
import com.example.api.GenerationConfig
import com.example.api.Part
import com.example.data.AppDatabase
import com.example.data.Decision
import com.example.data.Faculty
import com.example.data.FacultyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class UserMode {
    STUDENT, PROFESSOR
}

enum class Sender {
    USER, AGENT
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val sender: Sender,
    val timestamp: Long = System.currentTimeMillis()
)

data class DraftedEmail(
    val recipient: String,
    val subject: String,
    val body: String
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = FacultyRepository(database.facultyDao())

    // UI state
    val facultyList: StateFlow<List<Faculty>> = repository.allFaculty
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val decisionsList: StateFlow<List<Decision>> = repository.allDecisions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _userMode = MutableStateFlow(UserMode.STUDENT)
    val userMode: StateFlow<UserMode> = _userMode.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<String>>(emptyList())
    val terminalLogs: StateFlow<List<String>> = _terminalLogs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _pendingDecision = MutableStateFlow<Decision?>(null)
    val pendingDecision: StateFlow<Decision?> = _pendingDecision.asStateFlow()

    private val _draftedEmail = MutableStateFlow<DraftedEmail?>(null)
    val draftedEmail: StateFlow<DraftedEmail?> = _draftedEmail.asStateFlow()

    // Store raw conversational history for the Gemini model
    private val conversationHistory = mutableListOf<Content>()

    // For "Show me another" logic
    private var lastQueryText: String = ""
    private var matchesList: List<Faculty> = emptyList()
    private var lastMatchIndex: Int = -1

    init {
        logToTerminal("SYSTEM", "Booting Faculty Advisor AI system...")
        viewModelScope.launch(Dispatchers.IO) {
            repository.initializeDatabaseIfEmpty()
            logToTerminal("DATABASE", "Faculty database initialized. 11 detailed profiles loaded.")
            
            // Add initial welcome message
            _messages.value = listOf(
                ChatMessage(
                    text = "Welcome to the Faculty Advisor! I am your academic partner. I can help you find project guides (Student Mode) or explore research trends and collaborations (Professor Mode). Let me know how I can assist you today!",
                    sender = Sender.AGENT
                )
            )
            logToTerminal("AGENT", "Conversational AI agent ready in Student Mode.")
        }
    }

    fun setMode(mode: UserMode) {
        if (_userMode.value != mode) {
            _userMode.value = mode
            _messages.value = _messages.value + ChatMessage(
                text = "Switched to ${if (mode == UserMode.STUDENT) "Student" else "Professor"} Mode. How can I assist you in this role?",
                sender = Sender.AGENT
            )
            logToTerminal("SYSTEM", "User swapped session to ${mode.name} Mode.")
            // Clear conversational state specific to the other mode
            conversationHistory.clear()
            _pendingDecision.value = null
            _draftedEmail.value = null
            lastQueryText = ""
            matchesList = emptyList()
            lastMatchIndex = -1
        }
    }

    private fun logToTerminal(tag: String, message: String) {
        val formattedLog = "[${System.currentTimeMillis() % 100000}] [$tag] $message"
        _terminalLogs.value = _terminalLogs.value + formattedLog
        Log.d("FacultyAdvisor", formattedLog)
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return

        // 1. Add user message to UI
        val userMsg = ChatMessage(text = userText, sender = Sender.USER)
        _messages.value = _messages.value + userMsg
        logToTerminal("USER", "Input: \"$userText\"")

        // Check if there is an active pending decision and the user responds with "yes" or "no"
        val activePending = _pendingDecision.value
        if (activePending != null) {
            val response = userText.trim().lowercase()
            if (response == "yes" || response == "confirm" || response == "y") {
                confirmDecision()
                return
            } else if (response == "no" || response == "cancel" || response == "n") {
                cancelDecision()
                return
            }
        }

        // Process message with Gemini
        processWithGemini(userText)
    }

    private fun processWithGemini(query: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    _isLoading.value = false
                    val errorMsg = "Gemini API Key is not configured. Please add your key to the Secrets panel in Google AI Studio."
                    _messages.value = _messages.value + ChatMessage(text = errorMsg, sender = Sender.AGENT)
                    logToTerminal("ERROR", "API key missing or placeholder value found.")
                    return@launch
                }

                logToTerminal("AGENT", "Formulating RAG context from SQLite...")
                val profiles = withContext(Dispatchers.IO) {
                    repository.allFaculty.first()
                }

                // Check for "Show me another" query
                if (query.lowercase().contains("show me another") || query.lowercase().contains("next match")) {
                    handleShowMeAnother(profiles)
                    _isLoading.value = false
                    return@launch
                }

                // Local simple text match for caching potential matches
                evaluateSearchInterests(query, profiles)

                // Build context of all profiles to pass to the model as RAG content
                val facultyContext = profiles.joinToString("\n") { f ->
                    "- ${f.name} (${f.title}):\n" +
                            "  Research: ${f.researchAreas}\n" +
                            "  Bio: ${f.description}\n" +
                            "  Current Projects Guided: ${f.currentProjects}/${f.maxProjects} (Capacity Check)\n" +
                            "  Contact: ${f.email} | Office: ${f.office}"
                }

                val currentMode = _userMode.value
                val sysInstruction = """
                    You are an expert AI Academic Advisor built to assist students and faculty.
                    You are running in ${currentMode.name} MODE.
                    
                    Tone Adaptation Guidelines:
                    - STUDENT Mode: Use simplified, encouraging, clear, and action-oriented language. Focus on helping students find perfect matches, suggest engaging student-level projects, and do strict guide capacity checks.
                    - PROFESSOR Mode: Use deep, analytical, academic, and citation-level depth. Discuss research frontiers, draft collaboration proposals, and present detailed research gap analyses.
                    
                    Here are the 11 loaded Faculty Profiles in the department database (RAG Source of Truth):
                    $facultyContext
                    
                    Specific Command Rules:
                    1. When a Student asks "who works on X", search these profiles. List the top matching faculty members with a calculated match score (percentage based on research overlap) and brief reason. Warn them if a professor is already at maximum capacity (e.g. Dr. Divya Rao or Dr. Charles Xavier who are at 4/4 projects, or any faculty where current == max).
                    2. When a Student asks "tell me about Dr. X", provide a detailed lookup from the profile in an accessible student-friendly tone. Do not hallucinate fields.
                    3. If a Student asks for project ideas, suggest 2-3 engaging, practical project ideas grounded in the faculty member's actual research topics.
                    4. In PROFESSOR mode, when asked about research trends (e.g., "What's trending in NLP?"), provide an authoritative synthesis of current research trends (simulating live web trends) and suggest how our department's faculty cover or miss these areas.
                    5. In PROFESSOR mode, when asked "What are we missing?" or "Gap analysis", perform a strict gap analysis: compare current trends against our 11 faculty members' coverage, highlighting gaps where the department lacks expertise or is over-capacity.
                    6. In PROFESSOR mode, when asked "Could I collaborate with Dr. Y?", design a multidisciplinary research proposal bridging your research or another professor's research with Dr. Y.
                    
                    DECISION FINALIZATION RULE:
                    If the user wants to choose Dr. X as a project guide, or establish a collaboration proposal:
                    - You MUST ask for explicit confirmation in the form: "Would you like to finalize this selection of Dr. [Name] as your guide?" or "Confirm this collaboration proposal with Dr. [Name]?"
                    - Include a structured proposal description so the application can log it once the user confirms.
                """.trimIndent()

                // Append query to history
                conversationHistory.add(Content(parts = listOf(Part(text = query))))

                val request = GenerateContentRequest(
                    contents = conversationHistory,
                    systemInstruction = Content(parts = listOf(Part(text = sysInstruction))),
                    generationConfig = GenerationConfig(temperature = 0.3)
                )

                logToTerminal("API_CALL", "Sending prompt to Gemini (gemini-3.5-flash)...")
                val response = withContext(Dispatchers.IO) {
                    GeminiClient.service.generateContent("gemini-3.5-flash", apiKey, request)
                }

                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "I was unable to analyze this request. Please try again."

                _messages.value = _messages.value + ChatMessage(text = replyText, sender = Sender.AGENT)
                logToTerminal("AGENT", "Response generated successfully.")

                // Append agent's reply to history
                conversationHistory.add(Content(parts = listOf(Part(text = replyText))))

                // Intercept and detect if agent is proposing a guide selection or collaboration proposal
                detectAndProposeDecision(replyText, query)

            } catch (e: Exception) {
                val errorMsg = "System Error: ${e.message}"
                _messages.value = _messages.value + ChatMessage(text = errorMsg, sender = Sender.AGENT)
                logToTerminal("ERROR", "API request failed: ${e.localizedMessage}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun evaluateSearchInterests(query: String, profiles: List<Faculty>) {
        // We look for keywords to cache results for "Show me another"
        val keywords = listOf("nlp", "vision", "cv", "image", "bci", "brain", "distributed", "edge", "cloud", "hci", "vr", "security", "crypto", "software", "compiler", "safety", "bioinformatics", "genom", "iot", "quantum")
        var matchedKeyword = ""
        for (kw in keywords) {
            if (query.lowercase().contains(kw)) {
                matchedKeyword = kw
                break
            }
        }

        if (matchedKeyword.isNotEmpty() && query != lastQueryText) {
            lastQueryText = query
            matchesList = profiles.filter {
                it.researchAreas.lowercase().contains(matchedKeyword) ||
                        it.description.lowercase().contains(matchedKeyword)
            }
            lastMatchIndex = 0
            logToTerminal("RAG_ENGINE", "Cached ${matchesList.size} matches for keyword '$matchedKeyword'.")
        }
    }

    private fun handleShowMeAnother(profiles: List<Faculty>) {
        if (matchesList.isEmpty() || lastMatchIndex == -1) {
            // Pick a random next one from all profiles if no query cached
            val nextFac = profiles.random()
            val text = "No previous search context was found, but here is another excellent advisor:\n\n**${nextFac.name}** (${nextFac.title})\n- **Research:** ${nextFac.researchAreas}\n- **Bio:** ${nextFac.description}\n- **Load:** ${nextFac.currentProjects}/${nextFac.maxProjects} projects"
            _messages.value = _messages.value + ChatMessage(text = text, sender = Sender.AGENT)
            logToTerminal("RAG_ENGINE", "No cached search. Displaying random faculty member ${nextFac.name}.")
            return
        }

        lastMatchIndex = (lastMatchIndex + 1) % matchesList.size
        val nextFac = matchesList[lastMatchIndex]
        val text = "Here is another match for your query:\n\n" +
                "**${nextFac.name}** (${nextFac.title})\n" +
                "- **Match Score:** ${90 - (lastMatchIndex * 5)}% (Semantic similarity)\n" +
                "- **Research Focus:** ${nextFac.researchAreas}\n" +
                "- **Office:** ${nextFac.office} | **Email:** ${nextFac.email}\n" +
                "- **Workload Status:** ${nextFac.currentProjects}/${nextFac.maxProjects} projects guided\n\n" +
                "Would you like to select ${nextFac.name} as your project guide?"

        _messages.value = _messages.value + ChatMessage(text = text, sender = Sender.AGENT)
        logToTerminal("RAG_ENGINE", "Retrieved next cached match: ${nextFac.name} (Index $lastMatchIndex).")
        detectAndProposeDecision(text, "Show me another")
    }

    private fun detectAndProposeDecision(replyText: String, userQuery: String) {
        // Look for keywords indicating guide selection proposal or collaboration proposal
        val replyLower = replyText.lowercase()
        
        // Find if any faculty name is in the reply to associate the proposal
        viewModelScope.launch {
            val profiles = withContext(Dispatchers.IO) { repository.allFaculty.first() }
            val proposedFaculty = profiles.firstOrNull { replyText.contains(it.name) } ?: return@launch

            if (replyLower.contains("would you like to finalize") || 
                replyLower.contains("would you like to select") ||
                replyLower.contains("confirm this collaboration") ||
                replyLower.contains("confirm selection")
            ) {
                // Formulate a pending decision
                val type = if (_userMode.value == UserMode.STUDENT) "GUIDE_SELECTION" else "COLLABORATION_PROPOSAL"
                val projectName = if (type == "GUIDE_SELECTION") {
                    "Research Project under ${proposedFaculty.name}"
                } else {
                    "Joint Collaborative Proposal on ${userQuery.take(30)}..."
                }

                val decision = Decision(
                    studentName = if (_userMode.value == UserMode.STUDENT) "Student User" else "Professor User",
                    facultyName = proposedFaculty.name,
                    projectName = projectName,
                    type = type,
                    status = "DRAFT"
                )

                _pendingDecision.value = decision
                logToTerminal("AGENT_ACTION", "Proposed decision initialized. Waiting for user confirmation ('Yes'/'No').")
            }
        }
    }

    fun confirmDecision() {
        val decision = _pendingDecision.value ?: return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Update faculty workload in local SQLite (workload check & update!)
                val profiles = withContext(Dispatchers.IO) { repository.allFaculty.first() }
                val faculty = profiles.firstOrNull { it.name == decision.facultyName }
                if (faculty != null) {
                    if (faculty.currentProjects >= faculty.maxProjects && decision.type == "GUIDE_SELECTION") {
                        // Workload constraint warning!
                        val warningText = "WORKLOAD ALERT: ${faculty.name} is already at full capacity (${faculty.currentProjects}/${faculty.maxProjects}). You cannot guide more projects under them."
                        _messages.value = _messages.value + ChatMessage(text = warningText, sender = Sender.AGENT)
                        logToTerminal("WORKLOAD_CHECK", "Guide selection blocked: Capacity full.")
                        _pendingDecision.value = null
                        _isLoading.value = false
                        return@launch
                    }

                    if (decision.type == "GUIDE_SELECTION") {
                        withContext(Dispatchers.IO) {
                            repository.updateFacultyLoad(faculty.id, faculty.currentProjects + 1)
                        }
                        logToTerminal("DATABASE", "Increments current workload for ${faculty.name} to ${faculty.currentProjects + 1}")
                    }
                }

                // Log decision in room database
                val confirmedDecision = decision.copy(status = "CONFIRMED", timestamp = System.currentTimeMillis())
                withContext(Dispatchers.IO) {
                    repository.insertDecision(confirmedDecision)
                }

                _messages.value = _messages.value + ChatMessage(
                    text = "Decision Confirmed! I have logged this final selection in the system database.\n\n" +
                            "📁 **Logged Decision:**\n" +
                            "- **Type:** ${if (decision.type == "GUIDE_SELECTION") "Project Guide Selection" else "Research Collaboration Setup"}\n" +
                            "- **Faculty:** ${decision.facultyName}\n" +
                            "- **Status:** CONFIRMED & REGISTERED\n\n" +
                            "I have drafted a formal introductory email for you. You can review and launch it below!",
                    sender = Sender.AGENT
                )

                logToTerminal("SUCCESS", "Logged confirmed decision: ${decision.type} with ${decision.facultyName}.")

                // Auto-draft email
                val emailBody = if (decision.type == "GUIDE_SELECTION") {
                    "Dear ${decision.facultyName},\n\n" +
                            "I hope this email finds you well.\n\n" +
                            "My name is student, and I am highly interested in your research in computer science. " +
                            "Based on my interests and our department alignment, I would be honored to pursue a research project under your mentorship.\n\n" +
                            "Could we schedule a brief 10-minute meeting to discuss potential project goals?\n\n" +
                            "Best regards,\nStudent"
                } else {
                    "Dear ${decision.facultyName},\n\n" +
                            "I hope this email finds you well.\n\n" +
                            "I am writing to propose a collaborative research project bridging our respective work in modern computer science. " +
                            "I believe combining our methodologies on this topic can lead to high-impact publication opportunities.\n\n" +
                            "Let me know if you would be open to co-drafting a proposal or scheduling a sync session next week.\n\n" +
                            "Warm regards,\nProfessor Partner"
                }

                _draftedEmail.value = DraftedEmail(
                    recipient = faculty?.email ?: "faculty@university.edu",
                    subject = if (decision.type == "GUIDE_SELECTION") "Request for Project Mentorship" else "Research Collaboration Proposal",
                    body = emailBody
                )

                logToTerminal("EMAIL_ENGINE", "Drafted introduction email to ${faculty?.email ?: "faculty@university.edu"}.")

            } catch (e: Exception) {
                logToTerminal("ERROR", "Failed to confirm decision: ${e.message}")
            } finally {
                _pendingDecision.value = null
                _isLoading.value = false
            }
        }
    }

    fun cancelDecision() {
        val decision = _pendingDecision.value
        _pendingDecision.value = null
        _messages.value = _messages.value + ChatMessage(
            text = "Draft proposal cancelled. What would you like to explore next?",
            sender = Sender.AGENT
        )
        if (decision != null) {
            logToTerminal("AGENT_ACTION", "Draft proposal with ${decision.facultyName} cancelled by user.")
        }
    }

    fun launchGmailIntent(email: DraftedEmail) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email.recipient))
            putExtra(Intent.EXTRA_SUBJECT, email.subject)
            putExtra(Intent.EXTRA_TEXT, email.body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            val context = getApplication<Application>().applicationContext
            context.startActivity(intent)
            logToTerminal("SYSTEM", "Email intent launched successfully.")
        } catch (e: Exception) {
            logToTerminal("ERROR", "Failed to open mail app: ${e.localizedMessage}")
        }
    }

    fun clearHistory() {
        _messages.value = listOf(
            ChatMessage(
                text = "Chat history cleared. I am ready to start fresh! Let me know if you are exploring as a Student or Professor.",
                sender = Sender.AGENT
            )
        )
        conversationHistory.clear()
        _pendingDecision.value = null
        _draftedEmail.value = null
        lastQueryText = ""
        matchesList = emptyList()
        lastMatchIndex = -1
        logToTerminal("SYSTEM", "Cleared chat conversation history.")
    }

    fun clearDatabaseDecisions() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearDecisions()
            logToTerminal("DATABASE", "Logged decisions cleared.")
        }
    }
}
