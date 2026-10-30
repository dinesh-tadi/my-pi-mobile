package com.argon.hub.data

import android.content.Context
import com.argon.hub.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed class AuthState {
    data object Initializing : AuthState()
    data object Unauthenticated : AuthState()
    data class Authenticated(val user: User, val token: String) : AuthState()
    data class Guest(val user: User = User.Guest) : AuthState()
}

class AuthManager(context: Context) {
    private val prefs = context.getSharedPreferences("argon_auth_prefs", Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        restoreSession()
    }

    private fun restoreSession() {
        val token = prefs.getString("argon_auth_token", null)
        val userJson = prefs.getString("argon_user_json", null)
        if (!token.isNullOrBlank() && !userJson.isNullOrBlank()) {
            val user = User.fromJson(userJson)
            if (user != null) {
                _authState.value = AuthState.Authenticated(user, token)
                return
            }
        }
        _authState.value = AuthState.Unauthenticated
    }

    private fun getCandidateHosts(activeHost: String): List<String> {
        val list = mutableListOf<String>()
        if (activeHost.isNotBlank()) list.add(activeHost)
        if (!list.contains("100.123.87.122")) list.add("100.123.87.122")
        if (!list.contains("192.168.1.10")) list.add("192.168.1.10")
        return list
    }

    suspend fun loginWithCredentials(activeHost: String, login: String, pass: String): Result<User> =
        withContext(Dispatchers.IO) {
            val hosts = getCandidateHosts(activeHost)
            var lastError: Exception? = null

            for (host in hosts) {
                try {
                    val url = URL("http://$host:8089/api/auth/login")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 2500
                        readTimeout = 2500
                        requestMethod = "POST"
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Accept", "application/json")
                    }

                    val body = JSONObject().apply {
                        put("login", login.trim())
                        put("password", pass)
                    }

                    conn.outputStream.use { os ->
                        os.write(body.toString().toByteArray())
                    }

                    val code = conn.responseCode
                    if (code == 200) {
                        val resp = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(resp)
                        val token = json.getString("token")
                        val userObj = json.getJSONObject("user")
                        val user = User(
                            id = userObj.getInt("id"),
                            username = userObj.getString("username"),
                            email = userObj.getString("email"),
                            role = userObj.optString("role", "user"),
                            displayName = if (userObj.has("display_name") && !userObj.isNull("display_name")) userObj.getString("display_name") else null
                        )

                        saveSession(token, user)
                        _authState.value = AuthState.Authenticated(user, token)
                        return@withContext Result.success(user)
                    } else {
                        val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                        val msg = try { JSONObject(err).optString("detail", "Login failed") } catch (_: Exception) { err }
                        return@withContext Result.failure(Exception(msg))
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }
            Result.failure(Exception(lastError?.localizedMessage ?: "Network error connecting to Auth gateway"))
        }

    suspend fun loginWithGoogle(activeHost: String, idToken: String): Result<User> =
        withContext(Dispatchers.IO) {
            val hosts = getCandidateHosts(activeHost)
            var lastError: Exception? = null

            for (host in hosts) {
                try {
                    val url = URL("http://$host:8089/api/auth/google")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 3000
                        readTimeout = 3000
                        requestMethod = "POST"
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Accept", "application/json")
                    }

                    val body = JSONObject().apply {
                        put("id_token", idToken)
                    }

                    conn.outputStream.use { os ->
                        os.write(body.toString().toByteArray())
                    }

                    val code = conn.responseCode
                    if (code == 200) {
                        val resp = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(resp)
                        val token = json.getString("token")
                        val userObj = json.getJSONObject("user")
                        val user = User(
                            id = userObj.getInt("id"),
                            username = userObj.getString("username"),
                            email = userObj.getString("email"),
                            role = userObj.optString("role", "user"),
                            displayName = if (userObj.has("display_name") && !userObj.isNull("display_name")) userObj.getString("display_name") else null
                        )

                        saveSession(token, user)
                        _authState.value = AuthState.Authenticated(user, token)
                        return@withContext Result.success(user)
                    } else {
                        val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                        val msg = try { JSONObject(err).optString("detail", "Google sign-in failed") } catch (_: Exception) { err }
                        return@withContext Result.failure(Exception(msg))
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }
            Result.failure(Exception(lastError?.localizedMessage ?: "Failed to connect to Google Auth endpoint"))
        }

    suspend fun verifySession(activeHost: String) = withContext(Dispatchers.IO) {
        val current = _authState.value
        if (current !is AuthState.Authenticated) return@withContext

        val hosts = getCandidateHosts(activeHost)
        for (host in hosts) {
            try {
                val url = URL("http://$host:8089/api/auth/me")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 1500
                    readTimeout = 1500
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer ${current.token}")
                    setRequestProperty("Accept", "application/json")
                }

                if (conn.responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(resp)
                    val userObj = json.getJSONObject("user")
                    val updatedUser = User(
                        id = userObj.getInt("id"),
                        username = userObj.getString("username"),
                        email = userObj.getString("email"),
                        role = userObj.optString("role", "user"),
                        displayName = if (userObj.has("display_name") && !userObj.isNull("display_name")) userObj.getString("display_name") else null
                    )
                    saveSession(current.token, updatedUser)
                    _authState.value = AuthState.Authenticated(updatedUser, current.token)
                    return@withContext
                } else if (conn.responseCode == 401) {
                    logout()
                    return@withContext
                }
            } catch (_: Exception) {
                // Try next host
            }
        }
    }

    fun continueAsGuest() {
        _authState.value = AuthState.Guest()
    }

    fun logout() {
        prefs.edit().clear().apply()
        _authState.value = AuthState.Unauthenticated
    }

    private fun saveSession(token: String, user: User) {
        prefs.edit()
            .putString("argon_auth_token", token)
            .putString("argon_user_json", user.toJson())
            .apply()
    }
}
