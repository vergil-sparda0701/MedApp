package com.medapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.medapp.model.AppointmentStatus
import com.medapp.model.UserRole
import com.medapp.ui.theme.*
import com.medapp.viewmodel.AppointmentViewModel
import com.medapp.viewmodel.AuthState
import com.medapp.viewmodel.AuthViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    authViewModel: AuthViewModel,
    appointmentViewModel: AppointmentViewModel,
    onNavigateBack: () -> Unit
) {
    val authState by authViewModel.authState.collectAsState()
    val user = (authState as? AuthState.Authenticated)?.user ?: return

    // Guard: doctor only
    if (user.role != UserRole.DOCTOR) {
        LaunchedEffect(Unit) { onNavigateBack() }
        return
    }

    val historyAppointments by appointmentViewModel.historyAppointments.collectAsState()
    val isLoadingHistory by appointmentViewModel.isLoadingHistory.collectAsState()

    // Filter state
    var sortNewest by remember { mutableStateOf(true) }
    var startDateMillis by remember { mutableStateOf<Long?>(null) }
    var endDateMillis by remember { mutableStateOf<Long?>(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var selectedStatusFilter by remember { mutableStateOf<AppointmentStatus?>(null) }

    val startDatePickerState = rememberDatePickerState()
    val endDatePickerState = rememberDatePickerState()

    fun reload() {
        val startTs = startDateMillis?.let { Timestamp(Date(it)) }
        val endTs = endDateMillis?.let {
            val cal = Calendar.getInstance().apply { timeInMillis = it; set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }
            Timestamp(cal.time)
        }
        appointmentViewModel.loadDoctorHistory(user.uid, startTs, endTs, sortNewest)
    }

    LaunchedEffect(user.uid) { reload() }

    if (showStartDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDateMillis = startDatePickerState.selectedDateMillis
                    showStartDatePicker = false
                    reload()
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartDatePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = startDatePickerState) }
    }

    if (showEndDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endDateMillis = endDatePickerState.selectedDateMillis
                    showEndDatePicker = false
                    reload()
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndDatePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = endDatePickerState) }
    }

    val filteredAppointments = if (selectedStatusFilter != null) {
        historyAppointments.filter { it.status == selectedStatusFilter }
    } else historyAppointments

    Scaffold(
        topBar = {
            MedTopBar(
                title = "Historial de Citas",
                onBack = onNavigateBack,
                actions = {
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            if (showFilters) Icons.Default.FilterListOff else Icons.Default.FilterList,
                            "Filtros"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MedSurface)
                .padding(padding)
        ) {
            // Filters panel
            if (showFilters) {
                Card(
                    shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Filtros", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MedBlueDark)

                        // Date range
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showStartDatePicker = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (startDateMillis != null) formatDate(Timestamp(Date(startDateMillis!!))) else "Desde",
                                    fontSize = 12.sp
                                )
                            }
                            OutlinedButton(
                                onClick = { showEndDatePicker = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (endDateMillis != null) formatDate(Timestamp(Date(endDateMillis!!))) else "Hasta",
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Sort
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Ordenar: ", fontSize = 13.sp, color = Color.Gray)
                            FilterChip(
                                selected = sortNewest,
                                onClick = { sortNewest = true; reload() },
                                label = { Text("Más recientes", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(14.dp)) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            FilterChip(
                                selected = !sortNewest,
                                onClick = { sortNewest = false; reload() },
                                label = { Text("Más antiguas", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(14.dp)) }
                            )
                        }

                        // Status filter
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedStatusFilter == null,
                                onClick = { selectedStatusFilter = null },
                                label = { Text("Todas", fontSize = 11.sp) }
                            )
                            AppointmentStatus.values().forEach { status ->
                                FilterChip(
                                    selected = selectedStatusFilter == status,
                                    onClick = { selectedStatusFilter = if (selectedStatusFilter == status) null else status },
                                    label = { Text(status.displayName(), fontSize = 11.sp) }
                                )
                            }
                        }

                        // Clear filters
                        if (startDateMillis != null || endDateMillis != null) {
                            TextButton(
                                onClick = {
                                    startDateMillis = null
                                    endDateMillis = null
                                    reload()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Limpiar filtros de fecha")
                            }
                        }
                    }
                }
            }

            // Results count
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MedBlue.copy(alpha = 0.07f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    "${filteredAppointments.size} citas encontradas",
                    color = MedBlue,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }

            if (isLoadingHistory) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MedBlue)
                }
            } else if (filteredAppointments.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                        Spacer(Modifier.height(16.dp))
                        Text("No hay citas en este período", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredAppointments) { appointment ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(appointment.patientName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text(
                                            formatTimestamp(appointment.dateTime),
                                            color = MedBlue,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    StatusChip(appointment.status)
                                }
                                if (appointment.reason.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Motivo: ${appointment.reason}",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                                if (appointment.notes.isNotBlank()) {
                                    Text(
                                        "Notas: ${appointment.notes}",
                                        fontSize = 12.sp,
                                        color = MedTeal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
