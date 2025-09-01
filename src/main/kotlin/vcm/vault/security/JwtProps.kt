package vcm.vault.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "security.jwt")
data class JwtProps(
    val secret: String,
    val issuer: String,
    val expirationSeconds: Long
)