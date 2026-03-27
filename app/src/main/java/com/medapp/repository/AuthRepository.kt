package com.medapp.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.medapp.model.User
import com.medapp.model.UserRole
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser

    // ─── Auth State Flow ──────────────────────────────────────────────────────
    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    // ─── Register ─────────────────────────────────────────────────────────────
    suspend fun register(
        email: String,
        password: String,
        name: String,
        phone: String,
        role: UserRole,
        specialty: String = ""
    ): Result<User> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user!!.uid

        val user = User(
            uid = uid,
            name = name,
            email = email,
            phone = phone,
            role = role,
            specialty = specialty
        )

        db.collection("users").document(uid).set(user.toMap()).await()
        user
    }

    // ─── Login ────────────────────────────────────────────────────────────────
    suspend fun login(email: String, password: String): Result<FirebaseUser> = runCatching {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        result.user!!
    }

    // ─── Logout ───────────────────────────────────────────────────────────────
    fun logout() = auth.signOut()

    // ─── Get Current User Profile ─────────────────────────────────────────────
    suspend fun getUserProfile(uid: String): Result<User> = runCatching {
        val doc = db.collection("users").document(uid).get().await()
        User.fromMap(doc.data ?: emptyMap())
    }

    // ─── Update FCM Token ─────────────────────────────────────────────────────
    suspend fun updateFcmToken(uid: String, token: String) {
        runCatching {
            db.collection("users").document(uid)
                .update("fcmToken", token).await()
        }
    }

    // ─── Get All Doctors ──────────────────────────────────────────────────────
    suspend fun getDoctors(): Result<List<User>> = runCatching {
        val snapshot = db.collection("users")
            .whereEqualTo("role", UserRole.DOCTOR.name)
            .get().await()
        snapshot.documents.mapNotNull { doc ->
            doc.data?.let { User.fromMap(it) }
        }
    }
}
