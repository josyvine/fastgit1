package com.vineyard.fastgit.app.utils

import android.content.Context
import android.content.SharedPreferences

class TokenManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("fastgit_prefs", Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    fun isLoggedIn(): Boolean {
        val token = getToken()
        return !token.isNullOrBlank()
    }

    fun setDemoMode(isDemo: Boolean) {
        prefs.edit().putBoolean(KEY_DEMO, isDemo).apply()
    }

    fun isDemoMode(): Boolean {
        return prefs.getBoolean(KEY_DEMO, false)
    }

    fun saveOAuthCredentials(clientId: String, clientSecret: String) {
        prefs.edit()
            .putString(KEY_OAUTH_CLIENT_ID, clientId)
            .putString(KEY_OAUTH_CLIENT_SECRET, clientSecret)
            .apply()
    }

    fun getOAuthClientId(): String {
        val saved = prefs.getString(KEY_OAUTH_CLIENT_ID, null)
        return if (!saved.isNullOrBlank()) saved else DEFAULT_CLIENT_ID
    }

    fun getOAuthClientSecret(): String {
        val saved = prefs.getString(KEY_OAUTH_CLIENT_SECRET, null)
        return if (!saved.isNullOrBlank()) saved else DEFAULT_CLIENT_SECRET
    }

    companion object {
        private const val KEY_TOKEN = "github_access_token"
        private const val KEY_DEMO = "is_demo_mode"
        private const val KEY_OAUTH_CLIENT_ID = "oauth_client_id"
        private const val KEY_OAUTH_CLIENT_SECRET = "oauth_client_secret"

        const val DEFAULT_CLIENT_ID = "Ov23liaVFastGitClient"
        const val DEFAULT_CLIENT_SECRET = "fastgit_oauth_app_secret"
        const val OAUTH_REDIRECT_URI = "fastgit://oauth-callback"
    }
}
