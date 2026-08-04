package com.vineyard.fastgit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vineyard.fastgit.app.models.Repository
import com.vineyard.fastgit.app.ui.theme.*
import com.vineyard.fastgit.app.viewmodel.RepositoryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepositoriesScreen(
    repositoryViewModel: RepositoryViewModel,
    onSelectRepo: (Repository) -> Unit,
    showCreateDialogInitially: Boolean = false,
    showImportDialogInitially: Boolean = false
) {
    val context = LocalContext.current
    val repositories by repositoryViewModel.repositories.collectAsState()
    val searchQuery by repositoryViewModel.searchQuery.collectAsState()
    val selectedFilter by repositoryViewModel.selectedFilter.collectAsState()
    val isLoading by repositoryViewModel.isLoading.collectAsState()
    val statusMessage by repositoryViewModel.statusMessage.collectAsState()

    var showCreateDialog by remember { mutableStateOf(showCreateDialogInitially) }
    var showImportDialog by remember { mutableStateOf(showImportDialogInitially) }
    var repoToDelete by remember { mutableStateOf<Repository?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val filterOptions = listOf("All", "Public", "Private", "Sources", "Forks")

    val filteredRepos = repositories.filter { repo ->
        val matchesQuery = repo.name.contains(searchQuery, ignoreCase = true) ||
                (repo.description?.contains(searchQuery, ignoreCase = true) == true)

        val matchesFilter = when (selectedFilter) {
            "Public" -> !repo.private
            "Private" -> repo.private
            "Forks" -> repo.fork
            "Sources" -> !repo.fork
            else -> true
        }

        matchesQuery && matchesFilter
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GhBgDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Repositories",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { showImportDialog = true },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = GhSurfaceDark)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "Import Repo", tint = GhAccentBlue)
                    }

                    FloatingActionButton(
                        onClick = { showCreateDialog = true },
                        containerColor = GhSuccessGreen,
                        contentColor = Color.White,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create Repo")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { repositoryViewModel.onSearchQueryChange(it) },
                placeholder = { Text("Search repositories...", color = GhTextSecondaryDark) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GhTextSecondaryDark) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GhAccentBlue,
                    unfocusedBorderColor = GhCardBorderDark,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = GhSurfaceDark,
                    unfocusedContainerColor = GhSurfaceDark
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Pills Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filterOptions) { filter ->
                    FilterChip(
                        selected = filter == selectedFilter,
                        onClick = { repositoryViewModel.onFilterSelect(filter) },
                        label = { Text(filter) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GhAccentBlue,
                            selectedLabelColor = Color.Black,
                            containerColor = GhSurfaceDark,
                            labelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Repos List Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        coroutineScope.launch {
                            repositoryViewModel.fetchRepositories()
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isLoading && !isRefreshing) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = GhAccentBlue)
                        }
                    } else if (filteredRepos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()), // Enables pull-to-refresh on empty lists
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No matching repositories found.", color = GhTextSecondaryDark)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredRepos) { repo ->
                                RepoCardItem(
                                    repo = repo, 
                                    onClick = { onSelectRepo(repo) },
                                    onDeleteClick = { repoToDelete = repo },
                                    onDownloadZipClick = {
                                        repositoryViewModel.downloadRepositoryAsZip(
                                            owner = repo.owner?.login ?: "developer",
                                            repoName = repo.name,
                                            branch = repo.defaultBranch,
                                            context = context
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Status Snackbar / Message
        statusMessage?.let { msg ->
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

    // Repository Delete Confirmation Dialog
    if (repoToDelete != null) {
        val target = repoToDelete!!
        AlertDialog(
            onDismissRequest = { repoToDelete = null },
            title = { Text("Delete Repository?", color = Color.Red, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Are you sure you want to delete '${target.fullName}'? This action is permanent and cannot be undone.",
                    color = Color.White
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        repoToDelete = null
                        repositoryViewModel.deleteRepository(target.owner?.login ?: "developer", target.name)
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

    // Create Repository Dialog
    if (showCreateDialog) {
        CreateRepoDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, desc, isPrivate, readme, gitignore, license ->
                showCreateDialog = false
                repositoryViewModel.createRepository(name, desc, isPrivate, readme, gitignore, license) { newRepo ->
                    onSelectRepo(newRepo)
                }
            }
        )
    }

    // Import Repository Dialog
    if (showImportDialog) {
        ImportRepoDialog(
            onDismiss = { showImportDialog = false },
            onImport = { url ->
                showImportDialog = false
                repositoryViewModel.importRepositoryUrl(url) { imported ->
                    onSelectRepo(imported)
                }
            }
        )
    }
}

@Composable
fun CreateRepoDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, desc: String, isPrivate: Boolean, readme: Boolean, gitignore: String?, license: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var initReadme by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Repository", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Repository Name *") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (optional)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Private Repository", color = Color.White)
                    Switch(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = GhAccentBlue)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Initialize with README", color = Color.White)
                    Checkbox(
                        checked = initReadme,
                        onCheckedChange = { initReadme = it },
                        colors = CheckboxDefaults.colors(checkedColor = GhAccentBlue)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, desc, isPrivate, initReadme, "Android", "MIT") },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = GhSuccessGreen)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = GhTextSecondaryDark)
            }
        },
        containerColor = GhSurfaceDark
    )
}

@Composable
fun ImportRepoDialog(
    onDismiss: () -> Unit,
    onImport: (url: String) -> Unit
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Repository", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Paste a GitHub repository URL (e.g. https://github.com/user/project)",
                    fontSize = 13.sp,
                    color = GhTextSecondaryDark
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("GitHub URL") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(url) },
                enabled = url.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = GhAccentBlue)
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = GhTextSecondaryDark)
            }
        },
        containerColor = GhSurfaceDark
    )
}