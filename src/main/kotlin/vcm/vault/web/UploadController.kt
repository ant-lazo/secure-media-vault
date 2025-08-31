package vcm.vault.web

import kotlinx.coroutines.reactor.mono
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import vcm.vault.service.FileService

@RestController
@RequestMapping("/api/files")
@Validated
class UploadController(
    private val fileService: FileService
) {
    data class UploadResponse(val objectName: String, val messagePublished: Boolean)

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@RequestPart("file") file: FilePart): Mono<UploadResponse> = mono {
        val objectName = fileService.saveToMinio(file)
        fileService.publishFileUploaded(objectName, file.filename())
        UploadResponse(objectName, true)
    }
}