package com.vineyard.fastgit.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vineyard.fastgit.app.ui.MainScreen
import com.vineyard.fastgit.app.ui.screens.AuthScreen
import com.vineyard.fastgit.app.ui.theme.FastGitTheme
import com.vineyard.fastgit.app.utils.AppLogger
import com.vineyard.fastgit.app.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {

    private var authViewModelInstance: AuthViewModel? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            AppLogger.s("Permissions", "External storage write/read permissions granted by user.")
        } else {
            AppLogger.i("Permissions", "Storage permission result received. Logging will proceed to accessible public paths.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Global Crash Handler & Public SDCARD Log directory 'fastgit log'
        AppLogger.initCrashHandler(this)

        // Request storage permissions if needed
        checkAndRequestStoragePermissions()

        setContent {
            FastGitTheme {
                val authViewModel: AuthViewModel = viewModel()
                authViewModelInstance = authViewModel

                LaunchedEffect(intent) {
                    handleOAuthIntent(intent, authViewModel)
                }

                val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

                if (isLoggedIn) {
                    MainScreen(authViewModel = authViewModel)
                } else {
                    AuthScreen(
                        authViewModel = authViewModel,
                        onLoginSuccess = { }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        authViewModelInstance?.let { vm ->
            handleOAuthIntent(intent, vm)
        }
    }

    private fun handleOAuthIntent(intent: Intent?, authViewModel: AuthViewModel) {
        val data = intent?.data
        if (data != null && data.scheme == "fastgit" && data.host == "oauth-callback") {
            val code = data.getQueryParameter("code")
            val error = data.getQueryParameter("error")
            if (!code.isNullOrEmpty()) {
                AppLogger.i("MainActivity", "OAuth deep link received code successfully!")
                authViewModel.handleOAuthCode(code)
            } else if (!error.isNullOrEmpty()) {
                AppLogger.e("MainActivity", "OAuth deep link received error: $error")
                authViewModel.setOAuthError("GitHub OAuth authorization failed: $error")
            }
        }
    }

    private fun checkAndRequestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+): Request MANAGE_EXTERNAL_STORAGE Special Settings Permission
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        addCategory("android.intent.category.DEFAULT")
                        data = Uri.parse("package:${packageName}")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        } else {
            // Android 10 and below: Request standard read/write external storage permissions
            val writePerm = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            val readPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            if (writePerm != PackageManager.PERMISSION_GRANTED || readPerm != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                )
            }
        }
    }
}