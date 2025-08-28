package vcm.vault.mq.dto

data class FileUploadedEvent(
    val objectName: String,
    val filename: String,
    val event: String = "FILE_UPLOADED"
)