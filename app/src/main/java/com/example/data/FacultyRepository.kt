package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class FacultyRepository(private val facultyDao: FacultyDao) {
    val allFaculty: Flow<List<Faculty>> = facultyDao.getAllFaculty()
    val allDecisions: Flow<List<Decision>> = facultyDao.getAllDecisions()

    suspend fun initializeDatabaseIfEmpty() {
        val currentFaculty = facultyDao.getAllFacultyList()
        if (currentFaculty.isEmpty()) {
            val defaultFaculty = listOf(
                Faculty(
                    name = "Dr. Aris Thorne",
                    title = "Associate Professor",
                    researchAreas = "Natural Language Processing (NLP), Large Language Models, Sentiment Analysis, Text Summarization",
                    description = "Specializes in modern transformer architectures, conversational agents, and semantic text mining in low-resource languages.",
                    currentProjects = 2,
                    maxProjects = 4,
                    email = "a.thorne@university.edu",
                    office = "Block A, Room 402"
                ),
                Faculty(
                    name = "Dr. Beatrix Vance",
                    title = "Professor",
                    researchAreas = "Computer Vision, Medical Image Segmentation, Object Detection, Generative Adversarial Networks",
                    description = "Researches deep learning models for automated tumor detection in clinical MRI scans and real-time industrial defect inspection systems.",
                    currentProjects = 3,
                    maxProjects = 4,
                    email = "b.vance@university.edu",
                    office = "Block B, Room 210"
                ),
                Faculty(
                    name = "Dr. Charles Xavier",
                    title = "Professor",
                    researchAreas = "Brain-Computer Interfaces, Neural Networks, Cyber-Physical Systems",
                    description = "A leading pioneer in biological signal processing, electroencephalography (EEG) signal mapping, and neuro-prosthetic control systems.",
                    currentProjects = 4,
                    maxProjects = 4,
                    email = "c.xavier@university.edu",
                    office = "Mansion Block, Office 1"
                ),
                Faculty(
                    name = "Dr. Divya Rao",
                    title = "Professor",
                    researchAreas = "Distributed Systems, Edge Computing, Blockchain Technology, Cloud Architecture",
                    description = "Investigates decentralized consensus protocols, load balancing in serverless cloud fabrics, and low-latency smart city networks.",
                    currentProjects = 4,
                    maxProjects = 4,
                    email = "d.rao@university.edu",
                    office = "Block A, Room 105"
                ),
                Faculty(
                    name = "Dr. Elena Rostova",
                    title = "Assistant Professor",
                    researchAreas = "Human-Computer Interaction (HCI), Accessibility in Tech, Virtual Reality (VR), User Experience",
                    description = "Focuses on designing interactive systems and assistive tactile/gaze interfaces for neurodiverse and visually impaired users.",
                    currentProjects = 1,
                    maxProjects = 3,
                    email = "e.rostova@university.edu",
                    office = "Block C, Room 301"
                ),
                Faculty(
                    name = "Dr. Frank Chen",
                    title = "Associate Professor",
                    researchAreas = "Cryptography, Privacy-Preserving Machine Learning, Federated Learning, Cybersecurity",
                    description = "Pioneers multi-party computation and homomorphic encryption to train artificial intelligence models securely without exposing proprietary source datasets.",
                    currentProjects = 2,
                    maxProjects = 4,
                    email = "f.chen@university.edu",
                    office = "Block A, Room 512"
                ),
                Faculty(
                    name = "Dr. Grace Hopper II",
                    title = "Professor",
                    researchAreas = "Software Engineering, Program Synthesis, Compiler Optimization, Automated Debugging",
                    description = "Researches automatic code generation from formal language specifications and self-healing microservice structures.",
                    currentProjects = 3,
                    maxProjects = 5,
                    email = "g.hopper@university.edu",
                    office = "Block B, Room 333"
                ),
                Faculty(
                    name = "Dr. Harold Finch",
                    title = "Professor",
                    researchAreas = "Artificial Intelligence Safety, Reinforcement Learning, Multi-Agent Systems, Game Theory",
                    description = "Explores value-alignment mechanisms in self-learning machines and behavioral optimization in multi-agent cooperative neural nets.",
                    currentProjects = 1,
                    maxProjects = 3,
                    email = "h.finch@university.edu",
                    office = "Library Suite 12"
                ),
                Faculty(
                    name = "Dr. Irene Adler",
                    title = "Assistant Professor",
                    researchAreas = "Bioinformatics, Computational Biology, Genomic Sequence Alignment, Machine Learning in Healthcare",
                    description = "Applies deep convolutional networks to predict protein folding sequences and identify novel gene expressions in clinical oncology databases.",
                    currentProjects = 2,
                    maxProjects = 3,
                    email = "i.adler@university.edu",
                    office = "Block C, Room 418"
                ),
                Faculty(
                    name = "Dr. Julian Sterling",
                    title = "Associate Professor",
                    researchAreas = "Internet of Things (IoT), Wireless Sensor Networks, Smart Cities, Embedded Systems",
                    description = "Designs energy-harvesting wireless mesh architectures for low-power pollution tracking and adaptive urban traffic scheduling.",
                    currentProjects = 3,
                    maxProjects = 4,
                    email = "j.sterling@university.edu",
                    office = "Block A, Room 204"
                ),
                Faculty(
                    name = "Dr. Karen Oh",
                    title = "Assistant Professor",
                    researchAreas = "Quantum Computing, Quantum Algorithms, Quantum Cryptography",
                    description = "Explores quantum error correction, optimizations for Shor's and Grover's algorithms, and post-quantum network security protocols.",
                    currentProjects = 0,
                    maxProjects = 3,
                    email = "k.oh@university.edu",
                    office = "Block C, Room 109"
                )
            )
            facultyDao.insertAllFaculty(defaultFaculty)
        }
    }

    suspend fun getFacultyByName(name: String): Faculty? {
        return facultyDao.getFacultyByName(name)
    }

    suspend fun getFacultyById(id: Int): Faculty? {
        return facultyDao.getFacultyById(id)
    }

    suspend fun insertDecision(decision: Decision) {
        facultyDao.insertDecision(decision)
    }

    suspend fun updateFacultyLoad(id: Int, load: Int) {
        facultyDao.updateFacultyLoad(id, load)
    }

    suspend fun clearDecisions() {
        facultyDao.clearAllDecisions()
    }
}
