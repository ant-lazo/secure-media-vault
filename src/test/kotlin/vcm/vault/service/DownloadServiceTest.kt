package vcm.vault.service

import io.minio.GetObjectArgs
import io.minio.GetObjectResponse
import io.minio.MinioClient
import io.minio.StatObjectResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import reactor.test.StepVerifier

class DownloadServiceTest {

    private val minio = mockk<MinioClient>()
    private val service = DownloadService(minio)
    private val bufferFactory = DefaultDataBufferFactory()

    @Test
    @DisplayName("Test for stream object with without range returns full object")
    fun `streamObject without range returns full object`() = runTest {
        val objectBytes = "Hello Minio".toByteArray()
        val totalSize = objectBytes.size.toLong()

        val statResponse = mockk<StatObjectResponse>()
        every { statResponse.size() } returns totalSize
        every { statResponse.contentType() } returns "text/plain"
        coEvery { minio.statObject(any()) } returns statResponse

        val inputStream = objectBytes.inputStream()
        val response = mockk<GetObjectResponse>(relaxed = true)
        every { response.read(any(), any(), any()) } answers {
            inputStream.read(arg(0), arg(1), arg(2))
        }
        every { response.close() } answers { inputStream.close() }

        coEvery { minio.getObject(any<GetObjectArgs>()) } returns response

        val result = service.streamObject(
            bucket = "my-bucket",
            objectName = "test.txt",
            rangeHeader = null,
            bufferFactory = bufferFactory
        )

        assertEquals(totalSize, result.contentLength)
        assertEquals("text/plain", result.contentType)
        assertFalse(result.status206)

        StepVerifier.create(result.flux)
            .consumeNextWith { dataBuffer ->
                val str = dataBuffer.toString(Charsets.UTF_8)
                assertEquals("Hello Minio", str)
            }
            .verifyComplete()
    }


    @Test
    @DisplayName("Test for stream object with range returns partial content")
    fun `streamObject with range returns partial content`() = runTest {
        // given
        val objectBytes = "Hello Minio".toByteArray()
        val totalSize = objectBytes.size.toLong()

        val statResponse = mockk<StatObjectResponse>()
        every { statResponse.size() } returns totalSize
        every { statResponse.contentType() } returns "text/plain"
        coEvery { minio.statObject(any()) } returns statResponse

        val inputStream = objectBytes.inputStream()
        val response = mockk<GetObjectResponse>(relaxed = true)
        every { response.read(any(), any(), any()) } answers {
            inputStream.read(arg(0), arg(1), arg(2))
        }
        every { response.close() } answers { inputStream.close() }

        coEvery { minio.getObject(any<GetObjectArgs>()) } returns response

        // when
        val result = service.streamObject(
            bucket = "my-bucket",
            objectName = "test.txt",
            rangeHeader = "bytes=0-4",
            bufferFactory = bufferFactory
        )

        // then
        assertEquals(5, result.contentLength)
        assertTrue(result.status206)
        assertEquals("bytes 0-4/$totalSize", result.contentRangeHeader)
        assertEquals("text/plain", result.contentType)

        StepVerifier.create(result.flux)
            .consumeNextWith { dataBuffer ->
                val str = dataBuffer.toString(Charsets.UTF_8)
                assertEquals("Hello Minio", str)
            }
            .verifyComplete()
    }
}
