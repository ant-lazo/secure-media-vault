package vcm.vault.config

import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
@EnableConfigurationProperties(MinioProps::class, AppRabbitProps::class)
class BeansConfig(
    private val minio: MinioProps,
    private val rabbit: AppRabbitProps
) {
    @Bean
    fun minioClient(): MinioClient =
        MinioClient.builder()
            .endpoint(minio.endpoint)
            .credentials(minio.accessKey, minio.secretKey)
            .build()

    // ensure bucket exists on startup
    @Bean
    fun ensureBucket(minIo: MinioClient): Any {
        val exists = minIo.bucketExists(BucketExistsArgs.builder().bucket(minio.bucket).build())
        if (!exists) {
            minIo.makeBucket(MakeBucketArgs.builder().bucket(minio.bucket).build())
        }
        return Any()
    }

    // RabbitMQ topology
    @Bean
    fun exchange() = DirectExchange(rabbit.exchange)

    @Bean
    fun queue() = Queue(rabbit.queue, true)

    @Bean
    fun binding(): Binding = BindingBuilder.bind(queue()).to(exchange()).with(rabbit.routingKey)

    @Bean fun jacksonMessageConverter(): MessageConverter = Jackson2JsonMessageConverter()

    @Bean
    @Primary
    fun rabbitTemplate(cf: ConnectionFactory, conv: MessageConverter): RabbitTemplate =
        RabbitTemplate(cf).apply { messageConverter = conv }

    @Bean
    fun downloadedQueue() = Queue(rabbit.downloadedQueue, true)

    @Bean
    fun downloadedBinding(): Binding =
        BindingBuilder.bind(downloadedQueue())
            .to(exchange())
            .with(rabbit.downloadedRoutingKey)

}