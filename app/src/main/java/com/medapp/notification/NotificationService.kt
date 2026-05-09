package com.medapp.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.medapp.model.Appointment
import com.medapp.model.AppointmentStatus
import com.medapp.notification.EmailService.ReminderType
import com.medapp.notification.EmailService.StatusEmailType
import com.medapp.repository.AppointmentRepository
import com.medapp.repository.AuthRepository
import com.medapp.viewmodel.buildNotificationContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

// ─── Notification Channel IDs ─────────────────────────────────────────────────
const val CHANNEL_ID = "med_appointments"
const val CHANNEL_NAME = "Recordatorios de Citas"

// ─── Notification Helper ──────────────────────────────────────────────────────
object NotificationHelper {
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Recordatorios automáticos para citas médicas"
                enableVibration(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun showAppointmentReminder(
        context: Context,
        title: String,
        body: String,
        notificationId: Int = System.currentTimeMillis().toInt()
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.medapp.R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        manager.notify(notificationId, notification)
    }

    // ─── Worker: recordatorio 24h antes de la cita ────────────────────────────
    @RequiresApi(Build.VERSION_CODES.GINGERBREAD)
    fun scheduleReminderCheck(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<ReminderWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "appointment_reminder_check",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    // ─── Worker: detectar cambios de estado para el paciente (background) ─────
    fun scheduleStatusChangeCheck(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<StatusChangeWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "appointment_status_change_check",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    // ─── Trigger an immediate reminder check (one-time) ────────────────────────
    fun triggerImmediateReminderCheck(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }

    fun scheduleExactAlarm(
        context: Context,
        timeInMillis: Long,
        title: String,
        body: String,
        notificationId: Int,
        patientEmail: String = "",
        patientName: String = "",
        doctorName: String = "",
        appointmentDate: Long = 0,
        reason: String = "",
        reminderType: String = ""
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AppointmentReminderReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("body", body)
            putExtra("notificationId", notificationId)
            putExtra("patientEmail", patientEmail)
            putExtra("patientName", patientName)
            putExtra("doctorName", doctorName)
            putExtra("appointmentDate", appointmentDate)
            putExtra("reason", reason)
            putExtra("reminderType", reminderType)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                pendingIntent
            )
        }
    }

    fun scheduleAllStageReminders(context: Context, appointment: Appointment) {
        val appointmentTime = appointment.dateTime.toDate().time
        val now = System.currentTimeMillis()

        val patientName = appointment.patientName
        val patientEmail = appointment.patientEmail
        val doctorName = appointment.doctorName
        val reason = appointment.reason
        val appId = appointment.id

        // 3 Hours (180 min)
        val time3h = appointmentTime - (180 * 60 * 1000)
        if (time3h > now) {
            scheduleExactAlarm(
                context, time3h, "Recordatorio de Cita 🏥",
                "Tu cita con Dr. $doctorName es en unas horas.", (appId + "3h").hashCode(),
                patientEmail, patientName, doctorName, appointmentTime, reason, "HOURS"
            )
        }

        // 1 Day (24h)
        val time1d = appointmentTime - (24 * 60 * 60 * 1000)
        if (time1d > now) {
            scheduleExactAlarm(
                context, time1d, "Recordatorio de Cita 🏥",
                "Recuerda tu cita de mañana con Dr. $doctorName.", (appId + "1d").hashCode(),
                patientEmail, patientName, doctorName, appointmentTime, reason, "ONE_DAY"
            )
        }

        // 2 Days (48h)
        val time2d = appointmentTime - (2 * 24 * 60 * 60 * 1000L)
        if (time2d > now) {
            scheduleExactAlarm(
                context, time2d, "Recordatorio de Cita 🏥",
                "Tienes una cita programada en 2 días con Dr. $doctorName.", (appId + "2d").hashCode(),
                patientEmail, patientName, doctorName, appointmentTime, reason, "TWO_DAYS"
            )
        }

        // 3 Days (72h)
        val time3d = appointmentTime - (3 * 24 * 60 * 60 * 1000L)
        if (time3d > now) {
            scheduleExactAlarm(
                context, time3d, "Recordatorio de Cita 🏥",
                "Tienes una cita programada en 3 días con Dr. $doctorName.", (appId + "3d").hashCode(),
                patientEmail, patientName, doctorName, appointmentTime, reason, "THREE_DAYS"
            )
        }
    }
}

// ─── WorkManager Worker: Recordatorio 24h ────────────────────────────────────
class ReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val appointmentRepo = AppointmentRepository()

