# MedApp 🏥
### Aplicación Android de Gestión de Citas Médicas

---

## 📋 Descripción

MedApp es una aplicación Android completa para la gestión de citas médicas, desarrollada con:

- **Kotlin** como lenguaje de programación
- **Jetpack Compose** como framework de UI
- **Firebase** (Auth + Firestore + Cloud Messaging) como backend

---

## ✨ Funcionalidades

| Funcionalidad | Paciente | Doctor |
|---|:---:|:---:|
| Registro de usuario | ✅ | ✅ |
| Inicio de sesión | ✅ | ✅ |
| Agendar citas | ✅ | — |
| Ver citas pendientes | ✅ | ✅ |
| Cancelar citas | ✅ | — |
| Confirmar / completar citas | — | ✅ |
| Recordatorios automáticos | ✅ | ✅ |
| Estadísticas de citas | ✅ | ✅ |
| Historial con filtros de fecha | ❌ | ✅ |

---

## 🚀 Configuración del Proyecto

### 1. Firebase Setup

1. Ve a [Firebase Console](https://console.firebase.google.com)
2. Crea un nuevo proyecto llamado **MedApp**
3. Agrega una aplicación Android con el package name `com.medapp`
4. Descarga el archivo `google-services.json` y colócalo en:
   ```
   app/google-services.json
   ```
5. Habilita los siguientes servicios en Firebase Console:

#### Authentication
- Ve a **Authentication → Sign-in method**
- Habilita **Email/Password**

#### Firestore Database
- Ve a **Firestore Database → Create database**
- Selecciona **Production mode**
- Despliega las reglas de seguridad del archivo `firestore.rules`

#### Firestore Indexes
- Ve a **Firestore Database → Indexes**
- Crea los índices compuestos del archivo `firestore.indexes.json`
- O ejecuta `firebase deploy --only firestore:indexes` con Firebase CLI

#### Cloud Messaging (FCM)
- FCM está habilitado automáticamente en proyectos Firebase
- Los tokens se guardan automáticamente al iniciar sesión

### 2. Abrir en Android Studio

1. Clona o descarga el proyecto
2. Abre Android Studio
3. Selecciona **File → Open** y navega a la carpeta `MedApp`
4. Espera a que Gradle sincronice las dependencias
5. Conecta un dispositivo Android (API 26+) o crea un emulador
6. Ejecuta con **Run → Run 'app'**

---

## 🏗️ Arquitectura

```
com.medapp/
├── MainActivity.kt                    # Punto de entrada
├── navigation/
│   └── AppNavGraph.kt                 # Navegación con NavController
├── model/
│   └── Models.kt                      # User, Appointment, AppointmentStats
├── repository/
│   ├── AuthRepository.kt              # Firebase Auth + Firestore users
│   └── AppointmentRepository.kt      # Firestore appointments (CRUD + Flows)
├── viewmodel/
│   ├── AuthViewModel.kt              # Estado de autenticación
│   └── AppointmentViewModel.kt       # Estado de citas
├── ui/
│   ├── theme/
│   │   └── Theme.kt                  # Colores, tipografía, tema Material 3
│   └── screens/
│       ├── Components.kt             # Componentes reutilizables
│       ├── LoginScreen.kt
│       ├── RegisterScreen.kt
│       ├── PatientHomeScreen.kt
│       ├── DoctorHomeScreen.kt
│       ├── BookAppointmentScreen.kt
│       ├── PendingAppointmentsScreen.kt
│       ├── StatisticsScreen.kt
│       └── HistoryScreen.kt
└── notification/
    └── NotificationService.kt        # WorkManager + FCM Service
```

### Patrón: MVVM + Repository

```
UI (Compose) ←→ ViewModel ←→ Repository ←→ Firebase
```

---

## 🗄️ Estructura de Firestore

### Colección: `users`
```json
{
  "uid": "string",
  "name": "string",
  "email": "string",
  "phone": "string",
  "role": "PATIENT | DOCTOR",
  "specialty": "string (solo para doctores)",
  "fcmToken": "string",
  "createdAt": "timestamp"
}
```

### Colección: `appointments`
```json
{
  "id": "string",
  "patientId": "string",
  "patientName": "string",
  "doctorId": "string",
  "doctorName": "string",
  "doctorSpecialty": "string",
  "dateTime": "timestamp",
  "reason": "string",
  "notes": "string",
  "status": "PENDING | CONFIRMED | COMPLETED | CANCELLED",
  "reminderSent": "boolean",
  "createdAt": "timestamp",
  "updatedAt": "timestamp"
}
```

---

## 🔔 Sistema de Recordatorios

Los recordatorios funcionan mediante **WorkManager** que ejecuta una tarea cada hora:

1. Consulta citas en las próximas 24 horas con `reminderSent = false`
2. Muestra una notificación local al paciente y al doctor
3. Marca la cita como `reminderSent = true`

Para recordatorios push en tiempo real se usa **Firebase Cloud Messaging (FCM)** — configura Cloud Functions en Firebase para enviar mensajes FCM automáticamente.

---

## 📱 Pantallas Principales

| Pantalla | Descripción |
|---|---|
| **Login** | Autenticación con email/password |
| **Registro** | Registro con rol: Paciente o Doctor |
| **Home Paciente** | Dashboard con acciones rápidas y citas recientes |
| **Home Doctor** | Panel con estadísticas del día y navegación |
| **Agendar Cita** | Selector de doctor, fecha/hora y motivo |
| **Citas Pendientes** | Lista en tiempo real con opciones de cancelar/confirmar |
| **Estadísticas** | Métricas con barras de progreso y tasas |
| **Historial** | Solo doctores: filtrado por fecha, estado y orden |

---

## 🛠️ Dependencias Principales

```kotlin
// Firebase
firebase-bom: 33.7.0
firebase-auth-ktx
firebase-firestore-ktx
firebase-messaging-ktx

// Compose + Navigation
androidx.navigation:navigation-compose: 2.8.4
androidx.compose.material3 (Material 3)
androidx.compose.material:material-icons-extended

// WorkManager
androidx.work:work-runtime-ktx: 2.10.0

// Lifecycle
lifecycle-viewmodel-compose: 2.8.7
```

---

## ⚙️ Permisos Requeridos

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
```

---

## 📝 Notas de Desarrollo

- El archivo `google-services.json` **no está incluido** — debes generarlo en Firebase Console
- El proyecto requiere **Android API 26+** (Android 8.0)
- Las reglas de Firestore deben desplegarse para que la app funcione correctamente
- Los índices compuestos de Firestore son necesarios para las consultas con múltiples filtros
