package com.medapp.notification

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Resend Email Service ─────────────────────────────────────────────────────
object EmailService {

    private const val TAG = "EmailService"
    private const val RESEND_API_URL = "https://api.resend.com/emails"
    private const val API_KEY = "re_CD7RHh1z_2yiCH3433oLtoj3nYkybkbgL"

    // Dirección "from" verificada en Resend
    private const val FROM_ADDRESS = "MedApp <info@medapp.lat>"

    // ─── Recordatorio de proximidad de cita ───────────────────────────────────
    fun sendReminderEmail(
        toEmail: String,
        patientName: String,
        doctorName: String,
        appointmentDate: Date,
        reason: String,
        reminderType: ReminderType
    ) {
        val dateFormatted = SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", Locale("es")).format(appointmentDate)
        val timeFormatted = SimpleDateFormat("HH:mm", Locale("es")).format(appointmentDate)

        val subject = when (reminderType) {
            ReminderType.HOURS -> "⏰ Tu cita es hoy en unas horas — MedApp"
            ReminderType.ONE_DAY -> "📅 Recordatorio: Tienes una cita mañana — MedApp"
            ReminderType.TWO_DAYS -> "📅 Recordatorio: Cita en 2 días — MedApp"
            ReminderType.THREE_DAYS -> "📅 Recordatorio: Cita en 3 días — MedApp"
        }

        val timeLabel = when (reminderType) {
            ReminderType.HOURS -> "en unas horas"
            ReminderType.ONE_DAY -> "mañana"
            ReminderType.TWO_DAYS -> "en 2 días"
            ReminderType.THREE_DAYS -> "en 3 días"
        }

        val html = buildReminderHtml(patientName, doctorName, dateFormatted, timeFormatted, reason, timeLabel)
        postToResend(toEmail, subject, html)
    }

    // ─── Confirmación inmediata al agendar ─────────────────────────────────────
    fun sendBookingConfirmationEmail(
        toEmail: String,
        patientName: String,
        doctorName: String,
        doctorSpecialty: String,
        appointmentDate: Date,
        reason: String
    ) {
        val dateFormatted = SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", Locale("es")).format(appointmentDate)
        val timeFormatted = SimpleDateFormat("HH:mm", Locale("es")).format(appointmentDate)
        val html = buildBookingConfirmationHtml(patientName, doctorName, doctorSpecialty, dateFormatted, timeFormatted, reason)
        postToResend(toEmail, "✅ Cita agendada correctamente — MedApp", html)
    }

    // ─── Cambio de estado de cita ─────────────────────────────────────────────
    fun sendStatusChangeEmail(
        toEmail: String,
        patientName: String,
        doctorName: String,
        appointmentDate: Date,
        newStatus: StatusEmailType
    ) {
        val dateFormatted = SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", Locale("es")).format(appointmentDate)
        val timeFormatted = SimpleDateFormat("HH:mm", Locale("es")).format(appointmentDate)

        val (subject, statusLabel, statusColor, statusIcon, statusMessage) = when (newStatus) {
            StatusEmailType.CONFIRMED -> StatusContent(
                subject = "✅ Tu cita ha sido confirmada — MedApp",
                label = "Confirmada",
                color = "#10b981",
                icon = "✅",
                message = "Tu cita ha sido <strong>confirmada</strong> por el médico. ¡Te esperamos!"
            )
            StatusEmailType.CANCELLED -> StatusContent(
                subject = "❌ Tu cita ha sido cancelada — MedApp",
                label = "Cancelada",
                color = "#ef4444",
                icon = "❌",
                message = "Lamentablemente tu cita ha sido <strong>cancelada</strong>. Por favor contáctanos para reprogramarla."
            )
            StatusEmailType.COMPLETED -> StatusContent(
                subject = "🎉 Tu cita ha sido completada — MedApp",
                label = "Completada",
                color = "#6366f1",
                icon = "🎉",
                message = "Tu cita ha sido marcada como <strong>completada</strong>. Esperamos que tu consulta haya sido satisfactoria."
            )
        }

        val html = buildStatusHtml(
            patientName, doctorName, dateFormatted, timeFormatted,
            statusLabel, statusColor, statusIcon, statusMessage
        )
        postToResend(toEmail, subject, html)
    }

