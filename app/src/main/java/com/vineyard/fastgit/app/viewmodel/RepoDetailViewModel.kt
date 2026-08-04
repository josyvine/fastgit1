package com.vineyard.fastgit.app.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vineyard.fastgit.app.models.*
import com.vineyard.fastgit.app.network.GitHubApiService
import com.vineyard.fastgit.app.network.RetrofitClient
import com.vineyard.fastgit.app.network.WorkflowRunJobsResponse
import com.vineyard.fastgit.app.network.WorkflowJob
import com.vineyard.fastgit.app.network.WorkflowStep
import com.vineyard.fastgit.app.utils.AppLogger
import com.vineyard.fastgit.app.utils.DownloadUtils
import com.vineyard.fastgit.app.utils.TokenManager
import com.vineyard.fastgit.app.utils.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import okhttp3.ResponseBody

class RepoDetailViewModel(
    application: Application,
    val owner: String,
    val repoName: String
) : AndroidViewModel(application) {

    private val tokenManager = TokenManager(application)
    private var pollingJob: Job? = null

    // Repository state
    private val _repository = MutableStateFlow<Repository?>(null)
    val repository: StateFlow<Repository?> = _repository

    // File Tree Explorer
    private val _treeItems = MutableStateFlow<List<FileItem>>(emptyList())
    val treeItems: StateFlow<List<FileItem>> = _treeItems

    private val _currentPath = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath

    // Active File Editor
    private val _activeFile = MutableStateFlow<FileItem?>(null)
    val activeFile: StateFlow<FileItem?> = _activeFile

    private val _fileContent = MutableStateFlow("")
    val fileContent: StateFlow<String> = _fileContent

    // Branches
    private val _branches = MutableStateFlow<List<Branch>>(emptyList())
    val branches: StateFlow<List<Branch>> = _branches

    private val _currentBranch = MutableStateFlow("main")
    val currentBranch: StateFlow<String> = _currentBranch

    // Commits
    private val _commits = MutableStateFlow<List<Commit>>(emptyList())
    val commits: StateFlow<List<Commit>> = _commits

    // Pull Requests
    private val _pullRequests = MutableStateFlow<List<PullRequest>>(emptyList())
    val pullRequests: StateFlow<List<PullRequest>> = _pullRequests

    // Issues
    private val _issues = MutableStateFlow<List<Issue>>(emptyList())
    val issues: StateFlow<List<Issue>> = _issues

    // Actions & Workflows
    private val _workflows = MutableStateFlow<List<Workflow>>(emptyList())
    val workflows: StateFlow<List<Workflow>> = _workflows

    private val _workflowRuns = MutableStateFlow<List<WorkflowRun>>(emptyList())
    val workflowRuns: StateFlow<List<WorkflowRun>> = _workflowRuns

    // Workflow Logs State
    private val _workflowLogs = MutableStateFlow<String?>(null)
    val workflowLogs: StateFlow<String?> = _workflowLogs

    private val _isLogsLoading = MutableStateFlow(false)
    val isLogsLoading: StateFlow<Boolean> = _isLogsLoading

    // Releases
    private val _releases = MutableStateFlow<List<Release>>(emptyList())
    val releases: StateFlow<List<Release>> = _releases

    // Progress State for Uploading Project ZIP
    private val _isUploadingZip = MutableStateFlow(false)
    val isUploadingZip: StateFlow<Boolean> = _isUploadingZip

    private val _uploadStep = MutableStateFlow("")
    val uploadStep: StateFlow<String> = _uploadStep

    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress

    // Status / Messages
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        AppLogger.i("RepoDetail", "Initializing RepoDetailViewModel for $owner/$repoName")
        loadRepositoryDetails()
    }

    private fun mergePaths(currentPath: String, fileName: String): String {
        if (currentPath.isBlank()) return fileName
        val cleanCurrent = currentPath.trim('/').replace('\\', '/')
        val cleanFile = fileName.trim('/').replace('\\', '/')

        val currentSegments = cleanCurrent.split('/')
        val fileSegments = cleanFile.split('/')

        var overlapCount = 0
        val maxPossibleOverlap = minOf(currentSegments.size, fileSegments.size)

        for (i in 1..maxPossibleOverlap) {
            val subCurrent = currentSegments.takeLast(i)
            val subFile = fileSegments.take(i)
            if (subCurrent == subFile) {
                overlapCount = i
            }
        }

        return if (overlapCount > 0) {
            val mergedSegments = currentSegments + fileSegments.drop(overlapCount)
            mergedSegments.joinToString("/")
        } else {
            "$cleanCurrent/$cleanFile"
        }
    }

    fun loadRepositoryDetails() {
        viewModelScope.launch {
            _isLoading.value = true
            AppLogger.i("RepoDetail", "Loading repository details for $owner/$repoName (DemoMode=${tokenManager.isDemoMode()})")
            try {
                if (tokenManager.isDemoMode()) {
                    _repository.value = Repository(
                        id = 1001,
                        name = repoName,
                        fullName = "$owner/$repoName",
                        owner = User(login = owner),
                        description = "Android GitHub Workspace client sample project",
                        defaultBranch = "main",
                        stargazersCount = 89,
                        forksCount = 24,
                        language = "Kotlin"
                    )
                    _branches.value = listOf(Branch("main"), Branch("feature/ui-updates"), Branch("dev"))
                    _treeItems.value = getSampleAndroidProjectTree()
                    _commits.value = getSampleCommits()
                    _pullRequests.value = getSamplePullRequests()
                    _issues.value = getSampleIssues()
                    _workflows.value = listOf(Workflow(1, "Android CI/CD", ".github/workflows/android.yml", "active"))
                    _workflowRuns.value = listOf(WorkflowRun(101, "Android CI/CD", "completed", "success", "main", 12))
                    _releases.value = listOf(Release(1, "v1.0.0", "FastGit Initial Release", "Initial Android App release"))
                    AppLogger.s("RepoDetail", "Loaded repository details successfully in Demo Mode")
                } else {
                    val api = RetrofitClient.getService(tokenManager)
                    val repo = api.getRepository(owner, repoName)
                    _repository.value = repo
                    _currentBranch.value = repo.defaultBranch

                    val branchList = try { api.getBranches(owner, repoName) } catch (e: Exception) { listOf(Branch(repo.defaultBranch)) }
                    _branches.value = branchList

                    loadContents("")
                    loadCommits()
                    loadPullRequests()
                    loadIssues()
                    loadWorkflows()
                    loadReleases()
                    AppLogger.s("RepoDetail", "Fetched live repository details from GitHub API")
                }
            } catch (e: Exception) {
                AppLogger.e("RepoDetail", "Failed to load repository details: ${e.message}", e)
                // Fallback demo sample
                _repository.value = Repository(name = repoName, fullName = "$owner/$repoName")
                _treeItems.value = getSampleAndroidProjectTree()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadContents(path: String) {
        navigateToDirectory(path)
    }

    fun refreshExplorer() {
        loadContents(_currentPath.value)
    }

    fun refreshWorkflows() {
        loadWorkflows()
    }

    fun navigateToDirectory(path: String) {
        _currentPath.value = path
        if (tokenManager.isDemoMode()) {
            val root = getSampleAndroidProjectTree()
            _treeItems.value = root
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                AppLogger.i("GitHubAPI", "Fetching contents for path '$path' on branch '${_currentBranch.value}'")
                val api = RetrofitClient.getService(tokenManager)
                val contents = try {
                    api.getContents(owner, repoName, path, _currentBranch.value)
                } catch (e: retrofit2.HttpException) {
                    if (e.code() == 404) {
                        AppLogger.i("GitHubAPI", "Path '$path' returned 404 (empty repo or directory)")
                        emptyList()
                    } else {
                        throw e
                    }
                }
                
                if (path.isEmpty()) {
                    _treeItems.value = contents
                } else {
                    val currentTree = _treeItems.value
                    if (currentTree.isEmpty()) {
                        _treeItems.value = contents
                    } else {
                        val updated = updateTreeWithContents(currentTree, path, contents)
                        _treeItems.value = updated
                    }
                }
                AppLogger.s("GitHubAPI", "Successfully loaded ${contents.size} items for path '$path'")
            } catch (e: Exception) {
                AppLogger.e("GitHubAPI", "Error loading contents for path '$path': ${e.message}", e)
                if (path.isEmpty()) {
                    _treeItems.value = emptyList()
                } else {
                    _statusMessage.value = "Error loading directory '$path': ${e.message}"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchSubfolderContents(dirPath: String) {
        if (tokenManager.isDemoMode()) return
        viewModelScope.launch {
            try {
                AppLogger.i("GitHubAPI", "Fetching subfolder contents for '$dirPath'")
                val api = RetrofitClient.getService(tokenManager)
                val contents = api.getContents(owner, repoName, dirPath, _currentBranch.value)
                val updated = updateTreeWithContents(_treeItems.value, dirPath, contents)
                _treeItems.value = updated
                AppLogger.s("GitHubAPI", "Populated ${contents.size} children for subfolder '$dirPath'")
            } catch (e: Exception) {
                AppLogger.e("GitHubAPI", "Error fetching subfolder '$dirPath': ${e.message}", e)
            }
        }
    }

    private fun updateTreeWithContents(
        currentTree: List<FileItem>,
        targetPath: String,
        contents: List<FileItem>
    ): List<FileItem> {
        if (targetPath.isEmpty()) return contents

        val segments = targetPath.split("/").filter { it.isNotEmpty() }
        return updateTreeSegmentsRecursively(currentTree, segments, 0, contents)
    }

    private fun updateTreeSegmentsRecursively(
        nodes: List<FileItem>,
        segments: List<String>,
        segmentIndex: Int,
        newChildren: List<FileItem>
    ): List<FileItem> {
        if (segmentIndex >= segments.size) return nodes

        val currentSegmentName = segments[segmentIndex]
        val currentSegmentPath = segments.take(segmentIndex + 1).joinToString("/")

        // Check if the current segment already exists in the list
        val existingNodeIndex = nodes.indexOfFirst { it.name == currentSegmentName && it.type == "dir" }

        val mutableNodes = nodes.toMutableList()

        if (existingNodeIndex != -1) {
            val existingNode = mutableNodes[existingNodeIndex]
            if (segmentIndex == segments.size - 1) {
                // We reached the final target directory segment. Graft the newly fetched contents.
                mutableNodes[existingNodeIndex] = existingNode.copy(children = newChildren.toMutableList())
            } else {
                // Intermediate segment. Recursively traverse down.
                val updatedChildren = updateTreeSegmentsRecursively(
                    existingNode.children,
                    segments,
                    segmentIndex + 1,
                    newChildren
                )
                mutableNodes[existingNodeIndex] = existingNode.copy(children = updatedChildren.toMutableList())
            }
        } else {
            // Segment doesn't exist. We must create the missing directory node to preserve tree structure.
            val isFinalSegment = segmentIndex == segments.size - 1
            val createdNode = FileItem(
                name = currentSegmentName,
                path = currentSegmentPath,
                type = "dir",
                children = if (isFinalSegment) newChildren.toMutableList() else mutableListOf()
            )

            if (!isFinalSegment) {
                // Populate intermediate descendants recursively
                val populatedChildren = updateTreeSegmentsRecursively(
                    createdNode.children,
                    segments,
                    segmentIndex + 1,
                    newChildren
                )
                mutableNodes.add(createdNode.copy(children = populatedChildren.toMutableList()))
            } else {
                mutableNodes.add(createdNode)
            }
        }

        return mutableNodes
    }

    private fun copyTreeWithUpdatedChildren(node: FileItem, targetPath: String, newChildren: List<FileItem>): FileItem {
        if (node.path == targetPath) {
            return node.copy(children = newChildren.toMutableList())
        }
        if (node.type == "dir" && node.children.isNotEmpty()) {
            val updatedChildren = node.children.map { copyTreeWithUpdatedChildren(it, targetPath, newChildren) }.toMutableList()
            return node.copy(children = updatedChildren)
        }
        return node
    }

    fun openFile(fileItem: FileItem) {
        AppLogger.i("CodeEditor", "Opening file '${fileItem.path}'")
        _isLoading.value = true // Display the linear loading progress indicator to provide immediate user feedback
        viewModelScope.launch {
            try {
                if (tokenManager.isDemoMode()) {
                    _fileContent.value = fileItem.content ?: "// Sample Code Content for ${fileItem.name}\npackage com.vineyard.fastgit.app\n\nclass ${fileItem.name.removeSuffix(".kt")} {\n    fun init() {\n        println(\"FastGit Explorer\")\n    }\n}"
                    AppLogger.s("CodeEditor", "Opened file in Demo Mode: ${fileItem.name}")
                    _activeFile.value = fileItem // Transition UI once loading is safe and complete
                } else {
                    val api = RetrofitClient.getService(tokenManager)
                    val details = api.getSingleFileContent(owner, repoName, fileItem.path, _currentBranch.value)
                    if (details.encoding == "base64" && details.content != null) {
                        val cleanB64 = details.content.replace("\n", "").replace("\r", "")
                        val decoded = String(Base64.decode(cleanB64, Base64.DEFAULT))
                        _fileContent.value = decoded
                    } else {
                        _fileContent.value = details.content ?: ""
                    }
                    AppLogger.s("CodeEditor", "Successfully fetched file content for ${fileItem.path}")
                    _activeFile.value = fileItem // Transition UI once loading is safe and complete
                }
            } catch (e: Exception) {
                AppLogger.e("CodeEditor", "Failed to load content for ${fileItem.path}: ${e.message}", e)
                _fileContent.value = "// Error loading file content: ${e.message}"
                _activeFile.value = fileItem // Display error message details in editor
            } finally {
                _isLoading.value = false // Dismiss loader safely
            }
        }
    }

    fun downloadSingleFileToDevice(fileItem: FileItem, content: String, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Call our MediaStore file helper to resolve Android Q+ ENOENT file creation issues
                val targetFile = DownloadUtils.saveTextToDownloads(context, "", fileItem.name, content)
                withContext(Dispatchers.Main) {
                    if (targetFile != null) {
                        Toast.makeText(context, "Saved successfully to: Downloads/FastGit/${fileItem.name}", Toast.LENGTH_LONG).show()
                        _statusMessage.value = "Downloaded: Downloads/FastGit/${fileItem.name}"
                    } else {
                        Toast.makeText(context, "Failed to download: MediaStore write error", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("DownloadFile", "Error saving editor file: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun saveAndCommitFile(fileItem: FileItem, updatedContent: String, commitMessage: String) {
        viewModelScope.launch {
            _isLoading.value = true
            AppLogger.i("CodeEditor", "Committing changes to '${fileItem.path}' with message: '$commitMessage'")
            try {
                if (tokenManager.isDemoMode()) {
                    _fileContent.value = updatedContent
                    _statusMessage.value = "Changes committed: '$commitMessage'"
                    AppLogger.s("CodeEditor", "Committed file changes in Demo Mode")
                } else {
                    val api = RetrofitClient.getService(tokenManager)
                    val b64Content = Base64.encodeToString(updatedContent.toByteArray(), Base64.NO_WRAP)
                    val req = CreateFileRequest(
                        message = commitMessage,
                        content = b64Content,
                        sha = fileItem.sha,
                        branch = _currentBranch.value
                    )
                    api.createOrUpdateFile(owner, repoName, fileItem.path, req)
                    _fileContent.value = updatedContent
                    _statusMessage.value = "File updated and committed successfully!"
                    AppLogger.s("CodeEditor", "Committed file '${fileItem.path}' to GitHub")
                    loadContents(_currentPath.value)
                }
            } catch (e: Exception) {
                AppLogger.e("CodeEditor", "Failed to commit file '${fileItem.path}': ${e.message}", e)
                _statusMessage.value = "Failed to commit: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createNewFile(fileName: String, initialContent: String, commitMessage: String) {
        val fullPath = mergePaths(_currentPath.value, fileName)
        viewModelScope.launch {
            _isLoading.value = true
            AppLogger.i("FileTree", "Creating new file '$fullPath'")
            try {
                if (tokenManager.isDemoMode()) {
                    val newFile = FileItem(name = fileName, path = fullPath, type = "file", content = initialContent)
                    _treeItems.value = _treeItems.value + newFile
                    _statusMessage.value = "File '$fileName' created!"
                    AppLogger.s("FileTree", "Created new file '$fullPath' in local tree")
                } else {
                    val api = RetrofitClient.getService(tokenManager)
                    val b64Content = Base64.encodeToString(initialContent.toByteArray(), Base64.NO_WRAP)
                    val req = CreateFileRequest(
                        message = commitMessage,
                        content = b64Content,
                        branch = _currentBranch.value
                    )
                    api.createOrUpdateFile(owner, repoName, fullPath, req)
                    _statusMessage.value = "File '$fileName' created successfully!"
                    AppLogger.s("FileTree", "Created file '$fullPath' on GitHub branch '${_currentBranch.value}'")
                    loadContents(_currentPath.value)
                }
            } catch (e: Exception) {
                AppLogger.e("FileTree", "Failed to create file '$fullPath': ${e.message}", e)
                _statusMessage.value = "Failed to create file: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun uploadProjectZip(zipUri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            AppLogger.i("ZipUpload", "Starting ZIP upload pipeline for URI: $zipUri")
            _isUploadingZip.value = true
            _uploadProgress.value = 0.05f
            _uploadStep.value = "Reading ZIP File..."

            try {
                val inputStream = context.contentResolver.openInputStream(zipUri)
                if (inputStream == null) {
                    val errorMsg = "Failed to open InputStream for ZIP URI: $zipUri"
                    AppLogger.e("ZipUpload", errorMsg)
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = errorMsg
                        _isUploadingZip.value = false
                    }
                    return@launch
                }
                AppLogger.s("ZipUpload", "Opened InputStream successfully!")

                _uploadStep.value = "Extracting files..."
                _uploadProgress.value = 0.20f
                val tempDir = File(context.cacheDir, "unzipped_project_${System.currentTimeMillis()}")
                AppLogger.i("ZipUpload", "Extracting ZIP contents into temporary workspace: ${tempDir.absolutePath}")

                val extractedFiles = ZipUtils.unzip(inputStream, tempDir)
                AppLogger.s("ZipUpload", "Unzipped ${extractedFiles.size} raw file objects to disk")

                _uploadStep.value = "Scanning folder structure..."
                _uploadProgress.value = 0.35f
                val scannedFiles = ZipUtils.scanDirectory(tempDir)
                AppLogger.i("ZipUpload", "Scanned ${scannedFiles.size} project files with relative paths")

                if (scannedFiles.isEmpty()) {
                    val msg = "ZIP archive contains no valid project files"
                    AppLogger.e("ZipUpload", msg)
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = msg
                        _isUploadingZip.value = false
                    }
                    return@launch
                }

                // Log each scanned file relative path
                scannedFiles.forEachIndexed { idx, scanned ->
                    AppLogger.i("ZipUpload", "[$idx] Parsed file: '${scanned.relativePath}' (Base64 size: ${scanned.contentBase64.length})")
                }

                _uploadStep.value = "Building File Tree and Uploading..."
                val total = scannedFiles.size
                var uploadedCount = 0

                val api = if (!tokenManager.isDemoMode()) RetrofitClient.getService(tokenManager) else null

                for (scanned in scannedFiles) {
                    uploadedCount++
                    val progressRatio = 0.35f + (uploadedCount.toFloat() / total) * 0.55f
                    _uploadProgress.value = progressRatio
                    _uploadStep.value = "Processing ($uploadedCount/$total): ${scanned.relativePath}"

                    if (api != null) {
                        try {
                            AppLogger.i("GitHubAPI", "Uploading file $uploadedCount/$total to GitHub: ${scanned.relativePath}")
                            val req = CreateFileRequest(
                                message = "Add ${scanned.relativePath} via FastGit Mobile App",
                                content = scanned.contentBase64,
                                branch = _currentBranch.value
                            )
                            api.createOrUpdateFile(owner, repoName, scanned.relativePath, req)
                            AppLogger.s("GitHubAPI", "Committed ${scanned.relativePath} to GitHub!")
                        } catch (e: Exception) {
                            AppLogger.e("GitHubAPI", "Failed to commit file ${scanned.relativePath}: ${e.message}", e)
                        }
                    }
                }

                // Construct full FileItem hierarchy from scanned files and set state
                val newlyExtractedTree = buildFileTreeFromScannedFiles(scannedFiles)
                AppLogger.s("ZipUpload", "Generated ${newlyExtractedTree.size} top-level nodes in local File Explorer tree")

                _uploadStep.value = "Updating Repository Explorer Tree..."
                _uploadProgress.value = 1.0f

                withContext(Dispatchers.Main) {
                    // Update tree items so user sees all uploaded files immediately!
                    _treeItems.value = newlyExtractedTree
                    _statusMessage.value = "Project ZIP uploaded successfully! ($total files extracted)"
                    _isUploadingZip.value = false
                    AppLogger.s("ZipUpload", "ZIP upload completed successfully! Explorer tree refreshed with $total files.")
                }

                // Cleanup temp dir
                tempDir.deleteRecursively()
                AppLogger.i("ZipUpload", "Temporary extraction directory cleaned up")

            } catch (e: Exception) {
                AppLogger.e("ZipUpload", "ZIP Upload process caught exception: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "ZIP Upload failed: ${e.message}"
                    _isUploadingZip.value = false
                }
            }
        }
    }

    fun downloadFolderAsZip(folderItem: FileItem, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _isLoading.value = true
                _statusMessage.value = "Downloading ${folderItem.name} as ZIP..."
            }
            
            try {
                val safeName = if (folderItem.name.isNotBlank() && folderItem.name != "..") folderItem.name else repoName
                val fileName = "$safeName.zip"

                if (tokenManager.isDemoMode()) {
                    val demoItems = listOf(
                        FileItem(name = "AndroidManifest.xml", type = "file", content = "<?xml version=\"1.0\"?>\n<manifest/>"),
                        FileItem(name = "MainActivity.kt", type = "file", content = "package com.example\n\nclass MainActivity")
                    )
                    val zip = DownloadUtils.createZipFromFolderItems(context, safeName, demoItems)
                    val bytes = zip.readBytes()
                    val savedFile = DownloadUtils.saveBinaryToDownloads(context, "", fileName, bytes)
                    zip.delete() // Cleanup cached temporary file

                    withContext(Dispatchers.Main) {
                        if (savedFile != null) {
                            _statusMessage.value = "Folder saved successfully to: Downloads/FastGit/$fileName"
                        } else {
                            _statusMessage.value = "Failed to save ZIP to local storage"
                        }
                    }
                    return@launch
                }

                val api = RetrofitClient.getService(tokenManager)

                // 1. Try downloading GitHub's native zipball if downloading root or parent folder
                val isRootOrParent = folderItem.path.isBlank() || folderItem.name == ".." || folderItem.name == repoName || folderItem.name == "root"
                if (isRootOrParent) {
                    try {
                        AppLogger.i("GitHubAPI", "Attempting GitHub zipball download for $owner/$repoName on branch ${_currentBranch.value}")
                        val zipResponse = api.downloadZipball(owner, repoName, _currentBranch.value)
                        if (zipResponse.isSuccessful && zipResponse.body() != null) {
                            val bytes = zipResponse.body()!!.bytes()
                            val savedFile = DownloadUtils.saveBinaryToDownloads(context, "", fileName, bytes)
                            if (savedFile != null) {
                                AppLogger.s("GitHubAPI", "Downloaded zipball successfully and saved to local storage.")
                                withContext(Dispatchers.Main) {
                                    _statusMessage.value = "Exported $fileName successfully to Downloads/FastGit"
                                }
                                return@launch
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.e("GitHubAPI", "Zipball download failed, falling back to recursive download: ${e.message}", e)
                    }
                }

                // 2. Recursive file fetcher for specific subfolder or fallback
                val resolvedFiles = fetchFolderFilesRecursively(api, owner, repoName, folderItem.path, _currentBranch.value)
                val zip = DownloadUtils.createZipFromFolderItems(context, safeName, resolvedFiles)
                val bytes = zip.readBytes()
                val savedFile = DownloadUtils.saveBinaryToDownloads(context, "", fileName, bytes)
                zip.delete() // Cleanup cached temporary file
                
                withContext(Dispatchers.Main) {
                    if (savedFile != null) {
                        _statusMessage.value = "Folder saved successfully to: Downloads/FastGit/$fileName"
                    } else {
                        _statusMessage.value = "Failed to save ZIP to local storage"
                    }
                }

            } catch (e: Exception) {
                AppLogger.e("GitHubAPI", "Error downloading folder zip: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Failed to export ZIP: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    private suspend fun fetchFolderFilesRecursively(
        api: com.vineyard.fastgit.app.network.GitHubApiService,
        owner: String,
        repo: String,
        dirPath: String,
        branch: String
    ): List<FileItem> {
        val result = mutableListOf<FileItem>()
        try {
            val items = api.getContents(owner, repo, dirPath, branch)
            for (item in items) {
                if (item.type == "dir") {
                    val subChildren = fetchFolderFilesRecursively(api, owner, repo, item.path, branch)
                    result.add(item.copy(children = subChildren.toMutableList()))
                } else if (item.type == "file") {
                    try {
                        val single = api.getSingleFileContent(owner, repo, item.path, branch)
                        val bytes = if (single.encoding == "base64" && single.content != null) {
                            val cleanB64 = single.content.replace("\n", "").replace("\r", "")
                            Base64.decode(cleanB64, Base64.DEFAULT)
                        } else {
                            single.content?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
                        }
                        result.add(item.copy(byteContent = bytes, content = single.content))
                    } catch (e: Exception) {
                        AppLogger.e("GitHubAPI", "Failed to fetch content for file ${item.path}: ${e.message}", e)
                        result.add(item)
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("GitHubAPI", "Failed to fetch contents for directory '$dirPath': ${e.message}", e)
        }
        return result
    }

    private val _copiedItem = MutableStateFlow<FileItem?>(null)
    val copiedItem: StateFlow<FileItem?> = _copiedItem

    fun copyItem(item: FileItem) {
        _copiedItem.value = item
        _statusMessage.value = "Copied '${item.name}' to clipboard"
    }

    fun pasteCopiedItem(targetPath: String) {
        val item = _copiedItem.value ?: return
        val newFileName = item.name
        val destPath = if (targetPath.isBlank()) newFileName else "$targetPath/$newFileName"
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (tokenManager.isDemoMode()) {
                    _statusMessage.value = "Pasted '${item.name}' to /$destPath"
                } else {
                    val api = RetrofitClient.getService(tokenManager)
                    val contentToPaste = if (item.content != null) {
                        Base64.encodeToString(item.content.toByteArray(), Base64.NO_WRAP)
                    } else {
                        val single = api.getSingleFileContent(owner, repoName, item.path, _currentBranch.value)
                        single.content ?: ""
                    }
                    val req = CreateFileRequest(
                        message = "Paste ${item.name} into /$destPath",
                        content = contentToPaste,
                        branch = _currentBranch.value
                    )
                    api.createOrUpdateFile(owner, repoName, destPath, req)
                    _statusMessage.value = "Pasted '${item.name}' to /$destPath"
                    loadContents(_currentPath.value)
                }
            } catch (e: Exception) {
                _statusMessage.value = "Failed to paste: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteItem(item: FileItem) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (tokenManager.isDemoMode()) {
                    fun removeRecursive(list: List<FileItem>): List<FileItem> {
                        return list.filter { it.path != item.path }.map {
                            if (it.children.isNotEmpty()) it.copy(children = removeRecursive(it.children).toMutableList()) else it
                        }
                    }
                    _treeItems.value = removeRecursive(_treeItems.value)
                    _statusMessage.value = "Deleted '${item.name}'"
                } else {
                    val api = RetrofitClient.getService(tokenManager)
                    if (item.type == "dir") {
                        _statusMessage.value = "Deleting folder '${item.name}'..."
                        deleteDirectoryRecursively(api, owner, repoName, item.path, _currentBranch.value)
                        _statusMessage.value = "Deleted folder '${item.name}' successfully!"
                    } else {
                        val sha = if (item.sha.isNotBlank()) item.sha else {
                            try {
                                val single = api.getSingleFileContent(owner, repoName, item.path, _currentBranch.value)
                                single.sha
                            } catch (e: Exception) { "" }
                        }
                        val body = mapOf(
                            "message" to "Delete ${item.name} via FastGit Mobile",
                            "sha" to sha,
                            "branch" to _currentBranch.value
                        )
                        val resp = api.deleteFile(owner, repoName, item.path, body)
                        if (resp.isSuccessful) {
                            _statusMessage.value = "Deleted '${item.name}'"
                        } else {
                            _statusMessage.value = "Delete failed: ${resp.errorBody()?.string() ?: resp.message()}"
                        }
                    }
                    loadContents(_currentPath.value)
                }
            } catch (e: Exception) {
                AppLogger.e("Delete", "Failed to delete ${item.path}: ${e.message}", e)
                _statusMessage.value = "Failed to delete: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun deleteDirectoryRecursively(
        api: GitHubApiService,
        owner: String,
        repo: String,
        dirPath: String,
        branch: String
    ) {
        val items = try {
            api.getContents(owner, repo, dirPath, branch)
        } catch (e: Exception) {
            emptyList()
        }
        for (child in items) {
            if (child.type == "dir") {
                deleteDirectoryRecursively(api, owner, repo, child.path, branch)
            } else {
                val sha = if (child.sha.isNotBlank()) child.sha else {
                    try {
                        val single = api.getSingleFileContent(owner, repo, child.path, branch)
                        single.sha
                    } catch (e: Exception) { "" }
                }
                val body = mapOf(
                    "message" to "Delete ${child.name} in $dirPath via FastGit Mobile",
                    "sha" to sha,
                    "branch" to branch
                )
                api.deleteFile(owner, repo, child.path, body)
            }
        }
    }

    fun renameItem(item: FileItem, newName: String) {
        if (newName.isBlank() || newName == item.name) return
        val parentDir = if (item.path.contains("/")) item.path.substringBeforeLast("/") else ""
        val newPath = if (parentDir.isBlank()) newName else "$parentDir/$newName"
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (tokenManager.isDemoMode()) {
                    _statusMessage.value = "Renamed '${item.name}' to '$newName'"
                } else {
                    val api = RetrofitClient.getService(tokenManager)
                    val single = api.getSingleFileContent(owner, repoName, item.path, _currentBranch.value)
                    val b64 = single.content ?: ""
                    val sha = single.sha ?: ""
                    val req = CreateFileRequest(
                        message = "Rename ${item.name} -> $newName",
                        content = b64,
                        branch = _currentBranch.value
                    )
                    api.createOrUpdateFile(owner, repoName, newPath, req)
                    val delBody = mapOf("message" to "Remove old ${item.name}", "sha" to sha, "branch" to _currentBranch.value)
                    api.deleteFile(owner, repoName, item.path, delBody)
                    _statusMessage.value = "Renamed to '$newName'"
                    loadContents(_currentPath.value)
                }
            } catch (e: Exception) {
                _statusMessage.value = "Failed to rename: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun performGlobalSearchAndReplace(searchQuery: String, replaceQuery: String, onFinished: (Int, Int) -> Unit) {
        if (searchQuery.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _isLoading.value = true
                _statusMessage.value = "Searching for '$searchQuery'..."
            }
            try {
                if (tokenManager.isDemoMode()) {
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Replaced '$searchQuery' with '$replaceQuery' in 2 files (Demo)"
                        onFinished(2, 5)
                    }
                    return@launch
                }

                val api = RetrofitClient.getService(tokenManager)
                val allFiles = fetchFolderFilesRecursively(api, owner, repoName, "", _currentBranch.value)
                val textFiles = mutableListOf<FileItem>()

                fun collectFiles(items: List<FileItem>) {
                    for (i in items) {
                        if (i.type == "file") textFiles.add(i)
                        if (i.children.isNotEmpty()) collectFiles(i.children)
                    }
                }
                collectFiles(allFiles)

                var filesModified = 0
                var totalOccurrences = 0

                for (file in textFiles) {
                    val rawContent = file.content ?: continue
                    val text = if (file.encoding == "base64") {
                        try { String(Base64.decode(rawContent.replace("\n", "").replace("\r", ""), Base64.DEFAULT)) } catch (e: Exception) { rawContent }
                    } else rawContent

                    if (text.contains(searchQuery)) {
                        val count = text.split(searchQuery).size - 1
                        totalOccurrences += count
                        val updated = text.replace(searchQuery, replaceQuery)
                        val b64 = Base64.encodeToString(updated.toByteArray(), Base64.NO_WRAP)
                        val req = CreateFileRequest(
                            message = "Refactor: Replace '$searchQuery' -> '$replaceQuery' in ${file.name}",
                            content = b64,
                            sha = file.sha,
                            branch = _currentBranch.value
                        )
                        api.createOrUpdateFile(owner, repoName, file.path, req)
                        filesModified++
                    }
                }

                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Replaced $totalOccurrences occurrences across $filesModified files!"
                    onFinished(filesModified, totalOccurrences)
                    loadContents(_currentPath.value)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Search & replace failed: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    fun switchBranch(branchName: String) {
        _currentBranch.value = branchName
        loadContents("")
        loadCommits()
    }

    fun createBranch(newBranchName: String) {
        viewModelScope.launch {
            try {
                if (tokenManager.isDemoMode()) {
                    _branches.value = _branches.value + Branch(newBranchName)
                    _currentBranch.value = newBranchName
                    _statusMessage.value = "Branch '$newBranchName' created!"
                } else {
                    val api = RetrofitClient.getService(tokenManager)
                    val commits = api.getCommits(owner, repoName, _currentBranch.value)
                    val latestSha = commits.firstOrNull()?.sha ?: "main"
                    api.createBranch(owner, repoName, mapOf("ref" to "refs/heads/$newBranchName", "sha" to latestSha))
                    _branches.value = _branches.value + Branch(newBranchName)
                    _currentBranch.value = newBranchName
                    _statusMessage.value = "Branch '$newBranchName' created!"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Failed to create branch: ${e.message}"
            }
        }
    }

    fun loadCommits() {
        if (tokenManager.isDemoMode()) return
        viewModelScope.launch {
            try {
                val api = RetrofitClient.getService(tokenManager)
                _commits.value = api.getCommits(owner, repoName, _currentBranch.value)
            } catch (e: Exception) { }
        }
    }

    fun loadPullRequests() {
        if (tokenManager.isDemoMode()) return
        viewModelScope.launch {
            try {
                val api = RetrofitClient.getService(tokenManager)
                _pullRequests.value = api.getPullRequests(owner, repoName)
            } catch (e: Exception) { }
        }
    }

    fun createPullRequest(title: String, head: String, base: String, body: String) {
        viewModelScope.launch {
            try {
                if (tokenManager.isDemoMode()) {
                    val pr = PullRequest(id = System.currentTimeMillis(), number = _pullRequests.value.size + 1, title = title, body = body, user = User(login = owner))
                    _pullRequests.value = listOf(pr) + _pullRequests.value
                    _statusMessage.value = "Pull Request #${pr.number} created!"
                } else {
                    val api = RetrofitClient.getService(tokenManager)
                    val pr = api.createPullRequest(owner, repoName, CreatePRRequest(title, head, base, body))
                    _pullRequests.value = listOf(pr) + _pullRequests.value
                    _statusMessage.value = "Pull Request #${pr.number} created!"
                }
            } catch (e: Exception) {
                _statusMessage.value = "PR creation failed: ${e.message}"
            }
        }
    }

    fun loadIssues() {
        if (tokenManager.isDemoMode()) return
        viewModelScope.launch {
            try {
                val api = RetrofitClient.getService(tokenManager)
                _issues.value = api.getIssues(owner, repoName)
            } catch (e: Exception) { }
        }
    }

    fun createIssue(title: String, body: String) {
        viewModelScope.launch {
            try {
                if (tokenManager.isDemoMode()) {
                    val issue = Issue(id = System.currentTimeMillis(), number = _issues.value.size + 1, title = title, body = body, user = User(login = owner))
                    _issues.value = listOf(issue) + _issues.value
                    _statusMessage.value = "Issue #${issue.number} created!"
                } else {
                    val api = RetrofitClient.getService(tokenManager)
                    val issue = api.createIssue(owner, repoName, CreateIssueRequest(title, body))
                    _issues.value = listOf(issue) + _issues.value
                    _statusMessage.value = "Issue #${issue.number} created!"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Issue creation failed: ${e.message}"
            }
        }
    }

    fun loadWorkflows() {
        if (tokenManager.isDemoMode()) return
        viewModelScope.launch {
            try {
                val api = RetrofitClient.getService(tokenManager)
                _workflows.value = api.getWorkflows(owner, repoName).workflows
                _workflowRuns.value = api.getWorkflowRuns(owner, repoName).workflowRuns
            } catch (e: Exception) { }
        }
    }

    fun fetchWorkflowRunLogs(runId: Long) {
        // Cancel any previously active background polling sessions first
        pollingJob?.cancel()

        if (tokenManager.isDemoMode()) {
            _isLogsLoading.value = true
            viewModelScope.launch {
                delay(1200)
                _workflowLogs.value = """
                    [Actions] Initializing build agent workspace environment...
                    [CI/CD] Triggered run matching standard Kotlin-Android compilation sequence.
                    [Gradle] Executing job tasks: ':app:compileDebugKotlin' and ':app:bundleDebugClasses'
                    [Compiler] Scanned 34 project code sources with warnings: 0, errors: 0.
                    [Artifact] app-debug.apk bundle successfully constructed (Size: 3.42 MB).
                    [Finished] Process session completed cleanly.
                """.trimIndent()
                _isLogsLoading.value = false
            }
            return
        }

        // Launch a continuous, active polling loop scoped to the viewModelScope
        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            var shouldContinuePolling = true
            var isFirstFetch = true

            while (shouldContinuePolling) {
                if (isFirstFetch) {
                    _isLogsLoading.value = true
                    withContext(Dispatchers.Main) {
                        _workflowLogs.value = "Retrieving workflow build parameters..."
                    }
                }

                try {
                    val api = RetrofitClient.getService(tokenManager)
                    val jobsResponse = api.getWorkflowRunJobs(owner, repoName, runId)
                    val jobs = jobsResponse.jobs ?: emptyList()

                    if (jobs.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            _workflowLogs.value = "No build jobs detected for this workflow run."
                        }
                        shouldContinuePolling = false
                    } else {
                        val combinedLogs = StringBuilder()
                        var anyJobActive = false

                        for (job in jobs) {
                            // Track if there are active in-progress or queued jobs to determine polling state
                            if (job.status != "completed") {
                                anyJobActive = true
                            }

                            combinedLogs.append("--- JOB STEP: ${job.name} (Status: ${job.status}, Conclusion: ${job.conclusion ?: "pending"}) ---\n")
                            try {
                                val logBody = api.getJobLogs(owner, repoName, job.id)
                                combinedLogs.append(logBody.string())
                            } catch (e: Exception) {
                                // Handle active step progress checklist fallback on HTTP 404
                                if (e is retrofit2.HttpException && e.code() == 404) {
                                    combinedLogs.append("[Active Job Build Steps]\n")
                                    combinedLogs.append("--------------------------------------------------\n")
                                    val steps = job.steps ?: emptyList()
                                    if (steps.isEmpty()) {
                                        combinedLogs.append("Initializing build runner steps...\n")
                                    } else {
                                        for (step in steps) {
                                            val statusIcon = when (step.status) {
                                                "completed" -> if (step.conclusion == "success") "✔" else "✘"
                                                "in_progress" -> "⟳"
                                                else -> "○"
                                            }
                                            val conclusionText = if (step.conclusion != null) {
                                                " (${step.conclusion})"
                                            } else ""
                                            combinedLogs.append("$statusIcon ${step.number}. ${step.name} - ${step.status}$conclusionText\n")
                                        }
                                    }
                                    combinedLogs.append("--------------------------------------------------\n")
                                    combinedLogs.append("(Live build is in-progress. Full raw logs will be finalized on completion.)\n")
                                } else {
                                    combinedLogs.append("Unable to retrieve logs for step execution: ${e.message}\n")
                                }
                            }
                            combinedLogs.append("\n")
                        }

                        withContext(Dispatchers.Main) {
                            _workflowLogs.value = combinedLogs.toString()
                        }

                        // If all build steps and jobs have completed, we can safely terminate the polling loop
                        if (!anyJobActive) {
                            shouldContinuePolling = false
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("WorkflowLogs", "Active polling cycle failed: ${e.message}", e)
                    if (isFirstFetch) {
                        withContext(Dispatchers.Main) {
                            _workflowLogs.value = "Workflow logs retrieve error: ${e.message}"
                        }
                        shouldContinuePolling = false
                    }
                } finally {
                    if (isFirstFetch) {
                        _isLogsLoading.value = false
                        isFirstFetch = false
                    }
                }

                // Delay polling for 3 seconds before executing the next check
                if (shouldContinuePolling) {
                    delay(3000)
                }
            }
        }
    }

    fun downloadWorkflowRunLogs(runId: Long, runNumber: Int, context: Context) {
        val logs = _workflowLogs.value
        if (logs.isNullOrBlank()) {
            Toast.makeText(context, "Request log parameters first to trigger download.", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val fastGitDir = File(downloadsDir, "FastGit/Logs")
                if (!fastGitDir.exists()) {
                    fastGitDir.mkdirs()
                }
                val targetFile = File(fastGitDir, "build_run_${runNumber}.txt")
                targetFile.writeText(logs)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved log report to: Downloads/FastGit/Logs/build_run_${runNumber}.txt", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                AppLogger.e("DownloadLogs", "Error exporting logs to storage: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Log download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun copyWorkflowLogsDirect(runId: Long, context: Context) {
        if (tokenManager.isDemoMode()) {
            val mockLogs = """
                [Actions] Initializing build agent workspace environment...
                [CI/CD] Triggered run matching standard Kotlin-Android compilation sequence.
                [Gradle] Executing job tasks: ':app:compileDebugKotlin' and ':app:bundleDebugClasses'
                [Compiler] Scanned 34 project code sources with warnings: 0, errors: 0.
                [Artifact] app-debug.apk bundle successfully constructed (Size: 3.42 MB).
                [Finished] Process session completed cleanly.
            """.trimIndent()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Build Logs", mockLogs)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Build logs copied to clipboard!", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val api = RetrofitClient.getService(tokenManager)
                val jobsResponse = api.getWorkflowRunJobs(owner, repoName, runId)
                val jobs = jobsResponse.jobs ?: emptyList()
                if (jobs.isEmpty()) {
                    Toast.makeText(context, "No jobs available to extract logs.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val combinedLogs = StringBuilder()
                for (job in jobs) {
                    combinedLogs.append("--- JOB STEP: ${job.name} (Status: ${job.status}, Conclusion: ${job.conclusion ?: "pending"}) ---\n")
                    try {
                        val logBody = api.getJobLogs(owner, repoName, job.id)
                        combinedLogs.append(logBody.string())
                    } catch (e: Exception) {
                        if (e is retrofit2.HttpException && e.code() == 404) {
                            combinedLogs.append("[Active Job Build Steps]\n")
                            combinedLogs.append("--------------------------------------------------\n")
                            val steps = job.steps ?: emptyList()
                            if (steps.isEmpty()) {
                                combinedLogs.append("Initializing build runner steps...\n")
                            } else {
                                for (step in steps) {
                                    val statusIcon = when (step.status) {
                                        "completed" -> if (step.conclusion == "success") "✔" else "✘"
                                        "in_progress" -> "⟳"
                                        else -> "○"
                                    }
                                    val conclusionText = if (step.conclusion != null) {
                                        " (${step.conclusion})"
                                    } else ""
                                    combinedLogs.append("$statusIcon ${step.number}. ${step.name} - ${step.status}$conclusionText\n")
                                }
                            }
                            combinedLogs.append("--------------------------------------------------\n")
                            combinedLogs.append("(Live build is in-progress. Full raw logs will be finalized on completion.)\n")
                        } else {
                            combinedLogs.append("Unable to copy logs for this step: ${e.message}\n")
                        }
                    }
                    combinedLogs.append("\n")
                }

                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Build Logs", combinedLogs.toString())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Build logs copied to clipboard successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to capture build logs: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearWorkflowLogs() {
        _workflowLogs.value = null
        // Ensure any active log background polling task is immediately halted on dialog dismiss
        pollingJob?.cancel()
    }

    fun triggerWorkflow(workflowId: Long) {
        viewModelScope.launch {
            try {
                if (tokenManager.isDemoMode()) {
                    _statusMessage.value = "Workflow triggered successfully!"
                } else {
                    val api = RetrofitClient.getService(tokenManager)
                    api.dispatchWorkflow(owner, repoName, workflowId, mapOf("ref" to _currentBranch.value))
                    _statusMessage.value = "Workflow dispatch signal sent to GitHub Actions!"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Failed to dispatch workflow: ${e.message}"
            }
        }
    }

    fun loadReleases() {
        if (tokenManager.isDemoMode()) return
        viewModelScope.launch {
            try {
                val api = RetrofitClient.getService(tokenManager)
                _releases.value = api.getReleases(owner, repoName)
            } catch (e: Exception) { }
        }
    }

    fun cancelZipUpload() {
        _isUploadingZip.value = false
        _statusMessage.value = "Upload cancelled"
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    fun closeActiveFile() {
        _activeFile.value = null
        _fileContent.value = ""
    }

    fun searchAndJumpToPath(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Searching deeply for '$query'..."
            try {
                if (tokenManager.isDemoMode()) {
                    val fullTree = getSampleAndroidProjectTree()
                    val matchedItem = findItemInTreeRecursively(fullTree, query)
                    if (matchedItem != null) {
                        val targetPath = if (matchedItem.type == "dir") {
                            matchedItem.path
                        } else {
                            if (matchedItem.path.contains("/")) {
                                matchedItem.path.substringBeforeLast("/")
                            } else {
                                ""
                            }
                        }
                        AppLogger.s("SearchExplorer", "Found match: '${matchedItem.path}'. Navigating to '$targetPath'")
                        navigateToDirectory(targetPath)
                    } else {
                        _statusMessage.value = "No match found for '$query'"
                    }
                } else {
                    val api = RetrofitClient.getService(tokenManager)
                    val branchRef = _currentBranch.value
                    AppLogger.i("GitHubAPI", "Fetching recursive tree for branch: $branchRef")
                    val response = api.getRecursiveTree(owner, repoName, branchRef)
                    
                    val matchedEntry = response.tree.find { entry ->
                        val name = entry.path.substringAfterLast('/')
                        name.contains(query, ignoreCase = true)
                    }
                    
                    if (matchedEntry != null) {
                        val targetPath = if (matchedEntry.type == "tree") {
                            matchedEntry.path
                        } else {
                            if (matchedEntry.path.contains("/")) {
                                matchedEntry.path.substringBeforeLast("/")
                            } else {
                                ""
                            }
                        }
                        AppLogger.s("SearchExplorer", "Deep search found: '${matchedEntry.path}'. Navigating to '$targetPath'")
                        navigateToDirectory(targetPath)
                    } else {
                        _statusMessage.value = "No match found for '$query'"
                        AppLogger.i("SearchExplorer", "No deep match found for query: '$query'")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("SearchExplorer", "Deep search failed: ${e.message}", e)
                _statusMessage.value = "Search failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun findItemInTreeRecursively(items: List<FileItem>, query: String): FileItem? {
        for (item in items) {
            if (item.name.contains(query, ignoreCase = true)) {
                return item
            }
            if (item.children.isNotEmpty()) {
                val found = findItemInTreeRecursively(item.children, query)
                if (found != null) return found
            }
        }
        return null
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}

// Data structures representing GitHub Actions Job response structures safely
data class WorkflowRunJobsResponse(val jobs: List<WorkflowJob>?)

data class WorkflowJob(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val steps: List<WorkflowStep>? = emptyList() // Added steps tracking to parse runner step details
)

data class WorkflowStep(
    val name: String,
    val status: String, // "queued", "in_progress", "completed"
    val conclusion: String?, // "success", "failure", "cancelled"
    val number: Int
)

// Builds a nested FileItem hierarchy from scanned extracted ZIP files
fun buildFileTreeFromScannedFiles(scannedFiles: List<ZipUtils.ExtractedFileInfo>): List<FileItem> {
    if (scannedFiles.isEmpty()) return emptyList()

    // Determine if all paths share a single top-level directory prefix (e.g., "ProjectName-main/")
    val firstSegments = scannedFiles.mapNotNull {
        val parts = it.relativePath.split('/')
        if (parts.size > 1) parts[0] else null
    }
    val commonRoot = if (firstSegments.isNotEmpty() && firstSegments.distinct().size == 1 && scannedFiles.all { it.relativePath.contains('/') }) {
        firstSegments.first()
    } else null

    val rootList = mutableListOf<FileItem>()

    fun getOrCreateDir(parentList: MutableList<FileItem>, dirName: String, fullPath: String): FileItem {
        var dir = parentList.find { it.name == dirName && it.type == "dir" }
        if (dir == null) {
            dir = FileItem(
                name = dirName,
                path = fullPath,
                type = "dir",
                children = mutableListOf()
            )
            parentList.add(dir)
        }
        return dir
    }

    for (file in scannedFiles) {
        val cleanPath = if (commonRoot != null && file.relativePath.startsWith("$commonRoot/")) {
            file.relativePath.removePrefix("$commonRoot/")
        } else {
            file.relativePath
        }

        val parts = cleanPath.split('/')
        var currentParentList = rootList
        var currentPathAcc = ""

        for (i in 0 until parts.size - 1) {
            val part = parts[i]
            currentPathAcc = if (currentPathAcc.isEmpty()) part else "$currentPathAcc/$part"
            val dirNode = getOrCreateDir(currentParentList, part, currentPathAcc)
            currentParentList = dirNode.children
        }

        // Add file node
        val fileName = parts.last()
        val filePath = cleanPath
        val decodedContent = try {
            val bytes = Base64.decode(file.contentBase64, Base64.DEFAULT)
            String(bytes)
        } catch (e: Exception) {
            "// Binary content: ${file.relativePath}"
        }

        val fileNode = FileItem(
            name = fileName,
            path = filePath,
            type = "file",
            size = decodedContent.length.toLong(),
            content = decodedContent
        )
        currentParentList.add(fileNode)
    }

    return rootList
}

// Sample Tree Generator for Android Project Structure in Explorer View
fun getSampleAndroidProjectTree(): List<FileItem> {
    return listOf(
        FileItem(
            name = ".github",
            path = ".github",
            type = "dir",
            children = mutableListOf(
                FileItem(name = "workflows", path = ".github/workflows", type = "dir", children = mutableListOf(
                    FileItem(name = "android.yml", path = ".github/workflows/android.yml", type = "file", content = "name: Android CI\non: [push]\njobs:\n  build:\n    runs-on: ubuntu-latest\n    steps:\n    - uses: actions/checkout@v3\n    - name: set up JDK\n      uses: actions/setup-java@v3\n    - name: Build with Gradle\n      run: ./gradlew build")
                ))
            )
        ),
        FileItem(
            name = "app",
            path = "app",
            type = "dir",
            children = mutableListOf(
                FileItem(
                    name = "src",
                    path = "app/src",
                    type = "dir",
                    children = mutableListOf(
                        FileItem(
                            name = "main",
                            path = "app/src/main",
                            type = "dir",
                            children = mutableListOf(
                                FileItem(name = "AndroidManifest.xml", path = "app/src/main/AndroidManifest.xml", type = "file", content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n    <application\n        android:allowBackup=\"true\"\n        android:label=\"@string/app_name\"\n        android:supportsRtl=\"true\">\n        <activity android:name=\".MainActivity\" android:exported=\"true\"/>\n    </application>\n</manifest>"),
                                FileItem(
                                    name = "java",
                                    path = "app/src/main/java",
                                    type = "dir",
                                    children = mutableListOf(
                                        FileItem(name = "MainActivity.kt", path = "app/src/main/java/MainActivity.kt", type = "file", content = "package com.vineyard.fastgit.app\n\nimport android.os.Bundle\nimport androidx.activity.ComponentActivity\n\nclass MainActivity : ComponentActivity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        println(\"Welcome to FastGit\")\n    }\n}")
                                    )
                                ),
                                FileItem(name = "res", path = "app/src/main/res", type = "dir", children = mutableListOf(
                                    FileItem(name = "values", path = "app/src/main/res/values", type = "dir", children = mutableListOf(
                                        FileItem(name = "strings.xml", path = "app/src/main/res/values/strings.xml", type = "file", content = "<resources>\n    <string name=\"app_name\">FastGit</string>\n</resources>")
                                    ))
                                ))
                            )
                        )
                    )
                ),
                FileItem(name = "build.gradle.kts", path = "app/build.gradle.kts", type = "file", content = "plugins {\n    alias(libs.plugins.android.application)\n    alias(libs.plugins.kotlin.compose)\n}\n\nandroid {\n    namespace = \"com.vineyard.fastgit.app\"\n    compileSdk = 36\n}")
            )
        ),
        FileItem(name = "build.gradle.kts", path = "build.gradle.kts", type = "file", content = "// Top-level build file\nplugins {\n    alias(libs.plugins.android.application) apply false\n}"),
        FileItem(name = "settings.gradle.kts", path = "settings.gradle.kts", type = "file", content = "rootProject.name = \"FastGit\"\ninclude(\":app\")"),
        FileItem(name = "README.md", path = "README.md", type = "file", content = "# FastGit Android Client\n\nA modern GitHub repository manager for Android developers.")
    )
}

fun getSamplePullRequests(): List<PullRequest> {
    return listOf(
        PullRequest(id = 1, number = 4, title = "Refactor file explorer tree nodes for fast collapse", state = "open", user = User(login = "developer_android"), createdAt = "2 days ago"),
        PullRequest(id = 2, number = 3, title = "Add OAuth Token auto-refresh support", state = "closed", user = User(login = "octocat"), createdAt = "1 week ago", merged = true)
    )
}

fun getSampleIssues(): List<Issue> {
    return listOf(
        Issue(id = 1, number = 12, title = "Support syntax highlighting for Gradle KTS files", state = "open", user = User(login = "developer_android"), createdAt = "3 hours ago"),
        Issue(id = 2, number = 9, title = "ZIP upload percentage counter animation smooth scroll", state = "open", user = User(login = "octocat"), createdAt = "Yesterday")
    )
}