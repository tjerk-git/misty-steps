package com.mistysteps.app.maps

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class PackInstallerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val body = ByteArray(100_000) { (it * 31 % 251).toByte() }
    private val ref = FileRef("https://maps.potloodgum.com/v2/nl/2026/fr/map.pmtiles", body.size.toLong(), sha(body))

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Serves [body]; remembers the offsets it was asked for. [failAfter] cuts the stream with an IOException. */
    private class Fake(val body: ByteArray, val honorRange: Boolean = true, val failAfter: Int? = null, val emptyOn206: Boolean = false) : Downloader {
        val asked = mutableListOf<Long>()
        override fun open(url: String, from: Long): Response {
            asked += from
            val ranged = honorRange && from > 0
            val start = if (ranged) from.toInt() else 0
            val data = if (ranged && emptyOn206) ByteArray(0) else body.copyOfRange(start, body.size)
            val stream: InputStream = ByteArrayInputStream(data)
            val limited = failAfter?.let { cut ->
                object : InputStream() {
                    var n = 0
                    override fun read(): Int = if (n++ >= cut) throw IOException("cut") else stream.read()
                }
            } ?: stream
            return Response(if (ranged) 206 else 200, data.size.toLong(), limited)
        }
    }

    private fun dest() = File(tmp.root, "2026/fr/map.pmtiles")
    private fun part() = File(dest().path + ".part")

    @Test
    fun freshDownloadWritesFileAndChecksOut() {
        val result = PackInstaller(Fake(body)).install(ref, dest())
        assertTrue(result is InstallResult.Done)
        assertArrayEquals(body, dest().readBytes())
        assertFalse(part().exists())
    }

    @Test
    fun resumeContinuesFromPartLength() {
        part().parentFile!!.mkdirs()
        part().writeBytes(body.copyOfRange(0, 40_000))
        val fake = Fake(body)
        val result = PackInstaller(fake).install(ref, dest())
        assertTrue(result is InstallResult.Done)
        assertEquals(listOf(40_000L), fake.asked)
        assertArrayEquals(body, dest().readBytes())
    }

    @Test
    fun serverIgnoringRangeRestartsFromZero() {
        part().parentFile!!.mkdirs()
        part().writeBytes(body.copyOfRange(0, 40_000))
        val result = PackInstaller(Fake(body, honorRange = false)).install(ref, dest())
        assertTrue(result is InstallResult.Done)
        assertArrayEquals(body, dest().readBytes())
    }

    @Test
    fun cutAtTheEndStillFinishes() {
        part().parentFile!!.mkdirs()
        part().writeBytes(body)
        val result = PackInstaller(Fake(body, emptyOn206 = true)).install(ref, dest())
        assertTrue(result is InstallResult.Done)
        assertArrayEquals(body, dest().readBytes())
    }

    @Test
    fun emptyPartFileIsTreatedAsFresh() {
        part().parentFile!!.mkdirs()
        part().writeBytes(ByteArray(0))
        val fake = Fake(body)
        assertTrue(PackInstaller(fake).install(ref, dest()) is InstallResult.Done)
        assertEquals(listOf(0L), fake.asked)
    }

    @Test
    fun partLongerThanTheFileStartsOver() {
        part().parentFile!!.mkdirs()
        part().writeBytes(ByteArray(body.size + 5))
        assertTrue(PackInstaller(Fake(body)).install(ref, dest()) is InstallResult.Done)
        assertArrayEquals(body, dest().readBytes())
    }

    @Test
    fun wrongChecksumDeletesPartAndFails() {
        val bad = ref.copy(sha256 = "0".repeat(64))
        val result = PackInstaller(Fake(body)).install(bad, dest())
        assertEquals(Reason.CHECKSUM, (result as InstallResult.Failed).reason)
        assertFalse(part().exists())
        assertFalse(dest().exists())
    }

    @Test
    fun connectionCutKeepsPartAndFails() {
        val result = PackInstaller(Fake(body, failAfter = 30_000)).install(ref, dest())
        assertEquals(Reason.NETWORK, (result as InstallResult.Failed).reason)
        assertEquals(30_000L, part().length())
        assertFalse(dest().exists())
    }

    @Test
    fun shortBodyWithoutAnErrorIsANetworkFailure() {
        val short = Fake(body.copyOfRange(0, 60_000))
        val result = PackInstaller(short).install(ref, dest())
        assertEquals(Reason.NETWORK, (result as InstallResult.Failed).reason)
        assertEquals(60_000L, part().length())
    }

    @Test
    fun progressReportsBytesSoFar() {
        val seen = mutableListOf<Long>()
        PackInstaller(Fake(body)).install(ref, dest()) { seen += it }
        assertTrue(seen.isNotEmpty())
        assertEquals(body.size.toLong(), seen.last())
        assertEquals(seen.sorted(), seen)
    }
}
