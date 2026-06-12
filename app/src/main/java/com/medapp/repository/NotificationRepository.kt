package com.medapp.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.medapp.model.AppNotification
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class NotificationRepository {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("notifications")

    // Obtener notificaciones de un usuario (en tiempo real) y ordenar localmente para evitar error de índice
    fun getUserNotifications(userId: String): Flow<List<AppNotification>> = callbackFlow {
        val listenerRegistration = collection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val notifications = snapshot.documents.mapNotNull { doc ->
                        AppNotification.fromMap(doc.data ?: emptyMap(), doc.id)
                    }.sortedByDescending { it.timestamp }
                    
                    trySend(notifications)
                }
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    // Guardar una nueva notificación en Firestore
    suspend fun saveNotification(notification: AppNotification): Result<String> {
        return try {
            val docRef = if (notification.id.isEmpty()) {
                collection.document() // Crear nuevo id si está vacío
            } else {
                collection.document(notification.id)
            }
            
            val finalNotification = notification.copy(id = docRef.id)
            docRef.set(finalNotification.toMap()).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Marcar notificación como leída
    suspend fun markAsRead(notificationId: String): Result<Unit> {
        return try {
            collection.document(notificationId).update("isRead", true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Marcar todas las notificaciones de un usuario como leídas
    suspend fun markAllAsRead(userId: String): Result<Unit> {
        return try {
            val unreadDocs = collection
                .whereEqualTo("userId", userId)
                .whereEqualTo("isRead", false)
                .get()
                .await()
                
            if (!unreadDocs.isEmpty) {
                db.runBatch { batch ->
                    for (doc in unreadDocs.documents) {
                        batch.update(doc.reference, "isRead", true)
                    }
                }.await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Eliminar una notificación
    suspend fun deleteNotification(notificationId: String): Result<Unit> {
        return try {
            collection.document(notificationId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Eliminar todas las notificaciones de un usuario
    suspend fun clearAllNotifications(userId: String): Result<Unit> {
        return try {
            val docs = collection.whereEqualTo("userId", userId).get().await()
            db.runBatch { batch ->
                for (doc in docs.documents) {
                    batch.delete(doc.reference)
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
