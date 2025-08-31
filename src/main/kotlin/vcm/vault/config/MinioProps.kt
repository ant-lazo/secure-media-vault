package vcm.vault.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "minio")
data class MinioProps(
    val endpoint: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String
)