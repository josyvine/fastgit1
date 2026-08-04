package com.vineyard.fastgit.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vineyard.fastgit.app.models.*
import com.vineyard.fastgit.app.ui.theme.*
import com.vineyard.fastgit.app.viewmodel.RepoDetailViewModel
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RepoDetailScreen(
    repoDetailViewModel: RepoDetailViewModel,
    onBack: () -> Unit
) {
    val repository by repoDetailViewModel.repository.collectAsState()
    val branches by repoDetailViewModel.branches.collectAsState()
    val currentBranch by repoDetailViewModel.currentBranch.collectAsState()
    val statusMessage by repoDetailViewModel.statusMessage.collectAsState()

    val isUploadingZip by repoDetailViewModel.isUploadingZip.collectAsState()
    val uploadStep by repoDetailViewModel.uploadStep.collectAsState()
    val uploadProgress by repoDetailViewModel.uploadProgress.collectAsState()

    val activeFile by repoDetailViewModel.activeFile.collectAsState()
    val fileContent by repoDetailViewModel.fileContent.collectAsState()

    // Logs states
    var selectedRunForLogs by remember { mutableStateOf<WorkflowRun?>(null) }
    val workflowLogs by repoDetailViewModel.workflowLogs.collectAsState()
    val isLogsLoading by repoDetailViewModel.isLogsLoading.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Explorer", "Branches", "Commits", "PRs", "Issues", "Actions", "Releases", "Settings")

    var isExplorerMaximized by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // File launcher for ZIP upload
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { repoDetailViewModel.uploadProjectZip(it, context) }
    }

    if (activeFile != null) {
        CodeEditorScreen(
            fileItem = activeFile!!,
            initialContent = fileContent,
            onBack = { repoDetailViewModel.closeActiveFile() },
            onSaveAndCommit = { updatedContent, commitMsg ->
                repoDetailViewModel.saveAndCommitFile(activeFile!!, updatedContent, commitMsg)
            },
            onDownloadClick = { content ->
                repoDetailViewModel.downloadSingleFileToDevice(activeFile!!, content, context)
            }
        )
        return
    }

    Scaffold(
        topBar = {
            // Hide TopAppBar only when Explorer is maximized
            if (!isExplorerMaximized || selectedTab != 0) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = repository?.name ?: repoDetailViewModel.repoName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${repoDetailViewModel.owner} • branch: $currentBranch",
                                fontSize = 12.sp,
                                color = GhTextSecondaryDark
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        // Branch Selector Dropdown Button
                        var branchMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            TextButton(
                                onClick = { branchMenuExpanded = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = GhAccentBlue)
                            ) {
                                Icon(Icons.Default.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(currentBranch, fontSize = 13.sp)
                            }

                            DropdownMenu(
                                expanded = branchMenuExpanded,
                                onDismissRequest = { branchMenuExpanded = false },
                                modifier = Modifier.background(GhSurfaceDark)
                            ) {
                                branches.forEach { branch ->
                                    DropdownMenuItem(
                                        text = { Text(branch.name, color = Color.White) },
                                        onClick = {
                                            branchMenuExpanded = false
                                            repoDetailViewModel.switchBranch(branch.name)
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = GhSurfaceDark)
                )
            }
        },
        containerColor = GhBgDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isExplorerMaximized && selectedTab == 0) PaddingValues(0.dp) else innerPadding)
        ) {
            // Scrollable Sub-Tabs Row (Hide when Explorer is maximized)
            if (!isExplorerMaximized || selectedTab != 0) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = GhSurfaceDark,
                    contentColor = GhAccentBlue,
                    edgePadding = 12.dp
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) GhAccentBlue else GhTextSecondaryDark
                                )
                            }
                        )
                    }
                }
            }

            // Tab Content
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> ExplorerTabContent(
                        repoDetailViewModel = repoDetailViewModel,
                        onUploadZipClick = { zipPickerLauncher.launch("application/zip") },
                        isMaximized = isExplorerMaximized,
                        onToggleMaximize = { isExplorerMaximized = !isExplorerMaximized }
                    )
                    1 -> BranchesTabContent(repoDetailViewModel)
                    2 -> CommitsTabContent(repoDetailViewModel)
                    3 -> PRsTabContent(repoDetailViewModel)
                    4 -> IssuesTabContent(repoDetailViewModel)
                    5 -> ActionsTabContent(
                        repoDetailViewModel = repoDetailViewModel,
                        onShowLogViewer = { run ->
                            repoDetailViewModel.fetchWorkflowRunLogs(run.id)
                            selectedRunForLogs = run
                        }
                    )
                    6 -> ReleasesTabContent(repoDetailViewModel)
                    7 -> RepoSettingsTabContent(repoDetailViewModel, onBack)
                }
            }
        }
    }

    // Workflow Build Logs Overlay Dialog (Custom Sized Dialog Overlay)
    if (selectedRunForLogs != null) {
        var showLogsContextMenu by remember { mutableStateOf(false) }
        Dialog(
            onDismissRequest = {
                selectedRunForLogs = null
                repoDetailViewModel.clearWorkflowLogs()
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(12.dp),
                color = GhSurfaceDark
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Compact Custom Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = GhAccentBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Build Logs: Run #${selectedRunForLogs?.runNumber}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        IconButton(
                            onClick = {
                                selectedRunForLogs = null
                                repoDetailViewModel.clearWorkflowLogs()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Logs",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Monospace Log Console Area (Filling maximum canvas real estate)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                            .background(Color(0xFF04060A), RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = { /* dismiss selection states */ },
                                onLongClick = { showLogsContextMenu = true }
                            )
                            .padding(12.dp)
                    ) {
                        if (isLogsLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = GhAccentBlue)
                            }
                        } else {
                            val verticalScroll = rememberScrollState()
                            Text(
                                text = workflowLogs ?: "Build parameters requested. Awaiting actions context...",
                                color = Color(0xFFC9D1D9),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(verticalScroll)
                            )
                        }

                        DropdownMenu(
                            expanded = showLogsContextMenu,
                            onDismissRequest = { showLogsContextMenu = false },
                            modifier = Modifier.background(GhSurfaceDark)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Copy Build Logs", color = Color.White) },
                                onClick = {
                                    showLogsContextMenu = false
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Build Logs", workflowLogs ?: "")
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Build logs copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GhAccentBlue) }
                            )
                            DropdownMenuItem(
                                text = { Text("Download Build Logs", color = GhSuccessGreen) },
                                onClick = {
                                    showLogsContextMenu = false
                                    selectedRunForLogs?.let { run ->
                                        repoDetailViewModel.downloadWorkflowRunLogs(run.id, run.runNumber, context)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = GhSuccessGreen) }
                            )
                        }
                    }
                }
            }
        }
    }

    // ZIP Upload Progress Dialog
    if (isUploadingZip) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Uploading Android Project ZIP", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Extracting, scanning, and preserving folder hierarchy on GitHub...",
                        fontSize = 13.sp,
                        color = GhTextSecondaryDark
                    )

                    LinearProgressIndicator(
                        progress = { uploadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = GhSuccessGreen,
                        trackColor = GhCardBorderDark
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uploadStep,
                            fontSize = 12.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(uploadProgress * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GhAccentBlue
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { repoDetailViewModel.cancelZipUpload() }) {
                    Text("Cancel", color = GhErrorRed)
                }
            },
            containerColor = GhSurfaceDark
        )
    }

    // Status Message Snackbar
    statusMessage?.let { msg ->
        LaunchedEffect(msg) {
            // Auto dismiss snackbar state handled by ViewModel or user tap
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerTabContent(
    repoDetailViewModel: RepoDetailViewModel,
    onUploadZipClick: () -> Unit,
    isMaximized: Boolean,
    onToggleMaximize: () -> Unit
) {
    val treeItems by repoDetailViewModel.treeItems.collectAsState()
    val currentPath by repoDetailViewModel.currentPath.collectAsState()
    val copiedItem by repoDetailViewModel.copiedItem.collectAsState()
    val isLoading by repoDetailViewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 0: Tree Explorer (VS Code Style), 1: Folder Navigation (GitHub App Style)
    var explorerMode by remember { mutableStateOf(0) }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showSearchReplaceDialog by remember { mutableStateOf(false) }

    // Dialog state for Rename and Delete
    var renameTargetItem by remember { mutableStateOf<FileItem?>(null) }
    var deleteTargetItem by remember { mutableStateOf<FileItem?>(null) }

    // Maintain expanded directory paths in a state set (Preserve across tree updates)
    var expandedPaths by remember { mutableStateOf(setOf<String>()) }

    // Helper to find node by path inside tree
    fun findNodeByPath(nodes: List<FileItem>, path: String): FileItem? {
        if (path.isEmpty()) return null
        for (node in nodes) {
            if (node.path == path) return node
            if (node.children.isNotEmpty()) {
                val found = findNodeByPath(node.children, path)
                if (found != null) return found
            }
        }
        return null
    }

    // Flatten tree dynamically into a visible list for LazyColumn recycling
    val visibleItems = remember(treeItems, expandedPaths, explorerMode, currentPath) {
        if (explorerMode == 1) {
            // Folder Navigation Mode: show items at currentPath level only
            if (currentPath.isEmpty()) {
                treeItems
            } else {
                val node = findNodeByPath(treeItems, currentPath)
                node?.children ?: emptyList()
            }
        } else {
            // Tree Explorer Mode: flatten expanded nodes
            val flatList = mutableListOf<FileItem>()

            fun flatten(nodes: List<FileItem>, level: Int) {
                for (node in nodes) {
                    val isExpanded = expandedPaths.contains(node.path)
                    val nodeCopy = node.copy(
                        level = level,
                        isExpanded = isExpanded
                    )

                    flatList.add(nodeCopy)

                    if (node.type == "dir" && isExpanded && node.children.isNotEmpty()) {
                        flatten(node.children, level + 1)
                    }
                }
            }

            flatten(treeItems, level = 0)
            flatList
        }
    }

    var isRefreshing by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            coroutineScope.launch {
                repoDetailViewModel.refreshExplorer()
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Safely offset the maximized explorer content under the translucent system status bar
                .then(if (isMaximized) Modifier.statusBarsPadding() else Modifier)
                .padding(12.dp)
        ) {
            // Quick Action Toolbar for Explorer (Hide entirely when maximized)
            if (!isMaximized) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = GhSurfaceDark),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Upload Project ZIP
                            Button(
                                onClick = onUploadZipClick,
                                colors = ButtonDefaults.buttonColors(containerColor = GhPrimaryViolet),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upload ZIP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Search & Replace (VS Code Style)
                            OutlinedButton(
                                onClick = { showSearchReplaceDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.FindReplace, contentDescription = null, tint = GhAccentBlue, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Refactor / Replace", fontSize = 11.sp, color = Color.White)
                            }

                            // New File
                            OutlinedButton(
                                onClick = { showNewFileDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = GhSuccessGreen, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New File", fontSize = 11.sp, color = Color.White)
                            }
                        }

                        if (copiedItem != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF161B22), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Copied: ${copiedItem?.name}",
                                    fontSize = 11.sp,
                                    color = GhAccentBlue,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { repoDetailViewModel.pasteCopiedItem(currentPath) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Paste to /${currentPath.ifEmpty { "root" }}", fontSize = 11.sp, color = GhSuccessGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // View Mode Switcher Pills (Tree vs Folder View) and Local Search Layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // View Mode selection controls
                Row(
                    modifier = Modifier
                        .background(GhSurfaceDark, RoundedCornerShape(8.dp))
                        .padding(2.dp)
                ) {
                    Surface(
                        onClick = { explorerMode = 0 },
                        color = if (explorerMode == 0) GhAccentBlue else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountTree,
                                contentDescription = null,
                                tint = if (explorerMode == 0) Color.White else GhTextSecondaryDark,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Tree View",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (explorerMode == 0) Color.White else GhTextSecondaryDark
                            )
                        }
                    }

                    Surface(
                        onClick = { explorerMode = 1 },
                        color = if (explorerMode == 1) GhAccentBlue else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (explorerMode == 1) Color.White else GhTextSecondaryDark,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Folder View",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (explorerMode == 1) Color.White else GhTextSecondaryDark
                            )
                        }
                    }
                }

                // High-performance search explorer logic layout replacing static label
                var isLocalSearchActive by remember { mutableStateOf(false) }
                var localSearchQuery by remember { mutableStateOf("") }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                ) {
                    if (isLocalSearchActive) {
                        OutlinedTextField(
                            value = localSearchQuery,
                            onValueChange = { localSearchQuery = it },
                            placeholder = { Text("Search file/folder...", fontSize = 12.sp, color = GhTextSecondaryDark) },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = GhSurfaceDark,
                                unfocusedContainerColor = GhSurfaceDark,
                                focusedBorderColor = GhAccentBlue,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            if (localSearchQuery.isNotBlank()) {
                                                repoDetailViewModel.searchAndJumpToPath(localSearchQuery)
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Submit Search",
                                            tint = GhAccentBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            isLocalSearchActive = false
                                            localSearchQuery = ""
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cancel Search",
                                            tint = Color.Red,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        )
                    } else {
                        IconButton(
                            onClick = { isLocalSearchActive = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search Directory",
                                tint = GhAccentBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (explorerMode == 0) {
                            Text(
                                text = "Tap arrow to expand inline",
                                fontSize = 10.sp,
                                color = GhTextSecondaryDark,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Breadcrumb Navigation Bar (Including the Maximize/Minimize Toggle button and persistent inline Plus button)
            BreadcrumbBar(
                currentPath = currentPath,
                onNavigatePath = { targetPath -> repoDetailViewModel.navigateToDirectory(targetPath) },
                isMaximized = isMaximized,
                onToggleMaximize = onToggleMaximize,
                onCreateFileClick = { showNewFileDialog = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = GhAccentBlue,
                    trackColor = GhCardBorderDark
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Directory Explorer Tree List (Optimized with LazyColumn Recycling)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Parent Folder Row (..) when inside a subfolder
                if (currentPath.isNotEmpty()) {
                    item(key = "parent_folder_nav_up") {
                        ParentFolderNodeRow(
                            currentPath = currentPath,
                            onNavigateUp = {
                                val parentPath = if (currentPath.contains("/")) currentPath.substringBeforeLast("/") else ""
                                repoDetailViewModel.navigateToDirectory(parentPath)
                            }
                        )
                    }
                }

                if (visibleItems.isEmpty() && !isLoading) {
                    item(key = "empty_directory_banner") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = GhSurfaceDark),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(20.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = GhAccentBlue,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (currentPath.isEmpty()) "This repository is empty" else "This folder is empty",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (currentPath.isEmpty()) 
                                        "No files found on branch. Upload a ZIP project or add a file to get started." 
                                    else 
                                        "No files found in /$currentPath",
                                    color = GhTextSecondaryDark,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = onUploadZipClick,
                                        colors = ButtonDefaults.buttonColors(containerColor = GhPrimaryViolet),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Upload ZIP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { showNewFileDialog = true },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = GhSuccessGreen, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("New File", fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                items(visibleItems, key = { "tree_${it.type}_${it.path}" }) { item ->
                    TreeItemNodeRow(
                        item = item,
                        explorerMode = explorerMode,
                        copiedItem = copiedItem,
                        onItemClick = { target ->
                            if (target.type == "dir") {
                                if (explorerMode == 1) {
                                    // Folder View mode: Navigate directly into subfolder
                                    repoDetailViewModel.navigateToDirectory(target.path)
                                } else {
                                    // Tree View mode: Expand/collapse inline
                                    val isExp = expandedPaths.contains(target.path)
                                    if (!isExp && target.children.isEmpty()) {
                                        repoDetailViewModel.fetchSubfolderContents(target.path)
                                    }
                                    expandedPaths = if (isExp) expandedPaths - target.path else expandedPaths + target.path
                                }
                            } else {
                                repoDetailViewModel.openFile(target)
                            }
                        },
                        onOpenFolderDirect = { folder ->
                            repoDetailViewModel.navigateToDirectory(folder.path)
                        },
                        onCopyItem = { fileItem -> repoDetailViewModel.copyItem(fileItem) },
                        onPasteIntoFolder = { folderPath -> repoDetailViewModel.pasteCopiedItem(folderPath) },
                        onRenameItem = { fileItem -> renameTargetItem = fileItem },
                        onDeleteItem = { fileItem -> deleteTargetItem = fileItem },
                        onDownloadFolderZip = { folder ->
                            // Directly invoke VM to download and save folder ZIP natively
                            repoDetailViewModel.downloadFolderAsZip(folder, context)
                        }
                    )
                }
            }
        }
    }

    // New File Dialog
    if (showNewFileDialog) {
        var newFileName by remember { mutableStateOf("") }
        var initialCode by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Create New File", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val activeDirText = if (currentPath.isBlank()) "root" else "/$currentPath"
                    Text(
                        text = "Creating file inside: $activeDirText",
                        color = GhAccentBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = { Text("File Name (e.g. MyClass.kt)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = initialCode,
                        onValueChange = { initialCode = it },
                        label = { Text("Initial Content (optional)") },
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
                    onClick = {
                        showNewFileDialog = false
                        repoDetailViewModel.createNewFile(newFileName, initialCode, "Create $newFileName via FastGit")
                    },
                    enabled = newFileName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = GhSuccessGreen)
                ) {
                    Text("Create File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = GhSurfaceDark
        )
    }

    // Rename Dialog
    if (renameTargetItem != null) {
        val target = renameTargetItem!!
        var newNameInput by remember { mutableStateOf(target.name) }

        AlertDialog(
            onDismissRequest = { renameTargetItem = null },
            title = { Text("Rename ${target.type.replaceFirstChar { it.uppercase() }}", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current path: /${target.path}", fontSize = 12.sp, color = GhTextSecondaryDark)
                    OutlinedTextField(
                        value = newNameInput,
                        onValueChange = { newNameInput = it },
                        label = { Text("New Name") },
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
                    onClick = {
                        val t = target
                        renameTargetItem = null
                        repoDetailViewModel.renameItem(t, newNameInput)
                    },
                    enabled = newNameInput.isNotBlank() && newNameInput != target.name,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GhAccentBlue)
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetItem = null }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = GhSurfaceDark
        )
    }

    // Delete Confirmation Dialog
    if (deleteTargetItem != null) {
        val target = deleteTargetItem!!

        AlertDialog(
            onDismissRequest = { deleteTargetItem = null },
            title = { Text("Delete ${target.name}?", color = Color.Red, fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to delete '${target.path}' from this repository? This will commit a deletion on branch.", color = Color.White)
            },
            confirmButton = {
                Button(
                    onClick = {
                        val t = target
                        deleteTargetItem = null
                        repoDetailViewModel.deleteItem(t)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetItem = null }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = GhSurfaceDark
        )
    }

    // Global Search & Replace Dialog (Refactoring Tool)
    if (showSearchReplaceDialog) {
        var searchQueryInput by remember { mutableStateOf("") }
        var replaceQueryInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showSearchReplaceDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FindReplace, contentDescription = null, tint = GhAccentBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Global Search & Replace", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Refactor package names or search/replace code across all files in repository.", fontSize = 12.sp, color = GhTextSecondaryDark)
                    OutlinedTextField(
                        value = searchQueryInput,
                        onValueChange = { searchQueryInput = it },
                        label = { Text("Search string (e.g. com.oldpackage)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = replaceQueryInput,
                        onValueChange = { replaceQueryInput = it },
                        label = { Text("Replace with (e.g. com.newpackage)") },
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
                    onClick = {
                        val sq = searchQueryInput
                        val rq = replaceQueryInput
                        showSearchReplaceDialog = false
                        repoDetailViewModel.performGlobalSearchAndReplace(sq, rq) { files, count -> }
                    },
                    enabled = searchQueryInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = GhAccentBlue)
                ) {
                    Text("Replace All Occurrences")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSearchReplaceDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = GhSurfaceDark
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TreeItemNodeRow(
    item: FileItem,
    explorerMode: Int,
    copiedItem: FileItem?,
    onItemClick: (FileItem) -> Unit,
    onOpenFolderDirect: (FileItem) -> Unit,
    onCopyItem: (FileItem) -> Unit,
    onPasteIntoFolder: (String) -> Unit,
    onRenameItem: (FileItem) -> Unit,
    onDeleteItem: (FileItem) -> Unit,
    onDownloadFolderZip: (FileItem) -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }

    val icon = if (item.type == "dir") {
        if (item.isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder
    } else {
        Icons.Default.Description
    }

    val iconTint = if (item.type == "dir") GhAccentBlue else Color(0xFFC9D1D9)

    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onItemClick(item) },
                    onLongClick = { showContextMenu = true }
                ),
            color = GhSurfaceDark,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(
                        horizontal = if (explorerMode == 0) (12 + item.level * 14).dp else 12.dp,
                        vertical = 10.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = item.name,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                if (item.type == "dir") {
                    IconButton(
                        onClick = { onOpenFolderDirect(item) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SubdirectoryArrowRight,
                            contentDescription = "Navigate into folder",
                            tint = GhAccentBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (explorerMode == 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (item.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = GhTextSecondaryDark,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            modifier = Modifier.background(GhSurfaceDark)
        ) {
            if (item.type == "dir") {
                DropdownMenuItem(
                    text = { Text("Open Folder", color = Color.White) },
                    onClick = {
                        showContextMenu = false
                        onOpenFolderDirect(item)
                    },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, tint = GhAccentBlue) }
                )
                if (copiedItem != null) {
                    DropdownMenuItem(
                        text = { Text("Paste '${copiedItem.name}' Here", color = GhSuccessGreen) },
                        onClick = {
                            showContextMenu = false
                            onPasteIntoFolder(item.path)
                        },
                        leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null, tint = GhSuccessGreen) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Copy Folder Path", color = Color.White) },
                    onClick = {
                        showContextMenu = false
                        onCopyItem(item)
                    },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GhAccentBlue) }
                )
                DropdownMenuItem(
                    text = { Text("Rename Folder", color = Color.White) },
                    onClick = {
                        showContextMenu = false
                        onRenameItem(item)
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = GhAccentBlue) }
                )
                DropdownMenuItem(
                    text = { Text("Delete Folder", color = Color.Red) },
                    onClick = {
                        showContextMenu = false
                        onDeleteItem(item)
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                )
                DropdownMenuItem(
                    text = { Text("Download Folder as ZIP", color = GhSuccessGreen) },
                    onClick = {
                        showContextMenu = false
                        onDownloadFolderZip(item)
                    },
                    leadingIcon = { Icon(Icons.Default.FolderZip, contentDescription = null, tint = GhSuccessGreen) }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Edit / View Code", color = Color.White) },
                    onClick = {
                        showContextMenu = false
                        onItemClick(item)
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = GhAccentBlue) }
                )
                DropdownMenuItem(
                    text = { Text("Copy File", color = Color.White) },
                    onClick = {
                        showContextMenu = false
                        onCopyItem(item)
                    },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GhAccentBlue) }
                )
                DropdownMenuItem(
                    text = { Text("Rename File", color = Color.White) },
                    onClick = {
                        showContextMenu = false
                        onRenameItem(item)
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = GhAccentBlue) }
                )
                DropdownMenuItem(
                    text = { Text("Delete File", color = Color.Red) },
                    onClick = {
                        showContextMenu = false
                        onDeleteItem(item)
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                )
            }
        }
    }
}

@Composable
fun ParentFolderNodeRow(
    currentPath: String,
    onNavigateUp: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateUp() },
        color = Color(0xFF161B22),
        shape = RoundedCornerShape(8.dp),
        border = ButtonDefaults.outlinedButtonBorder(enabled = true)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DriveFileMove,
                contentDescription = "Parent Directory",
                tint = GhAccentBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ".. (Parent Directory)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = GhAccentBlue
                )
                Text(
                    text = "Go up from /$currentPath",
                    fontSize = 11.sp,
                    color = GhTextSecondaryDark
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowUpward,
                contentDescription = null,
                tint = GhAccentBlue,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun BreadcrumbBar(
    currentPath: String,
    onNavigatePath: (String) -> Unit,
    isMaximized: Boolean,
    onToggleMaximize: () -> Unit,
    onCreateFileClick: () -> Unit
) {
    val segments = remember(currentPath) {
        if (currentPath.isBlank()) {
            listOf(BreadcrumbSegment("root", ""))
        } else {
            val parts = currentPath.split("/").filter { it.isNotBlank() }
            val list = mutableListOf(BreadcrumbSegment("root", ""))
            var accum = ""
            for (part in parts) {
                accum = if (accum.isEmpty()) part else "$accum/$part"
                list.add(BreadcrumbSegment(part, accum))
            }
            list
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GhSurfaceDark,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = GhAccentBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))

                androidx.compose.foundation.lazy.LazyRow(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(segments.size) { index ->
                        val seg = segments[index]
                        val isLast = index == segments.size - 1

                        if (index > 0) {
                            Text(
                                text = "/",
                                color = GhTextSecondaryDark,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            onClick = { onNavigatePath(seg.path) },
                            color = if (isLast) Color(0xFF21262D) else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = seg.name,
                                fontSize = 12.sp,
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace,
                                color = if (isLast) Color.White else GhAccentBlue,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Persistent + Create Icon beside folder view
                IconButton(
                    onClick = onCreateFileClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create New File",
                        tint = GhSuccessGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Maximize / Minimize Icon Trigger on the extreme right
                IconButton(
                    onClick = onToggleMaximize,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isMaximized) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isMaximized) "Minimize View" else "Maximize View",
                        tint = GhAccentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

data class BreadcrumbSegment(val name: String, val path: String)

@Composable
fun BranchesTabContent(repoDetailViewModel: RepoDetailViewModel) {
    val branches by repoDetailViewModel.branches.collectAsState()
    val currentBranch by repoDetailViewModel.currentBranch.collectAsState()

    var showCreateBranchDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Branch Manager", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)

            Button(
                onClick = { showCreateBranchDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = GhAccentBlue)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Branch")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(branches) { branch ->
                val isSelected = branch.name == currentBranch
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) GhSurfaceDark else Color(0xFF13171D)
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CallSplit, contentDescription = null, tint = if (isSelected) GhAccentBlue else GhTextSecondaryDark)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(branch.name, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                        if (isSelected) {
                            Surface(
                                color = GhSuccessGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Active", color = GhSuccessGreen, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        } else {
                            TextButton(onClick = { repoDetailViewModel.switchBranch(branch.name) }) {
                                Text("Switch", color = GhAccentBlue)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateBranchDialog) {
        var branchName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateBranchDialog = false },
            title = { Text("Create Branch", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = branchName,
                    onValueChange = { branchName = it },
                    label = { Text("Branch Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCreateBranchDialog = false
                        repoDetailViewModel.createBranch(branchName)
                    },
                    enabled = branchName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = GhAccentBlue)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateBranchDialog = false }) { Text("Cancel", color = Color.White) }
            },
            containerColor = GhSurfaceDark
        )
    }
}

@Composable
fun CommitsTabContent(repoDetailViewModel: RepoDetailViewModel) {
    val commits by repoDetailViewModel.commits.collectAsState()
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(commits) { commit ->
            CommitCardItem(commit = commit)
        }
    }
}

@Composable
fun PRsTabContent(repoDetailViewModel: RepoDetailViewModel) {
    val pullRequests by repoDetailViewModel.pullRequests.collectAsState()
    var showCreatePRDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Pull Requests", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Button(onClick = { showCreatePRDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = GhPrimaryViolet)) {
                Text("New PR")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(pullRequests) { pr ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = GhSurfaceDark),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CallMerge, contentDescription = null, tint = GhPrimaryViolet)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("#${pr.number} ${pr.title}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            Text("opened by ${pr.user?.login ?: "user"} • ${pr.createdAt ?: "recently"}", fontSize = 11.sp, color = GhTextSecondaryDark)
                        }
                    }
                }
            }
        }
    }

    if (showCreatePRDialog) {
        var prTitle by remember { mutableStateOf("") }
        var prBody by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePRDialog = false },
            title = { Text("Create Pull Request", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = prTitle, onValueChange = { prTitle = it }, label = { Text("Title") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = prBody, onValueChange = { prBody = it }, label = { Text("Body") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    showCreatePRDialog = false
                    repoDetailViewModel.createPullRequest(prTitle, "feature/ui", "main", prBody)
                }, enabled = prTitle.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = GhPrimaryViolet)) {
                    Text("Submit PR")
                }
            },
            dismissButton = { TextButton(onClick = { showCreatePRDialog = false }) { Text("Cancel", color = Color.White) } },
            containerColor = GhSurfaceDark
        )
    }
}

@Composable
fun IssuesTabContent(repoDetailViewModel: RepoDetailViewModel) {
    val issues by repoDetailViewModel.issues.collectAsState()
    var showCreateIssueDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Issues Tracker", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Button(onClick = { showCreateIssueDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = GhSuccessGreen)) {
                Text("New Issue")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(issues) { issue ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = GhSurfaceDark),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = GhSuccessGreen)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("#${issue.number} ${issue.title}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            Text("opened by ${issue.user?.login ?: "user"}", fontSize = 11.sp, color = GhTextSecondaryDark)
                        }
                    }
                }
            }
        }
    }

    if (showCreateIssueDialog) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateIssueDialog = false },
            title = { Text("Create Issue", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Issue Title") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Description") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    showCreateIssueDialog = false
                    repoDetailViewModel.createIssue(title, body)
                }, enabled = title.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = GhSuccessGreen)) {
                    Text("Create")
                }
            },
            dismissButton = { TextButton(onClick = { showCreateIssueDialog = false }) { Text("Cancel", color = Color.White) } },
            containerColor = GhSurfaceDark
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ActionsTabContent(
    repoDetailViewModel: RepoDetailViewModel,
    onShowLogViewer: (WorkflowRun) -> Unit
) {
    val workflows by repoDetailViewModel.workflows.collectAsState()
    val workflowRuns by repoDetailViewModel.workflowRuns.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            coroutineScope.launch {
                repoDetailViewModel.refreshWorkflows()
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("GitHub Actions Workflows", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            items(workflows) { wf ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = GhSurfaceDark),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = GhAccentBlue)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(wf.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            Text(wf.path, fontSize = 11.sp, color = GhTextSecondaryDark)
                        }
                        Button(onClick = { repoDetailViewModel.triggerWorkflow(wf.id) }, colors = ButtonDefaults.buttonColors(containerColor = GhAccentBlue)) {
                            Text("Run", fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                Text("Recent Workflow Runs", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            items(workflowRuns) { run ->
                var runMenuExpanded by remember { mutableStateOf(false) }
                val isRunning = run.status == "in_progress" || run.status == "queued"

                Box {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onShowLogViewer(run) },
                                onLongClick = { runMenuExpanded = true }
                            ),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = GhSurfaceDark),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = GhAccentBlue,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (run.conclusion == "success") Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = if (run.conclusion == "success") GhSuccessGreen else GhErrorRed,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(run.name ?: "Workflow Run #${run.runNumber}", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 13.sp)
                                Text("branch: ${run.headBranch} • status: ${run.status}", fontSize = 11.sp, color = GhTextSecondaryDark)
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = runMenuExpanded,
                        onDismissRequest = { runMenuExpanded = false },
                        modifier = Modifier.background(GhSurfaceDark)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy Run Logs", color = Color.White) },
                            onClick = {
                                runMenuExpanded = false
                                repoDetailViewModel.copyWorkflowLogsDirect(run.id, context)
                            },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GhAccentBlue) }
                        )
                        DropdownMenuItem(
                            text = { Text("Download Run Logs", color = GhSuccessGreen) },
                            onClick = {
                                runMenuExpanded = false
                                repoDetailViewModel.downloadWorkflowRunLogs(run.id, run.runNumber, context)
                            },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = GhSuccessGreen) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReleasesTabContent(repoDetailViewModel: RepoDetailViewModel) {
    val releases by repoDetailViewModel.releases.collectAsState()
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Releases & Assets", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        items(releases) { release ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = GhSurfaceDark),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("${release.tagName} - ${release.name}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                    Text(release.body ?: "No release notes provided", color = GhTextSecondaryDark, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
fun RepoSettingsTabContent(repoDetailViewModel: RepoDetailViewModel, onBack: () -> Unit) {
    val repository by repoDetailViewModel.repository.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Repository Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = GhSurfaceDark),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Metadata", fontWeight = FontWeight.Bold, color = GhAccentBlue)
                Text("Name: ${repository?.name}", color = Color.White)
                Text("Full Name: ${repository?.fullName}", color = Color.White)
                Text("Visibility: ${if (repository?.private == true) "Private" else "Public"}", color = Color.White)
            }
        }
    }
}