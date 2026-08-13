package com.medapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.medapp.model.User
import com.medapp.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.medapp.model.Specialty
import com.medapp.repository.SpecialtyRepository

sealed class AdminState {
    object Idle : AdminState()
    object Loading : AdminState()
    data class Success(val message: String) : AdminState()
    data class Error(val error: String) : AdminState()
}

sealed class SpecialtiesState {
    object Loading : SpecialtiesState()
    data class Success(val specialties: List<Specialty>) : SpecialtiesState()
    data class Error(val error: String) : SpecialtiesState()
}

sealed class SpecialtyOperationState {
    object Idle : SpecialtyOperationState()
    object Loading : SpecialtyOperationState()
    data class Success(val message: String) : SpecialtyOperationState()
    data class Error(val error: String) : SpecialtyOperationState()
}

class AdminViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val _adminState = MutableStateFlow<AdminState>(AdminState.Idle)
    val adminState: StateFlow<AdminState> = _adminState.asStateFlow()

    // consulta para todos los doctores (para asignar recepcionista)
    private val _doctors = MutableStateFlow<List<User>>(emptyList())
    val doctors: StateFlow<List<User>> = _doctors.asStateFlow()

    // consulta para todos los pacientes (para el modulo de usuarios)
    private val _allUsers = MutableStateFlow<List<User>>(emptyList())
    val allUsers: StateFlow<List<User>> = _allUsers.asStateFlow()

    // citas en el panel del administrador
    private val appointmentRepository = com.medapp.repository.AppointmentRepository()
    private val _allAppointments = MutableStateFlow<List<com.medapp.model.Appointment>>(emptyList())
    val allAppointments: StateFlow<List<com.medapp.model.Appointment>> = _allAppointments.asStateFlow()

    // Especialidades
    private val specialtyRepository = SpecialtyRepository()
    private val _specialtiesState = MutableStateFlow<SpecialtiesState>(SpecialtiesState.Loading)
    val specialtiesState: StateFlow<SpecialtiesState> = _specialtiesState.asStateFlow()

    private val _specialtyOpState = MutableStateFlow<SpecialtyOperationState>(SpecialtyOperationState.Idle)
    val specialtyOpState: StateFlow<SpecialtyOperationState> = _specialtyOpState.asStateFlow()

    init {
        loadDoctors()
        loadAllUsers()
        loadAllAppointments()
        loadSpecialties()
    }

    private fun loadAllAppointments() {
        viewModelScope.launch {
            appointmentRepository.getAllAppointmentsFlow().collect { appointments ->
                _allAppointments.value = appointments
            }
        }
    }

    private fun loadAllUsers() {
        viewModelScope.launch {
            try {
                firestore.collection("users").addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    _allUsers.value = snapshot?.documents?.mapNotNull { doc ->
                        doc.data?.let { User.fromMap(it) }
                    } ?: emptyList()
                }
            } catch (e: Exception) {
                // omitir
            }
        }
    }

    private fun loadDoctors() {
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("users")
                    .whereEqualTo("role", UserRole.DOCTOR.name)
                    .get()
                    .await()
                _doctors.value = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { User.fromMap(it) }
                }
            } catch (e: Exception) {
                // omitir
            }
        }
    }

    fun createUser(
        context: Context,
        email: String,
        password: String,
        name: String,
        phone: String,
        role: UserRole,
        specialty: String = "",
        specialtyId: String = "",
        schedule: com.medapp.model.DoctorSchedule? = null,
        assignedDoctorIds: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            _adminState.value = AdminState.Loading
            try {
                // Aplicación secundaria de Firebase para evitar que el administrador actual cierre sesión.
                val primaryApp = FirebaseApp.getInstance()
                val secondaryAppName = "SecondaryApp_${System.currentTimeMillis()}"
                val secondaryApp = FirebaseApp.initializeApp(context, primaryApp.options, secondaryAppName)

                val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)
                val authResult = secondaryAuth.createUserWithEmailAndPassword(email, password).await()
                val newUserId = authResult.user?.uid

                if (newUserId != null) {
                    val newUser = User(
                        uid = newUserId,
                        name = name,
                        email = email,
                        phone = phone,
                        role = role,
                        specialty = specialty,
                        specialtyId = specialtyId,
                        schedule = schedule,
                        assignedDoctorIds = assignedDoctorIds
                    )
                    
                    // Guardar usuario en Firestore usando la aplicación secundaria para que request.auth.uid == userId
                    val secondaryFirestore = FirebaseFirestore.getInstance(secondaryApp)
                    secondaryFirestore.collection("users").document(newUserId).set(newUser.toMap()).await()
                    
                    _adminState.value = AdminState.Success("Usuario creado exitosamente")
                    if (role == UserRole.DOCTOR) {
                        loadDoctors() // actualiza la lista si un usuario nuevo se crea
                    }
                } else {
                    _adminState.value = AdminState.Error("No se pudo obtener el ID del usuario")
                }
                
                // limpiar la aplicación secundaria
                secondaryApp.delete()

            } catch (e: Exception) {
                _adminState.value = AdminState.Error(e.message ?: "Error al crear usuario")
            }
        }
    }

    fun updateUser(user: User) {
        viewModelScope.launch {
            _adminState.value = AdminState.Loading
            try {
                firestore.collection("users").document(user.uid).set(user.toMap()).await()
                _adminState.value = AdminState.Success("Usuario actualizado exitosamente")
                loadDoctors()
            } catch (e: Exception) {
                _adminState.value = AdminState.Error(e.message ?: "Error al actualizar usuario")
            }
        }
    }

    fun updateUserPassword(context: Context, userEmail: String, newPassword: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                // Usa una app secundaria para iniciar sesión con las credenciales del usuario
                // y cambiar su contraseña sin afectar la sesión del administrador
                val primaryApp = FirebaseApp.getInstance()
                val secondaryAppName = "SecondaryApp_pwd_${System.currentTimeMillis()}"
                val secondaryApp = FirebaseApp.initializeApp(context, primaryApp.options, secondaryAppName)
                val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)

                // Para cambiar la contraseña necesitamos iniciar sesión como ese usuario.
                // Como el admin no conoce la contraseña actual, usamos el Admin SDK a través
                // de una función de Cloud Functions o enviamos un correo de restablecimiento.
                // En este caso enviamos un correo de restablecimiento de contraseña.
                FirebaseAuth.getInstance().sendPasswordResetEmail(userEmail).await()
                secondaryApp.delete()
                onResult(true, "Se envió un correo de restablecimiento a $userEmail")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Error al enviar correo de restablecimiento")
            }
        }
    }

    fun deleteUser(userId: String) {
        viewModelScope.launch {
            _adminState.value = AdminState.Loading
            try {
                // Nota: Esto solo elimina los datos de Firestore. Para eliminarlos de Firebase Auth se requiere
                // el SDK de administración o que el usuario haya iniciado sesión. Por ahora, solo eliminamos sus datos.
                firestore.collection("users").document(userId).delete().await()
                _adminState.value = AdminState.Success("Usuario eliminado exitosamente")
                loadDoctors()
            } catch (e: Exception) {
                _adminState.value = AdminState.Error(e.message ?: "Error al eliminar usuario")
            }
        }
    }

    fun resetState() {
        _adminState.value = AdminState.Idle
    }

    // ─── Specialties Management ────────────────────────────────────────────────
    
    private fun loadSpecialties() {
        viewModelScope.launch {
            try {
                specialtyRepository.getSpecialtiesFlow()
                    .catch { e -> 
                        _specialtiesState.value = SpecialtiesState.Error(e.message ?: "Error al cargar especialidades") 
                    }
                    .collect { result ->
                        _specialtiesState.value = SpecialtiesState.Success(result)
                    }
            } catch (e: Exception) {
                _specialtiesState.value = SpecialtiesState.Error(e.message ?: "Error al cargar especialidades")
            }
        }
    }

    fun addSpecialty(name: String) {
        viewModelScope.launch {
            _specialtyOpState.value = SpecialtyOperationState.Loading
            specialtyRepository.addSpecialty(name).fold(
                onSuccess = {
                    _specialtyOpState.value = SpecialtyOperationState.Success("Especialidad creada exitosamente")
                },
                onFailure = { error ->
                    _specialtyOpState.value = SpecialtyOperationState.Error(error.message ?: "Error al crear especialidad")
                }
            )
        }
    }

    fun updateSpecialty(id: String, name: String, oldName: String) {
        viewModelScope.launch {
            _specialtyOpState.value = SpecialtyOperationState.Loading
            specialtyRepository.updateSpecialty(id, name, oldName).fold(
                onSuccess = {
                    _specialtyOpState.value = SpecialtyOperationState.Success("Especialidad actualizada exitosamente")
                    // If we updated doctor specialties, we should refresh the doctor list
                    if (oldName != name) {
                        loadDoctors()
                    }
                },
                onFailure = { error ->
                    _specialtyOpState.value = SpecialtyOperationState.Error(error.message ?: "Error al actualizar especialidad")
                }
            )
        }
    }

    fun deleteSpecialty(id: String, name: String) {
        viewModelScope.launch {
            _specialtyOpState.value = SpecialtyOperationState.Loading
            specialtyRepository.deleteSpecialty(id, name).fold(
                onSuccess = {
                    _specialtyOpState.value = SpecialtyOperationState.Success("Especialidad eliminada exitosamente")
                },
                onFailure = { error ->
                    _specialtyOpState.value = SpecialtyOperationState.Error(error.message ?: "Error al eliminar especialidad")
                }
            )
        }
    }

    fun resetSpecialtyOpState() {
        _specialtyOpState.value = SpecialtyOperationState.Idle
    }
}
