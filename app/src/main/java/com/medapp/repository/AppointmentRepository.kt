package com.medapp.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.medapp.model.Appointment
import com.medapp.model.AppointmentStats
import com.medapp.model.AppointmentStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Calendar

class AppointmentRepository {

    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("appointments")

    // ─── agendar/crear cita ───────────────────────────────────────────────────
    suspend fun createAppointment(appointment: Appointment): Result<Appointment> = runCatching {
        val docRef = collection.document()
        val withId = appointment.copy(
            id = docRef.id,
            lastUpdatedBy = appointment.patientId
        )
        docRef.set(withId.toMap()).await()
        withId
    }

    // ─── verificar si hay un conflicto en el agendamiento de citas ────────────────────────────────────────
    // Verifica si el doctor ya tiene una cita dentro de un intervalo de ±30 min
    // (duración promedio de una consulta médica)
    suspend fun hasConflictingAppointment(doctorId: String, dateTime: Timestamp): Result<Boolean> = runCatching {
        try {
            val appointmentDurationMs = 30 * 60 * 1000L // 30 minutos en milisegundos
            val requestedMs = dateTime.toDate().time

            // Ventana de conflicto: desde 30 min antes hasta 30 min después
            val windowStart = Timestamp(java.util.Date(requestedMs - appointmentDurationMs))
            val windowEnd   = Timestamp(java.util.Date(requestedMs + appointmentDurationMs))

            val snapshot = collection
                .whereEqualTo("doctorId", doctorId)
                .whereGreaterThanOrEqualTo("dateTime", windowStart)
                .whereLessThanOrEqualTo("dateTime", windowEnd)
                .get()
                .await()

            // Filtrar localmente por estado activo (Pendiente o Confirmada)
            val hasConflict = snapshot.documents.any { doc ->
                val status = doc.getString("status")
                status == AppointmentStatus.PENDING.name || status == AppointmentStatus.CONFIRMED.name
            }
            hasConflict
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    // ─── obtener las citas para el paciente (realtime) ──────────────────────────────
    fun getPatientAppointmentsFlow(patientId: String): Flow<List<Appointment>> = callbackFlow {
        val listener = collection
            .whereEqualTo("patientId", patientId)
            .orderBy("dateTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Appointment.fromMap(it, doc.id) }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    // ─── obtener las citas para el doctor (realtime) ───────────────────────────────
    fun getDoctorAppointmentsFlow(doctorId: String): Flow<List<Appointment>> = callbackFlow {
        val listener = collection
            .whereEqualTo("doctorId", doctorId)
            .orderBy("dateTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Appointment.fromMap(it, doc.id) }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    // ─── obtener todas las citas pendientes ─────────────────────────────────────────────
    fun getPendingAppointmentsFlow(userId: String, isDoctor: Boolean): Flow<List<Appointment>> = callbackFlow {
        val field = if (isDoctor) "doctorId" else "patientId"
        val now = Timestamp.now()
        val listener = collection
            .whereEqualTo(field, userId)
            .whereIn("status", listOf(AppointmentStatus.PENDING.name, AppointmentStatus.CONFIRMED.name))
            .whereGreaterThanOrEqualTo("dateTime", now)
            .orderBy("dateTime", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Appointment.fromMap(it, doc.id) }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    // ─── obtener todas las citas (Admin) ─────────────────────────────────────────
    fun getAllAppointmentsFlow(): Flow<List<Appointment>> = callbackFlow {
        val listener = collection
            .orderBy("dateTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Appointment.fromMap(it, doc.id) }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    // ─── obtener las citas para recepcionista (realtime) ───────────────────────────────
    fun getReceptionistAppointmentsFlow(doctorIds: List<String>): Flow<List<Appointment>> = callbackFlow {
        if (doctorIds.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val listener = collection
            .whereIn("doctorId", doctorIds)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Appointment.fromMap(it, doc.id) }
                }?.sortedByDescending { it.dateTime.toDate().time } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    // ─── obtener todas las citas pendientes para recepcionista ─────────────────────────────────────────────
    fun getReceptionistPendingAppointmentsFlow(doctorIds: List<String>): Flow<List<Appointment>> = callbackFlow {
        if (doctorIds.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val listener = collection
            .whereIn("doctorId", doctorIds)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val nowMs = Timestamp.now().toDate().time
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Appointment.fromMap(it, doc.id) }
                }?.filter {
                    (it.status == AppointmentStatus.PENDING || it.status == AppointmentStatus.CONFIRMED) &&
                    it.dateTime.toDate().time >= nowMs
                }?.sortedBy { it.dateTime.toDate().time } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    // ─── actualizar el estado de la cita ────────────────────────────────────────────
    suspend fun updateStatus(appointmentId: String, status: AppointmentStatus, updatedBy: String): Result<Unit> = runCatching {
        collection.document(appointmentId).update(
            mapOf(
                "status" to status.name,
                "lastUpdatedBy" to updatedBy,
                "updatedAt" to Timestamp.now()
            )
        ).await()
    }

    // ─── actualizar las notas de la cita (doctor) ────────────────────────────────────
    suspend fun updateNotes(appointmentId: String, notes: String): Result<Unit> = runCatching {
        collection.document(appointmentId).update(
            mapOf(
                "notes" to notes,
                "updatedAt" to Timestamp.now()
            )
        ).await()
    }

    // ─── Get History with Date Filter (Doctor only) ───────────────────────────
    suspend fun getDoctorHistory(
        doctorId: String,
        startDate: Timestamp? = null,
        endDate: Timestamp? = null,
        sortNewest: Boolean = true
    ): Result<List<Appointment>> = runCatching {
        var query: Query = collection.whereEqualTo("doctorId", doctorId)

        if (startDate != null) query = query.whereGreaterThanOrEqualTo("dateTime", startDate)
        if (endDate != null) query = query.whereLessThanOrEqualTo("dateTime", endDate)

        val direction = if (sortNewest) Query.Direction.DESCENDING else Query.Direction.ASCENDING
        query = query.orderBy("dateTime", direction)

        val snapshot = query.get().await()
        snapshot.documents.mapNotNull { doc ->
            doc.data?.let { Appointment.fromMap(it, doc.id) }
        }
    }

    // ─── obtener stats ───────────────────────────────────────────────────────
    suspend fun getStats(userId: String, isDoctor: Boolean): Result<AppointmentStats> = runCatching {
        val field = if (isDoctor) "doctorId" else "patientId"
        val snapshot = collection.whereEqualTo(field, userId).get().await()

        val appointments = snapshot.documents.mapNotNull { doc ->
            doc.data?.let { Appointment.fromMap(it, doc.id) }
        }

        val total = appointments.size
        val pending = appointments.count { it.status == AppointmentStatus.PENDING }
        val confirmed = appointments.count { it.status == AppointmentStatus.CONFIRMED }
        val completed = appointments.count { it.status == AppointmentStatus.COMPLETED }
        val cancelled = appointments.count { it.status == AppointmentStatus.CANCELLED }

        AppointmentStats(
            total = total,
            pending = pending,
            confirmed = confirmed,
            completed = completed,
            cancelled = cancelled,
            completionRate = if (total > 0) completed.toFloat() / total * 100 else 0f,
            cancellationRate = if (total > 0) cancelled.toFloat() / total * 100 else 0f
        )
    }

    // ─── obtener stats para recepcionista ───────────────────────────────────────────────────────
    suspend fun getReceptionistStats(doctorIds: List<String>): Result<AppointmentStats> = runCatching {
        if (doctorIds.isEmpty()) return@runCatching AppointmentStats()
        
        val snapshot = collection.whereIn("doctorId", doctorIds).get().await()

        val appointments = snapshot.documents.mapNotNull { doc ->
            doc.data?.let { Appointment.fromMap(it, doc.id) }
        }

        val total = appointments.size
        val pending = appointments.count { it.status == AppointmentStatus.PENDING }
        val confirmed = appointments.count { it.status == AppointmentStatus.CONFIRMED }
        val completed = appointments.count { it.status == AppointmentStatus.COMPLETED }
        val cancelled = appointments.count { it.status == AppointmentStatus.CANCELLED }

        AppointmentStats(
            total = total,
            pending = pending,
            confirmed = confirmed,
            completed = completed,
            cancelled = cancelled,
            completionRate = if (total > 0) completed.toFloat() / total * 100 else 0f,
            cancellationRate = if (total > 0) cancelled.toFloat() / total * 100 else 0f
        )
    }

    // ─── obtener las próximas citas para recibir recordatorios. ───────────────────
    suspend fun getAppointmentsNeedingReminder(): Result<List<Appointment>> = runCatching {
        val now = Timestamp.now()
        // Consulta sencilla por fecha para evitar requisitos de índice complejos durante el desarrollo.
        val snapshot = collection
            .whereGreaterThanOrEqualTo("dateTime", now)
            .get().await()

        snapshot.documents.mapNotNull { doc ->
            doc.data?.let { Appointment.fromMap(it, doc.id) }
        }
    }

    // ─── Marcar recordatorios especificos como enviados ────────────────────────────────────
    suspend fun markReminderAsSent(appointmentId: String, field: String) {
        runCatching {
            collection.document(appointmentId).update(field, true).await()
        }
    }

    // ─── Obtener las citas de los pacientes con los estados cambiados ──────────────────
    // Usado por StatusChangeWorker y AppointmentViewModel para notificar al paciente
    // cuando el doctor confirma, cancela o completa una cita.
    suspend fun getPatientRecentStatusChanges(
        patientId: String,
        since: Timestamp
    ): Result<List<Appointment>> = runCatching {
        val snapshot = collection
            .whereEqualTo("patientId", patientId)
            .whereGreaterThan("updatedAt", since)
            .get().await()

        snapshot.documents
            .mapNotNull { doc -> doc.data?.let { Appointment.fromMap(it, doc.id) } }
            .filter {
                it.status == AppointmentStatus.CONFIRMED ||
                it.status == AppointmentStatus.CANCELLED ||
                it.status == AppointmentStatus.COMPLETED
            }
    }

    // ─── Obtener el correo/email de los pacientes para enviar recordatorios por correo electronico ────────────────────────────────
    suspend fun getPatientEmail(patientId: String): String? = runCatching {
        val doc = db.collection("users").document(patientId).get().await()
        doc.getString("email")?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
