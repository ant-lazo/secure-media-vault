package vcm.vault.web

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever



import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.mock.http.server.reactive.MockServerHttpResponse
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import vcm.vault.mq.dto.FileDownloadedEvent
import vcm.vault.service.DownloadService

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadControllerTest {

    private val downloadService: DownloadService = mock()
    private val rabbitTemplate: RabbitTemplate = mock()
    private val bucket = "test-bucket"
    private val exchange = "test-exchange"
    private val routingKey = "downloaded.key"

    private val controller = DownloadController(
        downloadService,
        rabbitTemplate,
        bucket,
        exchange,
        routingKey
    )

    @Test
    fun `should download full file without range header`() = runTest {
        // given
        val objectName = "test.txt"
        val content = "Hello World".toByteArray()
        val dataBuffer: DataBuffer = DefaultDataBufferFactory().wrap(content)

        val rangeResult = DownloadService.RangeResult(
            flux = Flux.just(dataBuffer),
            contentLength = content.size.toLong(),
            status206 = false,
            contentRangeHeader = null,
            contentType = "text/plain"
        )

        whenever(
            downloadService.streamObject(eq(bucket), eq(objectName), isNull(), any())
        ).thenReturn(rangeResult)

        val response: ServerHttpResponse = MockServerHttpResponse()

        // when
        val result = controller.download(objectName, null, response)

        // then
        StepVerifier.create(result).verifyComplete()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("text/plain", response.headers.contentType.toString())
        assertEquals(content.size.toString(), response.headers.getFirst(HttpHeaders.CONTENT_LENGTH))
        assertEquals("bytes", response.headers.getFirst(HttpHeaders.ACCEPT_RANGES))

        // Verificar que RabbitTemplate se llamó al terminar
        verify(rabbitTemplate, timeout(5000)).convertAndSend(
            eq(exchange),
            eq(routingKey),
            check<FileDownloadedEvent> { evt ->
                assertEquals(objectName, evt.objectName)
                assertEquals(objectName, evt.filename)
            }
        )
    }


    @Test
    fun `should download partial file with range header`() = runTest {
        // given
        val objectName = "test.txt"
        val content = "Partial Data".toByteArray()
        val dataBuffer: DataBuffer = DefaultDataBufferFactory().wrap(content)

        val rangeResult = DownloadService.RangeResult(
            flux = Flux.just(dataBuffer),
            contentLength = content.size.toLong(),
            status206 = true,
            contentRangeHeader = null,
            contentType = "text/plain"
        )

        whenever(
            downloadService.streamObject(eq(bucket), eq(objectName), isNull(), any())
        ).thenReturn(rangeResult)

        val response: ServerHttpResponse = MockServerHttpResponse()

        // when
        val result = controller.download(objectName, null, response)

        // then
        StepVerifier.create(result).verifyComplete()

        assertEquals(HttpStatus.PARTIAL_CONTENT, response.statusCode)
        assertEquals(null, response.headers.getFirst(HttpHeaders.CONTENT_RANGE))
        assertEquals("text/plain", response.headers.contentType.toString())
        assertEquals(content.size.toString(), response.headers.getFirst(HttpHeaders.CONTENT_LENGTH))

        // Verificar que RabbitTemplate se llamó
        verify(rabbitTemplate, timeout(5000)).convertAndSend(any(), any(), any<FileDownloadedEvent>())
    }
}
