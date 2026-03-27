package com.medapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medapp.model.User
import com.medapp.model.UserRole
import com.medapp.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class DoctorsState {
    object Idle : DoctorsState()
    object Loading : DoctorsState()
    data class Success(val doctors: List<User>) : DoctorsState()
    data class Empty(val message: String = "No hay doctores registrados") : DoctorsState()
    data class Error(val message: String) : DoctorsState()
}

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUserProfile = MutableStateFlow<User?>(null)
    val currentUserProfile: StateFlow<User?> = _currentUserProfile.asStateFlow()

    private val _doctors = MutableStateFlow<List<User>>(emptyList())
    val doctors: StateFlow<List<User>> = _doctors.asStateFlow()

    private val _doctorsState = MutableStateFlow<DoctorsState>(DoctorsState.Idle)
    val doctorsState: StateFlow<DoctorsState> = _doctorsState.asStateFlow()

    init {
        checkAuthState()
    }

    private fun checkAuthState() {
        viewModelScope.launch {
            repository.authStateFlow.collect { firebaseUser ->
                if (firebaseUser == null) {
                    _authState.value = AuthState.Unauthenticated
                    _currentUserProfile.value = null
                } else {
                    loadUserProfile(firebaseUser.uid)
                }
            }
        }
    }

    private suspend fun loadUserProfile(uid: String) {
        repository.getUserProfile(uid).fold(
            onSuccess = { user ->
                _currentUserProfile.value = user
                _authState.value = AuthState.Authenticated(user)
                loadDoctors()
            },
            onFailure = {
                _authState.value = AuthState.Error("Error cargando perfil: ${it.message}")
            }
        )
    }

    fun register(email: String, password: String, name: String, phone: String,
                 role: UserRole, specialty: String = "") {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            repository.register(email, password, name, phone, role, specialty).fold(
                onSuccess = { user ->
                    _currentUserProfile.value = user
                    _authState.value = AuthState.Authenticated(user)
                    loadDoctors()
                },
                onFailure = {
                    _authState.value = AuthState.Error(it.message ?: "Error al registrar")
                }
            )
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            repository.login(email, password).fold(
                onSuccess = { firebaseUser -> loadUserProfile(firebaseUser.uid) },
                onFailure = {
                    _authState.value = AuthState.Error(it.message ?: "Error al iniciar sesion")
                }
            )
        }
    }

    fun logout() {
        repository.logout()
        _authState.value = AuthState.Unauthenticated
        _currentUserProfile.value = null
        _doctors.value = emptyList()
        _doctorsState.value = DoctorsState.Idle
    }

    fun loadDoctors() {
        viewModelScope.launch {
            _doctorsState.value = DoctorsState.Loading
            repository.getDoctors().fold(
                onSuccess = { list ->
                    _doctors.value = list
                    _doctorsState.value = if (list.isEmpty())
                        DoctorsState.Empty("No hay doctores registrados aun")
                    else
                        DoctorsState.Success(list)
                },
                onFailure = { error ->
                    _doctors.value = emptyList()
                    _doctorsState.value = DoctorsState.Error("Error: ${error.message}")
                }
            )
        }
    }
}
