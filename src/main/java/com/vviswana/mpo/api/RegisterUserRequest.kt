package com.vviswana.mpo.api

data class RegisterUserRequest(
    val firstName: String,
    val lastName: String,
    val email: String,
    val password: String
)