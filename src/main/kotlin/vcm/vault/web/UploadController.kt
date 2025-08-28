package vcm.vault.web

import vcm.vault.service.FileService
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.support.MissingServletRequestPartException
import reactor.core.publisher.Mono
import kotlinx.coroutines.reactor.mono


@RestController
@RequestMapping("/api/files")
@Validated
class UploadController(
    private val fileService: FileService
) {
    data class UploadResponse(val objectName: String, val messagePublished: Boolean)


    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@RequestPart("file") file: FilePart): Mono<UploadResponse> = mono {
        val objectName = fileService.saveToMinio(file)     // suspend
        val payload = mapOf("objectName" to objectName, "filename" to file.filename(), "event" to "FILE_UPLOADED")
        fileService.publishFileUploaded(objectName, file.filename())               // suspend
        UploadResponse(objectName, true)
    }
}