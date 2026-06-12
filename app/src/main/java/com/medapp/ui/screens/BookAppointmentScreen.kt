package com.medapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.medapp.model.User
import com.medapp.model.UserRole
import com.medapp.ui.theme.MedBlue
import com.medapp.ui.theme.MedBlueDark
import com.medapp.ui.theme.MedSurface
import com.medapp.ui.theme.MedTeal
import com.medapp.viewmodel.AppointmentResult
import com.medapp.viewmodel.AppointmentViewModel
import com.medapp.viewmodel.AuthState
import com.medapp.viewmodel.AuthViewModel
import com.medapp.viewmodel.DoctorsState
import com.medapp.viewmodel.PatientsState
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookAppointmentScreen(
    authViewModel: AuthViewModel,
    appointmentViewModel: AppointmentViewModel,
    onNavigateBack: () -> Unit
) {
    val authState by authViewModel.authState.collectAsState()
    val currentUser = (authState as? AuthState.Authenticated)?.user ?: return
    val doctorsState by authViewModel.doctorsState.collectAsState()
    val patientsState by authViewModel.patientsState.collectAsState()
    val operationResult by appointmentViewModel.operationResult.collectAsState()

    var selectedPatient by remember { mutableStateOf<User?>(if (currentUser.role == UserRole.PATIENT) currentUser else null) }
    var selectedDoctor by remember { mutableStateOf<User?>(null) }
    var reason by remember { mutableStateOf("") }
    var showPatientPicker by remember { mutableStateOf(false) }
    var showDoctorPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<Long?>(null) }
    var selectedHour by remember { mutableIntStateOf(9) }
    var selectedMinute by remember { mutableIntStateOf(0) }

    var timeError by remember { mutableStateOf<String?>(null) }

    val filteredDoctorsState = remember(doctorsState, currentUser) {
        val currentDoctorsState = doctorsState
        if (currentUser.role == UserRole.RECEPTIONIST && currentDoctorsState is DoctorsState.Success) {
            val assigned = currentDoctorsState.doctors.filter { it.uid in currentUser.assignedDoctorIds }
            if (assigned.isEmpty()) DoctorsState.Empty("No tienes doctores asignados.")
            else DoctorsState.Success(assigned)
        } else {
            currentDoctorsState
        }
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis() + 86400000L,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = utcTimeMillis
                }
                val today = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                
                if (utcTimeMillis < today) return false
                
                val doctorSchedule = selectedDoctor?.schedule
                if (doctorSchedule == null || doctorSchedule.workingDays.isEmpty()) {
                    return true
                }
                
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                return doctorSchedule.workingDays.contains(dayOfWeek)
            }
        }
    )
    val timePickerState = rememberTimePickerState(initialHour = 9, initialMinute = 0)

    // Recargar doctores cada vez que se entra a la pantalla
    LaunchedEffect(Unit) {
        authViewModel.loadDoctors()
    }

    LaunchedEffect(operationResult) {
        if (operationResult is AppointmentResult.Success) {
            appointmentViewModel.resetOperationResult()
            onNavigateBack()
        }
    }

    if (showPatientPicker) {
        PatientPickerDialog(
            patientsState = patientsState,
            onDismiss = { showPatientPicker = false },
            onSelect = { pat ->
                selectedPatient = pat
                showPatientPicker = false
            },
            onRetry = { authViewModel.loadPatients() }
        )
    }

    if (showDoctorPicker) {
        DoctorPickerDialog(
            doctorsState = filteredDoctorsState,
            onDismiss = { showDoctorPicker = false },
            onSelect = { doctor ->
                selectedDoctor = doctor
                timeError = null
                showDoctorPicker = false
            },
            onRetry = { authViewModel.loadDoctors() }
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDate = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Seleccionar hora") },
            text = { 
                Column {
                    TimePicker(state = timePickerState)
                    timeError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val shift = selectedDoctor?.schedule?.shiftType
                    val hour = timePickerState.hour
                    val isValidTime = when (shift) {
                        com.medapp.model.ShiftType.MORNING -> hour in 8..11
                        com.medapp.model.ShiftType.AFTERNOON -> hour in 14..16
                        com.medapp.model.ShiftType.FULL_DAY -> (hour in 8..11) || (hour in 14..16)
                        null -> true
                    }
                    if (isValidTime) {
                        selectedHour = timePickerState.hour
                        selectedMinute = timePickerState.minute
                        timeError = null
                        showTimePicker = false
                    } else {
                        timeError = "Hora fuera del horario del doctor (${shift?.displayName})."
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    timeError = null
                    showTimePicker = false 
                }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            MedTopBar(title = "Agendar Cita", onBack = onNavigateBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MedSurface)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // paso 0: Paciente (Solo recepcionistas)
            if (currentUser.role == UserRole.RECEPTIONIST) {
                SectionCard(title = "0. Seleccionar Paciente") {
                    if (selectedPatient != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPatientPicker = true }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MedBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, null, tint = MedBlue)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(selectedPatient!!.name, fontWeight = FontWeight.Bold)
                                Text(selectedPatient!!.email, color = Color.Gray, fontSize = 13.sp)
                            }
                            TextButton(onClick = { showPatientPicker = true }) {
                                Text("Cambiar")
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showPatientPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PersonSearch, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Seleccionar paciente")
                        }
                    }
                }
            }

            // paso 1: Doctor
            SectionCard(title = "1. Seleccionar Doctor") {
                if (selectedDoctor != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDoctorPicker = true }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MedBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MedicalServices, null, tint = MedBlue)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dr. ${selectedDoctor!!.name}", fontWeight = FontWeight.Bold)
                            Text(selectedDoctor!!.specialty, color = Color.Gray, fontSize = 13.sp)
                            selectedDoctor!!.schedule?.let { schedule ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Turno: ${schedule.shiftType.displayName}", color = MedTeal, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        TextButton(onClick = { showDoctorPicker = true }) {
                            Text("Cambiar")
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showDoctorPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PersonSearch, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Seleccionar doctor")
                    }
                }
            }

            // paso 2: fecha y hora
            SectionCard(title = "2. Seleccionar Fecha y Hora") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (selectedDate != null) {
                                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = selectedDate!! }
                                "${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.YEAR)}"
                            } else "Fecha"
                        )
                    }
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Schedule, null)
                        Spacer(Modifier.width(8.dp))
                        Text(String.format("%02d:%02d", selectedHour, selectedMinute))
                    }
                }
            }

            // paso 3: motivo
            SectionCard(title = "3. Motivo de Consulta") {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Describe el motivo") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (operationResult is AppointmentResult.Error) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        (operationResult as AppointmentResult.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp
                    )
                }
            }

            val canBook = selectedPatient != null && selectedDoctor != null && selectedDate != null && reason.isNotBlank()

            Button(
                onClick = {
                    val date = selectedDate ?: return@Button
                    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                        timeInMillis = date
                    }
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                        set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                        set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                        set(Calendar.HOUR_OF_DAY, selectedHour)
                        set(Calendar.MINUTE, selectedMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    appointmentViewModel.bookAppointment(
                        patient = selectedPatient!!,
                        doctor = selectedDoctor!!,
                        dateTime = Timestamp(cal.time),
                        reason = reason
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = canBook && operationResult !is AppointmentResult.Loading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MedBlue)
            ) {
                if (operationResult is AppointmentResult.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.5.dp)
                } else {
                    Icon(Icons.Default.CalendarMonth, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Confirmar Cita", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PatientPickerDialog(
    patientsState: PatientsState,
    onDismiss: () -> Unit,
    onSelect: (com.medapp.model.User) -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar Paciente", fontWeight = FontWeight.Bold) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                contentAlignment = Alignment.Center
            ) {
                when (patientsState) {
                    is PatientsState.Loading, is PatientsState.Idle -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MedBlue)
                            Spacer(Modifier.height(12.dp))
                            Text("Buscando pacientes...", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                    is PatientsState.Empty -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(Icons.Default.PersonOff, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No hay pacientes disponibles", fontWeight = FontWeight.SemiBold, color = Color.DarkGray)
                        }
                    }
                    is PatientsState.Error -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(Icons.Default.WifiOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Error al cargar pacientes", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(4.dp))
                            Text(patientsState.message, color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onRetry) { Text("Reintentar") }
                        }
                    }
                    is PatientsState.Success -> {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(patientsState.patients) { pat ->
                                ListItem(
                                    headlineContent = { Text(pat.name, fontWeight = FontWeight.Medium) },
                                    supportingContent = { Text(pat.email, color = Color.Gray, fontSize = 13.sp) },
                                    leadingContent = {
                                        Box(
                                            modifier = Modifier.size(44.dp).clip(CircleShape).background(MedBlue.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Person, null, tint = MedBlue, modifier = Modifier.size(22.dp))
                                        }
                                    },
                                    modifier = Modifier.clickable { onSelect(pat) }.clip(RoundedCornerShape(8.dp))
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MedBlueDark)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun DoctorPickerDialog(
    doctorsState: DoctorsState,
    onDismiss: () -> Unit,
    onSelect: (com.medapp.model.User) -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar Doctor", fontWeight = FontWeight.Bold) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                contentAlignment = Alignment.Center
            ) {
                when (doctorsState) {
                    // ── Cargando ──────────────────────────────────────────────
                    is DoctorsState.Loading, is DoctorsState.Idle -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MedBlue)
                            Spacer(Modifier.height(12.dp))
                            Text("Buscando doctores...", color = Color.Gray, fontSize = 14.sp)
                        }
                    }

                    // ── Sin doctores registrados ──────────────────────────────
                    is DoctorsState.Empty -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                Icons.Default.PersonOff,
                                null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No hay doctores disponibles",
                                fontWeight = FontWeight.SemiBold,
                                color = Color.DarkGray
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Registra al menos un doctor con rol 'Doctor' para poder agendar citas.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = onRetry) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reintentar")
                            }
                        }
                    }

                    // ── Error de red / permisos ───────────────────────────────
                    is DoctorsState.Error -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                Icons.Default.WifiOff,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Error al cargar doctores",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                doctorsState.message,
                                color = Color.Gray,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(containerColor = MedBlue)
                            ) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reintentar")
                            }
                        }
                    }

                    // ── Lista de doctores ─────────────────────────────────────
                    is DoctorsState.Success -> {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(doctorsState.doctors) { doctor ->
                                ListItem(
                                    headlineContent = {
                                        Text("Dr. ${doctor.name}", fontWeight = FontWeight.Medium)
                                    },
                                    supportingContent = {
                                        Text(doctor.specialty, color = Color.Gray, fontSize = 13.sp)
                                    },
                                    leadingContent = {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(MedBlue.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.MedicalServices,
                                                null,
                                                tint = MedBlue,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .clickable { onSelect(doctor) }
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
