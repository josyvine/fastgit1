package com.vineyard.fastgit.app.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vineyard.fastgit.app.database.AppDatabase
import com.vineyard.fastgit.app.database.CacheEntity
import com.vineyard.fastgit.app.models.*
import com.vineyard.fastgit.app.network.GitHubApiService
import com.vineyard.fastgit.app.network.RetrofitClient
import com.vineyard.fastgit.app.utils.DownloadUtils
import com.vineyard.fastgit.app.utils.TokenManager
import com.vineyard.fastgit.app.utils.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    val tokenManager = TokenManager(application)
    private val _isLoggedIn = MutableStateFlow(tokenManager.isLoggedIn() || tokenManager.isDemoMode())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _deviceCodeState = MutableStateFlow<com.vineyard.fastgit.app.models.DeviceCodeResponse?>(null)
    val deviceCodeState: StateFlow<com.vineyard.fastgit.app.models.DeviceCodeResponse?> = _deviceCodeState

    private val _isDeviceFlowLoading = MutableStateFlow(false)
    val isDeviceFlowLoading: StateFlow<Boolean> = _isDeviceFlowLoading

    init {
        if (tokenManager.isLoggedIn() || tokenManager.isDemoMode()) {
            loadCurrentUser()
        }
    }

    fun getOAuthAuthorizeUrl(): String {
        val clientId = tokenManager.getOAuthClientId()
        val redirectUri = Uri.encode(TokenManager.OAUTH_REDIRECT_URI)
        val scope = Uri.encode("repo workflow user read:org notifications gist delete_repo")
        return "https://github.com/login/oauth/authorize?client_id=$clientId&redirect_uri=$redirectUri&scope=$scope"
    }

    fun handleOAuthCode(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                com.vineyard.fastgit.app.utils.AppLogger.i("OAuth", "Exchanging OAuth authorization code with GitHub...")
                val response = RetrofitClient.getOAuthService().exchangeCodeForToken(
                    clientId = tokenManager.getOAuthClientId(),
                    clientSecret = tokenManager.getOAuthClientSecret(),
                    code = code,
                    redirectUri = TokenManager.OAUTH_REDIRECT_URI
                )

                val token = response.accessToken
                if (!token.isNullOrBlank()) {
                    tokenManager.saveToken(token)
                    tokenManager.setDemoMode(false)
                    com.vineyard.fastgit.app.utils.AppLogger.s("OAuth", "OAuth Token exchanged successfully! Validating user profile...")
                    val api = RetrofitClient.getService(tokenManager)
                    val u = api.getCurrentUser()
                    _user.value = u
                    _isLoggedIn.value = true
                } else {
                    val err = response.errorDescription ?: response.error ?: "Unable to exchange code for GitHub OAuth access token."
                    _errorMessage.value = "OAuth Error: $err"
                    com.vineyard.fastgit.app.utils.AppLogger.e("OAuth", "OAuth error response: $err")
                }
            } catch (e: Exception) {
                _errorMessage.value = "OAuth Authentication failed: ${e.message}"
                com.vineyard.fastgit.app.utils.AppLogger.e("OAuth", "OAuth exception: ${e.message}", e)
                tokenManager.clearToken()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setOAuthError(error: String) {
        _errorMessage.value = error
    }

    fun startDeviceFlow() {
        viewModelScope.launch {
            _isDeviceFlowLoading.value = true
            _errorMessage.value = null
            try {
                com.vineyard.fastgit.app.utils.AppLogger.i("DeviceFlow", "Requesting device code from GitHub...")
                val response = RetrofitClient.getOAuthService().requestDeviceCode(
                    clientId = tokenManager.getOAuthClientId(),
                    scope = "repo workflow user read:org notifications gist delete_repo"
                )
                if (!response.deviceCode.isNullOrBlank() && !response.userCode.isNullOrBlank()) {
                    _deviceCodeState.value = response
                    com.vineyard.fastgit.app.utils.AppLogger.s("DeviceFlow", "Device Code received: ${response.userCode}")
                    pollDeviceToken(response)
                } else {
                    val err = response.errorDescription ?: response.error ?: "Failed to obtain device code from GitHub"
                    _errorMessage.value = "Device Flow Error: $err"
                }
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 404) {
                    _errorMessage.value = "Device Flow Error: HTTP 404 (GitHub Client ID '${tokenManager.getOAuthClientId()}' not found or Device Flow is disabled in GitHub OAuth App settings. Tap 'Configure OAuth App ID' below to enter your GitHub Client ID)."
                } else {
                    _errorMessage.value = "Device Flow Error: HTTP ${e.code()} ${e.message()}"
                }
                com.vineyard.fastgit.app.utils.AppLogger.e("DeviceFlow", "HTTP Exception requesting device code: ${e.code()} ${e.message()}", e)
            } catch (e: Exception) {
                _errorMessage.value = "Device Flow Error: ${e.message}"
                com.vineyard.fastgit.app.utils.AppLogger.e("DeviceFlow", "Exception requesting device code: ${e.message}", e)
            } finally {
                _isDeviceFlowLoading.value = false
            }
        }
    }

    private fun pollDeviceToken(deviceResponse: com.vineyard.fastgit.app.models.DeviceCodeResponse) {
        viewModelScope.launch {
            val deviceCode = deviceResponse.deviceCode ?: return@launch
            val clientId = tokenManager.getOAuthClientId()
            var pollInterval = ((deviceResponse.interval ?: 5).coerceAtLeast(5)) * 1000L

            while (_deviceCodeState.value != null && _deviceCodeState.value?.deviceCode == deviceCode) {
                delay(pollInterval)
                if (_deviceCodeState.value?.deviceCode != deviceCode) break

                try {
                    val tokenResponse = RetrofitClient.getOAuthService().pollDeviceToken(
                        clientId = clientId,
                        deviceCode = deviceCode
                    )

                    val token = tokenResponse.accessToken
                    if (!token.isNullOrBlank()) {
                        com.vineyard.fastgit.app.utils.AppLogger.s("DeviceFlow", "Device Flow Token approved! Loading user profile...")
                        tokenManager.saveToken(token)
                        tokenManager.setDemoMode(false)
                        val api = RetrofitClient.getService(tokenManager)
                        val u = api.getCurrentUser()
                        _user.value = u
                        _isLoggedIn.value = true
                        _deviceCodeState.value = null
                        break
                    } else {
                        when (tokenResponse.error) {
                            "authorization_pending" -> {
                                // Keep polling
                            }
                            "slow_down" -> {
                                pollInterval += 5000L
                            }
                            "expired_token" -> {
                                _errorMessage.value = "Device code expired. Please try again."
                                _deviceCodeState.value = null
                                break
                            }
                            "access_denied" -> {
                                _errorMessage.value = "Access denied by user on GitHub."
                                _deviceCodeState.value = null
                                break
                            }
                            else -> {
                                val err = tokenResponse.errorDescription ?: tokenResponse.error ?: "Device Flow authorization failed"
                                _errorMessage.value = "Device Flow Error: $err"
                                _deviceCodeState.value = null
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    com.vineyard.fastgit.app.utils.AppLogger.e("DeviceFlow", "Polling exception: ${e.message}", e)
                }
            }
        }
    }

    fun cancelDeviceFlow() {
        _deviceCodeState.value = null
    }

    fun enableDemoMode() {
        tokenManager.setDemoMode(true)
        tokenManager.clearToken()
        _isLoggedIn.value = true
        _user.value = User(
            id = 101,
            login = "developer_android",
            name = "FastGit Mobile Developer",
            avatarUrl = "https://github.com/identicons/developer_android.png",
            bio = "Building high-performance Android apps with Kotlin & Compose",
            publicRepos = 12,
            followers = 142,
            following = 38
        )
    }

    fun loadCurrentUser() {
        if (tokenManager.isDemoMode()) {
            _user.value = User(
                id = 101,
                login = "developer_android",
                name = "FastGit Mobile Developer",
                avatarUrl = "https://github.com/identicons/developer_android.png",
                bio = "Building high-performance Android apps with Kotlin & Compose",
                publicRepos = 12,
                followers = 142,
                following = 38
            )
            return
        }

        viewModelScope.launch {
            try {
                val api = RetrofitClient.getService(tokenManager)
                _user.value = api.getCurrentUser()
            } catch (e: Exception) {
                // Ignore or fallback
            }
        }
    }

    fun logout() {
        tokenManager.clearToken()
        tokenManager.setDemoMode(false)
        _user.value = null
        _isLoggedIn.value = false
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val _recentRepos = MutableStateFlow<List<Repository>>(emptyList())
    val recentRepos: StateFlow<List<Repository>> = _recentRepos

    private val _recentCommits = MutableStateFlow<List<Commit>>(emptyList())
    val recentCommits: StateFlow<List<Commit>> = _recentCommits

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (tokenManager.isDemoMode()) {
                    _recentRepos.value = getSampleRepositories()
                    _recentCommits.value = getSampleCommits()
                } else {
                    val api = RetrofitClient.getService(tokenManager)
                    val repos = api.getUserRepositories()
                    _recentRepos.value = repos.take(5)

                    if (repos.isNotEmpty()) {
                        val first = repos.first()
                        val owner = first.owner?.login ?: ""
                        val commits = api.getCommits(owner, first.name)
                        _recentCommits.value = commits.take(5)
                    }
                }
            } catch (e: Exception) {
                _recentRepos.value = getSampleRepositories()
                _recentCommits.value = getSampleCommits()
            } finally {
                _isLoading.value = false
            }
        }
    }
}

class RepositoryViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val _repositories = MutableStateFlow<List<Repository>>(emptyList())
    val repositories: StateFlow<List<Repository>> = _repositories

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedFilter = MutableStateFlow("All") // "All", "Public", "Private", "Sources", "Forks"
    val selectedFilter: StateFlow<String> = _selectedFilter

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    init {
        fetchRepositories()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onFilterSelect(filter: String) {
        _selectedFilter.value = filter
    }

    fun fetchRepositories() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (tokenManager.isDemoMode()) {
                    _repositories.value = getSampleRepositories()
                } else {
                    val api = RetrofitClient.getService(tokenManager)
                    _repositories.value = api.getUserRepositories()
                }
            } catch (e: Exception) {
                _repositories.value = getSampleRepositories()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createRepository(
        name: String,
        description: String,
        isPrivate: Boolean,
        initReadme: Boolean,
        gitignore: String?,
        license: String?,
        onSuccess: (Repository) -> Unit
    ) {
        if (name.isBlank()) {
            _statusMessage.value = "Repository name is required"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (tokenManager.isDemoMode()) {
                    val newRepo = Repository(
                        id = System.currentTimeMillis(),
                        name = name,
                        fullName = "developer_android/$name",
                        owner = User(login = "developer_android"),
                        description = description,
                        private = isPrivate,
                        defaultBranch = "main",
                        stargazersCount = 0,
                        forksCount = 0,
                        language = "Kotlin",
                        updatedAt = "Just now"
                    )
                    _repositories.value = listOf(newRepo) + _repositories.value
                    _statusMessage.value = "Repository '$name' created successfully!"
                    onSuccess(newRepo)
                } else {
                    val api = RetrofitClient.getService(tokenManager)
                    val req = CreateRepoRequest(
                        name = name,
                        description = description,
                        private = isPrivate,
                        autoInit = initReadme,
                        gitignoreTemplate = gitignore.takeIf { !it.isNullOrBlank() },
                        licenseTemplate = license.takeIf { !it.isNullOrBlank() }
                    )
                    val newRepo = api.createRepository(req)
                    fetchRepositories()
                    _statusMessage.value = "Repository '${newRepo.name}' created!"
                    onSuccess(newRepo)
                }
            } catch (e: Exception) {
                _statusMessage.value = "Failed to create repository: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteRepository(owner: String, name: String, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (tokenManager.isDemoMode()) {
                    _repositories.value = _repositories.value.filterNot { it.name == name && it.owner?.login == owner }
                    _statusMessage.value = "Repository '$name' deleted successfully (Demo Mode)!"
                    onSuccess?.invoke()
                } else {
                    val api = RetrofitClient.getService(tokenManager)
                    val response = api.deleteRepository(owner, name)
                    if (response.isSuccessful) {
                        // Safely filter internal list immediately for snappy state refresh
                        _repositories.value = _repositories.value.filterNot { it.name == name && it.owner?.login == owner }
                        _statusMessage.value = "Repository '$name' deleted successfully!"
                        fetchRepositories()
                        onSuccess?.invoke()
                    } else {
                        val errorDetail = response.errorBody()?.string() ?: response.message()
                        _statusMessage.value = "Failed to delete: $errorDetail"
                        com.vineyard.fastgit.app.utils.AppLogger.e("RepositoryViewModel", "Delete failed: $errorDetail")
                    }
                }
            } catch (e: Exception) {
                _statusMessage.value = "Failed to delete: ${e.message}"
                com.vineyard.fastgit.app.utils.AppLogger.e("RepositoryViewModel", "Delete caught exception", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun downloadRepositoryAsZip(owner: String, repoName: String, branch: String, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _isLoading.value = true
                _statusMessage.value = "Downloading repository ZIP..."
            }

            try {
                val targetBranch = branch.ifBlank { "main" }
                val targetFileName = "$repoName-$targetBranch.zip"

                if (tokenManager.isDemoMode()) {
                    val mockItems = listOf(
                        FileItem(name = "README.md", type = "file", content = "# Demo Project\nLocal bundle saved offline.")
                    )
                    val zip = DownloadUtils.createZipFromFolderItems(context, repoName, mockItems)
                    val bytes = zip.readBytes()
                    val savedFile = DownloadUtils.saveBinaryToDownloads(context, "", targetFileName, bytes)
                    zip.delete()

                    withContext(Dispatchers.Main) {
                        if (savedFile != null) {
                            _statusMessage.value = "Saved successfully: Downloads/FastGit/$targetFileName"
                        } else {
                            _statusMessage.value = "Download failed: Local storage write error"
                        }
                    }
                    return@launch
                }

                val api = RetrofitClient.getService(tokenManager)
                com.vineyard.fastgit.app.utils.AppLogger.i("RepositoryViewModel", "Downloading zipball for $owner/$repoName ($targetBranch)")
                val response = api.downloadZipball(owner, repoName, targetBranch)

                if (response.isSuccessful && response.body() != null) {
                    val bytes = response.body()!!.bytes()
                    val savedFile = DownloadUtils.saveBinaryToDownloads(context, "", targetFileName, bytes)

                    withContext(Dispatchers.Main) {
                        if (savedFile != null) {
                            _statusMessage.value = "Saved successfully: Downloads/FastGit/$targetFileName"
                            com.vineyard.fastgit.app.utils.AppLogger.s("RepositoryViewModel", "Saved ZIP file $targetFileName successfully.")
                        } else {
                            _statusMessage.value = "Download failed: MediaStore write error"
                        }
                    }
                } else {
                    val errMsg = response.errorBody()?.string() ?: response.message()
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Download failed: $errMsg"
                    }
                    com.vineyard.fastgit.app.utils.AppLogger.e("RepositoryViewModel", "Download failed with error: $errMsg")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Download failed: ${e.message}"
                }
                com.vineyard.fastgit.app.utils.AppLogger.e("RepositoryViewModel", "Download exception caught", e)
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    fun importRepositoryUrl(url: String, onSuccess: (Repository) -> Unit) {
        if (url.isBlank()) {
            _statusMessage.value = "Please enter a valid GitHub URL"
            return
        }
        val cleanUrl = url.trim().removeSuffix("/").removeSuffix(".git")
        val parts = cleanUrl.split("/")
        if (parts.size < 2) {
            _statusMessage.value = "Invalid GitHub URL format. Use https://github.com/owner/repo"
            return
        }
        val owner = parts[parts.size - 2]
        val repoName = parts[parts.size - 1]

        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (tokenManager.isDemoMode()) {
                    val imported = Repository(
                        id = System.currentTimeMillis(),
                        name = repoName,
                        fullName = "$owner/$repoName",
                        owner = User(login = owner),
                        description = "Imported repository from $url",
                        private = false,
                        defaultBranch = "main",
                        stargazersCount = 128,
                        language = "Java",
                        updatedAt = "Today"
                    )
                    _repositories.value = listOf(imported) + _repositories.value
                    _statusMessage.value = "Repository imported successfully!"
                    onSuccess(imported)
                } else {
                    val api = RetrofitClient.getService(tokenManager)
                    val repo = api.getRepository(owner, repoName)
                    _repositories.value = listOf(repo) + _repositories.value
                    _statusMessage.value = "Imported '${repo.fullName}'"
                    onSuccess(repo)
                }
            } catch (e: Exception) {
                _statusMessage.value = "Could not locate repository: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }
}

class NotificationViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadNotifications()
    }

    fun loadNotifications() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (tokenManager.isDemoMode()) {
                    _notifications.value = getSampleNotifications()
                } else {
                    val api = RetrofitClient.getService(tokenManager)
                    _notifications.value = api.getNotifications()
                }
            } catch (e: Exception) {
                _notifications.value = getSampleNotifications()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun markAsRead(id: String) {
        _notifications.value = _notifications.value.map {
            if (it.id == id) it.copy(unread = false) else it
        }
        if (!tokenManager.isDemoMode()) {
            viewModelScope.launch {
                try {
                    val api = RetrofitClient.getService(tokenManager)
                    api.markNotificationAsRead(id)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }
}

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _pinnedRepos = MutableStateFlow<List<Repository>>(emptyList())
    val pinnedRepos: StateFlow<List<Repository>> = _pinnedRepos

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (tokenManager.isDemoMode()) {
                    _user.value = User(
                        id = 101,
                        login = "developer_android",
                        name = "FastGit Mobile Developer",
                        avatarUrl = "https://github.com/identicons/developer_android.png",
                        bio = "Building high-performance Android applications with Kotlin, Coroutines, and Jetpack Compose",
                        company = "Vineyard Apps",
                        location = "San Francisco, CA",
                        publicRepos = 14,
                        followers = 320,
                        following = 45
                    )
                    _pinnedRepos.value = getSampleRepositories()
                } else {
                    val api = RetrofitClient.getService(tokenManager)
                    val u = api.getCurrentUser()
                    _user.value = u
                    _pinnedRepos.value = api.getUserRepositories().take(4)
                }
            } catch (e: Exception) {
                _user.value = User(
                    login = "developer_android",
                    name = "FastGit Developer",
                    bio = "Android GitHub Workspace"
                )
                _pinnedRepos.value = getSampleRepositories()
            } finally {
                _isLoading.value = false
            }
        }
    }
}

