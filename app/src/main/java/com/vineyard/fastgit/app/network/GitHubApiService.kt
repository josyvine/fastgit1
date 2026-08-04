package com.vineyard.fastgit.app.network

import com.vineyard.fastgit.app.models.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface GitHubApiService {

    // User Profile
    @GET("user")
    suspend fun getCurrentUser(): User

    @GET("users/{username}")
    suspend fun getUser(@Path("username") username: String): User

    // Repositories
    @GET("user/repos")
    suspend fun getUserRepositories(
        @Query("sort") sort: String = "updated",
        @Query("direction") direction: String = "desc",
        @Query("per_page") perPage: Int = 50
    ): List<Repository>

    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Repository

    @POST("user/repos")
    suspend fun createRepository(
        @Body request: CreateRepoRequest
    ): Repository

    @DELETE("repos/{owner}/{repo}")
    suspend fun deleteRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<Unit>

    @PATCH("repos/{owner}/{repo}")
    suspend fun updateRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: Map<String, String>
    ): Repository

    // Contents & Directory Tree
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String = "",
        @Query("ref") ref: String? = null
    ): List<FileItem>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getSingleFileContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Query("ref") ref: String? = null
    ): FileItem

    @GET("repos/{owner}/{repo}/git/trees/{tree_sha}")
    suspend fun getRecursiveTree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "tree_sha", encoded = true) treeSha: String,
        @Query("recursive") recursive: Int = 1
    ): RecursiveTreeResponse

    @Streaming
    @GET("repos/{owner}/{repo}/zipball/{ref}")
    suspend fun downloadZipball(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "ref", encoded = true) ref: String = "main"
    ): Response<ResponseBody>

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun createOrUpdateFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Body request: CreateFileRequest
    ): Response<ResponseBody>

    @HTTP(method = "DELETE", path = "repos/{owner}/{repo}/contents/{path}", hasBody = true)
    suspend fun deleteFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    // Branches
    @GET("repos/{owner}/{repo}/branches")
    suspend fun getBranches(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<Branch>

    @POST("repos/{owner}/{repo}/git/refs")
    suspend fun createBranch(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    @DELETE("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun deleteBranch(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "branch", encoded = true) branch: String
    ): Response<Unit>

    // Commits
    @GET("repos/{owner}/{repo}/commits")
    suspend fun getCommits(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("sha") sha: String? = null
    ): List<Commit>

    // Issues
    @GET("repos/{owner}/{repo}/issues")
    suspend fun getIssues(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "all"
    ): List<Issue>

    @POST("repos/{owner}/{repo}/issues")
    suspend fun createIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateIssueRequest
    ): Issue

    @PATCH("repos/{owner}/{repo}/issues/{issue_number}")
    suspend fun updateIssueState(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("issue_number") issueNumber: Int,
        @Body body: Map<String, String>
    ): Issue

    // Pull Requests
    @GET("repos/{owner}/{repo}/pulls")
    suspend fun getPullRequests(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "all"
    ): List<PullRequest>

    @POST("repos/{owner}/{repo}/pulls")
    suspend fun createPullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreatePRRequest
    ): PullRequest

    @PUT("repos/{owner}/{repo}/pulls/{pull_number}/merge")
    suspend fun mergePullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    // Actions & Workflows
    @GET("repos/{owner}/{repo}/actions/workflows")
    suspend fun getWorkflows(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): WorkflowListResponse

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun getWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): WorkflowRunListResponse

    @POST("repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches")
    suspend fun dispatchWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow_id") workflowId: Long,
        @Body body: Map<String, String>
    ): Response<Unit>

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/jobs")
    suspend fun getWorkflowRunJobs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): WorkflowRunJobsResponse

    @GET("repos/{owner}/{repo}/actions/jobs/{job_id}/logs")
    suspend fun getJobLogs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("job_id") jobId: Long
    ): ResponseBody

    // Repository Secrets Management API
    @GET("repos/{owner}/{repo}/actions/secrets/public-key")
    suspend fun getActionsPublicKey(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): PublicKeyResponse

    @PUT("repos/{owner}/{repo}/actions/secrets/{secret_name}")
    suspend fun createOrUpdateActionsSecret(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("secret_name") secretName: String,
        @Body request: CreateSecretRequest
    ): Response<Unit>

    // Releases
    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<Release>

    @POST("repos/{owner}/{repo}/releases")
    suspend fun createRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateReleaseRequest
    ): Release

    // Notifications
    @GET("notifications")
    suspend fun getNotifications(
        @Query("all") all: Boolean = true
    ): List<Notification>

    @PATCH("notifications/threads/{thread_id}")
    suspend fun markNotificationAsRead(
        @Path("thread_id") threadId: String
    ): Response<Unit>
}

// Data structures representing mapping schemas safely
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