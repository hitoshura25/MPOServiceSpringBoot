package com.vviswana.mpo.api

data class RegisterUserResponse(
    val userDetails: UserDetails,
    val authenticationProvider: AuthenticationProvider
)