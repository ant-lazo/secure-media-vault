package vcm.vault.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.rabbit")
data class AppRabbitProps(
    val exchange: String,
    val routingKey: String,
    val queue: String,
    val downloadedRoutingKey: String,
    val downloadedQueue: String
)