package vcm.vault.service

import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.mockk.every;
import io.mockk.coEvery;
import io.mockk.coVerify
import io.mockk.mockk;
import io.mockk.verify;
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.http.codec.multipart.FilePart
import reactor.core.publisher.Mono
import vcm.vault.mq.dto.FileUploadedEvent

class FileServiceTest {

    private val minio = mockk<MinioClient>(relaxed = true)
    private val rabbitTemplate = mockk<RabbitTemplate>(relaxed = true)

    private val service = FileService(
        minio = minio,
        rabbitTemplate = rabbitTemplate,
        bucket = "test-bucket",
        exchange = "test-exchange",
        routingKey = "test-key"
    )

    @Test
    @DisplayName("Test for saveToMinio uploads file and returns object name")
    fun `saveToMinio uploads file and returns object name`() = runTest {
        val filePart = mockk<FilePart>()
        every { filePart.filename() } returns "example.txt"
        coEvery { filePart.transferTo(any<java.nio.file.Path>()) } returns Mono.empty()

        val objectName = service.saveToMinio(filePart)

        assertTrue(objectName.endsWith("-example.txt")) // nombre generado
        coVerify { filePart.transferTo(any<java.nio.file.Path>()) }
        verify {
            minio.putObject(
                withArg<PutObjectArgs> {
                    assertEquals("test-bucket", it.bucket())
                    assertTrue(it.`object`().endsWith("-example.txt"))
                }
            )
        }
    }

    @Test
    @DisplayName("Test for publishFileUploaded sends event to RabbitMQ")
    fun `publishFileUploaded sends event to RabbitMQ`() = runTest {
        val objectName = "123-file.txt"
        val filename = "file.txt"

        service.publishFileUploaded(objectName, filename)

        verify {
            rabbitTemplate.convertAndSend(
                "test-exchange",
                "test-key",
                match<FileUploadedEvent> {
                    it.objectName == objectName && it.filename == filename
                }
            )
        }
    }
}
