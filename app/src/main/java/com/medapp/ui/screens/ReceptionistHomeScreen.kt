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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medapp.model.AppointmentStatus
import com.medapp.ui.theme.*
import com.medapp.viewmodel.AppointmentResult
import com.medapp.viewmodel.AppointmentViewModel
import com.medapp.viewmodel.AuthState
import com.medapp.viewmodel.AuthViewModel
import com.medapp.viewmodel.NotificationViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceptionistHomeScreen(
    authViewModel: AuthViewModel,
    appointmentViewModel: AppointmentViewModel,
    notificationViewModel: NotificationViewModel,
    onViewPending: () -> Unit,
    onBookAppointment: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onLogout: () -> Unit
) {
    val authState by authViewModel.authState.collectAsState()
    val user = (authState as? AuthState.Authenticated)?.user ?: return
    val assignedDoctorIds = user.assignedDoctorIds

    val appointments by appointmentViewModel.appointments.collectAsState()
    val stats by appointmentViewModel.stats.collectAsState()
    val operationResult by appointmentViewModel.operationResult.collectAsState()
    val notifications by notificationViewModel.notifications.collectAsState()
    val unreadCount = notifications.count { !it.isRead }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(assignedDoctorIds) {
        if (assignedDoctorIds.isNotEmpty()) {
            appointmentViewModel.loadReceptionistAppointments(assignedDoctorIds)
            appointmentViewModel.loadReceptionistStats(assignedDoctorIds)
        }
        notificationViewModel.loadNotifications(user.uid)
    }

    // Recargar la lista y mostrar feedback después de confirmar/cancelar/completar
    LaunchedEffect(operationResult) {
        when (operationResult) {
            is AppointmentResult.Success -> {
                scope.launch {
                    snackbarHostState.showSnackbar("Estado de la cita actualizado")
                }
                if (assignedDoctorIds.isNotEmpty()) {
                    appointmentViewModel.loadReceptionistAppointments(assignedDoctorIds)
                    appointmentViewModel.loadReceptionistStats(assignedDoctorIds)
                }
                appointmentViewModel.resetOperationResult()
            }
            is AppointmentResult.Error -> {
                val msg = (operationResult as AppointmentResult.Error).message
                scope.launch {
                    snackbarHostState.showSnackbar("Error: $msg")
                }
                appointmentViewModel.resetOperationResult()
            }
            else -> {}
        }
    }

    val appointmentsByDoctor = appointments.groupBy { it.doctorName }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Recepcionista: ${user.name.split(" ").first()}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Gestión de citas", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToNotifications) {
                        if (unreadCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) {
                                        Text(if (unreadCount > 99) "99+" else unreadCount.toString())
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = "Notificaciones")
                            }
                        } else {
                            Icon(Icons.Default.Notifications, contentDescription = "Notificaciones")
                        }
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "Cerrar sesión")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        label = "Pendientes",
                        value = "${stats?.pending ?: 0}",
                        icon = Icons.Default.Schedule,
                        color = StatusPending,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Confirmadas",
                        value = "${stats?.confirmed ?: 0}",
                        icon = Icons.Default.EventAvailable,
                        color = StatusConfirmed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Text("Acciones Rápidas", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DoctorNavCard(
                        icon = Icons.Default.Add,
                        title = "Agendar Nueva Cita",
                        subtitle = "Para pacientes presenciales o por teléfono",
                        color = MedTeal,
                        onClick = onBookAppointment
                    )
                    DoctorNavCard(
                        icon = Icons.Default.CalendarToday,
                        title = "Gestionar Citas Pendientes",
                        subtitle = "Confirmar o cancelar solicitudes",
                        color = MedBlue,
                        onClick = onViewPending
                    )
                }
            }

            if (appointmentsByDoctor.isEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    Text("No hay citas para mostrar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            appointmentsByDoctor.forEach { (doctorName, doctorAppointments) ->
                val todayAppts = doctorAppointments.filter { appointment ->
                    val today = java.util.Calendar.getInstance()
                    val apptCal = java.util.Calendar.getInstance().apply {
                        time = appointment.dateTime.toDate()
                    }
                    today.get(java.util.Calendar.DAY_OF_YEAR) == apptCal.get(java.util.Calendar.DAY_OF_YEAR) &&
                            today.get(java.util.Calendar.YEAR) == apptCal.get(java.util.Calendar.YEAR) &&
                            appointment.status != AppointmentStatus.CANCELLED
                }
                val confirmedAppts = doctorAppointments.filter {
                    it.status == AppointmentStatus.CONFIRMED
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Dr. $doctorName",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    HorizontalDivider()
                }

                item {
                    Text(
                        "Citas Confirmadas por Atender",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (confirmedAppts.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Sin citas confirmadas", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    items(confirmedAppts) { appointment ->
                        DoctorAppointmentCard(
                            appointment = appointment,
                            onStatusChange = { newStatus, reason ->
                                appointmentViewModel.updateAppointmentStatus(appointment, newStatus, user.uid, reason)
                            }
                        )
                    }
                }

                item {
                    Text(
                        "Citas de Hoy (${todayAppts.size})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (todayAppts.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Sin citas para hoy", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    items(todayAppts) { appointment ->
                        DoctorAppointmentCard(
                            appointment = appointment,
                            onStatusChange = { newStatus, reason ->
                                appointmentViewModel.updateAppointmentStatus(appointment, newStatus, user.uid, reason)
                            }
                        )
                    }
                }
            }
        }
    }
}
