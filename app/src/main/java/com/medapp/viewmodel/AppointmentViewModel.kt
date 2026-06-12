package com.medapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.medapp.model.Appointment
import com.medapp.model.AppointmentStats
import com.medapp.model.AppointmentStatus
import com.medapp.model.User
import com.medapp.notification.EmailService
import com.medapp.notification.NotificationHelper
import com.medapp.repository.AppointmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat

sealed class AppointmentResult {
    object Idle : AppointmentResult()
    object Loading : AppointmentResult()
    object Success : AppointmentResult()
    data class Error(val message: String) : AppointmentResult()
}

class AppointmentViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = AppointmentRepository()

    private val _appointments = MutableStateFlow<List<Appointment>>(emptyList())
    val appointments: StateFlow<List<Appointment>> = _appointments.asStateFlow()

    private val _pendingAppointments = MutableStateFlow<List<Appointment>>(emptyList())
    val pendingAppointments: StateFlow<List<Appointment>> = _pendingAppointments.asStateFlow()

    private val _historyAppointments = MutableStateFlow<List<Appointment>>(emptyList())
    val historyAppointments: StateFlow<List<Appointment>> = _historyAppointments.asStateFlow()

    private val _stats = MutableStateFlow<AppointmentStats?>(null)
    val stats: StateFlow<AppointmentStats?> = _stats.asStateFlow()

    private val _operationResult = MutableStateFlow<AppointmentResult>(AppointmentResult.Idle)
    val operationResult: StateFlow<AppointmentResult> = _operationResult.asStateFlow()

    private val _isLoadingHistory = MutableStateFlow(false)
    val isLoadingHistory: StateFlow<Boolean> = _isLoadingHistory.asStateFlow()

    // ─── Load All Appointments ────────────────────────────────────────────────
    // Para pacientes, detecta cambios de estado en tiempo real y muestra
    // notificación local cuando el doctor confirma, cancela o completa una cita.
    fun loadAppointments(userId: String, isDoctor: Boolean) {
        viewModelScope.launch {
            val flow = if (isDoctor)
                repository.getDoctorAppointmentsFlow(userId)
            else
                repository.getPatientAppointmentsFlow(userId)

            // Rastreo de estados anteriores para detección de cambios (solo paciente)
            var isFirstEmission = true
            var previousStatuses = mapOf<String, AppointmentStatus>()

            flow.collect { appointments ->
                if (!isDoctor) {
                    if (!isFirstEmission) {
                        // Detectar cambios de estado respecto a la emisión anterior
                        appointments.forEach { appointment ->
                            val prevStatus = previousStatuses[appointment.id]
                            val newStatus = appointment.status
                            val isRelevantChange = newStatus == AppointmentStatus.CONFIRMED ||
                                    newStatus == AppointmentStatus.CANCELLED ||
                                    newStatus == AppointmentStatus.COMPLETED

                            val isNotMe = appointment.lastUpdatedBy.isNotEmpty() && appointment.lastUpdatedBy != userId
                            if (prevStatus != null && prevStatus != newStatus && isRelevantChange && isNotMe) {
                                showStatusChangeNotification(appointment)
                                
                                val (title, message) = buildNotificationContent(appointment, false)
                                launch(Dispatchers.IO) {
                                    val notificationRepo = com.medapp.repository.NotificationRepository()
                                    notificationRepo.saveNotification(
                                        com.medapp.model.AppNotification(
                                            id = "${appointment.id}_${newStatus.name}",
                                            userId = userId,
                                            title = title,
                                            message = message,
                                            relatedId = appointment.id
                                        )
                                    )
                                }
                            }
                        }
                    }
                    previousStatuses = appointments.associate { it.id to it.status }
                    isFirstEmission = false
                }
                _appointments.value = appointments
            }
        }
    }

    // ─── Cargar citas para recepcionista ─────────────────────────────────────────
    fun loadReceptionistAppointments(doctorIds: List<String>) {
        viewModelScope.launch {
            repository.getReceptionistAppointmentsFlow(doctorIds).collect { appointments ->
                _appointments.value = appointments
            }
        }
    }

    // ─── Muestra notificación local según el nuevo estado ────────────────────
    private fun showStatusChangeNotification(appointment: Appointment) {
        val (title, body) = buildNotificationContent(appointment)
        NotificationHelper.showAppointmentReminder(
            context = getApplication(),
            title = title,
            body = body,
            // ID único por cita+estado para evitar sobreescribir otras notificaciones
            notificationId = (appointment.id + appointment.status.name).hashCode()
        )
        // Si la cita fue confirmada, programar sus recordatorios exactos
        if (appointment.status == AppointmentStatus.CONFIRMED) {
            NotificationHelper.scheduleAllStageReminders(getApplication(), appointment)
        }
    }

    // ─── Cargar citas pendientes ────────────────────────────────────────────
    fun loadPendingAppointments(userId: String, isDoctor: Boolean) {
        viewModelScope.launch {
            repository.getPendingAppointmentsFlow(userId, isDoctor).collect {
                _pendingAppointments.value = it
            }
        }
    }

    // ─── Cargar citas pendientes para recepcionista ─────────────────────────────────────────
    fun loadReceptionistPendingAppointments(doctorIds: List<String>) {
        viewModelScope.launch {
            repository.getReceptionistPendingAppointmentsFlow(doctorIds).collect {
                _pendingAppointments.value = it
            }
        }
    }

    // ─── Citas ─────────────────────────────────────────────────────
    fun bookAppointment(
        patient: User,
        doctor: User,
        dateTime: Timestamp,
        reason: String
    ) {
        viewModelScope.launch {
            _operationResult.value = AppointmentResult.Loading
            
            // Verificar si la fecha y hora seleccionadas son en el pasado
            if (dateTime.toDate().before(java.util.Date())) {
                _operationResult.value = AppointmentResult.Error("No puedes agendar una cita en un horario que ya pasó.")
                return@launch
            }
            
            // Verificar disponibilidad de horario (evitar citas duplicadas con el mismo doctor)
            val conflictResult = repository.hasConflictingAppointment(doctor.uid, dateTime)
            if (conflictResult.isSuccess && conflictResult.getOrDefault(false)) {
                _operationResult.value = AppointmentResult.Error(
                    "El doctor ya tiene una cita agendada cerca de ese horario. " +
                    "Por favor elige una hora con al menos 30 minutos de diferencia."
                )
                return@launch
            }
            
            val appointment = Appointment(
                patientId = patient.uid,
                patientName = patient.name,
                patientEmail = patient.email,
                patientPhone = patient.phone,
                doctorId = doctor.uid,
                doctorName = doctor.name,
                doctorSpecialty = doctor.specialty,
                doctorPhone = doctor.phone,
                dateTime = dateTime,
                reason = reason,
                status = AppointmentStatus.PENDING
            )
            repository.createAppointment(appointment).fold(
                onSuccess = { createdAppointment ->
                    NotificationHelper.scheduleAllStageReminders(getApplication(), createdAppointment)
                    NotificationHelper.triggerImmediateReminderCheck(getApplication())
                    
                    // Guardar notificación de nueva cita en el panel
                    launch(Dispatchers.IO) {
                        val notificationRepo = com.medapp.repository.NotificationRepository()
                        val formattedDate = SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("es", "ES")).format(dateTime.toDate())
                        notificationRepo.saveNotification(
                            com.medapp.model.AppNotification(
                                userId = patient.uid,
                                title = "¡Cita Agendada!",
                                message = "Tu cita con Dr. ${doctor.name} para el $formattedDate ha sido agendada.",
                                relatedId = createdAppointment.id
                            )
                        )
                        notificationRepo.saveNotification(
                            com.medapp.model.AppNotification(
                                userId = doctor.uid,
                                title = "¡Nueva Cita Agendada!",
                                message = "El paciente ${patient.name} ha agendado una cita para el $formattedDate.",
                                relatedId = createdAppointment.id
                            )
                        )
                    }

                    // Enviar email de confirmación inmediata al paciente
                    if (patient.email.isNotBlank()) {
                        launch(Dispatchers.IO) {
                            EmailService.sendBookingConfirmationEmail(
                                toEmail         = patient.email,
                                patientName     = patient.name,
                                doctorName      = doctor.name,
                                doctorSpecialty = doctor.specialty,
                                appointmentDate = dateTime.toDate(),
                                reason          = reason
                            )
                        }
                    }
                    _operationResult.value = AppointmentResult.Success
                },
                onFailure = { _operationResult.value = AppointmentResult.Error(it.message ?: "Error") }
            )
        }
    }

    // ─── actualizar estatus de la cita ────────────────────────────────────────────────────────
    fun updateAppointmentStatus(
        appointment: Appointment,
        status: AppointmentStatus,
        currentUserId: String,
        cancellationReason: String = ""
    ) {
        viewModelScope.launch {
            _operationResult.value = AppointmentResult.Loading
            repository.updateStatus(appointment.id, status, currentUserId).fold(
                onSuccess = {
                    NotificationHelper.triggerImmediateReminderCheck(getApplication())
                    
                    val updatedAppointment = appointment.copy(status = status)
                    
                    launch(Dispatchers.IO) {
                        val notificationRepo = com.medapp.repository.NotificationRepository()
                        
                        // Notificación al paciente (incluye motivo de cancelación si aplica)
                        val (titleP, messageP) = buildNotificationContent(
                            updatedAppointment, false, cancellationReason
                        )
                        notificationRepo.saveNotification(
                            com.medapp.model.AppNotification(
                                id = "${appointment.id}_${status.name}",
                                userId = appointment.patientId,
                                title = titleP,
                                message = messageP,
                                relatedId = appointment.id
                            )
                        )
                        
                        // Notificación al doctor
                        val (titleD, messageD) = buildNotificationContent(updatedAppointment, true)
                        notificationRepo.saveNotification(
                            com.medapp.model.AppNotification(
                                id = "${appointment.id}_${status.name}_doc",
                                userId = appointment.doctorId,
                                title = titleD,
                                message = messageD,
                                relatedId = appointment.id
                            )
                        )
                    }

                    _operationResult.value = AppointmentResult.Success
                },
                onFailure = { _operationResult.value = AppointmentResult.Error(it.message ?: "Error") }
            )
        }
    }

    // ─── actualizar notas ─────────────────────────────────────────────────────────
    fun updateNotes(appointmentId: String, notes: String) {
        viewModelScope.launch {
            repository.updateNotes(appointmentId, notes)
        }
    }

    // ─── cargar historial (Doctor) ────────────────────────────────────────────────
    fun loadDoctorHistory(
        doctorId: String,
        startDate: Timestamp? = null,
        endDate: Timestamp? = null,
        sortNewest: Boolean = true
    ) {
        viewModelScope.launch {
            _isLoadingHistory.value = true
            repository.getDoctorHistory(doctorId, startDate, endDate, sortNewest).fold(
                onSuccess = { _historyAppointments.value = it },
                onFailure = { _historyAppointments.value = emptyList() }
            )
            _isLoadingHistory.value = false
        }
    }

    // ─── cargar estadisticas ───────────────────────────────────────────────────────────
    fun loadStats(userId: String, isDoctor: Boolean) {
        viewModelScope.launch {
            repository.getStats(userId, isDoctor).fold(
                onSuccess = { _stats.value = it },
                onFailure = {}
            )
        }
    }

    // ─── cargar estadisticas recepcionista ───────────────────────────────────────────────────────────
    fun loadReceptionistStats(doctorIds: List<String>) {
        viewModelScope.launch {
            repository.getReceptionistStats(doctorIds).fold(
                onSuccess = { _stats.value = it },
                onFailure = {}
            )
        }
    }

    fun resetOperationResult() {
        _operationResult.value = AppointmentResult.Idle
    }
}

// ─── Contenido de notificación según estado ───────────────────────────────────
// Función top-level reutilizada también por StatusChangeWorker
fun buildNotificationContent(
    appointment: Appointment,
    isForDoctor: Boolean = false,
    cancellationReason: String = ""
): Pair<String, String> {
    val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        .format(appointment.dateTime.toDate())

    val personText = if (isForDoctor) "el paciente ${appointment.patientName}" else "Dr. ${appointment.doctorName}"

    return when (appointment.status) {
        AppointmentStatus.CONFIRMED ->
            "✅ Cita Confirmada" to "Tu cita con $personText el $dateStr ha sido confirmada."

        AppointmentStatus.CANCELLED -> {
            val baseMsg = "Tu cita con $personText el $dateStr ha sido cancelada."
            val fullMsg = if (!isForDoctor && cancellationReason.isNotBlank())
                "$baseMsg\nMotivo: $cancellationReason"
            else baseMsg
            "❌ Cita Cancelada" to fullMsg
        }

        AppointmentStatus.COMPLETED ->
            "🏁 Cita Completada" to "Tu cita con $personText el $dateStr ha sido marcada como completada."

        else -> "MedApp" to "Tu cita con $personText ha sido actualizada."
    }
}
