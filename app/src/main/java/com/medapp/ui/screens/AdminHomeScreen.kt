package com.medapp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medapp.model.Appointment
import com.medapp.model.AppointmentStatus
import com.medapp.model.User
import com.medapp.model.UserRole
import com.medapp.viewmodel.AdminState
import com.medapp.viewmodel.AdminViewModel
import com.medapp.viewmodel.AuthViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminHomeScreen(
    authViewModel: AuthViewModel,
    adminViewModel: AdminViewModel,
    onLogout: () -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Usuarios", "Citas", "Historial", "Especialidades")

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Panel de Administración", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    actions = {
                        IconButton(onClick = onLogout) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Cerrar sesión")
                        }
                    }
                )
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    edgePadding = 0.dp
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTabIndex) {
                0 -> AdminUsersTab(adminViewModel)
                1 -> AdminAppointmentsTab(adminViewModel)
                2 -> AdminHistoryTab(adminViewModel)
                3 -> AdminSpecialtiesTab(adminViewModel)
            }
        }
    }
}

@Composable
fun AdminUsersTab(adminViewModel: AdminViewModel) {
    val users by adminViewModel.allUsers.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<User?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(users) { user ->
                UserCard(
                    user = user,
                    onClick = { selectedUser = user }
                )
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Crear Usuario")
        }
    }

    if (showCreateDialog) {
        CreateUserDialog(
            adminViewModel = adminViewModel,
            onDismiss = { showCreateDialog = false }
        )
    }

    selectedUser?.let { user ->
        EditUserDialog(
            user = user,
            adminViewModel = adminViewModel,
            onDismiss = { selectedUser = null }
        )
    }
}

