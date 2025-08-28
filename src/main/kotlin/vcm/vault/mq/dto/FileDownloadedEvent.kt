package vcm.vault.mq.dto

data class FileDownloadedEvent(
    val objectName: String,
    val filename: String
)