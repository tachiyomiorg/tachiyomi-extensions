package eu.kanade.tachiyomi.extension.ko.mangashowme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/*
 * `v1` means url padding of image host.
 *  It's not need now, but it remains in this code for sometime.
 */

internal class ImageDecoder(scripts: String) {
    private val cnt = substringBetween(scripts, "var view_cnt = ", ";")
            .toIntOrNull() ?: 0
    private val chapter = substringBetween(scripts, "var chapter = ", ";")
            .toIntOrNull() ?: 0

    fun request(url: String): String {
        if (cnt < 10) return url
        return "$url??$chapter;$cnt"
    }
}


internal class ImageDecoderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val response = chain.proceed(req)

        val decodeHeader = req.header("ImageDecodeRequest")

        return if (decodeHeader != null) {
            try {
                val s = decodeHeader.split(";").map { it.toInt() }

                if (s[1] < 10) return response

                val res = response.body()!!.byteStream().use {
                    decodeImageRequest(it, s[0], s[1])
                }

                val rb = ResponseBody.create(MediaType.parse("image/png"), res)
                response.newBuilder().body(rb).build()
            } catch (e: Exception) {
                e.printStackTrace()
                throw IOException("Image decoder failure.", e)
            }
        } else {
            response
        }
    }

    private fun decodeImageRequest(img: InputStream, chapter: Int, view_cnt: Int): ByteArray {
        val decoded = BitmapFactory.decodeStream(img)
        val result = imageDecoder(decoded, chapter, view_cnt)

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.PNG, 100, output)
        return output.toByteArray()
    }

    /*
     * `imageDecoder` is modified version of
     *  https://github.com/junheah/MangaViewAndroid/blob/b69a4427258fe7fc5fb5363108572bbee0d65e94/app/src/main/java/ml/melun/mangaview/mangaview/Decoder.java#L6-L60
     *
     * MIT License
     *
     *
     * Copyright (c) 2019 junheah
     */
    private fun imageDecoder(input: Bitmap, chapter: Int, view_cnt: Int, half: Int = 0, _CX: Int = ManaMoa.V1_CX, _CY: Int = ManaMoa.V1_CY): Bitmap {
        if (view_cnt == 0) return input
        val viewCnt = view_cnt / 10
        var CX = _CX
        var CY = _CY

        //view_cnt / 10 > 30000 ? (this._CX = 1, this._CY = 6)  : view_cnt / 10 > 20000 ? this._CX = 1 : view_cnt / 10 > 10000 && (this._CY = 1)
        // DO NOT (AUTOMATICALLY) REPLACE TO when USING IDEA. seems it doesn't detect correct condition
        if (viewCnt > 30000) {
            CX = 1
            CY = 6
        } else if (viewCnt > 20000) {
            CX = 1
        } else if (viewCnt > 10000) {
            CY = 1
        }

        //decode image
        val order = Array(CX * CY) { IntArray(2) }
        val oSize = order.size - 1

        for (i in 0..oSize) {
            order[i][0] = i
            order[i][1] = decoderRandom(chapter, viewCnt, i)
        }

        java.util.Arrays.sort(order) { a, b -> java.lang.Double.compare(a[1].toDouble(), b[1].toDouble()) }

        //create new bitmap
        val outputWidth = if (half == 0) input.width else input.width / 2
        val output = Bitmap.createBitmap(outputWidth, input.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val rowWidth = input.width / CX
        val rowHeight = input.height / CY

        for (i in 0..oSize) {
            val o = order[i]
            val ox = i % CX
            val oy = i / CX
            val tx = o[0] % CX
            val ty = o[0] / CX
            val sx = if (half == 2) -input.width / 2 else 0

            val srcX = ox * rowWidth
            val srcY = oy * rowHeight
            val destX = (tx * rowWidth) + sx
            val destY = ty * rowHeight

            canvas.drawBitmap(input,
                    Rect(srcX, srcY, srcX + rowWidth, srcY + rowHeight),
                    Rect(destX, destY, destX + rowWidth, destY + rowHeight),
                    null)
        }

        return output
    }

    /*
     * `decodeRandom` is modified version of
     *  https://github.com/junheah/MangaViewAndroid/blob/b69a4427258fe7fc5fb5363108572bbee0d65e94/app/src/main/java/ml/melun/mangaview/mangaview/Decoder.java#L6-L60
     *
     * MIT License
     *
     * Copyright (c) 2019 junheah
     */
    private fun decoderRandom(chapter: Int, view_cnt: Int, index: Int): Int {
        if (chapter < 554714) {
            val x = 10000 * Math.sin((view_cnt + index).toDouble())
            return Math.floor(100000 * (x - Math.floor(x))).toInt()
        }

        val seed = view_cnt + index + 1
        val t = 100 * Math.sin((10 * seed).toDouble())
        val n = 1000 * Math.cos((13 * seed).toDouble())
        val a = 10000 * Math.tan((14 * seed).toDouble())

        return (Math.floor(100 * (t - Math.floor(t))) +
                Math.floor(1000 * (n - Math.floor(n))) +
                Math.floor(10000 * (a - Math.floor(a)))).toInt()
    }
}

private fun substringBetween(target: String, prefix: String, suffix: String): String = {
    target.substringAfter(prefix).substringBefore(suffix)
}()