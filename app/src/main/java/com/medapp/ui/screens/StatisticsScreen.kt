package com.medapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.medapp.model.UserRole
import com.medapp.ui.theme.*
import com.medapp.viewmodel.AppointmentViewModel
import com.medapp.viewmodel.AuthState
import com.medapp.viewmodel.AuthViewModel

@Composable
fun StatisticsScreen(
    authViewModel: AuthViewModel,
    appointmentViewModel: AppointmentViewModel,
    onNavigateBack: () -> Unit
) {
    val authState by authViewModel.authState.collectAsState()
    val user = (authState as? AuthState.Authenticated)?.user ?: return
    val isDoctor = user.role == UserRole.DOCTOR
    val stats by appointmentViewModel.stats.collectAsState()

    LaunchedEffect(user.uid) {
        appointmentViewModel.loadStats(user.uid, isDoctor)
    }

    Scaffold(
        topBar = {
            MedTopBar(title = "Estadísticas", onBack = onNavigateBack)
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
            if (stats == null) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MedBlue)
                }
            } else {
                val s = stats!!

                // Total card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MedBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Total de Citas",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp
                            )
                            Text(
                                "${s.total}",
                                color = Color.White,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            Icons.Default.Assignment,
                            null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                // Status grid
                Text("Desglose por Estado", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MedBlueDark)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        label = "Pendientes",
                        value = "${s.pending}",
                        icon = Icons.Default.Schedule,
                        color = StatusPending,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Confirmadas",
                        value = "${s.confirmed}",
                        icon = Icons.Default.CheckCircle,
                        color = StatusConfirmed,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        label = "Completadas",
                        value = "${s.completed}",
                        icon = Icons.Default.TaskAlt,
                        color = StatusCompleted,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Canceladas",
                        value = "${s.cancelled}",
                        icon = Icons.Default.Cancel,
                        color = StatusCancelled,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Rates card
                Text("Tasas de Rendimiento", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MedBlueDark)

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        RateRow(
                            label = "Tasa de completadas",
                            rate = s.completionRate,
                            color = StatusCompleted
                        )
                        HorizontalDivider()
                        RateRow(
                            label = "Tasa de cancelaciones",
                            rate = s.cancellationRate,
                            color = StatusCancelled
                        )
                    }
                }

                // Visual bar chart
                if (s.total > 0) {
                    Text("Distribución Visual", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MedBlueDark)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            BarChartRow("Pendientes", s.pending, s.total, StatusPending)
                            BarChartRow("Confirmadas", s.confirmed, s.total, StatusConfirmed)
                            BarChartRow("Completadas", s.completed, s.total, StatusCompleted)
                            BarChartRow("Canceladas", s.cancelled, s.total, StatusCancelled)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RateRow(label: String, rate: Float, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 14.sp, color = Color.Gray)
            Text(
                "${String.format("%.1f", rate)}%",
                fontWeight = FontWeight.Bold,
                color = color,
                fontSize = 16.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (rate / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = color,
            trackColor = color.copy(alpha = 0.15f)
        )
    }
}

@Composable
private fun BarChartRow(label: String, count: Int, total: Int, color: Color) {
    val fraction = if (total > 0) count.toFloat() / total else 0f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.width(100.dp),
            fontSize = 13.sp,
            color = Color.Gray
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                color = color,
                trackColor = color.copy(alpha = 0.12f)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "$count",
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.width(28.dp),
            fontSize = 13.sp
        )
    }
}
