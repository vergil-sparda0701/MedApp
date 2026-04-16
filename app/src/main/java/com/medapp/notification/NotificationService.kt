package com.medapp.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.medapp.repository.AppointmentRepository
import com.medapp.repository.AuthRepository
import com.medapp.viewmodel.buildNotificationContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(notificationId, notification)
    }

    // ─── Worker: recordatorio 24h antes de la cita ────────────────────────────
    fun scheduleReminderCheck(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<ReminderWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "appointment_reminder_check",
            ExistingPeriodicWorkPolicy.KEEP,
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

            appointments.forEach { appointment ->
                // Notify patient
                NotificationHelper.showAppointmentReminder(
                    context,
                    title = "Recordatorio de Cita 🏥",
                    body = "Tienes una cita con Dr. ${appointment.doctorName} " +
                            "mañana a las ${formatHour(appointment.dateTime.toDate())}. " +
                            "Motivo: ${appointment.reason}",
                    notificationId = appointment.id.hashCode()
                )
                // Mark as reminder sent
                appointmentRepo.markReminderSent(appointment.id)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
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
                val (title, body) = buildNotificationContent(appointment)
                NotificationHelper.showAppointmentReminder(
                    context = context,
                    title = title,
                    body = body,
                    // ID único por cita + estado para no sobreescribir otras notificaciones
                    notificationId = (appointment.id + appointment.status.name).hashCode()
                )
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