// Global Sample Helpers
fun getSampleRepositories(): List<Repository> {
    return listOf(
        Repository(
            id = 1,
            name = "FastGit-Android",
            fullName = "developer_android/FastGit-Android",
            owner = User(login = "developer_android"),
            description = "Complete Android GitHub Client with modern file management & ZIP upload capabilities",
            private = false,
            defaultBranch = "main",
            stargazersCount = 342,
            forksCount = 89,
            language = "Kotlin",
            updatedAt = "2 hours ago"
        ),
        Repository(
            id = 2,
            name = "android-mvvm-compose-starter",
            fullName = "developer_android/android-mvvm-compose-starter",
            owner = User(login = "developer_android"),
            description = "Clean Architecture template with Room, Retrofit, Coroutines, and Jetpack Compose",
            private = false,
            defaultBranch = "main",
            stargazersCount = 156,
            forksCount = 42,
            language = "Kotlin",
            updatedAt = "Yesterday"
        ),
        Repository(
            id = 3,
            name = "fast-zip-extractor",
            fullName = "developer_android/fast-zip-extractor",
            owner = User(login = "developer_android"),
            description = "High speed native directory scanner & zip streaming utility for Android",
            private = true,
            defaultBranch = "main",
            stargazersCount = 45,
            forksCount = 12,
            language = "Java",
            updatedAt = "3 days ago"
        ),
        Repository(
            id = 4,
            name = "github-actions-android-ci",
            fullName = "developer_android/github-actions-android-ci",
            owner = User(login = "developer_android"),
            description = "Reusable GitHub Actions workflows for Android build, linting, and Robolectric testing",
            private = false,
            defaultBranch = "main",
            stargazersCount = 88,
            forksCount = 19,
            language = "YAML",
            updatedAt = "1 week ago"
        )
    )
}

