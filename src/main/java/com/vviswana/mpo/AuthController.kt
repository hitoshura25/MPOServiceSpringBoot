package com.vviswana.mpo

import com.okta.sdk.authc.credentials.TokenClientCredentials
import com.okta.sdk.client.Client
import com.okta.sdk.client.Clients
import com.okta.sdk.resource.user.UserBuilder
import com.vviswana.mpo.api.AuthenticationProvider
import com.vviswana.mpo.api.RegisterUserRequest
import com.vviswana.mpo.api.RegisterUserResponse
import com.vviswana.mpo.api.UserDetails
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.core.AbstractOAuth2Token
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.request.async.DeferredResult
import java.util.concurrent.ForkJoinPool

@RestController
class AuthController {
    private val client: Client by lazy {
        Clients.builder()
            .setOrgUrl(System.getenv("OKTA_DOMAIN"))
            .setClientCredentials(TokenClientCredentials(System.getenv("OKTA_AUTH_TOKEN")))
            .build()
    }

    // 1. App UI will enter in all info including password
    // 2. Request is made to the API to create the user. We'll use Okta for this
    // 3. Now we'll need to have the app authenticate...best practice now appears to use PKCE Flow in OAuth.
    //    As this requires a redirect back to the app, only way this will really work is if authentication is done on the
    //    device. So will create a forward in this controller to the OAuth authorization server. This will allow our API
    //    to act as sort of an abstraction of how exactly identification is done. Will use the App Auth OpenId Library
    //    for Android (https://github.com/openid/AppAuth-Android), specifying the url and params as this server
    @PostMapping(value = ["/register_user"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun registerUser(@RequestBody user: RegisterUserRequest): DeferredResult<RegisterUserResponse> {
        val deferredResult = DeferredResult<RegisterUserResponse>()
        ForkJoinPool.commonPool().submit {
            val registeredUser = UserBuilder.instance()
                .setEmail(user.email)
                .setFirstName(user.firstName)
                .setLastName(user.lastName)
                .setPassword(user.password.toCharArray())
                .buildAndCreate(client)

            deferredResult.setResult(
                RegisterUserResponse(
                    userDetails = UserDetails(
                        firstName = registeredUser.profile.firstName,
                        lastName = registeredUser.profile.lastName,
                        email = registeredUser.profile.email
                    ),
                    authenticationProvider = AuthenticationProvider.OPEN_ID
                )
            )
        }

        return deferredResult
    }

    @GetMapping("/user")
    @PreAuthorize("hasAuthority('SCOPE_profile')")
    fun <A : AbstractOAuth2TokenAuthenticationToken<AbstractOAuth2Token>> getUserDetails(authentication: A): UserDetails {
        val principal = authentication.name
        val user = client.getUser(principal)
        return UserDetails(
            firstName = user.profile.firstName,
            lastName = user.profile.lastName,
            email = user.profile.email
        )
    }
}