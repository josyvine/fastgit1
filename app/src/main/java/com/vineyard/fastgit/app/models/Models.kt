package com.vineyard.fastgit.app.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

data class OAuthTokenResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "token_type") val tokenType: String? = null,
    @Json(name = "scope") val scope: String? = null,
    @Json(name = "error") val error: String? = null,
    @Json(name = "error_description") val errorDescription: String? = null
)

data class DeviceCodeResponse(
    @Json(name = "device_code") val deviceCode: String? = null,
    @Json(name = "user_code") val userCode: String? = null,
    @Json(name = "verification_uri") val verificationUri: String? = null,
    @Json(name = "verification_uri_complete") val verificationUriComplete: String? = null,
    @Json(name = "expires_in") val expiresIn: Int? = null,
    @Json(name = "interval") val interval: Int? = null,
    @Json(name = "error") val error: String? = null,
    @Json(name = "error_description") val errorDescription: String? = null
)

@JsonClass(generateAdapter = true)
data class User(
    val id: Long = 0,
    val login: String = "",
    val name: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String = "",
    val bio: String? = null,
    val company: String? = null,
    val location: String? = null,
    val email: String? = null,
    @Json(name = "public_repos") val publicRepos: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
    @Json(name = "html_url") val htmlUrl: String = ""
)

@JsonClass(generateAdapter = true)
data class Repository(
    val id: Long = 0,
    val name: String = "",
    @Json(name = "full_name") val fullName: String = "",
    val owner: User? = null,
    val description: String? = null,
    val private: Boolean = false,
    val fork: Boolean = false,
    @Json(name = "html_url") val htmlUrl: String = "",
    @Json(name = "default_branch") val defaultBranch: String = "main",
    @Json(name = "stargazers_count") val stargazersCount: Int = 0,
    @Json(name = "forks_count") val forksCount: Int = 0,
    @Json(name = "open_issues_count") val openIssuesCount: Int = 0,
    val size: Int = 0,
    val language: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

data class FileItem(
    val name: String = "",
    val path: String = "",
    val sha: String = "",
    val type: String = "file", // "file" or "dir"
    val size: Long = 0,
    @Json(name = "download_url") val downloadUrl: String? = null,
    val content: String? = null,
    val encoding: String? = null,
    @Transient val byteContent: ByteArray? = null,
    var isExpanded: Boolean = false,
    var level: Int = 0,
    val children: MutableList<FileItem> = mutableListOf()
)

@JsonClass(generateAdapter = true)
data class Commit(
    val sha: String = "",
    val commit: CommitDetail? = null,
    @Json(name = "html_url") val htmlUrl: String? = null,
    val author: User? = null
)

@JsonClass(generateAdapter = true)
data class CommitDetail(
    val message: String = "",
    val author: CommitUser? = null,
    val committer: CommitUser? = null
)

@JsonClass(generateAdapter = true)
data class CommitUser(
    val name: String = "",
    val email: String = "",
    val date: String = ""
)

@JsonClass(generateAdapter = true)
data class Branch(
    val name: String = "",
    val commit: CommitRef? = null,
    val protected: Boolean = false
)

@JsonClass(generateAdapter = true)
data class CommitRef(
    val sha: String = "",
    val url: String = ""
)

@JsonClass(generateAdapter = true)
data class Issue(
    val id: Long = 0,
    val number: Int = 0,
    val title: String = "",
    val body: String? = null,
    val state: String = "open", // "open" or "closed"
    val user: User? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "comments_count") val commentsCount: Int = 0,
    val assignees: List<User>? = emptyList(),
    val labels: List<Label>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class Label(
    val id: Long = 0,
    val name: String = "",
    val color: String = ""
)

@JsonClass(generateAdapter = true)
data class PullRequest(
    val id: Long = 0,
    val number: Int = 0,
    val title: String = "",
    val body: String? = null,
    val state: String = "open",
    val user: User? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    val head: PRRef? = null,
    val base: PRRef? = null,
    val merged: Boolean = false
)

@JsonClass(generateAdapter = true)
data class PRRef(
    val label: String = "",
    val ref: String = "",
    val sha: String = ""
)

@JsonClass(generateAdapter = true)
data class Workflow(
    val id: Long = 0,
    val name: String = "",
    val path: String = "",
    val state: String = "active",
    @Json(name = "html_url") val htmlUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class WorkflowRun(
    val id: Long = 0,
    val name: String? = "",
    val status: String = "", // "queued", "in_progress", "completed"
    val conclusion: String? = null, // "success", "failure", "cancelled"
    @Json(name = "head_branch") val headBranch: String? = "",
    @Json(name = "run_number") val runNumber: Int = 0,
    @Json(name = "html_url") val htmlUrl: String? = ""
)

@JsonClass(generateAdapter = true)
data class WorkflowRunListResponse(
    @Json(name = "total_count") val totalCount: Int = 0,
    @Json(name = "workflow_runs") val workflowRuns: List<WorkflowRun> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WorkflowListResponse(
    @Json(name = "total_count") val totalCount: Int = 0,
    val workflows: List<Workflow> = emptyList()
)

@JsonClass(generateAdapter = true)
data class Release(
    val id: Long = 0,
    @Json(name = "tag_name") val tagName: String = "",
    val name: String? = "",
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @Json(name = "created_at") val createdAt: String? = null,
    val assets: List<ReleaseAsset> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ReleaseAsset(
    val id: Long = 0,
    val name: String = "",
    val size: Long = 0,
    @Json(name = "download_count") val downloadCount: Int = 0,
    @Json(name = "browser_download_url") val browserDownloadUrl: String = ""
)

@JsonClass(generateAdapter = true)
data class Notification(
    val id: String = "",
    val repository: Repository? = null,
    val subject: NotificationSubject? = null,
    val reason: String = "",
    val unread: Boolean = true,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class NotificationSubject(
    val title: String = "",
    val url: String? = null,
    val type: String = "" // "PullRequest", "Issue", "Commit"
)

data class CreateRepoRequest(
    val name: String,
    val description: String? = null,
    val private: Boolean = false,
    @Json(name = "auto_init") val autoInit: Boolean = true,
    @Json(name = "gitignore_template") val gitignoreTemplate: String? = null,
    @Json(name = "license_template") val licenseTemplate: String? = null
)

data class CreateFileRequest(
    val message: String,
    val content: String, // Base64 encoded
    val sha: String? = null,
    val branch: String? = null
)

data class CreatePRRequest(
    val title: String,
    val head: String,
    val base: String,
    val body: String? = null
)

data class CreateIssueRequest(
    val title: String,
    val body: String? = null
)

data class CreateReleaseRequest(
    @Json(name = "tag_name") val tagName: String,
    val name: String,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false
)

data class RecursiveTreeResponse(
    val sha: String,
    val url: String,
    val tree: List<GitTreeEntry>,
    val truncated: Boolean
)

data class GitTreeEntry(
    val path: String,
    val mode: String,
    val type: String, // "blob" (file) or "tree" (directory/folder)
    val sha: String,
    val size: Long? = null,
    val url: String
)

data class PublicKeyResponse(
    val key_id: String,
    val key: String
)

data class CreateSecretRequest(
    val encrypted_value: String,
    val key_id: String
)