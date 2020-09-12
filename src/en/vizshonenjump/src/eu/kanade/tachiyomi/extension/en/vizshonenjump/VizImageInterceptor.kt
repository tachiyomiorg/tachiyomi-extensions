package eu.kanade.tachiyomi.extension.en.vizshonenjump

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifSubIFDDirectory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody

class VizImageInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (chain.request().url().queryParameter(SIGNATURE) == null)
            return response

        val image = decodeImage(response.body()!!.byteStream())
        val body = ResponseBody.create(MEDIA_TYPE, image)
        return response.newBuilder()
            .body(body)
            .build()
    }

    private fun decodeImage(image: InputStream): ByteArray {
        // See: https://stackoverflow.com/a/5924132
        // See: https://github.com/inorichi/tachiyomi-extensions/issues/2678#issuecomment-645857603
        val byteOutputStream = ByteArrayOutputStream()
        image.copyTo(byteOutputStream)

        val byteInputStreamForImage = ByteArrayInputStream(byteOutputStream.toByteArray())
        val byteInputStreamForMetadata = ByteArrayInputStream(byteOutputStream.toByteArray())

        val imageData = getImageData(byteInputStreamForMetadata)

        val input = BitmapFactory.decodeStream(byteInputStreamForImage)
        val width = input.width
        val height = input.height
        val newWidth = (width - WIDTH_CUT).coerceAtLeast(imageData.width)
        val newHeight = (height - HEIGHT_CUT).coerceAtLeast(imageData.height)
        val blockWidth = newWidth / CELL_WIDTH_COUNT
        val blockHeight = newHeight / CELL_HEIGHT_COUNT

        val result = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Draw the borders.

        // Top border.
        canvas.drawImage(
            from = input,
            srcX = 0, srcY = 0,
            dstX = 0, dstY = 0,
            width = newWidth, height = blockHeight
        )
        // Left border.
        canvas.drawImage(
            from = input,
            srcX = 0, srcY = blockHeight + 10,
            dstX = 0, dstY = blockHeight,
            width = blockWidth, height = newHeight - 2 * blockHeight
        )
        // Bottom border.
        canvas.drawImage(
            from = input,
            srcX = 0, srcY = (CELL_HEIGHT_COUNT - 1) * (blockHeight + 10),
            dstX = 0, dstY = (CELL_HEIGHT_COUNT - 1) * blockHeight,
            width = newWidth, height = height - (CELL_HEIGHT_COUNT - 1) * (blockHeight + 10)
        )
        // Right border.
        canvas.drawImage(
            from = input,
            srcX = (CELL_WIDTH_COUNT - 1) * (blockWidth + 10), srcY = blockHeight + 10,
            dstX = (CELL_WIDTH_COUNT - 1) * blockWidth, dstY = blockHeight,
            width = blockWidth + (newWidth - CELL_WIDTH_COUNT * blockWidth),
            height = newHeight - 2 * blockHeight
        )

        // Draw the inner cells.
        for ((m, y) in imageData.key.iterator().withIndex()) {
            canvas.drawImage(
                from = input,
                srcX = (m % INNER_CELL_COUNT + 1) * (blockWidth + 10),
                srcY = (m / INNER_CELL_COUNT + 1) * (blockHeight + 10),
                dstX = (y % INNER_CELL_COUNT + 1) * blockWidth,
                dstY = (y / INNER_CELL_COUNT + 1) * blockHeight,
                width = blockWidth, height = blockHeight
            )
        }

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.PNG, 100, output)
        return output.toByteArray()
    }

    private fun Canvas.drawImage(
        from: Bitmap,
        srcX: Int,
        srcY: Int,
        dstX: Int,
        dstY: Int,
        width: Int,
        height: Int
    ) {
        val srcRect = Rect(srcX, srcY, srcX + width, srcY + height)
        val dstRect = Rect(dstX, dstY, dstX + width, dstY + height)
        drawBitmap(from, srcRect, dstRect, null)
    }

    private fun getImageData(inputStream: InputStream): ImageData {
        val metadata = ImageMetadataReader.readMetadata(inputStream)

        val sizeDir = metadata.directories.firstOrNull {
            it.containsTag(ExifSubIFDDirectory.TAG_IMAGE_WIDTH) &&
                it.containsTag(ExifSubIFDDirectory.TAG_IMAGE_HEIGHT)
        }
        val metaWidth = sizeDir?.getInt(ExifSubIFDDirectory.TAG_IMAGE_WIDTH) ?: COMMON_WIDTH
        val metaHeight = sizeDir?.getInt(ExifSubIFDDirectory.TAG_IMAGE_HEIGHT) ?: COMMON_HEIGHT

        val keyDir = metadata.directories.firstOrNull {
            it.containsTag(ExifSubIFDDirectory.TAG_IMAGE_UNIQUE_ID)
        }
        val metaUniqueId = keyDir?.getString(ExifSubIFDDirectory.TAG_IMAGE_UNIQUE_ID)
            ?: throw IOException(KEY_NOT_FOUND)

        return ImageData(metaWidth, metaHeight, metaUniqueId)
    }

    private data class ImageData(val width: Int, val height: Int, val uniqueId: String) {
        val key: List<Int> by lazy {
            uniqueId.split(":")
                .map { it.toInt(16) }
        }
    }

    companion object {
        private const val SIGNATURE = "Signature"
        private val MEDIA_TYPE = MediaType.parse("image/png")

        private const val CELL_WIDTH_COUNT = 10
        private const val CELL_HEIGHT_COUNT = 15
        private const val INNER_CELL_COUNT = CELL_WIDTH_COUNT - 2

        private const val WIDTH_CUT = 90
        private const val HEIGHT_CUT = 140

        private const val COMMON_WIDTH = 800
        private const val COMMON_HEIGHT = 1200

        private const val KEY_NOT_FOUND = "Decryption key not found in image metadata."
    }
}
