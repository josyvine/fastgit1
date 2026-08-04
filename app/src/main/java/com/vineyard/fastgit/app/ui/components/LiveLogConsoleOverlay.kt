package com.vineyard.fastgit.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vineyard.fastgit.app.ui.theme.GhAccentBlue
import com.vineyard.fastgit.app.ui.theme.GhBgDark
import com.vineyard.fastgit.app.ui.theme.GhSurfaceDark
import com.vineyard.fastgit.app.ui.theme.GhTextSecondaryDark
import com.vineyard.fastgit.app.utils.AppLogger
import com.vineyard.fastgit.app.utils.LogEntry
import kotlinx.coroutines.launch

@Composable
fun LiveLogConsoleOverlay() {
    val logs by AppLogger.logs.collectAsState()
    val isOverlayVisible by AppLogger.isOverlayVisible.collectAsState()
    val isMinimized by AppLogger.isMinimized.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Count error logs
    val errorCount = remember(logs) { logs.count { it.isError } }
    val successCount = remember(logs) { logs.count { it.isSuccess } }

    var filterType by remember { mutableStateOf("ALL") } // "ALL", "ERROR", "SUCCESS"

    val filteredLogs = remember(logs, filterType) {
        when (filterType) {
            "ERROR" -> logs.filter { it.isError }
            "SUCCESS" -> logs.filter { it.isSuccess }
            else -> logs
        }
    }

    var showExportDialog by remember { mutableStateOf(false) }
    var exportedPathsList by remember { mutableStateOf<List<String>>(emptyList()) }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.FolderZip, contentDescription = null, tint = GhAccentBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Log File Saved!", fontSize = 18.sp, color = Color.White)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Log files have been written to the 'fastgit log' folder in your device storage:",
                        fontSize = 13.sp,
                        color = Color.LightGray
                    )
                    exportedPathsList.forEach { path ->
                        Surface(
                            color = Color(0xFF161B22),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF28A745), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(path, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                            }
                        }
                    }
                    Text(
                        "💡 Tip: Open your File Manager app (e.g., Files by Google or Samsung My Files) and go to Internal Storage > Download > fastgit log or Documents > fastgit log",
                        fontSize = 11.sp,
                        color = GhTextSecondaryDark
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showExportDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = GhAccentBlue)
                ) {
                    Text("OK", color = Color.White)
                }
            },
            containerColor = Color(0xFF0D1117),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray
        )
    }

    // Minimized Floating Circle / Pill (Always accessible at bottom-right)
    if (isMinimized || !isOverlayVisible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 72.dp, end = 16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Surface(
                onClick = { AppLogger.expandOverlay() },
                shape = CircleShape,
                color = if (errorCount > 0) Color(0xFF9E1515) else Color(0xFF1F6FEB),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .size(56.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Live Logs",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )

                    // Error counter badge if any
                    if (errorCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 2.dp, y = (-2).dp)
                                .size(20.dp)
                                .background(Color.Red, CircleShape)
                                .border(1.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (errorCount > 99) "99+" else errorCount.toString(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    // Full Screen Console Dialog
    if (isOverlayVisible && !isMinimized) {
        Dialog(
            onDismissRequest = { AppLogger.minimizeOverlay() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            val listState = rememberLazyListState()

            // Auto-scroll on new log entry
            LaunchedEffect(filteredLogs.size) {
                if (filteredLogs.isNotEmpty()) {
                    listState.animateScrollToItem(filteredLogs.size - 1)
                }
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF090D16) // Terminal deep pitch black background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Bar
                    Surface(
                        color = Color(0xFF161B22),
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = GhAccentBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "Live Process & Error Console",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${logs.size} total logs • $errorCount errors • $successCount successes",
                                        fontSize = 11.sp,
                                        color = GhTextSecondaryDark
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Minimize Button
                                IconButton(
                                    onClick = { AppLogger.minimizeOverlay() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Minimize",
                                        tint = Color.White
                                    )
                                }

                                // Close Button
                                IconButton(
                                    onClick = { AppLogger.closeOverlay() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.Red
                                    )
                                }
                            }
                        }
                    }

                    // Action Toolbar: Filter Chips, Copy, Clear
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0D1117))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = filterType == "ALL",
                                onClick = { filterType = "ALL" },
                                label = { Text("All (${logs.size})", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GhAccentBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = filterType == "ERROR",
                                onClick = { filterType = "ERROR" },
                                label = { Text("Errors ($errorCount)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFD73A49),
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = filterType == "SUCCESS",
                                onClick = { filterType = "SUCCESS" },
                                label = { Text("Success ($successCount)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF28A745),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Save Log File to SDCARD Button
                            TextButton(
                                onClick = {
                                    val paths = AppLogger.exportLogsToFiles()
                                    exportedPathsList = paths
                                    showExportDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SaveAlt,
                                    contentDescription = "Save to SD",
                                    tint = Color(0xFF28A745),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save File", fontSize = 12.sp, color = Color(0xFF28A745))
                            }

                            // Copy All Logs Button
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("FastGit App Logs", AppLogger.getFullLogText())
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Full log report copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = GhAccentBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy", fontSize = 12.sp, color = GhAccentBlue)
                            }

                            // Clear Logs Button
                            IconButton(
                                onClick = { AppLogger.clearLogs() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Clear",
                                    tint = GhTextSecondaryDark
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF21262D), thickness = 1.dp)

                    // Logs List
                    if (filteredLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircleOutline,
                                    contentDescription = null,
                                    tint = GhTextSecondaryDark,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No log entries found for filter '$filterType'",
                                    color = GhTextSecondaryDark,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF04060A))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredLogs, key = { "log_${it.id}" }) { item ->
                                LogItemRow(item)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogItemRow(item: LogEntry) {
    val bgColor = when {
        item.isError -> Color(0xFF2C0A0A)
        item.isSuccess -> Color(0xFF0A2C12)
        else -> Color(0xFF0D1117)
    }

    val textColor = when {
        item.isError -> Color(0xFFFF7B72) // Bright red
        item.isSuccess -> Color(0xFF7EE787) // Bright green
        else -> Color(0xFFC9D1D9) // White-gray
    }

    val tagColor = when {
        item.isError -> Color(0xFFFF4D4D)
        item.isSuccess -> Color(0xFF56D364)
        else -> GhAccentBlue
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(0.5.dp, tagColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Tag Badge
                    Text(
                        text = "[${item.tag}]",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = tagColor,
                        modifier = Modifier
                            .background(tagColor.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )

                    Text(
                        text = item.timestamp,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = GhTextSecondaryDark
                    )
                }

                if (item.isError) {
                    Text(
                        text = "ERROR",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red,
                        modifier = Modifier
                            .background(Color.Red.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                } else if (item.isSuccess) {
                    Text(
                        text = "SUCCESS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF28A745),
                        modifier = Modifier
                            .background(Color(0xFF28A745).copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.message,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = textColor,
                lineHeight = 16.sp
            )
        }
    }
}
