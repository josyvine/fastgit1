package com.vineyard.fastgit.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vineyard.fastgit.app.R
import com.vineyard.fastgit.app.ui.theme.GhAccentBlue
import com.vineyard.fastgit.app.ui.theme.GhPrimaryViolet
import com.vineyard.fastgit.app.viewmodel.AuthViewModel

@Composable
fun AuthScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val isLoading by authViewModel.isLoading.collectAsState()
    val errorMessage by authViewModel.errorMessage.collectAsState()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

    val isDeviceFlowLoading by authViewModel.isDeviceFlowLoading.collectAsState()
    val deviceCodeState by authViewModel.deviceCodeState.collectAsState()

    var showManualCodeDialog by remember { mutableStateOf(false) }
    var manualCodeInput by remember { mutableStateOf("") }

    var showOauthConfigDialog by remember { mutableStateOf(false) }
    var oauthClientIdInput by remember { mutableStateOf(authViewModel.tokenManager.getOAuthClientId()) }
    var oauthClientSecretInput by remember { mutableStateOf(authViewModel.tokenManager.getOAuthClientSecret()) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D1117),
                        Color(0xFF161B22)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // App Logo Icon Frame
            Surface(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp)),
                color = GhPrimaryViolet.copy(alpha = 0.2f),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "FastGit Logo",
                        modifier = Modifier.size(54.dp),
                        tint = GhAccentBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "FastGit Workspace",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Full Android GitHub Client & File Manager",
                fontSize = 14.sp,
                color = Color(0xFF8B949E),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF21262D)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Sign in with GitHub",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 18.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Text(
                        text = "Authorize via official GitHub OAuth2 portal",
                        fontSize = 13.sp,
                        color = Color(0xFF8B949E),
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(top = 4.dp, bottom = 20.dp)
                    )

                    // Primary Official OAuth Button
                    Button(
                        onClick = {
                            authViewModel.clearError()
                            val authorizeUrl = authViewModel.getOAuthAuthorizeUrl()
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authorizeUrl))
                            context.startActivity(intent)
                        },
                        enabled = !isLoading && !isDeviceFlowLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF238636),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_github),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Sign in with GitHub",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 3: Sign in with GitHub (Device Flow)
                    OutlinedButton(
                        onClick = {
                            authViewModel.clearError()
                            authViewModel.startDeviceFlow()
                        },
                        enabled = !isLoading && !isDeviceFlowLoading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = GhAccentBlue
                        ),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (isDeviceFlowLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = GhAccentBlue,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = GhAccentBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sign in with GitHub (Device Flow)",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(
                            onClick = { showManualCodeDialog = true }
                        ) {
                            Text(
                                text = "Enter OAuth Code",
                                color = GhAccentBlue,
                                fontSize = 12.sp
                            )
                        }

                        TextButton(
                            onClick = {
                                oauthClientIdInput = authViewModel.tokenManager.getOAuthClientId()
                                oauthClientSecretInput = authViewModel.tokenManager.getOAuthClientSecret()
                                showOauthConfigDialog = true
                            }
                        ) {
                            Text(
                                text = "Configure OAuth App ID",
                                color = Color(0xFF8B949E),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Guest / Demo mode button
                    OutlinedButton(
                        onClick = { authViewModel.enableDemoMode() },
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = GhPrimaryViolet)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Explore in Demo / Guest Mode", color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }

            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = Color(0xFF3D1314),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        color = Color(0xFFFF7B72),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (showManualCodeDialog) {
        AlertDialog(
            onDismissRequest = { showManualCodeDialog = false },
            title = { Text("Manual OAuth Authorization Code", color = Color.White) },
            text = {
                Column {
                    Text(
                        text = "If browser redirect did not auto-return to app, copy the 'code' parameter from the URL bar (fastgit://oauth-callback?code=...) and paste it below:",
                        color = Color(0xFFC9D1D9),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = manualCodeInput,
                        onValueChange = { manualCodeInput = it },
                        label = { Text("OAuth Code") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GhAccentBlue,
                            unfocusedBorderColor = Color(0xFF30363D),
                            focusedLabelColor = GhAccentBlue,
                            unfocusedLabelColor = Color(0xFF8B949E),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val code = manualCodeInput.trim()
                        if (code.isNotEmpty()) {
                            showManualCodeDialog = false
                            authViewModel.handleOAuthCode(code)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GhAccentBlue)
                ) {
                    Text("Submit Code", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualCodeDialog = false }) {
                    Text("Cancel", color = Color(0xFF8B949E))
                }
            },
            containerColor = Color(0xFF21262D)
        )
    }

    deviceCodeState?.let { deviceState ->
        AlertDialog(
            onDismissRequest = { authViewModel.cancelDeviceFlow() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = GhAccentBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GitHub Device Flow", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Enter this user code on GitHub to authorize FastGit Workspace:",
                        color = Color(0xFFC9D1D9),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // User Code Box
                    Surface(
                        color = Color(0xFF161B22),
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = deviceState.userCode ?: "----",
                            color = GhAccentBlue,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Verification URL
                    Text(
                        text = "Verification URL:\n${deviceState.verificationUri ?: "https://github.com/login/device"}",
                        color = Color(0xFF8B949E),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Button: Open GitHub Verification Page
                    Button(
                        onClick = {
                            val targetUri = deviceState.verificationUriComplete ?: deviceState.verificationUri ?: "https://github.com/login/device"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUri))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_github),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open GitHub Verification Page", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = GhAccentBlue,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Waiting for authorization on GitHub...",
                            color = Color(0xFF8B949E),
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { authViewModel.cancelDeviceFlow() }) {
                    Text("Cancel", color = Color(0xFFFF7B72))
                }
            },
            containerColor = Color(0xFF21262D)
        )
    }

    if (showOauthConfigDialog) {
        AlertDialog(
            onDismissRequest = { showOauthConfigDialog = false },
            title = { Text("Configure GitHub OAuth App", color = Color.White) },
            text = {
                Column {
                    Text(
                        text = "To use Device Flow or official OAuth with your own GitHub registered App, enter your GitHub Client ID and Client Secret (from github.com/settings/developers):",
                        color = Color(0xFFC9D1D9),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = oauthClientIdInput,
                        onValueChange = { oauthClientIdInput = it },
                        label = { Text("Client ID") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GhAccentBlue,
                            unfocusedBorderColor = Color(0xFF30363D),
                            focusedLabelColor = GhAccentBlue,
                            unfocusedLabelColor = Color(0xFF8B949E),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = oauthClientSecretInput,
                        onValueChange = { oauthClientSecretInput = it },
                        label = { Text("Client Secret") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GhAccentBlue,
                            unfocusedBorderColor = Color(0xFF30363D),
                            focusedLabelColor = GhAccentBlue,
                            unfocusedLabelColor = Color(0xFF8B949E),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cid = oauthClientIdInput.trim()
                        val csec = oauthClientSecretInput.trim()
                        if (cid.isNotEmpty()) {
                            authViewModel.tokenManager.saveOAuthCredentials(cid, csec)
                            showOauthConfigDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GhAccentBlue)
                ) {
                    Text("Save Credentials", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOauthConfigDialog = false }) {
                    Text("Cancel", color = Color(0xFF8B949E))
                }
            },
            containerColor = Color(0xFF21262D)
        )
    }
}
