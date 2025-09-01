package vcm.vault.config

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import reactor.core.publisher.Mono

class JwtToAuthConverter(
    private val delegate: JwtAuthenticationConverter
) : Converter<Jwt, Mono<AbstractAuthenticationToken>> {
    override fun convert(source: Jwt): Mono<AbstractAuthenticationToken>? =
        Mono.justOrEmpty(delegate.convert(source))
}