@Composable
fun UserCard(user: User, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = user.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(text = user.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "Rol: ${user.role.name}", color = MaterialTheme.colorScheme.primary)
            if (user.role == UserRole.DOCTOR && user.specialty.isNotBlank()) {
                Text(text = "Especialidad: ${user.specialty}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AdminAppointmentsTab(adminViewModel: AdminViewModel) {
    val allAppointments by adminViewModel.allAppointments.collectAsState()
    val activeAppointments = allAppointments.filter { 
        it.status == AppointmentStatus.PENDING || it.status == AppointmentStatus.CONFIRMED 
    }
    
    val groupedByDoctor = activeAppointments.groupBy { it.doctorName }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (groupedByDoctor.isEmpty()) {
            item {
                Text("No hay citas activas agendadas.", modifier = Modifier.padding(16.dp))
            }
        }
        groupedByDoctor.forEach { (doctorName, appointments) ->
            stickyHeader {
                Text(
                    text = "Dr. $doctorName",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(vertical = 8.dp)
                )
            }
            items(appointments) { appointment ->
                AdminAppointmentCard(appointment)
            }
        }
    }
}

@Composable
fun AdminHistoryTab(adminViewModel: AdminViewModel) {
    val allAppointments by adminViewModel.allAppointments.collectAsState()
    val historyAppointments = allAppointments.filter { 
        it.status == AppointmentStatus.COMPLETED || it.status == AppointmentStatus.CANCELLED 
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (historyAppointments.isEmpty()) {
            item {
                Text("No hay historial de citas.", modifier = Modifier.padding(16.dp))
            }
        }
        items(historyAppointments) { appointment ->
            AdminAppointmentCard(appointment)
        }
    }
}

@Composable
fun AdminAppointmentCard(appointment: Appointment) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateString = dateFormat.format(appointment.dateTime.toDate())
    val statusColor = when(appointment.status) {
        AppointmentStatus.CONFIRMED -> MaterialTheme.colorScheme.primary
        AppointmentStatus.PENDING -> MaterialTheme.colorScheme.secondary
        AppointmentStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
        AppointmentStatus.CANCELLED -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = dateString, fontWeight = FontWeight.Bold)
                Text(text = appointment.status.displayName(), color = statusColor, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Paciente: ${appointment.patientName}")
            Text(text = "Dr: ${appointment.doctorName} (${appointment.doctorSpecialty})")
            if (appointment.reason.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Motivo: ${appointment.reason}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditUserDialog(user: User, adminViewModel: AdminViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val doctors by adminViewModel.doctors.collectAsState()

    var name by remember { mutableStateOf(user.name) }
    var phone by remember { mutableStateOf(user.phone) }
    var email by remember { mutableStateOf(user.email) }
    var specialty by remember { mutableStateOf(user.specialty) }

    val userRole = user.role
    val selectedDoctorIds = remember { mutableStateListOf(*user.assignedDoctorIds.toTypedArray()) }
    var doctorsDropdownExpanded by remember { mutableStateOf(false) }

    val specialtiesState by adminViewModel.specialtiesState.collectAsState()
    var specialtyDropdownExpanded by remember { mutableStateOf(false) }

    // Estado para el envío de correo de restablecimiento de contraseña
    var passwordResetMessage by remember { mutableStateOf<String?>(null) }
    var sendingReset by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Usuario") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Etiqueta de rol (solo informativo, no se cambia aquí)
                Text(
                    text = "Rol: ${userRole.name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Teléfono") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo electrónico") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true
                )

                // Doctor asignado — solo para recepcionistas
                if (userRole == UserRole.RECEPTIONIST) {
                    Text("Asignar a Doctores:", style = MaterialTheme.typography.labelMedium)
                    ExposedDropdownMenuBox(
                        expanded = doctorsDropdownExpanded,
                        onExpandedChange = { doctorsDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (selectedDoctorIds.isEmpty()) "Seleccionar doctores" else "${selectedDoctorIds.size} seleccionados",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = doctorsDropdownExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = doctorsDropdownExpanded,
                            onDismissRequest = { doctorsDropdownExpanded = false }
                        ) {
                            doctors.forEach { doc ->
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = selectedDoctorIds.contains(doc.uid),
                                                onCheckedChange = null
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(doc.name)
                                        }
                                    },
                                    onClick = {
                                        if (selectedDoctorIds.contains(doc.uid)) selectedDoctorIds.remove(doc.uid)
                                        else selectedDoctorIds.add(doc.uid)
                                    }
                                )
                            }
                        }
                    }
                    if (selectedDoctorIds.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedDoctorIds.forEach { id ->
                                val docName = doctors.find { it.uid == id }?.name ?: "Desconocido"
                                InputChip(
                                    selected = true,
                                    onClick = { selectedDoctorIds.remove(id) },
                                    label = { Text(docName) },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Eliminar", modifier = Modifier.size(16.dp)) }
                                )
                            }
                        }
                    }
                }

                // Especialidad — solo para doctores
                if (userRole == UserRole.DOCTOR) {
                    when (val state = specialtiesState) {
                        is com.medapp.viewmodel.SpecialtiesState.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                        is com.medapp.viewmodel.SpecialtiesState.Success -> {
                            ExposedDropdownMenuBox(
                                expanded = specialtyDropdownExpanded,
                                onExpandedChange = { specialtyDropdownExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = specialty.ifBlank { "Seleccione una especialidad" },
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Especialidad") },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = specialtyDropdownExpanded) }
                                )
                                ExposedDropdownMenu(
                                    expanded = specialtyDropdownExpanded,
                                    onDismissRequest = { specialtyDropdownExpanded = false }
                                ) {
                                    state.specialties.forEach { spec ->
                                        DropdownMenuItem(
                                            text = { Text(spec.name) },
                                            onClick = {
                                                specialty = spec.name
                                                specialtyDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            OutlinedTextField(
                                value = specialty,
                                onValueChange = { specialty = it },
                                label = { Text("Especialidad") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Sección de contraseña
                Text(
                    text = "Contraseña",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Se enviará un correo de restablecimiento de contraseña al usuario.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        sendingReset = true
                        passwordResetMessage = null
                        adminViewModel.updateUserPassword(
                            context = context,
                            userEmail = user.email,
                            newPassword = "",
                            onResult = { success, message ->
                                sendingReset = false
                                passwordResetMessage = message
                            }
                        )
                    },
                    enabled = !sendingReset && user.email.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (sendingReset) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Enviar correo de restablecimiento")
                }

                passwordResetMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (msg.startsWith("Se envió")) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    adminViewModel.updateUser(
                        user.copy(
                            name = name,
                            phone = phone,
                            email = email,
                            assignedDoctorIds = selectedDoctorIds.toList(),
                            specialty = specialty
                        )
                    )
                    onDismiss()
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    adminViewModel.deleteUser(user.uid)
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Eliminar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateUserDialog(adminViewModel: AdminViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val doctors by adminViewModel.doctors.collectAsState()
    val adminState by adminViewModel.adminState.collectAsState()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.DOCTOR) }
    var specialty by remember { mutableStateOf("") }
    
    val selectedDoctorIds = remember { mutableStateListOf<String>() }
    var doctorsDropdownExpanded by remember { mutableStateOf(false) }
    
    val specialtiesState by adminViewModel.specialtiesState.collectAsState()
    var specialtyDropdownExpanded by remember { mutableStateOf(false) }

    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(adminState) {
        if (adminState is AdminState.Success) {
            adminViewModel.resetState()
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear Nuevo Usuario") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedRole == UserRole.DOCTOR,
                        onClick = { selectedRole = UserRole.DOCTOR },
                        label = { Text("Doctor") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedRole == UserRole.RECEPTIONIST,
                        onClick = { selectedRole = UserRole.RECEPTIONIST },
                        label = { Text("Recepcionista") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Teléfono") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true
                )

                if (selectedRole == UserRole.DOCTOR) {
                    when (val state = specialtiesState) {
                        is com.medapp.viewmodel.SpecialtiesState.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                        is com.medapp.viewmodel.SpecialtiesState.Success -> {
                            if (state.specialties.isEmpty()) {
                                Text(
                                    "Debe crear al menos una especialidad antes de registrar doctores.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                ExposedDropdownMenuBox(
                                    expanded = specialtyDropdownExpanded,
                                    onExpandedChange = { specialtyDropdownExpanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = specialty.ifBlank { "Seleccione una especialidad" },
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Especialidad") },
                                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = specialtyDropdownExpanded) }
                                    )
                                    ExposedDropdownMenu(
                                        expanded = specialtyDropdownExpanded,
                                        onDismissRequest = { specialtyDropdownExpanded = false }
                                    ) {
                                        state.specialties.forEach { spec ->
                                            DropdownMenuItem(
                                                text = { Text(spec.name) },
                                                onClick = {
                                                    specialty = spec.name
                                                    specialtyDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            OutlinedTextField(
                                value = specialty,
                                onValueChange = { specialty = it },
                                label = { Text("Especialidad") },
                                singleLine = true
                            )
                        }
                    }
                }

                if (selectedRole == UserRole.RECEPTIONIST) {
                    Text("Asignar a Doctores:", style = MaterialTheme.typography.labelMedium)
                    ExposedDropdownMenuBox(
                        expanded = doctorsDropdownExpanded,
                        onExpandedChange = { doctorsDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (selectedDoctorIds.isEmpty()) "Seleccionar doctores" else "${selectedDoctorIds.size} seleccionados",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = doctorsDropdownExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = doctorsDropdownExpanded,
                            onDismissRequest = { doctorsDropdownExpanded = false }
                        ) {
                            doctors.forEach { doc ->
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = selectedDoctorIds.contains(doc.uid),
                                                onCheckedChange = null
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(doc.name)
                                        }
                                    },
                                    onClick = {
                                        if (selectedDoctorIds.contains(doc.uid)) selectedDoctorIds.remove(doc.uid)
                                        else selectedDoctorIds.add(doc.uid)
                                    }
                                )
                            }
                        }
                    }
                    if (selectedDoctorIds.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedDoctorIds.forEach { id ->
                                val docName = doctors.find { it.uid == id }?.name ?: "Desconocido"
                                InputChip(
                                    selected = true,
                                    onClick = { selectedDoctorIds.remove(id) },
                                    label = { Text(docName) },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Eliminar", modifier = Modifier.size(16.dp)) }
                                )
                            }
                        }
                    }
                }

                if (adminState is AdminState.Error) {
                    Text(
                        text = (adminState as AdminState.Error).error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            val isValid = name.isNotBlank() && email.isNotBlank() && phone.isNotBlank() && password.isNotBlank() &&
                    (selectedRole == UserRole.RECEPTIONIST && selectedDoctorIds.isNotEmpty() || selectedRole == UserRole.DOCTOR && specialty.isNotBlank())
            
            Button(
                onClick = {
                    adminViewModel.createUser(
                        context = context,
                        email = email,
                        password = password,
                        name = name,
                        phone = phone,
                        role = selectedRole,
                        specialty = if (selectedRole == UserRole.DOCTOR) specialty else "",
                        assignedDoctorIds = if (selectedRole == UserRole.RECEPTIONIST) selectedDoctorIds.toList() else emptyList()
                    )
                },
                enabled = isValid && adminState !is AdminState.Loading
            ) {
                if (adminState is AdminState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Crear")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// ─── Admin Specialties Tab ───────────────────────────────────────────────────

@Composable
fun AdminSpecialtiesTab(adminViewModel: AdminViewModel) {
    val specialtiesState by adminViewModel.specialtiesState.collectAsState()
    val opState by adminViewModel.specialtyOpState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var specialtyToEdit by remember { mutableStateOf<com.medapp.model.Specialty?>(null) }
    var specialtyToDelete by remember { mutableStateOf<com.medapp.model.Specialty?>(null) }

    // Handle operation messages
    LaunchedEffect(opState) {
        when (opState) {
            is com.medapp.viewmodel.SpecialtyOperationState.Success -> {
                adminViewModel.resetSpecialtyOpState()
            }
            is com.medapp.viewmodel.SpecialtyOperationState.Error -> {
                // We'll show the error inside the dialogs if needed, or rely on a snackbar
            }
            else -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = specialtiesState) {
            is com.medapp.viewmodel.SpecialtiesState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is com.medapp.viewmodel.SpecialtiesState.Error -> {
                Text("Error: ${state.error}", color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
            }
            is com.medapp.viewmodel.SpecialtiesState.Success -> {
                if (state.specialties.isEmpty()) {
                    Text("No hay especialidades registradas.", modifier = Modifier.align(Alignment.Center).padding(16.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.specialties) { specialty ->
                            SpecialtyCard(
                                specialty = specialty,
                                onEdit = { specialtyToEdit = specialty },
                                onDelete = { specialtyToDelete = specialty }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Crear Especialidad")
        }
    }

    if (showCreateDialog) {
        SpecialtyDialog(
            title = "Nueva Especialidad",
            initialName = "",
            opState = opState,
            onDismiss = { 
                showCreateDialog = false
                adminViewModel.resetSpecialtyOpState()
            },
            onConfirm = { name -> adminViewModel.addSpecialty(name) }
        )
    }

    specialtyToEdit?.let { specialty ->
        SpecialtyDialog(
            title = "Editar Especialidad",
            initialName = specialty.name,
            opState = opState,
            onDismiss = { 
                specialtyToEdit = null
                adminViewModel.resetSpecialtyOpState()
            },
            onConfirm = { name -> 
                adminViewModel.updateSpecialty(specialty.id, name, specialty.name) 
                specialtyToEdit = null
            }
        )
    }

    specialtyToDelete?.let { specialty ->
        AlertDialog(
            onDismissRequest = { 
                specialtyToDelete = null
                adminViewModel.resetSpecialtyOpState()
            },
            title = { Text("Eliminar Especialidad") },
            text = { 
                Column {
                    Text("¿Estás seguro de que deseas eliminar '${specialty.name}'?")
                    if (opState is com.medapp.viewmodel.SpecialtyOperationState.Error) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = (opState as com.medapp.viewmodel.SpecialtyOperationState.Error).error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { adminViewModel.deleteSpecialty(specialty.id, specialty.name) },
                    enabled = opState !is com.medapp.viewmodel.SpecialtyOperationState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (opState is com.medapp.viewmodel.SpecialtyOperationState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onError)
                    } else {
                        Text("Eliminar")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    specialtyToDelete = null
                    adminViewModel.resetSpecialtyOpState()
                }) {
                    Text("Cancelar")
                }
            }
        )
        // Auto-dismiss on success
        if (opState is com.medapp.viewmodel.SpecialtyOperationState.Success) {
            specialtyToDelete = null
            adminViewModel.resetSpecialtyOpState()
        }
    }
}

@Composable
fun SpecialtyCard(
    specialty: com.medapp.model.Specialty,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = specialty.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun SpecialtyDialog(
    title: String,
    initialName: String,
    opState: com.medapp.viewmodel.SpecialtyOperationState,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    // Auto-dismiss on success
    LaunchedEffect(opState) {
        if (opState is com.medapp.viewmodel.SpecialtyOperationState.Success) {
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de la especialidad") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (opState is com.medapp.viewmodel.SpecialtyOperationState.Error) {
                    Text(
                        text = (opState as com.medapp.viewmodel.SpecialtyOperationState.Error).error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank() && opState !is com.medapp.viewmodel.SpecialtyOperationState.Loading
            ) {
                if (opState is com.medapp.viewmodel.SpecialtyOperationState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Guardar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
