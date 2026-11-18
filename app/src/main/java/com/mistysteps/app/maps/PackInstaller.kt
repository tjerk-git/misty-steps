package com.mistysteps.app.maps

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** An HTTP answer: 200 (whole file) or 206 (from the offset that was asked for). */
class Response(val status: Int, val contentLength: Long, val body: InputStream)

interface Downloader {
    /** Opens [url] from byte [from] on. Throws [IOException] for anything other than 200 or 206. */
    fun open(url: String, from: Long): Response
}


sealed class InstallResult {
    object Done : InstallResult()
    class Failed(val reason: Reason, val cause: Throwable? = null) : InstallResult()
}

/**
 * Downloads one file to [dest] so that it only ever appears there complete and checked: the bytes go to
 * `<dest>.part`, a cut-off download resumes from that file's length, the sha256 is checked, and only then is
 * the file renamed into place.
 */
class PackInstaller(private val downloader: Downloader) {
    fun install(ref: FileRef, dest: File, onProgress: (Long) -> Unit = {}): InstallResult {
        val part = File(dest.path + ".part")
        dest.parentFile?.mkdirs()
        var have = if (part.exists()) part.length() else 0L
        if (have > ref.bytes) { part.delete(); have = 0 }
        onProgress(have)

        var seen = "no request"
        if (have < ref.bytes) {
            try {
                val response = downloader.open(ref.url, have)
                seen = "status ${response.status}, content length ${response.contentLength}, asked from $have"

                // A server that ignores the Range header sends the whole file: start over, never append to it.
                val append = response.status == 206 && have > 0
                if (!append) have = 0
                response.body.use { input ->
                    FileOutputStream(part, append).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var total = have
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            total += n
                            onProgress(total)
                        }
                    }
                }
            } catch (e: IOException) {
                return Failed(e)
            }
        }
        if (part.length() != ref.bytes) {
            return InstallResult.Failed(Reason.NETWORK, IOException("file is ${part.length()} of ${ref.bytes} bytes ($seen)"))
        }

        if (sha256(part) != ref.sha256) {
            part.delete()
            return InstallResult.Failed(Reason.CHECKSUM)
        }
        try {
            Files.move(part.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            return Failed(e)
        }
        return InstallResult.Done
    }

    private fun Failed(e: IOException) =
        InstallResult.Failed(if (e.message?.contains("No space left", ignoreCase = true) == true) Reason.NO_SPACE else Reason.NETWORK, e)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/** The real downloader. Asks for the bytes as they are: a gzip answer would not match a byte offset. */
class HttpDownloader(private val userAgent: String) : Downloader {
    override fun open(url: String, from: Long): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", userAgent)
        connection.setRequestProperty("Accept-Encoding", "identity")
        if (from > 0) connection.setRequestProperty("Range", "bytes=$from-")
        val code = connection.responseCode
        if (code != 200 && code != 206) {
            connection.disconnect()
            throw IOException("HTTP $code")
        }
        val stream = object : java.io.FilterInputStream(connection.inputStream) {
            override fun close() { super.close(); connection.disconnect() }
        }
        return Response(code, connection.contentLengthLong, stream)
    }
}
