package com.argon.hub.model

import org.json.JSONObject

data class User(
    val id: Int,
    val username: String,
    val email: String,
    val role: String,
    val displayName: String? = null
) {
    val isAdmin: Boolean
        get() = role.equals("admin", ignoreCase = true)

    val displayTitle: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: username

    fun toJson(): String {
        return JSONObject().apply {
            put("id", id)
            put("username", username)
            put("email", email)
            put("role", role)
            put("displayName", displayName ?: "")
        }.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): User? {
            return try {
                val obj = JSONObject(jsonStr)
                User(
                    id = obj.getInt("id"),
                    username = obj.getString("username"),
                    email = obj.getString("email"),
                    role = obj.optString("role", "user"),
                    displayName = if (obj.has("displayName") && !obj.isNull("displayName")) obj.getString("displayName") else null
                )
            } catch (_: Exception) {
                null
            }
        }

        val Guest = User(
            id = 0,
            username = "guest",
            email = "guest@argon.local",
            role = "user",
            displayName = "Guest User"
        )
    }
}
