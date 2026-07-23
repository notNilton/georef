package com.nilbyte.georef.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserAccount(
    val id: String,
    val email: String,
    val name: String,
    val token: String,
    @SerialName("created_at")
    val createdAt: Long = 0L
)

@Serializable
data class AuthResponse(
    val success: Boolean,
    val user: UserAccount? = null,
    val token: String? = null,
    val message: String? = null
)
