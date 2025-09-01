package vcm.vault.web

import org.springframework.http.HttpStatus
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import vcm.vault.security.JwtService
import java.time.Instant

data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val token: String, val expiresAt: Instant)

@RestController
@RequestMapping("/auth")
class AuthController(
    private val users: ReactiveUserDetailsService,
    private val pe: PasswordEncoder,
    private val jwt: JwtService
) {
    @PostMapping("/login")
    fun login(@RequestBody body: LoginRequest): Mono<LoginResponse> =
        users.findByUsername(body.username).flatMap { user ->
            if (pe.matches(body.password, user.password)) {
                val token = jwt.generateToken(user)
                val exp = Instant.now().plusSeconds(3600)
                Mono.just(LoginResponse(token, exp))
            } else {
                Mono.error(Unauthorized())
            }
        }.switchIfEmpty(Mono.error(Unauthorized()))
}

@ResponseStatus(HttpStatus.UNAUTHORIZED)
class Unauthorized : RuntimeException("Invalid credentials")