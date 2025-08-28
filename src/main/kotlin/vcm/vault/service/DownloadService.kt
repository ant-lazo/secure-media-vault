package vcm.vault.service

import io.minio.GetObjectArgs
import io.minio.MinioClient
import io.minio.StatObjectArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferFactory
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpRange
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.io.InputStream

@Service
class DownloadService(
    private val minio: MinioClient
) {
    data class RangeResult(
        val flux: Flux<DataBuffer>,
        val contentLength: Long,
        val status206: Boolean,
        val contentRangeHeader: String?, // ej: "bytes 0-1023/9999"
        val contentType: String?
    )

    /**
     * Devuelve un stream (Flux<DataBuffer>) del objeto en MinIO.
     * Si 'rangeHeader' viene (e.g., "bytes=0-1023"), responde solo ese segmento y
     * marca status 206 + Content-Range. Si no, devuelve todo el objeto (200).
     */
    suspend fun streamObject(
        bucket: String,
        objectName: String,
        rangeHeader: String?,
        bufferFactory: DataBufferFactory
    ): RangeResult = withContext(Dispatchers.IO) {
        // 1) Obtener metadata (tamaño, tipo de contenido)
        val stat = minio.statObject(
            StatObjectArgs.builder()
                .bucket(bucket)
                .`object`(objectName)
                .build()
        )
        val totalSize = stat.size()
        val contentType = stat.contentType() ?: "application/octet-stream"

        // 2) Parsear Range (si llega y es válido); si no, full file
        val parsedRanges = try {
            if (rangeHeader.isNullOrBlank()) emptyList() else HttpRange.parseRanges(rangeHeader)
        } catch (_: Exception) {
            emptyList() // Range inválido -> servir archivo completo
        }

        val resolved = if (parsedRanges.isNotEmpty()) parsedRanges.first() else null
        val isPartial = resolved != null
        val offset = resolved?.getRangeStart(totalSize) ?: 0L
        val end = resolved?.getRangeEnd(totalSize) ?: (totalSize - 1)
        val length = (end - offset + 1).coerceAtLeast(0)
        val contentRangeHeader = if (isPartial) "bytes $offset-$end/$totalSize" else null

        // 3) Abrir InputStream desde MinIO (completo o segmentado)
        val input: InputStream =
            if (isPartial) {
                minio.getObject(
                    GetObjectArgs.builder()
                        .bucket(bucket)
                        .`object`(objectName)
                        .offset(offset)
                        .length(length)
                        .build()
                )
            } else {
                minio.getObject(
                    GetObjectArgs.builder()
                        .bucket(bucket)
                        .`object`(objectName)
                        .build()
                )
            }

        // 4) Convertir a Flux<DataBuffer> y asegurar cierre
        val flux = DataBufferUtils.readInputStream(
            { input },
            bufferFactory,
            DEFAULT_BUFFER_SIZE
        ).doFinally {
            try { input.close() } catch (_: Exception) {}
        }

        RangeResult(
            flux = flux,
            contentLength = if (isPartial) length else totalSize,
            status206 = isPartial,
            contentRangeHeader = contentRangeHeader,
            contentType = contentType
        )
    }

    private companion object {
        // Sube este buffer si sirves vídeo/audio grandes: 64 KB o 256 KB mejoran throughput.
        const val DEFAULT_BUFFER_SIZE = 64 * 1024
    }
}