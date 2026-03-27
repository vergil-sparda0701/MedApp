package com.medapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.medapp.model.Appointment
import com.medapp.model.AppointmentStats
import com.medapp.model.AppointmentStatus
import com.medapp.model.User
import com.medapp.repository.AppointmentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AppointmentResult {
    object Idle : AppointmentResult()
    object Loading : AppointmentResult()
    object Success : AppointmentResult()
    data class Error(val message: String) : AppointmentResult()
}

class AppointmentViewModel(
    private val repository: AppointmentRepository = AppointmentRepository()
) : ViewModel() {

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
    fun loadAppointments(userId: String, isDoctor: Boolean) {
        viewModelScope.launch {
            val flow = if (isDoctor)
                repository.getDoctorAppointmentsFlow(userId)
            else
                repository.getPatientAppointmentsFlow(userId)

            flow.collect { _appointments.value = it }
        }
    }

    // ─── Load Pending Appointments ────────────────────────────────────────────
    fun loadPendingAppointments(userId: String, isDoctor: Boolean) {
        viewModelScope.launch {
            repository.getPendingAppointmentsFlow(userId, isDoctor).collect {
                _pendingAppointments.value = it
            }
        }
    }

    // ─── Book Appointment ─────────────────────────────────────────────────────
    fun bookAppointment(
        patient: User,
        doctor: User,
        dateTime: Timestamp,
        reason: String
    ) {
        viewModelScope.launch {
            _operationResult.value = AppointmentResult.Loading
            val appointment = Appointment(
                patientId = patient.uid,
                patientName = patient.name,
                doctorId = doctor.uid,
                doctorName = doctor.name,
                doctorSpecialty = doctor.specialty,
                dateTime = dateTime,
                reason = reason,
                status = AppointmentStatus.PENDING
            )
            repository.createAppointment(appointment).fold(
                onSuccess = { _operationResult.value = AppointmentResult.Success },
                onFailure = { _operationResult.value = AppointmentResult.Error(it.message ?: "Error") }
            )
        }
    }

    // ─── Update Status ────────────────────────────────────────────────────────
    fun updateAppointmentStatus(appointmentId: String, status: AppointmentStatus) {
        viewModelScope.launch {
            _operationResult.value = AppointmentResult.Loading
            repository.updateStatus(appointmentId, status).fold(
                onSuccess = { _operationResult.value = AppointmentResult.Success },
                onFailure = { _operationResult.value = AppointmentResult.Error(it.message ?: "Error") }
            )
        }
    }

    // ─── Update Notes ─────────────────────────────────────────────────────────
    fun updateNotes(appointmentId: String, notes: String) {
        viewModelScope.launch {
            repository.updateNotes(appointmentId, notes)
        }
    }

    // ─── Load History (Doctor) ────────────────────────────────────────────────
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

    // ─── Load Stats ───────────────────────────────────────────────────────────
    fun loadStats(userId: String, isDoctor: Boolean) {
        viewModelScope.launch {
            repository.getStats(userId, isDoctor).fold(
                onSuccess = { _stats.value = it },
                onFailure = {}
            )
        }
    }

    fun resetOperationResult() {
        _operationResult.value = AppointmentResult.Idle
    }
}
