package vcm.vault.web

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.mono
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.MediaTypeFactory
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import vcm.vault.mq.dto.FileDownloadedEvent
import vcm.vault.service.DownloadService
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/api/files")
class DownloadController(
    private val downloadService: DownloadService,
    private val rabbitTemplate: RabbitTemplate,
    @Value("\${minio.bucket}") private val bucket: String,
    @Value("\${app.rabbit.exchange}") private val exchange: String,
    @Value("\${app.rabbit.downloadedRoutingKey}") private val downloadedRoutingKey: String
) {
    private val ioScope = CoroutineScope(Dispatchers.IO)

    // Range: ej. Range: bytes=0-1023
    @GetMapping("/{objectName}")
    fun download(
        @PathVariable objectName: String,
        @RequestHeader(name = HttpHeaders.RANGE, required = false) rangeHeader: String?,
        response: ServerHttpResponse
    ): Mono<Void> =
        mono {
            val rr = downloadService.streamObject(
                bucket = bucket,
                objectName = objectName,
                rangeHeader = rangeHeader,
                bufferFactory = response.bufferFactory()
            )

            // 1) Content-Type: use the storage account and take the extension
            val mediaType: MediaType =
                rr.contentType?.let { MediaType.parseMediaType(it) }
                    ?: MediaTypeFactory.getMediaType(objectName)
                        .orElse(MediaType.APPLICATION_OCTET_STREAM)
            response.headers.contentType = mediaType

            // 2) Content-Disposition with filename, take the fullname
            val fileName = objectName.substringAfterLast('/')
            val disposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8) // agrega filename*
                .build()
            response.headers[HttpHeaders.CONTENT_DISPOSITION] = listOf(disposition.toString())

            // 3) Ranges support
            response.headers[HttpHeaders.ACCEPT_RANGES] = listOf("bytes")
            response.headers[HttpHeaders.CONTENT_LENGTH] = listOf(rr.contentLength.toString())
            if (rr.status206) {
                response.setStatusCode(HttpStatus.PARTIAL_CONTENT)
                rr.contentRangeHeader?.let { response.headers[HttpHeaders.CONTENT_RANGE] = listOf(it) }
            } else {
                response.setStatusCode(HttpStatus.OK)
            }

            // 4) Public the stream
            response.writeWith(
                rr.flux.doOnComplete {
                    ioScope.launch {
                        try {
                            rabbitTemplate.convertAndSend(
                                exchange,
                                downloadedRoutingKey,
                                FileDownloadedEvent(objectName = objectName, filename = fileName)
                            )
                        } catch (_: Exception) { }
                    }
                }
            )
        }.flatMap { it } // transforms Mono<Mono<Void>> to Mono<Void>
}