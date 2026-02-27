package uz.zero.mmapp

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.stereotype.Service
import org.springframework.web.filter.OncePerRequestFilter
import java.util.*
import javax.crypto.SecretKey

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtFilter: JwtFilter
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers("/api/auth/login", "/h2-console/**").permitAll()
                    .anyRequest().authenticated()
            }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}

@Service
class JwtService {
    private val secretString = "myVerySecretKeyForJwtAuthenticationWhichShouldBeLongEnough"
    private val key: SecretKey = Keys.hmacShaKeyFor(secretString.toByteArray())

    fun generateToken(username: String, role: String): String {
        return Jwts.builder()
            .subject(username)
            .claim("role", role)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // 10 hours
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}

class UserPrincipal(
    val id: Long,
    val usernameValue: String,
    val passwordValue: String,
    val authoritiesValue: Collection<SimpleGrantedAuthority>
) : org.springframework.security.core.userdetails.UserDetails {
    override fun getAuthorities() = authoritiesValue
    override fun getPassword() = passwordValue
    override fun getUsername() = usernameValue
    override fun isAccountNonExpired() = true
    override fun isAccountNonLocked() = true
    override fun isCredentialsNonExpired() = true
    override fun isEnabled() = true
}

@Service
class CustomUserDetailsService(private val userRepository: UserRepository) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserPrincipal {
        val user = userRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("User not found")
        
        if (user.deleted) {
            throw UsernameNotFoundException("User deleted")
        }
        
        if (!user.isActive) {
            throw InactiveUserException()
        }
        
        return UserPrincipal(
            user.id!!,
            user.username,
            user.password,
            listOf(SimpleGrantedAuthority(user.role.name))
        )
    }
}

@Service
class JwtFilter(
    private val jwtService: JwtService,
    private val userDetailsService: CustomUserDetailsService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val token = authHeader.substring(7)
            try {
                val claims = jwtService.validateToken(token)
                val username = claims.subject
                
                if (username != null && SecurityContextHolder.getContext().authentication == null) {
                    val userDetails = userDetailsService.loadUserByUsername(username)
                    val authToken = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                    SecurityContextHolder.getContext().authentication = authToken
                }
            } catch (e: Exception) {
                // Token invalid
            }
        }
        filterChain.doFilter(request, response)
    }
}

@Configuration
class AuditConfig {
    @Bean
    fun auditorProvider(userRepository: UserRepository): org.springframework.data.domain.AuditorAware<Long> {
        return AuditorAwareImpl(userRepository)
    }
}

class AuditorAwareImpl(private val userRepository: UserRepository) : org.springframework.data.domain.AuditorAware<Long> {
    override fun getCurrentAuditor(): Optional<Long> {
        val authentication = SecurityContextHolder.getContext().authentication
        
        if (authentication == null || !authentication.isAuthenticated || authentication.principal == "anonymousUser") {
            return Optional.empty()
        }
        
        val principal = authentication.principal
        return if (principal is UserPrincipal) {
            Optional.of(principal.id)
        } else {
            Optional.empty()
        }
    }
}
