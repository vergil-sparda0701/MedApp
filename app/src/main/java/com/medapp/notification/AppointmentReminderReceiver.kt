package com.medapp.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

class AppointmentReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Recordatorio de Cita"
        val body = intent.getStringExtra("body") ?: "Tienes una cita programada"
        val notificationId = intent.getIntExtra("notificationId", System.currentTimeMillis().toInt())

        // 1. Mostrar notificación push
        NotificationHelper.showAppointmentReminder(context, title, body, notificationId)

        // 2. Enviar email (si hay datos disponibles)
        val patientEmail = intent.getStringExtra("patientEmail") ?: ""
        if (patientEmail.isNotBlank()) {
            val patientName = intent.getStringExtra("patientName") ?: ""
            val doctorName = intent.getStringExtra("doctorName") ?: ""
            val appointmentTime = intent.getLongExtra("appointmentDate", 0)
            val reason = intent.getStringExtra("reason") ?: ""
            val reminderTypeStr = intent.getStringExtra("reminderType") ?: ""

            val reminderType = try {
                EmailService.ReminderType.valueOf(reminderTypeStr)
            } catch (e: Exception) {
                EmailService.ReminderType.HOURS
            }

            if (appointmentTime > 0) {
                // Usamos GlobalScope o un scope de corta vida para el receiver
                CoroutineScope(Dispatchers.IO).launch {
                    EmailService.sendReminderEmail(
                        toEmail = patientEmail,
                        patientName = patientName,
                        doctorName = doctorName,
                        appointmentDate = Date(appointmentTime),
                        reason = reason,
                        reminderType = reminderType
                    )
                }
            }
        }
    }
}
