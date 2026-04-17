package com.medapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medapp.model.Appointment
import com.medapp.model.AppointmentStatus
import com.medapp.ui.theme.*
import com.medapp.viewmodel.AppointmentViewModel
import com.medapp.viewmodel.AuthState
import com.medapp.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorHomeScreen(
    authViewModel: AuthViewModel,
    appointmentViewModel: AppointmentViewModel,
    onViewPending: () -> Unit,
    onViewStats: () -> Unit,
    onViewHistory: () -> Unit,
    onLogout: () -> Unit
) {
    val authState by authViewModel.authState.collectAsState()
    val user = (authState as? AuthState.Authenticated)?.user ?: return
    val appointments by appointmentViewModel.appointments.collectAsState()
    val stats by appointmentViewModel.stats.collectAsState()

    LaunchedEffect(user.uid) {
        appointmentViewModel.loadAppointments(user.uid, true)
        appointmentViewModel.loadStats(user.uid, true)
    }

    val todayAppointments = appointments.filter { appointment ->
        val today = java.util.Calendar.getInstance()
        val apptCal = java.util.Calendar.getInstance().apply {
            time = appointment.dateTime.toDate()
        }
        today.get(java.util.Calendar.DAY_OF_YEAR) == apptCal.get(java.util.Calendar.DAY_OF_YEAR) &&
                today.get(java.util.Calendar.YEAR) == apptCal.get(java.util.Calendar.YEAR)
    }

    // Citas confirmadas pendientes de completar o cancelar
    val confirmedAppointments = appointments.filter {
        it.status == AppointmentStatus.CONFIRMED
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Dr. ${user.name.split(" ").first()}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(user.specialty, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "Cerrar sesión")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MedBlueDark,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MedSurface)
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary stats row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        label = "Total",
                        value = "${stats?.total ?: 0}",
                        icon = Icons.Default.Assignment,
                        color = MedBlue,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Pendientes",
                        value = "${stats?.pending ?: 0}",
                        icon = Icons.Default.Schedule,
                        color = StatusPending,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Completadas",
                        value = "${stats?.completed ?: 0}",
                        icon = Icons.Default.TaskAlt,
                        color = StatusCompleted,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Navigation cards
            item {
                Text("Panel de Control", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MedBlueDark)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DoctorNavCard(
                        icon = Icons.Default.CalendarToday,
                        title = "Citas Pendientes",
                        subtitle = "${stats?.pending ?: 0} citas próximas",
                        color = MedBlue,
                        onClick = onViewPending
                    )
                    DoctorNavCard(
                        icon = Icons.Default.BarChart,
                        title = "Estadísticas",
                        subtitle = "Ver tasa de completadas y canceladas",
                        color = MedTeal,
                        onClick = onViewStats
                    )
                    DoctorNavCard(
                        icon = Icons.Default.History,
                        title = "Historial de Citas",
                        subtitle = "Acceso exclusivo del médico",
                        color = MedGreen,
                        onClick = onViewHistory
                    )
                }
            }

            // ── Citas Confirmadas ─────────────────────────────────────────────
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Citas Confirmadas",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MedBlueDark
                    )
                    if (confirmedAppointments.isNotEmpty()) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = StatusConfirmed
                        ) {
                            Text(
                                "${confirmedAppointments.size}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (confirmedAppointments.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = StatusConfirmed.copy(alpha = 0.06f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = StatusConfirmed.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Sin citas confirmadas por atender",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                items(confirmedAppointments) { appointment ->
                    DoctorAppointmentCard(
                        appointment = appointment,
                        onStatusChange = { newStatus ->
                            appointmentViewModel.updateAppointmentStatus(appointment.id, newStatus, user.uid)
                        }
                    )
                }
            }

            // ── Citas de Hoy ──────────────────────────────────────────────────
            item {
                Text(
                    "Citas de Hoy (${todayAppointments.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MedBlueDark
                )
            }

            if (todayAppointments.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.EventAvailable,
                                    null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MedBlue.copy(alpha = 0.5f)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("Sin citas para hoy", color = Color.Gray)
                            }
                        }
                    }
                }
            } else {
                items(todayAppointments) { appointment ->
                    DoctorAppointmentCard(
                        appointment = appointment,
                        onStatusChange = { newStatus ->
                            appointmentViewModel.updateAppointmentStatus(appointment.id, newStatus, user.uid)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DoctorNavCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
        }
    }
}

@Composable
fun DoctorAppointmentCard(
    appointment: Appointment,
    onStatusChange: ((AppointmentStatus) -> Unit)? = null,
    showActions: Boolean = true
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MedBlue.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        null,
                        tint = MedBlue,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(appointment.patientName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        formatTimestamp(appointment.dateTime),
                        fontSize = 13.sp,
                        color = MedBlue
                    )
                }
                StatusChip(appointment.status)
                if (showActions && onStatusChange != null) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, null)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (appointment.status == AppointmentStatus.PENDING) {
                                DropdownMenuItem(
                                    text = { Text("Confirmar") },
                                    onClick = {
                                        onStatusChange(AppointmentStatus.CONFIRMED)
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.CheckCircle, null, tint = StatusConfirmed)
                                    }
                                )
                            }
                            if (appointment.status != AppointmentStatus.COMPLETED) {
                                DropdownMenuItem(
                                    text = { Text("Marcar completada") },
                                    onClick = {
                                        onStatusChange(AppointmentStatus.COMPLETED)
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.TaskAlt, null, tint = StatusCompleted)
                                    }
                                )
                            }
                            if (appointment.status != AppointmentStatus.CANCELLED) {
                                DropdownMenuItem(
                                    text = { Text("Cancelar") },
                                    onClick = {
                                        onStatusChange(AppointmentStatus.CANCELLED)
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Cancel, null, tint = StatusCancelled)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (appointment.reason.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Motivo: ${appointment.reason}",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
            if (appointment.notes.isNotBlank()) {
                Text(
                    "Notas: ${appointment.notes}",
                    fontSize = 13.sp,
                    color = MedTeal
                )
            }
        }
    }
}