    // ─── HTTP POST a Resend ────────────────────────────────────────────────────
    private fun postToResend(toEmail: String, subject: String, html: String) {
        try {
            val body = JSONObject().apply {
                put("from", FROM_ADDRESS)
                put("to", JSONArray().put(toEmail))
                put("subject", subject)
                put("html", html)
            }.toString()

            Log.d(TAG, "➡ Enviando email | to=$toEmail | subject=$subject | from=$FROM_ADDRESS")

            val url = URL(RESEND_API_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $API_KEY")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            connection.outputStream.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                    writer.write(body)
                    writer.flush()
                }
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseBody = connection.inputStream.bufferedReader().readText()
                Log.d(TAG, "✅ Email enviado | HTTP $responseCode | to=$toEmail | response=$responseBody")
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "(sin cuerpo)"
                Log.e(TAG, "❌ Resend rechazó el email | HTTP $responseCode | to=$toEmail | error=$errorBody")
            }
            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "💥 Excepción enviando email a $toEmail | ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    // ─── HTML: Confirmación de reserva ────────────────────────────────────────
    private fun buildBookingConfirmationHtml(
        patientName: String,
        doctorName: String,
        specialty: String,
        date: String,
        time: String,
        reason: String
    ): String = """
        <!DOCTYPE html>
        <html lang="es">
        <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Cita Agendada</title></head>
        <body style="margin:0;padding:0;background-color:#f0f4f8;font-family:'Segoe UI',Helvetica,Arial,sans-serif;">
          <table width="100%" cellpadding="0" cellspacing="0" style="background-color:#f0f4f8;padding:40px 20px;">
            <tr><td align="center">
              <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);">
                <!-- Header -->
                <tr><td style="background:linear-gradient(135deg,#10b981 0%,#059669 100%);padding:36px 40px;text-align:center;">
                  <p style="margin:0 0 8px;font-size:36px;">✅</p>
                  <h1 style="margin:0;color:#ffffff;font-size:24px;font-weight:700;letter-spacing:-0.5px;">¡Cita Agendada!</h1>
                  <p style="margin:8px 0 0;color:rgba(255,255,255,0.85);font-size:14px;">MedApp · Confirmación de Reserva</p>
                </td></tr>
                <!-- Body -->
                <tr><td style="padding:40px;">
                  <p style="margin:0 0 24px;color:#374151;font-size:16px;">Hola, <strong>$patientName</strong> 👋</p>
                  <p style="margin:0 0 28px;color:#6b7280;font-size:15px;line-height:1.6;">
                    Tu cita médica ha sido <strong style="color:#059669;">agendada exitosamente</strong>. Aquí están los detalles:
                  </p>
                  <!-- Info card -->
                  <table width="100%" cellpadding="0" cellspacing="0" style="background:#f0fdf8;border:1px solid #a7f3d0;border-radius:12px;overflow:hidden;margin-bottom:28px;">
                    <tr><td style="padding:24px;">
                      <table width="100%" cellpadding="0" cellspacing="8">
                        <tr>
                          <td style="color:#6b7280;font-size:13px;width:130px;padding:6px 0;">👨‍⚕️ Médico</td>
                          <td style="color:#111827;font-size:14px;font-weight:600;padding:6px 0;">Dr. $doctorName</td>
                        </tr>
                        <tr>
                          <td style="color:#6b7280;font-size:13px;padding:6px 0;">🩺 Especialidad</td>
                          <td style="color:#111827;font-size:14px;padding:6px 0;">$specialty</td>
                        </tr>
                        <tr>
                          <td style="color:#6b7280;font-size:13px;padding:6px 0;">📅 Fecha</td>
                          <td style="color:#111827;font-size:14px;font-weight:600;padding:6px 0;">$date</td>
                        </tr>
                        <tr>
                          <td style="color:#6b7280;font-size:13px;padding:6px 0;">⏰ Hora</td>
                          <td style="color:#111827;font-size:14px;font-weight:600;padding:6px 0;">$time</td>
                        </tr>
                        <tr>
                          <td style="color:#6b7280;font-size:13px;padding:6px 0;">📋 Motivo</td>
                          <td style="color:#111827;font-size:14px;padding:6px 0;">$reason</td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                  <!-- Status badge -->
                  <p style="text-align:center;margin:0 0 8px;">
                    <span style="display:inline-block;background:#fef3c7;color:#d97706;border:1px solid #fcd34d;border-radius:999px;padding:6px 16px;font-size:13px;font-weight:600;">
                      ⏳ &nbsp; Pendiente de confirmación por el médico
                    </span>
                  </p>
                  <p style="margin:16px 0 0;color:#9ca3af;font-size:13px;text-align:center;">Te notificaremos cuando el médico confirme tu cita.</p>
                </td></tr>
                <!-- Footer -->
                <tr><td style="background:#f9fafb;padding:20px 40px;text-align:center;border-top:1px solid #f3f4f6;">
                  <p style="margin:0;color:#9ca3af;font-size:12px;">© 2026 MedApp · Este es un mensaje automático, por favor no respondas a este email.</p>
                </td></tr>
              </table>
            </td></tr>
          </table>
        </body>
        </html>
    """.trimIndent()

    // ─── HTML: Recordatorio de cita ───────────────────────────────────────────
    private fun buildReminderHtml(
        patientName: String,
        doctorName: String,
        date: String,
        time: String,
        reason: String,
        timeLabel: String
    ): String = """
        <!DOCTYPE html>
        <html lang="es">
        <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Recordatorio de Cita</title></head>
        <body style="margin:0;padding:0;background-color:#f0f4f8;font-family:'Segoe UI',Helvetica,Arial,sans-serif;">
          <table width="100%" cellpadding="0" cellspacing="0" style="background-color:#f0f4f8;padding:40px 20px;">
            <tr><td align="center">
              <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);">
                <!-- Header -->
                <tr><td style="background:linear-gradient(135deg,#6366f1 0%,#4f46e5 100%);padding:36px 40px;text-align:center;">
                  <p style="margin:0 0 8px;font-size:32px;">🏥</p>
                  <h1 style="margin:0;color:#ffffff;font-size:24px;font-weight:700;letter-spacing:-0.5px;">MedApp</h1>
                  <p style="margin:8px 0 0;color:rgba(255,255,255,0.85);font-size:14px;">Recordatorio de Cita Médica</p>
                </td></tr>
                <!-- Body -->
                <tr><td style="padding:40px;">
                  <p style="margin:0 0 24px;color:#374151;font-size:16px;">Hola, <strong>$patientName</strong> 👋</p>
                  <p style="margin:0 0 28px;color:#6b7280;font-size:15px;line-height:1.6;">
                    Te recordamos que tienes una cita médica programada <strong style="color:#4f46e5;">$timeLabel</strong>.
                  </p>
                  <!-- Info card -->
                  <table width="100%" cellpadding="0" cellspacing="0" style="background:#f8faff;border:1px solid #e0e7ff;border-radius:12px;overflow:hidden;margin-bottom:28px;">
                    <tr><td style="padding:24px;">
                      <table width="100%" cellpadding="0" cellspacing="8">
                        <tr>
                          <td style="color:#6b7280;font-size:13px;width:130px;padding:6px 0;">👨‍⚕️ Médico</td>
                          <td style="color:#111827;font-size:14px;font-weight:600;padding:6px 0;">Dr. $doctorName</td>
                        </tr>
                        <tr>
                          <td style="color:#6b7280;font-size:13px;padding:6px 0;">📅 Fecha</td>
                          <td style="color:#111827;font-size:14px;font-weight:600;padding:6px 0;">$date</td>
                        </tr>
                        <tr>
                          <td style="color:#6b7280;font-size:13px;padding:6px 0;">⏰ Hora</td>
                          <td style="color:#111827;font-size:14px;font-weight:600;padding:6px 0;">$time</td>
                        </tr>
                        <tr>
                          <td style="color:#6b7280;font-size:13px;padding:6px 0;">📋 Motivo</td>
                          <td style="color:#111827;font-size:14px;padding:6px 0;">$reason</td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                  <p style="margin:0 0 8px;color:#9ca3af;font-size:13px;text-align:center;">Por favor, llega con 10 minutos de anticipación.</p>
                </td></tr>
                <!-- Footer -->
                <tr><td style="background:#f9fafb;padding:20px 40px;text-align:center;border-top:1px solid #f3f4f6;">
                  <p style="margin:0;color:#9ca3af;font-size:12px;">© 2026 MedApp · Este es un mensaje automático, por favor no respondas a este email.</p>
                </td></tr>
              </table>
            </td></tr>
          </table>
        </body>
        </html>
    """.trimIndent()

    // ─── HTML: Cambio de estado ────────────────────────────────────────────────
    private fun buildStatusHtml(
        patientName: String,
        doctorName: String,
        date: String,
        time: String,
        statusLabel: String,
        statusColor: String,
        statusIcon: String,
        statusMessage: String
    ): String = """
        <!DOCTYPE html>
        <html lang="es">
        <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Estado de tu Cita</title></head>
        <body style="margin:0;padding:0;background-color:#f0f4f8;font-family:'Segoe UI',Helvetica,Arial,sans-serif;">
          <table width="100%" cellpadding="0" cellspacing="0" style="background-color:#f0f4f8;padding:40px 20px;">
            <tr><td align="center">
              <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);">
                <!-- Header -->
                <tr><td style="background:linear-gradient(135deg,#6366f1 0%,#4f46e5 100%);padding:36px 40px;text-align:center;">
                  <p style="margin:0 0 8px;font-size:32px;">🏥</p>
                  <h1 style="margin:0;color:#ffffff;font-size:24px;font-weight:700;letter-spacing:-0.5px;">MedApp</h1>
                  <p style="margin:8px 0 0;color:rgba(255,255,255,0.85);font-size:14px;">Actualización de tu Cita</p>
                </td></tr>
                <!-- Status badge -->
                <tr><td style="padding:32px 40px 0;text-align:center;">
                  <span style="display:inline-block;background:${statusColor}18;color:$statusColor;border:1px solid ${statusColor}40;border-radius:999px;padding:8px 20px;font-size:14px;font-weight:600;">
                    $statusIcon &nbsp; $statusLabel
                  </span>
                </td></tr>
                <!-- Body -->
                <tr><td style="padding:24px 40px 40px;">
                  <p style="margin:0 0 20px;color:#374151;font-size:16px;">Hola, <strong>$patientName</strong> 👋</p>
                  <p style="margin:0 0 28px;color:#6b7280;font-size:15px;line-height:1.6;">$statusMessage</p>
                  <!-- Info card -->
                  <table width="100%" cellpadding="0" cellspacing="0" style="background:#f8faff;border:1px solid #e0e7ff;border-radius:12px;overflow:hidden;margin-bottom:28px;">
                    <tr><td style="padding:24px;">
                      <table width="100%" cellpadding="0" cellspacing="8">
                        <tr>
                          <td style="color:#6b7280;font-size:13px;width:130px;padding:6px 0;">👨‍⚕️ Médico</td>
                          <td style="color:#111827;font-size:14px;font-weight:600;padding:6px 0;">Dr. $doctorName</td>
                        </tr>
                        <tr>
                          <td style="color:#6b7280;font-size:13px;padding:6px 0;">📅 Fecha</td>
                          <td style="color:#111827;font-size:14px;font-weight:600;padding:6px 0;">$date</td>
                        </tr>
                        <tr>
                          <td style="color:#6b7280;font-size:13px;padding:6px 0;">⏰ Hora</td>
                          <td style="color:#111827;font-size:14px;font-weight:600;padding:6px 0;">$time</td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </td></tr>
                <!-- Footer -->
                <tr><td style="background:#f9fafb;padding:20px 40px;text-align:center;border-top:1px solid #f3f4f6;">
                  <p style="margin:0;color:#9ca3af;font-size:12px;">© 2026 MedApp · Este es un mensaje automático, por favor no respondas a este email.</p>
                </td></tr>
              </table>
            </td></tr>
          </table>
        </body>
        </html>
    """.trimIndent()

    // ─── Data classes internas ────────────────────────────────────────────────
    enum class ReminderType { HOURS, ONE_DAY, TWO_DAYS, THREE_DAYS }
    enum class StatusEmailType { CONFIRMED, CANCELLED, COMPLETED }

    private data class StatusContent(
        val subject: String,
        val label: String,
        val color: String,
        val icon: String,
        val message: String
    )
}
