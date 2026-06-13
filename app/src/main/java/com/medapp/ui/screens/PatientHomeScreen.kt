package com.medapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medapp.model.Appointment
import com.medapp.model.UserRole
import com.medapp.ui.theme.*
import com.medapp.viewmodel.AppointmentViewModel
import com.medapp.viewmodel.AuthState
import com.medapp.viewmodel.AuthViewModel
import com.medapp.viewmodel.NotificationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientHomeScreen(
    authViewModel: AuthViewModel,
    appointmentViewModel: AppointmentViewModel,
    notificationViewModel: NotificationViewModel,
    onBookAppointment: () -> Unit,
    onViewPending: () -> Unit,
    onViewStats: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onLogout: () -> Unit
) {
    val authState by authViewModel.authState.collectAsState()
    val user = (authState as? AuthState.Authenticated)?.user ?: return
    val appointments by appointmentViewModel.appointments.collectAsState()
    val notifications by notificationViewModel.notifications.collectAsState()
    val unreadCount = notifications.count { !it.isRead }

    LaunchedEffect(user.uid) {
        appointmentViewModel.loadAppointments(user.uid, false)
        appointmentViewModel.loadStats(user.uid, false)
        notificationViewModel.loadNotifications(user.uid)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Hola, ${user.name.split(" ").first()}!", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Paciente", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
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
        //botón flotante para agendar cita
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onBookAppointment,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Nueva Cita") },
                containerColor = MedBlue,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cards de acciones rapidas
            item {
                Text("Acciones rápidas", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MedBlueDark)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionCard(
                        icon = Icons.Default.CalendarMonth,
                        label = "Citas\nPendientes",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = onViewPending
                    )
                    QuickActionCard(
                        icon = Icons.Default.BarChart,
                        label = "Mis\nEstadísticas",
                        color = MedTeal,
                        modifier = Modifier.weight(1f),
                        onClick = onViewStats
                    )
                    QuickActionCard(
                        icon = Icons.Default.Add,
                        label = "Agendar\nCita",
                        color = MedGreen,
                        modifier = Modifier.weight(1f),
                        onClick = onBookAppointment
                    )
                }
            }

            // Citas pendientes
            item {
                Text("Mis citas pendientes", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            }

            val recentAppointments = appointments.filter { it.status != com.medapp.model.AppointmentStatus.CANCELLED }

            if (recentAppointments.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.EventBusy,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = MedBlue.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No tienes citas registradas",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onBookAppointment) {
                                Text("Agendar mi primera cita")
                            }
                        }
                    }
                }
            } else {
                items(recentAppointments.take(5)) { appointment ->
                    PatientAppointmentCard(appointment = appointment)
                }

                if (recentAppointments.size > 5) {
                    item {
                        TextButton(
                            onClick = onViewPending,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Ver todas las citas (${recentAppointments.size})")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun PatientAppointmentCard(appointment: Appointment) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MedBlue.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MedicalServices,
                    null,
                    tint = MedBlue,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Dr. ${appointment.doctorName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    appointment.doctorSpecialty,
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                Text(
                    formatTimestamp(appointment.dateTime),
                    fontSize = 12.sp,
                    color = MedBlue
                )
                if (appointment.reason.isNotBlank()) {
                    Text(
                        appointment.reason,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusChip(appointment.status)
                
                if (appointment.doctorPhone.isNotBlank()) {
                    val context = LocalContext.current
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
                            tint = Color(0xFF25D366) // WhatsApp green
                        )
                    }
                }
            }
        }
    }
}
