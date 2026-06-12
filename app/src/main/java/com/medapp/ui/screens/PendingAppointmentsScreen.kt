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
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medapp.model.AppointmentStatus
import com.medapp.model.UserRole
import com.medapp.ui.theme.*
import com.medapp.viewmodel.AppointmentResult
import com.medapp.viewmodel.AppointmentViewModel
import com.medapp.viewmodel.AuthState
import com.medapp.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingAppointmentsScreen(
    authViewModel: AuthViewModel,
    appointmentViewModel: AppointmentViewModel,
    onNavigateBack: () -> Unit
) {
    val authState by authViewModel.authState.collectAsState()
    val user = (authState as? AuthState.Authenticated)?.user ?: return
    val isDoctorOrReceptionist = user.role == UserRole.DOCTOR || user.role == UserRole.RECEPTIONIST

    val pendingAppointments by appointmentViewModel.pendingAppointments.collectAsState()
    val operationResult by appointmentViewModel.operationResult.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Cargar citas al entrar a la pantalla
    LaunchedEffect(user.uid, user.assignedDoctorIds) {
        if (user.role == UserRole.RECEPTIONIST) {
            if (user.assignedDoctorIds.isNotEmpty()) {
                appointmentViewModel.loadReceptionistPendingAppointments(user.assignedDoctorIds)
            }
        } else {
            appointmentViewModel.loadPendingAppointments(user.uid, user.role == UserRole.DOCTOR)
        }
    }

    // Observar el resultado de operaciones (confirmar, cancelar, completar)
    LaunchedEffect(operationResult) {
        when (operationResult) {
            is AppointmentResult.Success -> {
                scope.launch {
                    snackbarHostState.showSnackbar("Estado de la cita actualizado")
                }
                // Recargar las citas después del cambio
                if (user.role == UserRole.RECEPTIONIST) {
                    if (user.assignedDoctorIds.isNotEmpty()) {
                        appointmentViewModel.loadReceptionistPendingAppointments(user.assignedDoctorIds)
                    }
                } else {
                    appointmentViewModel.loadPendingAppointments(user.uid, user.role == UserRole.DOCTOR)
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

    Scaffold(
        topBar = {
            MedTopBar(
                title = "Citas Pendientes",
                onBack = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MedSurface)
                .padding(padding)
        ) {
            // Count banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MedBlue.copy(alpha = 0.07f))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarMonth, null, tint = MedBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${pendingAppointments.size} cita${if (pendingAppointments.size != 1) "s" else ""} próxima${if (pendingAppointments.size != 1) "s" else ""}",
                        fontWeight = FontWeight.SemiBold,
                        color = MedBlue
                    )
                }
            }

            if (pendingAppointments.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.EventAvailable,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.LightGray
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("No hay citas pendientes", color = Color.Gray, fontSize = 16.sp)
                        Text("¡Tienes todo al día!", color = Color.LightGray, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (user.role == UserRole.RECEPTIONIST) {
                        val grouped = pendingAppointments.groupBy { it.doctorName }
                        grouped.forEach { (docName, apps) ->
                            item {
                                Text(
                                    text = "Dr. $docName",
                                    fontWeight = FontWeight.Bold,
                                    color = MedBlue,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                    fontSize = 16.sp
                                )
                            }
                            items(apps) { appointment ->
                                DoctorAppointmentCard(
                                    appointment = appointment,
                                    onStatusChange = { status, reason ->
                                        appointmentViewModel.updateAppointmentStatus(appointment, status, user.uid, reason)
                                    }
                                )
                            }
                        }
                    } else {
                        items(pendingAppointments) { appointment ->
                            if (isDoctorOrReceptionist) {
                                DoctorAppointmentCard(
                                    appointment = appointment,
                                    onStatusChange = { status, reason ->
                                        appointmentViewModel.updateAppointmentStatus(appointment, status, user.uid, reason)
                                    }
                                )
                            } else {
                                // vista de pacientes
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "Dr. ${appointment.doctorName}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                                Text(
                                                    appointment.doctorSpecialty,
                                                    color = Color.Gray,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                StatusChip(appointment.status)
                                                if (appointment.doctorPhone.isNotBlank()) {
                                                    IconButton(
                                                        onClick = {
                                                            val phoneNum = appointment.doctorPhone.replace("[^\\d+]".toRegex(), "")
                                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phoneNum"))
                                                            try {
                                                                context.startActivity(intent)
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "WhatsApp no está instalado", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        modifier = Modifier.padding(top = 4.dp).size(32.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Chat, 
                                                            contentDescription = "Contactar por WhatsApp",
                                                            tint = Color(0xFF25D366)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Schedule,
                                                null,
                                                tint = MedBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                formatTimestamp(appointment.dateTime),
                                                color = MedBlue,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        if (appointment.reason.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "Motivo: ${appointment.reason}",
                                                fontSize = 13.sp,
                                                color = Color.Gray
                                            )
                                        }
                                        if (appointment.status != AppointmentStatus.CANCELLED) {
                                            Spacer(Modifier.height(12.dp))
                                            HorizontalDivider()
                                            Spacer(Modifier.height(8.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    appointmentViewModel.updateAppointmentStatus(
                                                        appointment,
                                                        AppointmentStatus.CANCELLED,
                                                        user.uid
                                                    )
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = StatusCancelled
                                                )
                                            ) {
                                                Icon(Icons.Default.Cancel, null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Cancelar cita")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
