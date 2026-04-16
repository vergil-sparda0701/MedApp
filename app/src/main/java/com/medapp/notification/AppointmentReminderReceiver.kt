package com.medapp.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AppointmentReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Recordatorio de Cita"
        val body = intent.getStringExtra("body") ?: "Tienes una cita programada"
        val notificationId = intent.getIntExtra("notificationId", System.currentTimeMillis().toInt())

        NotificationHelper.showAppointmentReminder(context, title, body, notificationId)
    }
}
