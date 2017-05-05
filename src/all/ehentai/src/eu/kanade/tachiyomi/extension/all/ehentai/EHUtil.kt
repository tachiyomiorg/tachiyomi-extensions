package eu.kanade.tachiyomi.extension.all.ehentai

/**
 * Various utility methods used in the E-Hentai source
 */

/**
 * Return null if String is blank, otherwise returns the original String
 * @returns null if the String is blank, otherwise returns the original String
 */
fun String?.nullIfBlank(): String? = if(isNullOrBlank())
    null
else
    this

/**
 * Ignores any exceptions thrown inside a block
 */
fun <T> ignore(expr: () -> T): T? {
    return try { expr() } catch (t: Throwable) { null }
}

/**
 * Use '+' to append Strings onto a StringBuilder
 */
operator fun StringBuilder.plusAssign(other: String) { append(other) }

/**
 * Converts bytes into a human readable String
 */
fun humanReadableByteCount(bytes: Long, si: Boolean): String {
    val unit = if (si) 1000 else 1024
    if (bytes < unit) return bytes.toString() + " B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
    val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1] + if (si) "" else "i"
    return String.format("%.1f %sB", bytes / Math.pow(unit.toDouble(), exp.toDouble()), pre)
}

private val KB_FACTOR: Long = 1000
private val KIB_FACTOR: Long = 1024
private val MB_FACTOR = 1000 * KB_FACTOR
private val MIB_FACTOR = 1024 * KIB_FACTOR
private val GB_FACTOR = 1000 * MB_FACTOR
private val GIB_FACTOR = 1024 * MIB_FACTOR

/**
 * Parse human readable size Strings
 */
fun parseHumanReadableByteCount(arg0: String): Double? {
    val spaceNdx = arg0.indexOf(" ")
    val ret = arg0.substring(0 until spaceNdx).toDouble()
    when (arg0.substring(spaceNdx + 1)) {
        "GB" -> return ret * GB_FACTOR
        "GiB" -> return ret * GIB_FACTOR
        "MB" -> return ret * MB_FACTOR
        "MiB" -> return ret * MIB_FACTOR
        "KB" -> return ret * KB_FACTOR
        "KiB" -> return ret * KIB_FACTOR
    }
    return null
}