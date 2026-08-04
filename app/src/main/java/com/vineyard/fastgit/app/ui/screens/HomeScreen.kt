package com.vineyard.fastgit.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vineyard.fastgit.app.models.Commit
import com.vineyard.fastgit.app.models.Repository
import com.vineyard.fastgit.app.ui.theme.*
import com.vineyard.fastgit.app.viewmodel.HomeViewModel
import com.vineyard.fastgit.app.viewmodel.RepositoryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    onCreateRepoClick: () -> Unit,
    onImportRepoClick: () -> Unit,
    onSelectRepo: (Repository) -> Unit,
    onDeleteRepo: ((Repository) -> Unit)? = null,
    onDownloadZipRepo: ((Repository) -> Unit)? = null
) {
    val context = LocalContext.current
    // Retrieve the shared RepositoryViewModel scoped to the current ViewModelStoreOwner
    val repositoryViewModel: RepositoryViewModel = viewModel()

    val repositoryStatusMessage by repositoryViewModel.statusMessage.collectAsState()
    var repoToDelete by remember { mutableStateOf<Repository?>(null) }

    // Fallback callbacks to the local ViewModel if parameters are not provided
    val finalDeleteRepo = onDeleteRepo ?: { repo ->
        repositoryViewModel.deleteRepository(repo.owner?.login ?: "developer", repo.name) {
            homeViewModel.loadHomeData()
        }
    }

    val finalDownloadZipRepo = onDownloadZipRepo ?: { repo ->
        repositoryViewModel.downloadRepositoryAsZip(
            repo.owner?.login ?: "developer",
            repo.name,
            repo.defaultBranch,
            context
        )
    }

    val recentRepos by homeViewModel.recentRepos.collectAsState()
    val recentCommits by homeViewModel.recentCommits.collectAsState()
    val isLoading by homeViewModel.isLoading.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                coroutineScope.launch {
                    homeViewModel.loadHomeData()
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GhBgDark)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Welcome Header
                item {
                    Column {
                        Text(
                            text = "FastGit Mobile Workspace",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Manage GitHub repositories without terminal Git commands",
                            fontSize = 13.sp,
                            color = GhTextSecondaryDark
                        )
                    }
                }

                // Quick Action Cards Grid Row
                item {
                    Text(
                        text = "Quick Actions",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionCard(
                            title = "Create Repo",
                            subtitle = "New GitHub project",
                            icon = Icons.Default.AddCircle,
                            color = GhSuccessGreen,
                            modifier = Modifier.weight(1f),
                            onClick = onCreateRepoClick
                        )

                        QuickActionCard(
                            title = "Import Repo",
                            subtitle = "From GitHub URL",
                            icon = Icons.Default.CloudDownload,
                            color = GhAccentBlue,
                            modifier = Modifier.weight(1f),
                            onClick = onImportRepoClick
                        )
                    }
                }

                // Recent Repositories Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Repositories",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                if (isLoading && !isRefreshing) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = GhAccentBlue)
                        }
                    }
                } else {
                    items(recentRepos) { repo ->
                        RepoCardItem(
                            repo = repo,
                            onClick = { onSelectRepo(repo) },
                            onDeleteClick = { repoToDelete = repo }, // Show safety dialog warning
                            onDownloadZipClick = { finalDownloadZipRepo(repo) }
                        )
                    }
                }

                // Recent Commits Activity Feed
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Recent Commits",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                items(recentCommits) { commit ->
                    CommitCardItem(commit = commit)
                }
            }
        }

        // Repository Deletion Safety Confirmation Warning Dialog
        if (repoToDelete != null) {
            val target = repoToDelete!!
            AlertDialog(
                onDismissRequest = { repoToDelete = null },
                title = { Text("Delete Repository?", color = Color.Red, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "Are you sure you want to delete '${target.name}'? This action is permanent and cannot be undone.",
                        color = Color.White
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val toDelete = target
                            repoToDelete = null
                            finalDeleteRepo(toDelete)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Delete Permanently", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { repoToDelete = null }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = GhSurfaceDark
            )
        }

        // Home Tab Operations Feedback Snackbar (Collection)
        repositoryStatusMessage?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { repositoryViewModel.clearStatus() }) {
                        Text("OK", color = GhAccentBlue)
                    }
                },
                containerColor = GhSurfaceDark,
                contentColor = Color.White
            ) {
                Text(msg)
            }
        }
    }
}

@Composable
fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GhSurfaceDark),
        border = ButtonDefaults.outlinedButtonBorder(enabled = true)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.2f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                }
            }

            Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
            Text(subtitle, color = GhTextSecondaryDark, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RepoCardItem(
    repo: Repository, 
    onClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    onDownloadZipClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = GhSurfaceDark),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (repo.private) Icons.Default.Lock else Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (repo.private) GhWarningYellow else GhAccentBlue,
                    modifier = Modifier.size(28.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = repo.name,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    repo.description?.let { desc ->
                        Text(
                            text = desc,
                            color = GhTextSecondaryDark,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repo.language?.let { lang ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(GhPrimaryViolet)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(lang, fontSize = 11.sp, color = GhTextSecondaryDark)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = GhWarningYellow, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("${repo.stargazersCount}", fontSize = 11.sp, color = GhTextSecondaryDark)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CallSplit, contentDescription = null, tint = GhTextSecondaryDark, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("${repo.forksCount}", fontSize = 11.sp, color = GhTextSecondaryDark)
                        }
                    }
                }

                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = GhTextSecondaryDark)
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(GhSurfaceDark)
        ) {
            DropdownMenuItem(
                text = { Text("Copy Repo URL", color = Color.White) },
                onClick = {
                    showMenu = false
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Repository URL", repo.htmlUrl)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Repository URL copied to clipboard!", Toast.LENGTH_SHORT).show()
                },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GhAccentBlue) }
            )
            if (onDownloadZipClick != null) {
                DropdownMenuItem(
                    text = { Text("Download Repository (ZIP)", color = Color.White) },
                    onClick = {
                        showMenu = false
                        onDownloadZipClick()
                    },
                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = GhSuccessGreen) }
                )
            }
            if (onDeleteClick != null) {
                DropdownMenuItem(
                    text = { Text("Delete Repository", color = Color.Red) },
                    onClick = {
                        showMenu = false
                        onDeleteClick()
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                )
            }
        }
    }
}

@Composable
fun CommitCardItem(commit: Commit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = GhSurfaceDark),
        border = ButtonDefaults.outlinedButtonBorder(enabled = true)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Commit, contentDescription = null, tint = GhPrimaryViolet, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = commit.commit?.message ?: "Update repository content",
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${commit.commit?.author?.name ?: "FastGit"} • ${commit.sha.take(7)}",
                    fontSize = 11.sp,
                    color = GhTextSecondaryDark
                )
            }
        }
    }
}