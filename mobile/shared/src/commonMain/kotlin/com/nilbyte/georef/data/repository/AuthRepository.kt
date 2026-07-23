package com.nilbyte.georef.data.repository

import com.nilbyte.georef.domain.model.UserAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

class AuthRepository {
    private val _currentUser = MutableStateFlow<UserAccount?>(null)
    val currentUser: StateFlow<UserAccount?> = _currentUser.asStateFlow()

    suspend fun login(email: String, pass: String): Boolean {
        if (email.isNotBlank() && pass.length >= 4) {
            val user = UserAccount(
                id = "usr-" + email.hashCode().toString().take(8),
                email = email.trim(),
                name = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                token = "jwt-token-georef-" + Clock.System.now().toEpochMilliseconds(),
                createdAt = Clock.System.now().toEpochMilliseconds()
            )
            _currentUser.value = user
            return true
        }
        return false
    }

    suspend fun register(name: String, email: String, pass: String): Boolean {
        if (email.isNotBlank() && pass.length >= 4) {
            val user = UserAccount(
                id = "usr-" + email.hashCode().toString().take(8),
                email = email.trim(),
                name = name.ifBlank { email.substringBefore("@") },
                token = "jwt-token-georef-" + Clock.System.now().toEpochMilliseconds(),
                createdAt = Clock.System.now().toEpochMilliseconds()
            )
            _currentUser.value = user
            return true
        }
        return false
    }

    fun logout() {
        _currentUser.value = null
    }

    fun loginAsGuest() {
        _currentUser.value = UserAccount(
            id = "guest-local-user",
            email = "convidado@georef.local",
            name = "Operador de Campo",
            token = "guest-token",
            createdAt = Clock.System.now().toEpochMilliseconds()
        )
    }
}
