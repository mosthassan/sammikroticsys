package com.example.data.auth

data class UserSession(
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val uid: String = "",
    val isSignedIn: Boolean = true,
    val lastLoginEpochMs: Long = System.currentTimeMillis()
)
