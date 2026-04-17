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

    // ─── Create Appointment ───────────────────────────────────────────────────
    suspend fun createAppointment(appointment: Appointment): Result<Appointment> = runCatching {
        val docRef = collection.document()
        val withId = appointment.copy(
            id = docRef.id,
            lastUpdatedBy = appointment.patientId
        )
        docRef.set(withId.toMap()).await()
        withId
    }

    // ─── Get Appointments for Patient (realtime) ──────────────────────────────
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

    // ─── Get Appointments for Doctor (realtime) ───────────────────────────────
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

    // ─── Get Pending Appointments ─────────────────────────────────────────────
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

    // ─── Update Appointment Status ────────────────────────────────────────────
    suspend fun updateStatus(appointmentId: String, status: AppointmentStatus, updatedBy: String): Result<Unit> = runCatching {
        collection.document(appointmentId).update(
            mapOf(
                "status" to status.name,
                "lastUpdatedBy" to updatedBy,
                "updatedAt" to Timestamp.now()
            )
        ).await()
    }

    // ─── Update Appointment Notes (doctor) ────────────────────────────────────
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

    // ─── Get Statistics ───────────────────────────────────────────────────────
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

    // ─── Get upcoming appointments for reminder processing ───────────────────
    suspend fun getAppointmentsNeedingReminder(): Result<List<Appointment>> = runCatching {
        val now = Timestamp.now()
        // Simple query by date to avoid complex index requirements during development
        val snapshot = collection
            .whereGreaterThanOrEqualTo("dateTime", now)
            .get().await()

        snapshot.documents.mapNotNull { doc ->
            doc.data?.let { Appointment.fromMap(it, doc.id) }
        }
    }

    // ─── Mark a specific reminder as sent ────────────────────────────────────
    suspend fun markReminderAsSent(appointmentId: String, field: String) {
        runCatching {
            collection.document(appointmentId).update(field, true).await()
        }
    }

    // ─── Get patient appointments with recent status changes ──────────────────
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
}
