package com.vineyard.fastgit.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vineyard.fastgit.app.models.Repository
import com.vineyard.fastgit.app.ui.theme.*
import com.vineyard.fastgit.app.viewmodel.AuthViewModel
import com.vineyard.fastgit.app.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    authViewModel: AuthViewModel,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val cacheSize by settingsViewModel.cacheSize.collectAsState()

    // Propagation Feature States
    val repositories by settingsViewModel.repositories.collectAsState()
    val savedAliases by settingsViewModel.savedAliases.collectAsState()
    val statusMessage by settingsViewModel.statusMessage.collectAsState()
    val isLoading by settingsViewModel.isLoading.collectAsState()

    var showLogoutDialog by remember { mutableStateOf(false) }

    // local Form Input States
    var showCreateForm by remember { mutableStateOf(false) }
    var aliasInput by remember { mutableStateOf("") }
    var keystoreBase64Input by remember { mutableStateOf("") }
    var keystorePasswordInput by remember { mutableStateOf("") }
    var keyAliasInput by remember { mutableStateOf("") }
    var keyPasswordInput by remember { mutableStateOf("") }

    // local Dropdowns States
    var selectedRepo by remember { mutableStateOf<Repository?>(null) }
    var selectedAlias by remember { mutableStateOf("") }
    var repoDropdownExpanded by remember { mutableStateOf(false) }
    var aliasDropdownExpanded by remember { mutableStateOf(false) }

    // Status / Messages Handler
    statusMessage?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            settingsViewModel.clearStatus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GhBgDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        // General Preferences Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = GhSurfaceDark),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("App Preferences", fontWeight = FontWeight.Bold, color = GhAccentBlue, fontSize = 14.sp)

                SettingsRow(
                    icon = Icons.Default.Palette,
                    title = "Theme Mode",
                    subtitle = themeMode,
                    onClick = {
                        val next = when (themeMode) {
                            "Dark" -> "Light"
                            "Light" -> "System"
                            else -> "Dark"
                        }
                        settingsViewModel.setTheme(next)
                    }
                )

                Divider(color = GhCardBorderDark)

                SettingsRow(
                    icon = Icons.Default.FolderZip,
                    title = "Downloads Directory",
                    subtitle = "Internal Storage / Downloads / FastGit",
                    onClick = {}
                )

                Divider(color = GhCardBorderDark)

                SettingsRow(
                    icon = Icons.Default.CleaningServices,
                    title = "Clear Cache",
                    subtitle = "Cached trees and offline DB ($cacheSize)",
                    onClick = { settingsViewModel.clearCache() }
                )
            }
        }

        // Keystore & Secrets Propagation Manager Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = GhSurfaceDark),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Automated Keystore Secrets",
                    fontWeight = FontWeight.Bold,
                    color = GhAccentBlue,
                    fontSize = 14.sp
                )

                Text(
                    text = "Persist build credentials locally and auto-propagate them as Actions Secrets to newly imported repositories.",
                    fontSize = 12.sp,
                    color = GhTextSecondaryDark
                )

                Divider(color = GhCardBorderDark)

                // Sub-Section 1: Propagate to GitHub Repository Form
                Text(
                    text = "Propagate Credentials to GitHub",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White
                )

                // Select Target Repository Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { repoDropdownExpanded = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedRepo?.fullName ?: "Select Target Repository",
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = GhAccentBlue)
                        }
                    }
                    DropdownMenu(
                        expanded = repoDropdownExpanded,
                        onDismissRequest = { repoDropdownExpanded = false },
                        modifier = Modifier
                            .background(GhSurfaceDark)
                            .fillMaxWidth(0.85f)
                    ) {
                        repositories.forEach { repo ->
                            DropdownMenuItem(
                                text = { Text(repo.fullName, color = Color.White) },
                                onClick = {
                                    selectedRepo = repo
                                    repoDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Select Keystore Profile Alias Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { aliasDropdownExpanded = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedAlias.isEmpty()) "Select Keystore Alias Profile" else selectedAlias,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = GhAccentBlue)
                        }
                    }
                    DropdownMenu(
                        expanded = aliasDropdownExpanded,
                        onDismissRequest = { aliasDropdownExpanded = false },
                        modifier = Modifier
                            .background(GhSurfaceDark)
                            .fillMaxWidth(0.85f)
                    ) {
                        savedAliases.forEach { alias ->
                            DropdownMenuItem(
                                text = { Text(alias, color = Color.White) },
                                onClick = {
                                    selectedAlias = alias
                                    aliasDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Deploy Propagation Action Button
                Button(
                    onClick = {
                        selectedRepo?.let { repo ->
                            settingsViewModel.propagateKeystoreToRepository(
                                targetRepoOwner = repo.owner?.login ?: "",
                                targetRepoName = repo.name,
                                profileAlias = selectedAlias
                            )
                        }
                    },
                    enabled = selectedRepo != null && selectedAlias.isNotEmpty() && !isLoading,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GhSuccessGreen,
                        disabledContainerColor = GhCardBorderDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Propagate Keystore Secrets", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Divider(color = GhCardBorderDark)

                // Sub-Section 2: Expandable Form to Save a New Keystore local Configuration Profile
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCreateForm = !showCreateForm },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = GhAccentBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add New Keystore Profile", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                    }
                    Icon(
                        imageVector = if (showCreateForm) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = GhTextSecondaryDark
                    )
                }

                if (showCreateForm) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = aliasInput,
                            onValueChange = { aliasInput = it },
                            label = { Text("Profile Alias Name (e.g. MyKeystore)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = keystoreBase64Input,
                            onValueChange = { keystoreBase64Input = it },
                            label = { Text("Keystore Base64 String") },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = keystorePasswordInput,
                            onValueChange = { keystorePasswordInput = it },
                            label = { Text("Keystore Password") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = keyAliasInput,
                            onValueChange = { keyAliasInput = it },
                            label = { Text("Key Alias") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = keyPasswordInput,
                            onValueChange = { keyPasswordInput = it },
                            label = { Text("Key Password") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                settingsViewModel.saveKeystoreProfile(
                                    alias = aliasInput.trim(),
                                    keystoreBase64 = keystoreBase64Input.trim(),
                                    keystorePassword = keystorePasswordInput.trim(),
                                    keyAlias = keyAliasInput.trim(),
                                    keyPassword = keyPasswordInput.trim()
                                )
                                // Clear inputs on safe save
                                aliasInput = ""
                                keystoreBase64Input = ""
                                keystorePasswordInput = ""
                                keyAliasInput = ""
                                keyPasswordInput = ""
                                showCreateForm = false
                            },
                            enabled = aliasInput.isNotBlank() && keystoreBase64Input.isNotBlank() && keystorePasswordInput.isNotBlank(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GhAccentBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Keystore Profile", fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        }

        // Account & Security Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = GhSurfaceDark),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Account & Security", fontWeight = FontWeight.Bold, color = GhAccentBlue, fontSize = 14.sp)

                SettingsRow(
                    icon = Icons.Default.Security,
                    title = "Authentication Token",
                    subtitle = if (authViewModel.tokenManager.isDemoMode()) "Demo Account" else "Encrypted Token Stored",
                    onClick = {}
                )

                Divider(color = GhCardBorderDark)

                SettingsRow(
                    icon = Icons.Default.ExitToApp,
                    title = "Log Out",
                    subtitle = "Disconnect current account session",
                    iconTint = GhErrorRed,
                    onClick = { showLogoutDialog = true }
                )
            }
        }

        // About & Version Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = GhSurfaceDark),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("About FastGit", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                Text("Version 1.0.0 (Build 100)", color = GhTextSecondaryDark, fontSize = 13.sp)
                Text("Built with Kotlin, Coroutines & Jetpack Compose for Android", color = GhTextSecondaryDark, fontSize = 12.sp)
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out", color = Color.White) },
            text = { Text("Are you sure you want to log out of FastGit?", color = GhTextSecondaryDark) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        authViewModel.logout()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GhErrorRed)
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = GhSurfaceDark
        )
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = GhAccentBlue,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 15.sp)
            Text(subtitle, color = GhTextSecondaryDark, fontSize = 12.sp)
        }
    }
}