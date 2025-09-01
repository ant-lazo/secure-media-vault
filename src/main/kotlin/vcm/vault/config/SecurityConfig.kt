package vcm.vault.config

import io.jsonwebtoken.security.Keys
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.Customizer
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.server.SecurityWebFilterChain
import reactor.core.publisher.Mono
import vcm.vault.security.JwtProps
import javax.crypto.SecretKey

@Configuration
@EnableConfigurationProperties(JwtProps::class)
class SecurityConfig(private val jwtProps: JwtProps) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun users(pe: PasswordEncoder): MapReactiveUserDetailsService =
        MapReactiveUserDetailsService(
            User.withUsername("omar")
                .password(pe.encode("omar"))
                .roles("ADMIN")
                .build()
        )

    @Bean
    fun jwtDecoder(): ReactiveJwtDecoder {
        val key: SecretKey = Keys.hmacShaKeyFor(jwtProps.secret.toByteArray(Charsets.UTF_8))
        return NimbusReactiveJwtDecoder
            .withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
    }

    @Bean
    fun jwtAuthConverter(): Converter<Jwt, Mono<AbstractAuthenticationToken>> {
        val roles = JwtGrantedAuthoritiesConverter().apply {
            setAuthoritiesClaimName("roles")
            setAuthorityPrefix("")
        }
        val delegate = JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter(roles)
        }
        return JwtToAuthConverter(delegate)
    }

    @Bean
    fun springSecurityFilterChain(
        http: ServerHttpSecurity,
        jwtAuthConverter: Converter<Jwt, Mono<AbstractAuthenticationToken>> // tu bean
    ): SecurityWebFilterChain =
        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .authorizeExchange { auth ->
                auth
                    .pathMatchers("/health", "/actuator/health", "/auth/login").permitAll()
                    .anyExchange().authenticated()
            }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .oauth2ResourceServer { rs ->
                rs.jwt { jwt ->
                    jwt.jwtDecoder(jwtDecoder())
                    jwt.jwtAuthenticationConverter(jwtAuthConverter)
                }
            }
            .build()
}