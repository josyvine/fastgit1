package com.vineyard.fastgit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vineyard.fastgit.app.models.Notification
import com.vineyard.fastgit.app.ui.theme.*
import com.vineyard.fastgit.app.viewmodel.NotificationViewModel

@Composable
fun NotificationsScreen(notificationViewModel: NotificationViewModel) {
    val notifications by notificationViewModel.notifications.collectAsState()
    val isLoading by notificationViewModel.isLoading.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GhBgDark)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Notifications",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                IconButton(onClick = { notificationViewModel.loadNotifications() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = GhAccentBlue)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GhAccentBlue)
                }
            } else if (notifications.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("All caught up! No notifications.", color = GhTextSecondaryDark)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(notifications) { notif ->
                        NotificationCardItem(
                            notification = notif,
                            onMarkRead = { notificationViewModel.markAsRead(notif.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationCardItem(notification: Notification, onMarkRead: () -> Unit) {
    val isUnread = notification.unread
    val icon = when (notification.subject?.type) {
        "PullRequest" -> Icons.Default.CallMerge
        "Issue" -> Icons.Default.ErrorOutline
        "Workflow" -> Icons.Default.PlayCircleOutline
        else -> Icons.Default.Notifications
    }

    val iconColor = when (notification.subject?.type) {
        "PullRequest" -> GhPrimaryViolet
        "Issue" -> GhSuccessGreen
        "Workflow" -> GhAccentBlue
        else -> GhWarningYellow
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMarkRead() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnread) GhSurfaceDark else Color(0xFF13171D)
        ),
        border = ButtonDefaults.outlinedButtonBorder(enabled = true)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = iconColor.copy(alpha = 0.2f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.repository?.fullName ?: "GitHub Alert",
                    fontSize = 11.sp,
                    color = GhTextSecondaryDark,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = notification.subject?.title ?: "Notification detail",
                    fontSize = 14.sp,
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                    color = Color.White
                )

                notification.updatedAt?.let { time ->
                    Text(
                        text = time,
                        fontSize = 11.sp,
                        color = GhTextSecondaryDark,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            if (isUnread) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(GhAccentBlue)
                )
            }
        }
    }
}
