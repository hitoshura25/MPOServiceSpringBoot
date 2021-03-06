package com.vviswana.mpo

import com.okta.spring.boot.oauth.Okta
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
        http.authorizeRequests()
            // allow anonymous access to certain paths
            .antMatchers("/podcasts/**").permitAll()
            .antMatchers("/podcastdetails/**").permitAll()
            .antMatchers("/podcastupdate/**").permitAll()
            .antMatchers("/register_user/**").permitAll()

            // all other requests
            .anyRequest().authenticated()
            .and()
            .oauth2ResourceServer().jwt()// replace .jwt() with .opaqueToken() for Opaque Token case

        // Send a 401 message to the browser (w/o this, you'll see a blank page)
        Okta.configureResourceServer401ResponseBody(http)
        http.requiresChannel()
            .requestMatchers(RequestMatcher { r: HttpServletRequest ->
                r.getHeader("X-Forwarded-Proto") != null
            })
            .requiresSecure()
        http.csrf().ignoringAntMatchers("/register_user/**")
    }
}