    override suspend fun doWork(): Result {
        return try {
            val appointments = appointmentRepo.getAppointmentsNeedingReminder()
                .getOrDefault(emptyList())

            val nowSeconds = Timestamp.now().seconds
            val currentUser = FirebaseAuth.getInstance().currentUser ?: return Result.success()

            appointments
                .filter { it.patientId == currentUser.uid } // Solo citas del usuario actual
                .filter { it.status == AppointmentStatus.PENDING || it.status == AppointmentStatus.CONFIRMED } // Filtro manual de estado
                .forEach { appointment ->
                    // Cálculo basado en segundos UTC para evitar errores de zona horaria
                    val diffMinutes = (appointment.dateTime.seconds - nowSeconds) / 60

                    when {
                        // Recordatorio: Unas horas antes (3h = 180 min)
                        diffMinutes in 0..180 && !appointment.isReminderHoursSent -> {
                            sendNotification(appointment, "Tu cita es en unas horas", "isReminderHoursSent", ReminderType.HOURS)
                        }
                        // Recordatorio: 1 día antes (24h = 1440 min)
                        diffMinutes in 181..1440 && !appointment.isReminder1dSent -> {
                            sendNotification(appointment, "Tienes una cita mañana", "isReminder1dSent", ReminderType.ONE_DAY)
                        }
                        // Recordatorio: 2 días antes (48h = 2880 min)
                        diffMinutes in 1441..2880 && !appointment.isReminder2dSent -> {
                            sendNotification(appointment, "Tienes una cita en 2 días", "isReminder2dSent", ReminderType.TWO_DAYS)
                        }
                        // Recordatorio: 3 días antes (72h = 4320 min)
                        diffMinutes in 2881..4320 && !appointment.isReminder3dSent -> {
                            sendNotification(appointment, "Tienes una cita en 3 días", "isReminder3dSent", ReminderType.THREE_DAYS)
                        }
                    }
                }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun sendNotification(
        appointment: Appointment,
        bodyPrefix: String,
        flagField: String,
        reminderType: ReminderType
    ) {
        // 1. Notificación push local
        NotificationHelper.showAppointmentReminder(
            context,
            title = "Recordatorio de Cita 🏥",
            body = "$bodyPrefix a las ${formatHour(appointment.dateTime.toDate())} con Dr. ${appointment.doctorName}. " +
                    "Motivo: ${appointment.reason}",
            notificationId = (appointment.id + flagField).hashCode()
        )
        // 2. Email via Resend: usar patientEmail del documento; fallback a Firestore para citas antiguas
        val email = appointment.patientEmail.ifBlank {
            appointmentRepo.getPatientEmail(appointment.patientId) ?: ""
        }
        if (email.isNotBlank()) {
            withContext(Dispatchers.IO) {
                EmailService.sendReminderEmail(
                    toEmail         = email,
                    patientName     = appointment.patientName,
                    doctorName      = appointment.doctorName,
                    appointmentDate = appointment.dateTime.toDate(),
                    reason          = appointment.reason,
                    reminderType    = reminderType
                )
            }
        }
        // 3. Actualizar el flag correspondiente en Firestore
        appointmentRepo.markReminderAsSent(appointment.id, flagField)
    }

    private fun formatHour(date: java.util.Date): String {
        val cal = java.util.Calendar.getInstance().apply { time = date }
        return String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    }
}

// ─── WorkManager Worker: Cambios de estado de citas (background) ─────────────
// Se ejecuta cada 15 minutos. Consulta las citas del paciente logueado cuyo
// campo `updatedAt` cayó en los últimos 20 minutos (15 min + 5 min buffer) y
// muestra una notificación local si el estado cambió a CONFIRMED/CANCELLED/COMPLETED.
class StatusChangeWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val appointmentRepo = AppointmentRepository()

    override suspend fun doWork(): Result {
        // Solo aplica a pacientes. Si no hay sesión activa, salir silenciosamente.
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return Result.success()

        // Ventana de tiempo: últimos 20 min (intervalo 15 + buffer 5)
        val since = Timestamp(Timestamp.now().seconds - 1200, 0)

        return try {
            val changed = appointmentRepo
                .getPatientRecentStatusChanges(currentUser.uid, since)
                .getOrDefault(emptyList())

            changed.forEach { appointment ->
                val isNotMe = appointment.lastUpdatedBy.isNotEmpty() && appointment.lastUpdatedBy != currentUser.uid
                if (isNotMe) {
                    val (title, body) = buildNotificationContent(appointment)
                    // 1. Notificación push local
                    NotificationHelper.showAppointmentReminder(
                        context = context,
                        title = title,
                        body = body,
                        // ID único por cita + estado para no sobreescribir otras notificaciones
                        notificationId = (appointment.id + appointment.status.name).hashCode()
                    )
                    // 2. Email via Resend: usar patientEmail del documento; fallback a Firestore para citas antiguas
                    val email = appointment.patientEmail.ifBlank {
                        appointmentRepo.getPatientEmail(appointment.patientId) ?: ""
                    }
                    val statusEmailType = when (appointment.status) {
                        AppointmentStatus.CONFIRMED  -> StatusEmailType.CONFIRMED
                        AppointmentStatus.CANCELLED  -> StatusEmailType.CANCELLED
                        AppointmentStatus.COMPLETED  -> StatusEmailType.COMPLETED
                        else                         -> null
                    }
                    if (email.isNotBlank() && statusEmailType != null) {
                        withContext(Dispatchers.IO) {
                            EmailService.sendStatusChangeEmail(
                                toEmail         = email,
                                patientName     = appointment.patientName,
                                doctorName      = appointment.doctorName,
                                appointmentDate = appointment.dateTime.toDate(),
                                newStatus       = statusEmailType
                            )
                        }
                    }
                    // 3. Si la cita fue confirmada, programar sus recordatorios exactos
                    if (appointment.status == AppointmentStatus.CONFIRMED) {
                        NotificationHelper.scheduleAllStageReminders(context, appointment)
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

// ─── FCM Service ──────────────────────────────────────────────────────────────
class MedFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Save token to Firestore for the current user
        val authRepo = AuthRepository()
        authRepo.currentUser?.let { user ->
            CoroutineScope(Dispatchers.IO).launch {
                authRepo.updateFcmToken(user.uid, token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: "MedApp"
        val body = message.notification?.body ?: "Tienes una notificación"
        NotificationHelper.showAppointmentReminder(this, title, body)
    }
}
