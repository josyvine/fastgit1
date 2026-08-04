package com.vineyard.fastgit.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vineyard.fastgit.app.models.Repository
import com.vineyard.fastgit.app.ui.components.LiveLogConsoleOverlay
import com.vineyard.fastgit.app.ui.screens.*
import com.vineyard.fastgit.app.ui.theme.*
import com.vineyard.fastgit.app.utils.AppLogger
import com.vineyard.fastgit.app.viewmodel.*
import kotlinx.coroutines.launch

data class NavTabItem(
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel = viewModel(),
    repositoryViewModel: RepositoryViewModel = viewModel(),
    notificationViewModel: NotificationViewModel = viewModel(),
    profileViewModel: ProfileViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val navItems = listOf(
        NavTabItem("Home", Icons.Default.Home, Icons.Default.Home),
        NavTabItem("Repos", Icons.Default.Folder, Icons.Default.FolderOpen),
        NavTabItem("Alerts", Icons.Default.NotificationsNone, Icons.Default.Notifications),
        NavTabItem("Profile", Icons.Default.PersonOutline, Icons.Default.Person),
        NavTabItem("Settings", Icons.Default.Settings, Icons.Default.Settings)
    )

    val pagerState = rememberPagerState(pageCount = { navItems.size })
    val coroutineScope = rememberCoroutineScope()

    var activeDetailRepo by remember { mutableStateOf<Repository?>(null) }
    var initialShowCreateDialog by remember { mutableStateOf(false) }
    var initialShowImportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        AppLogger.i("MainScreen", "Switched tab to: ${navItems[pagerState.currentPage].title}")
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (activeDetailRepo != null) {
            val repoDetailViewModel = viewModel<RepoDetailViewModel>(
                key = "${activeDetailRepo!!.owner?.login}_${activeDetailRepo!!.name}",
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return RepoDetailViewModel(
                            application = authViewModel.getApplication(),
                            owner = activeDetailRepo!!.owner?.login ?: "developer",
                            repoName = activeDetailRepo!!.name
                        ) as T
                    }
                }
            )

            RepoDetailScreen(
                repoDetailViewModel = repoDetailViewModel,
                onBack = {
                    AppLogger.i("MainScreen", "Navigating back from RepoDetail")
                    activeDetailRepo = null
                }
            )
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = GhSurfaceDark,
                        contentColor = GhAccentBlue,
                        tonalElevation = 8.dp
                    ) {
                        navItems.forEachIndexed { index, tab ->
                            val isSelected = pagerState.currentPage == index
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (isSelected) GhAccentBlue else GhTextSecondaryDark
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.title,
                                        fontSize = 11.sp,
                                        color = if (isSelected) GhAccentBlue else GhTextSecondaryDark
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = GhAccentBlue.copy(alpha = 0.15f)
                                )
                            )
                        }
                    }
                },
                containerColor = GhBgDark
            ) { innerPadding ->
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) { page ->
                    when (page) {
                        0 -> HomeScreen(
                            homeViewModel = homeViewModel,
                            onCreateRepoClick = {
                                AppLogger.i("HomeScreen", "User clicked Create Repo")
                                coroutineScope.launch {
                                    initialShowCreateDialog = true
                                    pagerState.animateScrollToPage(1)
                                }
                            },
                            onImportRepoClick = {
                                AppLogger.i("HomeScreen", "User clicked Import Repo")
                                coroutineScope.launch {
                                    initialShowImportDialog = true
                                    pagerState.animateScrollToPage(1)
                                }
                            },
                            onSelectRepo = { repo ->
                                AppLogger.i("HomeScreen", "Selected repository '${repo.name}'")
                                activeDetailRepo = repo
                            }
                        )
                        1 -> RepositoriesScreen(
                            repositoryViewModel = repositoryViewModel,
                            onSelectRepo = { repo ->
                                AppLogger.i("RepositoriesScreen", "Selected repository '${repo.name}'")
                                activeDetailRepo = repo
                            },
                            showCreateDialogInitially = initialShowCreateDialog,
                            showImportDialogInitially = initialShowImportDialog
                        )
                        2 -> NotificationsScreen(notificationViewModel)
                        3 -> ProfileScreen(
                            profileViewModel = profileViewModel,
                            onSelectRepo = { repo ->
                                AppLogger.i("ProfileScreen", "Selected repository '${repo.name}'")
                                activeDetailRepo = repo
                            }
                        )
                        4 -> SettingsScreen(
                            settingsViewModel = settingsViewModel,
                            authViewModel = authViewModel,
                            onLogout = {
                                AppLogger.i("SettingsScreen", "User logged out")
                                authViewModel.logout()
                            }
                        )
                    }
                }
            }
        }

        // Global Overlay for Live Process Logs and Errors (Always sits on top)
        LiveLogConsoleOverlay()
    }
}