fun getSampleCommits(): List<Commit> {
    return listOf(
        Commit(
            sha = "7f8b92a",
            commit = CommitDetail(
                message = "Add ZIP upload progress dialog and directory tree preservation",
                author = CommitUser(name = "FastGit Dev", date = "10 mins ago")
            )
        ),
        Commit(
            sha = "3e2a10c",
            commit = CommitDetail(
                message = "Refactor Room caching layer and Retrofit API interceptors",
                author = CommitUser(name = "FastGit Dev", date = "2 hours ago")
            )
        ),
        Commit(
            sha = "9c01d4f",
            commit = CommitDetail(
                message = "Implement syntax highlighting for Kotlin, Java, and XML files",
                author = CommitUser(name = "FastGit Dev", date = "Yesterday")
            )
        )
    )
}

fun getSampleNotifications(): List<Notification> {
    return listOf(
        Notification(
            id = "1",
            repository = Repository(name = "FastGit-Android", fullName = "developer_android/FastGit-Android"),
            subject = NotificationSubject(title = "Pull Request #14: Optimize ZIP extraction speed", type = "PullRequest"),
            reason = "author",
            unread = true,
            updatedAt = "15 mins ago"
        ),
        Notification(
            id = "2",
            repository = Repository(name = "android-mvvm-compose-starter", fullName = "developer_android/android-mvvm-compose-starter"),
            subject = NotificationSubject(title = "Issue #8: Add Room DB flow unit tests", type = "Issue"),
            reason = "assignee",
            unread = true,
            updatedAt = "1 hour ago"
        ),
        Notification(
            id = "3",
            repository = Repository(name = "github-actions-android-ci", fullName = "developer_android/github-actions-android-ci"),
            subject = NotificationSubject(title = "Workflow Run #102 succeeded on main", type = "Workflow"),
            reason = "subscribed",
            unread = false,
            updatedAt = "Yesterday"
        )
    )
}