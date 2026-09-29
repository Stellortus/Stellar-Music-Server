package top.stellortus.stellar_music_server.auth

import top.stellortus.stellar_music_server.database.auth.User
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val username: String, val password: String)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class UserResponse(val id: Int, val username: String)

@Serializable
data class AuthResponse(val token: String, val user: UserResponse)

@Serializable
data class MessageResponse(val message: String)

fun User.toResponse(): UserResponse = UserResponse(id = id, username = username)
