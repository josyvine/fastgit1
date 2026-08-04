package com.vineyard.fastgit.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vineyard.fastgit.app.models.Repository
import com.vineyard.fastgit.app.ui.theme.*
import com.vineyard.fastgit.app.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(
    profileViewModel: ProfileViewModel,
    onSelectRepo: (Repository) -> Unit
) {
    val user by profileViewModel.user.collectAsState()
    val pinnedRepos by profileViewModel.pinnedRepos.collectAsState()
    val isLoading by profileViewModel.isLoading.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GhBgDark)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GhAccentBlue)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Profile Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = GhSurfaceDark),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                model = user?.avatarUrl,
                                contentDescription = "Profile Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(GhCardBorderDark)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = user?.name ?: user?.login ?: "Developer",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Text(
                                text = "@${user?.login ?: "developer"}",
                                fontSize = 14.sp,
                                color = GhAccentBlue
                            )

                            user?.bio?.let { bio ->
                                Text(
                                    text = bio,
                                    fontSize = 13.sp,
                                    color = GhTextSecondaryDark,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Stats row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                StatChip(title = "Repos", value = "${user?.publicRepos ?: 0}")
                                StatChip(title = "Followers", value = "${user?.followers ?: 0}")
                                StatChip(title = "Following", value = "${user?.following ?: 0}")
                            }
                        }
                    }
                }

                // Pinned Repositories Header
                item {
                    Text(
                        text = "Pinned Repositories",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                items(pinnedRepos) { repo ->
                    RepoCardItem(repo = repo, onClick = { onSelectRepo(repo) })
                }
            }
        }
    }
}

@Composable
fun StatChip(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
        Text(text = title, color = GhTextSecondaryDark, fontSize = 12.sp)
    }
}
