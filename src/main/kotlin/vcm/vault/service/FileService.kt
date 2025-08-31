package vcm.vault.service

import io.minio.MinioClient
import io.minio.PutObjectArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.withContext
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import org.springframework.util.unit.DataSize
import reactor.core.publisher.Mono
import vcm.vault.mq.dto.FileUploadedEvent
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@Service
class FileService(
    private val minio: MinioClient,
    private val rabbitTemplate: RabbitTemplate,
    @Value("\${minio.bucket}") private val bucket: String,
    @Value("\${app.rabbit.exchange}") private val exchange: String,
    @Value("\${app.rabbit.routingKey}") private val routingKey: String,
) {
    /**
     * Saves the Multipart file into MinIO and returns the generated object name.
     */
    suspend fun saveToMinio(file: FilePart): String {
// Write incoming flux to a temp file (non-blocking step)
        val tmp: Path = Files.createTempFile("upload-", "-tmp")
        try {
// Transfer FilePart to disk (reactive -> await completion)
            file.transferTo(tmp).await()

            val objectName = buildObjectName(file.filename())
            val size = Files.size(tmp)

// Blocking MinIO call wrapped in IO dispatcher
            withContext(Dispatchers.IO) {
                Files.newInputStream(tmp).use { input ->
                    minio.putObject(
                        PutObjectArgs.builder()
                            .bucket(bucket)
                            .`object`(objectName)
                            .stream(input, size, DataSize.ofMegabytes(10).toBytes())
                            .contentType("application/octet-stream")
                            .build()
                    )
                }
            }
            return objectName
        } finally {
            try { Files.deleteIfExists(tmp) } catch (_: Exception) {}
        }
    }

    suspend fun publishFileUploaded(objectName: String, filename: String) {
        val evt = FileUploadedEvent(objectName = objectName, filename = filename)
        withContext(Dispatchers.IO) {
            rabbitTemplate.convertAndSend(exchange, routingKey, evt) // -> JSON
        }
    }

    private fun buildObjectName(original: String): String =
        "${Instant.now().toEpochMilli()}-${original.replace(" ", "_")}"
}

// small await extension for Mono<Void>
private suspend fun Mono<Void>.await() =
    this.awaitFirstOrNull()