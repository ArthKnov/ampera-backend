package com.ampera.infrastructure.security

import com.ampera.infrastructure.persistence.UsuarioRepository
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.stereotype.Component
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.OncePerRequestFilter
import java.util.Date
import javax.crypto.SecretKey

class UsuarioPrincipal(
    val id: Long,
    private val email: String,
    private val senhaHash: String,
) : UserDetails {
    override fun getAuthorities() = listOf(SimpleGrantedAuthority("ROLE_USER"))
    override fun getPassword() = senhaHash
    override fun getUsername() = email
}

@Component
class JwtService(
    @Value("\${ampera.jwt.secret}") secret: String,
    @Value("\${ampera.jwt.validade-ms}") private val validadeMs: Long,
) {
    private val chave: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    fun gerar(id: Long, email: String): String {
        val agora = Date()
        return Jwts.builder()
            .subject(email)
            .claim("uid", id)
            .issuedAt(agora)
            .expiration(Date(agora.time + validadeMs))
            .signWith(chave)
            .compact()
    }

    fun email(token: String): String? = try {
        Jwts.parser().verifyWith(chave).build().parseSignedClaims(token).payload.subject
    } catch (erro: JwtException) {
        log.warn("JWT recusado: {}", erro.message)
        null
    } catch (erro: IllegalArgumentException) {
        null
    }

    private companion object {
        val log = LoggerFactory.getLogger(JwtService::class.java)
    }
}

@Component
class UsuarioDetailsService(private val usuarios: UsuarioRepository) : UserDetailsService {
    override fun loadUserByUsername(email: String): UserDetails {
        val usuario = usuarios.findByEmail(email) ?: throw UsernameNotFoundException(email)
        return UsuarioPrincipal(usuario.id, usuario.email, usuario.senhaHash)
    }
}

@Component
class JwtFilter(
    private val jwt: JwtService,
    private val usuarios: UsuarioDetailsService,
    private val contextos: SecurityContextRepository,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header != null && header.startsWith("Bearer ")) {
            val email = jwt.email(header.removePrefix("Bearer ").trim())
            if (email != null && SecurityContextHolder.getContext().authentication == null) {
                val principal = runCatching { usuarios.loadUserByUsername(email) }.getOrNull()
                if (principal != null) {
                    val contexto = SecurityContextHolder.createEmptyContext()
                    contexto.authentication = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
                    SecurityContextHolder.setContext(contexto)
                    contextos.saveContext(contexto, request, response)
                }
            }
        }
        filterChain.doFilter(request, response)
    }
}

@Configuration
@EnableMethodSecurity
class SecurityConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager

    @Bean
    fun securityContextRepository(): SecurityContextRepository = RequestAttributeSecurityContextRepository()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val cors = CorsConfiguration()
        cors.allowedOriginPatterns = listOf(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://192.168.*:*",
            "http://10.*:*",
        )
        cors.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        cors.allowedHeaders = listOf("*")
        cors.allowCredentials = true
        val fonte = UrlBasedCorsConfigurationSource()
        fonte.registerCorsConfiguration("/**", cors)
        return fonte
    }

    @Bean
    fun filterChain(
        http: HttpSecurity,
        filtro: JwtFilter,
        repositorio: SecurityContextRepository,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .securityContext { it.securityContextRepository(repositorio) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/api/auth/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/actuator/health",
                ).permitAll()
                it.requestMatchers("/api/**").hasRole("USER")
                it.anyRequest().authenticated()
            }
            .addFilterBefore(filtro, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
