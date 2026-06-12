package com.medapp

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medapp.navigation.AppNavGraph
import com.medapp.notification.NotificationHelper
import com.medapp.ui.theme.MedAppTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // No se requiere ninguna acción específica; si se concede, los workers comenzarán a mostrar notificaciones.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // pide el permiso para enviar notificaciones desde Android 13+ en adelante
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // crea el canal de notificaciones
        NotificationHelper.createNotificationChannel(this)

        // Programa la comprobación de recordatorio periódica (24h horas antes de la cita)
        NotificationHelper.scheduleReminderCheck(this)

        // Programaa la comprobación periódica del estado (notifica al paciente si confirma/cancela/completa la cita).
        NotificationHelper.scheduleStatusChangeCheck(this)

        setContent {
            MedAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavGraph()
                }
            }
        }
    }
}
