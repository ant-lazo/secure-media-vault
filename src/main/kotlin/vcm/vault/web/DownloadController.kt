package vcm.vault.web

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.mono
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import vcm.vault.mq.dto.FileDownloadedEvent
import vcm.vault.service.DownloadService

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

    /**
     * Soporta Range: ej. Range: bytes=0-1023
     */
    @GetMapping("/{objectName}")
    fun download(
        @PathVariable objectName: String,
        @RequestHeader(name = HttpHeaders.RANGE, required = false) rangeHeader: String?,
        response: ServerHttpResponse
    ): Mono<Void> =
        // Usamos mono { } para llamar a la función suspend del service
        mono {
            // Pide al service el stream segmentado desde MinIO
            val rr = downloadService.streamObject(
                bucket = bucket,
                objectName = objectName,
                rangeHeader = rangeHeader,
                bufferFactory = response.bufferFactory()
            )

            // Headers de respuesta
            response.headers.contentType = MediaType.parseMediaType(rr.contentType ?: "application/octet-stream")
            response.headers[HttpHeaders.ACCEPT_RANGES] = listOf("bytes")
            response.headers[HttpHeaders.CONTENT_LENGTH] = listOf(rr.contentLength.toString())

            if (rr.status206) {
                response.setStatusCode(HttpStatus.PARTIAL_CONTENT)
                rr.contentRangeHeader?.let { response.headers[HttpHeaders.CONTENT_RANGE] = listOf(it) }
            } else {
                response.setStatusCode(HttpStatus.OK)
            }

            // Escribir el body con streaming; al terminar publicamos el evento por RabbitMQ
            response.writeWith(
                rr.flux.doOnComplete {
                    ioScope.launch {
                        try {
                            val evt = FileDownloadedEvent(
                                objectName = objectName,
                                filename = objectName // si guardaste el filename real en metadata, reemplázalo aquí
                            )
                            rabbitTemplate.convertAndSend(exchange, downloadedRoutingKey, evt)
                        } catch (_: Exception) {
                            // log opcional; no interrumpir la respuesta ya enviada
                        }
                    }
                }
            )
        }.flatMap { it } // aplanar el Mono<Mono<Void>> a Mono<Void>
}