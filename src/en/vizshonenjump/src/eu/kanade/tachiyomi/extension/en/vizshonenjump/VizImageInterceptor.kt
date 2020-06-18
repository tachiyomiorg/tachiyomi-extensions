package eu.kanade.tachiyomi.extension.en.vizshonenjump

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.support.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
        val byteInputStreamForExif = ByteArrayInputStream(byteOutputStream.toByteArray())

        val input = BitmapFactory.decodeStream(byteInputStreamForImage)
        val width = input.width
        val height = input.height
        val newWidth = width - WIDTH_CUT
        val newHeight = height - HEIGHT_CUT
        val blockWidth = newWidth / CELL_WIDTH_COUNT
        val blockHeight = newHeight / CELL_HEIGHT_COUNT

        val result = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Draw the borders.
        canvas.copyCell(input, Pair(0, 0), Pair(0, 0), newWidth, blockHeight)
        canvas.copyCell(input,
            Pair(0, blockHeight + 10), Pair(0, blockHeight),
            blockWidth, newHeight - 2 * blockHeight)
        canvas.copyCell(input,
            Pair(0, (CELL_HEIGHT_COUNT - 1) * (blockHeight + 10)),
            Pair(0, (CELL_HEIGHT_COUNT - 1) * blockHeight),
            newWidth, height - (CELL_HEIGHT_COUNT - 1) * (blockHeight + 10))
        canvas.copyCell(input,
            Pair((CELL_WIDTH_COUNT - 1) * (blockWidth + 10), blockHeight + 10),
            Pair((CELL_WIDTH_COUNT - 1) * blockWidth, blockHeight),
            blockWidth + (newWidth - CELL_WIDTH_COUNT * blockWidth),
            newHeight - 2 * blockHeight)

        // Get the key from the EXIF tag.
        val exifInterface = ExifInterface(byteInputStreamForExif)
        val uniqueId = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_UNIQUE_ID)!!
        val key = uniqueId.split(":")
            .map { it.toInt(16) }

        // Draw the inner cells.
        for ((m, y) in key.iterator().withIndex()) {
            canvas.copyCell(input,
                Pair((m % (CELL_WIDTH_COUNT - 2) + 1) * (blockWidth + 10), (m / (CELL_WIDTH_COUNT - 2) + 1) * (blockHeight + 10)),
                Pair((y % (CELL_WIDTH_COUNT - 2) + 1) * blockWidth, (y / (CELL_WIDTH_COUNT - 2) + 1) * blockHeight),
                blockWidth, blockHeight)
        }

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.PNG, 100, output)
        return output.toByteArray()
    }

    private fun Canvas.copyCell(from: Bitmap, src: Pair<Int, Int>, dst: Pair<Int, Int>, width: Int, height: Int) {
        val srcRect = Rect(src.first, src.second, src.first + width, src.second + height)
        val dstRect = Rect(dst.first, dst.second, dst.first + width, dst.second + height)
        drawBitmap(from, srcRect, dstRect, null)
    }

    companion object {
        private const val SIGNATURE = "Signature"
        private val MEDIA_TYPE = MediaType.parse("image/png")

        private const val CELL_WIDTH_COUNT = 10
        private const val CELL_HEIGHT_COUNT = 15
        private const val WIDTH_CUT = 90
        private const val HEIGHT_CUT = 140
    }
}
