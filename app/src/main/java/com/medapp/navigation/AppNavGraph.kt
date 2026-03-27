package com.medapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.medapp.model.UserRole
import com.medapp.ui.screens.*
import com.medapp.viewmodel.AppointmentViewModel
import com.medapp.viewmodel.AuthState
import com.medapp.viewmodel.AuthViewModel

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "registrarse"
    const val PATIENT_HOME = "home_pacientes"
    const val DOCTOR_HOME = "home_doctores"
    const val BOOK_APPOINTMENT = "book_appointment"
    const val PENDING_APPOINTMENTS = "citas_pendientes"
    const val STATISTICS = "estadisticas"
    const val HISTORY = "historial"
}

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = viewModel(),
    appointmentViewModel: AppointmentViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()

    val startDestination = when (authState) {
        is AuthState.Authenticated -> {
            val user = (authState as AuthState.Authenticated).user
            if (user.role == UserRole.DOCTOR) Routes.DOCTOR_HOME else Routes.PATIENT_HOME
        }
        else -> Routes.LOGIN
    }

    NavHost(
        navController = navController,
        startDestination = Routes.LOGIN
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = { user ->
                    val dest = if (user.role == UserRole.DOCTOR) Routes.DOCTOR_HOME else Routes.PATIENT_HOME
                    navController.navigate(dest) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) }
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                authViewModel = authViewModel,
                onRegisterSuccess = { user ->
                    val dest = if (user.role == UserRole.DOCTOR) Routes.DOCTOR_HOME else Routes.PATIENT_HOME
                    navController.navigate(dest) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PATIENT_HOME) {
            PatientHomeScreen(
                authViewModel = authViewModel,
                appointmentViewModel = appointmentViewModel,
                onBookAppointment = { navController.navigate(Routes.BOOK_APPOINTMENT) },
                onViewPending = { navController.navigate(Routes.PENDING_APPOINTMENTS) },
                onViewStats = { navController.navigate(Routes.STATISTICS) },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.DOCTOR_HOME) {
            DoctorHomeScreen(
                authViewModel = authViewModel,
                appointmentViewModel = appointmentViewModel,
                onViewPending = { navController.navigate(Routes.PENDING_APPOINTMENTS) },
                onViewStats = { navController.navigate(Routes.STATISTICS) },
                onViewHistory = { navController.navigate(Routes.HISTORY) },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.BOOK_APPOINTMENT) {
            BookAppointmentScreen(
                authViewModel = authViewModel,
                appointmentViewModel = appointmentViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PENDING_APPOINTMENTS) {
            PendingAppointmentsScreen(
                authViewModel = authViewModel,
                appointmentViewModel = appointmentViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.STATISTICS) {
            StatisticsScreen(
                authViewModel = authViewModel,
                appointmentViewModel = appointmentViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                authViewModel = authViewModel,
                appointmentViewModel = appointmentViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
