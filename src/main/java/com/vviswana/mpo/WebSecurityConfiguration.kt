package com.vviswana.mpo

import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.web.util.matcher.RequestMatcher
import javax.servlet.http.HttpServletRequest

@Configuration
@EnableWebSecurity
open class WebSecurityConfiguration : WebSecurityConfigurerAdapter() {
    @Throws(Exception::class)
    override fun configure(http: HttpSecurity) {
        http.requiresChannel()
            .requestMatchers(RequestMatcher { r: HttpServletRequest ->
                r.getHeader(
                    "X-Forwarded-Proto"
                ) != null
            })
            .requiresSecure()
    }
}