package com.medapp.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.medapp.model.Specialty
import com.medapp.model.UserRole
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SpecialtyRepository {
    private val db = FirebaseFirestore.getInstance()
    private val specialtiesCollection = db.collection("specialties")
    private val usersCollection = db.collection("users")

    fun getSpecialtiesFlow(): Flow<List<Specialty>> = callbackFlow {
        val listener = specialtiesCollection
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val specialties = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { Specialty.fromMap(it, doc.id) }
                    }
                    trySend(specialties)
                }
            }
        
        awaitClose { listener.remove() }
    }

    suspend fun addSpecialty(name: String): Result<Specialty> = runCatching {
        // revisar si existe
        val existing = specialtiesCollection
            .whereEqualTo("name", name)
            .get()
            .await()
            
        if (!existing.isEmpty) {
            throw Exception("Ya existe una especialidad con este nombre")
        }

        val id = UUID.randomUUID().toString()
        val specialty = Specialty(id = id, name = name)
        
        specialtiesCollection.document(id).set(specialty.toMap()).await()
        specialty
    }

    suspend fun updateSpecialty(id: String, name: String, oldName: String): Result<Unit> = runCatching {
        // Actualizar la tabla especialidad
        specialtiesCollection.document(id).update("name", name).await()
        
        // Actualizar todos los doctores que tienen el nombre viejo de la especialidad
        if (oldName.isNotEmpty() && oldName != name) {
            val doctorsToUpdate = usersCollection
                .whereEqualTo("role", UserRole.DOCTOR.name)
                .whereEqualTo("specialty", oldName)
                .get()
                .await()
                
            val batch = db.batch()
            for (doc in doctorsToUpdate.documents) {
                batch.update(doc.reference, "specialty", name)
            }
            batch.commit().await()
        }
    }

    suspend fun getDoctorCountBySpecialty(specialtyName: String): Result<Int> = runCatching {
        val countQuery = usersCollection
            .whereEqualTo("role", UserRole.DOCTOR.name)
            .whereEqualTo("specialty", specialtyName)
            .get()
            .await()
            
        countQuery.size()
    }

    suspend fun deleteSpecialty(id: String, name: String): Result<Unit> = runCatching {
        // revisar si hay algun doctor con esta especialidad primero
        val doctorCount = getDoctorCountBySpecialty(name).getOrElse { 0 }
        
        if (doctorCount > 0) {
            throw Exception("No se puede eliminar la especialidad '$name' porque hay $doctorCount doctor(es) asignados a ella. Actualice a los doctores primero.")
        }
        
        specialtiesCollection.document(id).delete().await()
    }
}
