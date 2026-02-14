package com.example.fortunateappbuilder

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

object FirebaseManager {
    private val auth: FirebaseAuth = Firebase.auth
    private val db: FirebaseFirestore = Firebase.firestore

    val currentUserId: String?
        get() = auth.currentUser?.uid

    suspend fun signInAnonymously(): Result<String> = runCatching {
        val result = auth.signInAnonymously().await()
        result.user?.uid ?: throw Exception("No UID after anonymous sign-in")
    }

    suspend fun saveChatHistory(userId: String, messages: List<Message>) {
        db.collection("users")
            .document(userId)
            .collection("chats")
            .document("current_chat")
            .set(mapOf("messages" to messages.map { mapOf("text" to it.text, "isUser" to it.isUser) }))
            .await()
    }

    suspend fun loadChatHistory(userId: String): List<Message>? {
        val doc = db.collection("users")
            .document(userId)
            .collection("chats")
            .document("current_chat")
            .get()
            .await()

        val list = doc.get("messages") as? List<Map<String, Any>>
        return list?.map {
            Message(
                text = it["text"] as String,
                isUser = it["isUser"] as Boolean
            )
        }
    }

    suspend fun saveGeneratedProject(userId: String, projectName: String, projectJson: String) {
        db.collection("users")
            .document(userId)
            .collection("projects")
            .document(projectName)
            .set(mapOf(
                "name" to projectName,
                "json" to projectJson,
                "timestamp" to System.currentTimeMillis()
            ))
            .await()
    }

    suspend fun getSavedProjects(userId: String): List<String> {
        val snapshot = db.collection("users")
            .document(userId)
            .collection("projects")
            .get()
            .await()

        return snapshot.documents.mapNotNull { it.getString("name") }
    }
}
