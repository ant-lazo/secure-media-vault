package vcm.vault.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date

@Component
class JwtService(private val props: JwtProps) {

    private val key = Keys.hmacShaKeyFor(props.secret.toByteArray(StandardCharsets.UTF_8))

    fun generateToken(user: UserDetails): String {
        val now = Instant.now()
        val roles = user.authorities.map { it.authority }
        return Jwts.builder()
            .subject(user.username)
            .issuer(props.issuer)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(props.expirationSeconds)))
            .claim("roles", roles) // it is very important to authorize
            .signWith(key, Jwts.SIG.HS256)
            .compact()
    }

    fun parseClaims(token: String): Map<String, Any> =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
}