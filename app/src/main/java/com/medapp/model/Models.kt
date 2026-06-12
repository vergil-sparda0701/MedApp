package com.medapp.model

import com.google.firebase.Timestamp

// crear roles de usuarios
enum class UserRole { PATIENT, DOCTOR, ADMIN, RECEPTIONIST }

// crea el modelo de usuario
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: UserRole = UserRole.PATIENT,
    val specialty: String = "",       // para doctores
    val assignedDoctorIds: List<String> = emptyList(),// para recepcionistas
    val fcmToken: String = "",
    val createdAt: Timestamp = Timestamp.now()
) {
    // estructura de datos para guardar los datos del en firebase
    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "name" to name,
        "email" to email,
        "phone" to phone,
        "role" to role.name,
        "specialty" to specialty,
        "assignedDoctorIds" to assignedDoctorIds,
        "fcmToken" to fcmToken,
        "createdAt" to createdAt
    )

    companion object {
        // recibe los datos de firestore y los combierte en un objeto user
        fun fromMap(map: Map<String, Any?>): User = User(
            uid = map["uid"] as? String ?: "",
            name = map["name"] as? String ?: "",
            email = map["email"] as? String ?: "",
            phone = map["phone"] as? String ?: "",
            role = UserRole.valueOf(map["role"] as? String ?: "PATIENT"),
            specialty = map["specialty"] as? String ?: "",
            assignedDoctorIds = (map["assignedDoctorIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            fcmToken = map["fcmToken"] as? String ?: "",
            createdAt = map["createdAt"] as? Timestamp ?: Timestamp.now()
        )
    }
}

// estado de la cita medica
enum class AppointmentStatus {
    PENDING,
    CONFIRMED,
    COMPLETED,
    CANCELLED;

    //cambia el enum class a un lenguaje mas natural para la UI
    fun displayName(): String = when (this) {
        PENDING -> "Pendiente"
        CONFIRMED -> "Confirmada"
        COMPLETED -> "Completada"
        CANCELLED -> "Cancelada"
    }
}

// crear modelo para las citas
data class Appointment(
    val id: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val patientEmail: String = "",
    val patientPhone: String = "",
    val doctorId: String = "",
    val doctorName: String = "",
    val doctorSpecialty: String = "",
    val doctorPhone: String = "",
    val dateTime: Timestamp = Timestamp.now(),
    val reason: String = "",
    val notes: String = "",
    val status: AppointmentStatus = AppointmentStatus.PENDING,
    val isReminder3dSent: Boolean = false,
    val isReminder2dSent: Boolean = false,
    val isReminder1dSent: Boolean = false,
    val isReminderHoursSent: Boolean = false,
    val lastUpdatedBy: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "patientId" to patientId,
        "patientName" to patientName,
        "patientEmail" to patientEmail,
        "patientPhone" to patientPhone,
        "doctorId" to doctorId,
        "doctorName" to doctorName,
        "doctorSpecialty" to doctorSpecialty,
        "doctorPhone" to doctorPhone,
        "dateTime" to dateTime,
        "reason" to reason,
        "notes" to notes,
        "status" to status.name,
        "isReminder3dSent" to isReminder3dSent,
        "isReminder2dSent" to isReminder2dSent,
        "isReminder1dSent" to isReminder1dSent,
        "isReminderHoursSent" to isReminderHoursSent,
        "lastUpdatedBy" to lastUpdatedBy,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>, id: String = ""): Appointment = Appointment(
            id = id.ifEmpty { map["id"] as? String ?: "" },
            patientId = map["patientId"] as? String ?: "",
            patientName = map["patientName"] as? String ?: "",
            patientEmail = map["patientEmail"] as? String ?: "",
            patientPhone = map["patientPhone"] as? String ?: "",
            doctorId = map["doctorId"] as? String ?: "",
            doctorName = map["doctorName"] as? String ?: "",
            doctorSpecialty = map["doctorSpecialty"] as? String ?: "",
            doctorPhone = map["doctorPhone"] as? String ?: "",
            dateTime = map["dateTime"] as? Timestamp ?: Timestamp.now(),
            reason = map["reason"] as? String ?: "",
            notes = map["notes"] as? String ?: "",
            status = AppointmentStatus.valueOf(map["status"] as? String ?: "PENDING"),
            isReminder3dSent = map["isReminder3dSent"] as? Boolean ?: false,
            isReminder2dSent = map["isReminder2dSent"] as? Boolean ?: false,
            isReminder1dSent = map["isReminder1dSent"] as? Boolean ?: false,
            isReminderHoursSent = map["isReminderHoursSent"] as? Boolean ?: false,
            lastUpdatedBy = map["lastUpdatedBy"] as? String ?: "",
            createdAt = map["createdAt"] as? Timestamp ?: Timestamp.now(),
            updatedAt = map["updatedAt"] as? Timestamp ?: Timestamp.now()
        )
    }
}

// modelo para las estadisticas
data class AppointmentStats(
    val total: Int = 0,
    val pending: Int = 0,
    val confirmed: Int = 0,
    val completed: Int = 0,
    val cancelled: Int = 0,
    val completionRate: Float = 0f,
    val cancellationRate: Float = 0f
)

// ─── Notification Model ───────────────────────────────────────────────────────
data class AppNotification(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val isRead: Boolean = false,
    val relatedId: String = "" // Optional ID (e.g. appointmentId)
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "userId" to userId,
        "title" to title,
        "message" to message,
        "timestamp" to timestamp,
        "isRead" to isRead,
        "relatedId" to relatedId
    )

    companion object {
        fun fromMap(map: Map<String, Any?>, id: String = ""): AppNotification = AppNotification(
            id = id.ifEmpty { map["id"] as? String ?: "" },
            userId = map["userId"] as? String ?: "",
            title = map["title"] as? String ?: "",
            message = map["message"] as? String ?: "",
            timestamp = map["timestamp"] as? Timestamp ?: Timestamp.now(),
            isRead = map["isRead"] as? Boolean ?: false,
            relatedId = map["relatedId"] as? String ?: ""
        )
    }
}
