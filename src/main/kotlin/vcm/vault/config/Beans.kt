package vcm.vault.config

import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary


@Configuration
class BeansConfig(
    @Value("\${minio.endpoint}") private val endpoint: String,
    @Value("\${minio.accessKey}") private val accessKey: String,
    @Value("\${minio.secretKey}") private val secretKey: String,
    @Value("\${minio.bucket}") private val bucket: String,
    @Value("\${app.rabbit.exchange}") private val exchangeName: String,
    @Value("\${app.rabbit.queue}") private val queueName: String,
    @Value("\${app.rabbit.routingKey}") private val routingKey: String,
    @Value("\${app.rabbit.downloadedQueue}") private val downloadedQueueName: String,
    @Value("\${app.rabbit.downloadedRoutingKey}") private val downloadedRoutingKey: String
) {
    @Bean
    fun minioClient(): MinioClient =
        MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build()


    // ensure bucket exists on startup
    @Bean
    fun ensureBucket(minio: MinioClient): Any {
        val exists = minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())
        if (!exists) {
            minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
        }
        return Any()
    }


    // RabbitMQ topology
    @Bean fun exchange() = DirectExchange(exchangeName)
    @Bean fun queue() = Queue(queueName, true)
    @Bean fun binding(): Binding = BindingBuilder.bind(queue()).to(exchange()).with(routingKey)

    @Bean fun jacksonMessageConverter(): MessageConverter = Jackson2JsonMessageConverter()

    @Bean
    @Primary
    fun rabbitTemplate(cf: ConnectionFactory, conv: MessageConverter): RabbitTemplate =
        RabbitTemplate(cf).apply { messageConverter = conv }

    @Bean // factory por defecto para @RabbitListener
    fun rabbitListenerContainerFactory(cf: ConnectionFactory, conv: MessageConverter) =
        SimpleRabbitListenerContainerFactory().apply {
            setConnectionFactory(cf)
            setMessageConverter(conv)
        }

    @Bean fun downloadedQueue() = Queue(downloadedQueueName, true)

    @Bean
    fun downloadedBinding(): Binding =
        BindingBuilder.bind(downloadedQueue())
            .to(exchange())
            .with(downloadedRoutingKey)

}