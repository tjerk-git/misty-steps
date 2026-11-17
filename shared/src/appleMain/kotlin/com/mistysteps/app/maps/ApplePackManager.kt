package com.mistysteps.app.maps

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.CoreCrypto.CC_SHA256
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.setValue
import platform.darwin.NSObject

/**
 * The map packs on iOS and macOS: files in `<root>/<year>/<province id>/map.pmtiles`, downloaded with NSURLSession (progress
 * included) and checked against the catalog's SHA-256 before they are put in place. Like the Android one, what is installed
 * comes from the files; "downloading" and "failed" are remembered while the app runs.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class ApplePackManager(private val root: String, private val userAgent: String) : PackManager {
    private val files = NSFileManager.defaultManager
    private val _version = MutableStateFlow(0)
    override val version: StateFlow<Int> = _version

    private val _needsAppUpdate = MutableStateFlow(false)
    override val needsAppUpdate: StateFlow<Boolean> = _needsAppUpdate

    /** The catalog is newer than this app understands: tell the person to update. */
    fun setNeedsAppUpdate(value: Boolean) { _needsAppUpdate.value = value }

    private val transient = HashMap<String, PackState>()
    private val tasks = HashMap<String, NSURLSessionDownloadTask>()
    private val entries = HashMap<String, PackEntry>()

    private fun packDir(year: Int, id: String) = "$root/$year/$id"
    private fun mapPath(year: Int, id: String) = "${packDir(year, id)}/map.pmtiles"

    private fun years(): List<Int> =
        (files.contentsOfDirectoryAtPath(root, null) as? List<*>).orEmpty().mapNotNull { (it as? String)?.toIntOrNull() }

    private fun installedYear(id: String): Int? = years().filter { files.fileExistsAtPath(mapPath(it, id)) }.maxOrNull()

    /** The installed map files, one per province (its newest year), sorted by province id. */
    fun mapFiles(): List<String> {
        val ids = years().flatMap { y ->
            (files.contentsOfDirectoryAtPath("$root/$y", null) as? List<*>).orEmpty().mapNotNull { it as? String }
        }.distinct().sorted()
        return ids.mapNotNull { id -> installedYear(id)?.let { mapPath(it, id) } }
    }

    override fun state(entry: PackEntry): PackState {
        transient[entry.id]?.let { return it }
        val installed = installedYear(entry.id) ?: return PackState.NotDownloaded
        return if (entry.year > installed) PackState.UpdateAvailable(installed, entry.year) else PackState.Ready(installed)
    }

    private fun set(id: String, state: PackState?) {
        if (state == null) transient.remove(id) else transient[id] = state
        _version.value++
    }

    override fun download(entry: PackEntry, unmeteredOnly: Boolean) {
        if (state(entry).let { it is PackState.Downloading || it is PackState.Ready }) return
        entries[entry.id] = entry
        set(entry.id, PackState.Downloading(0, entry.map.bytes))
        val config = NSURLSessionConfiguration.defaultSessionConfiguration
        if (unmeteredOnly) {
            config.allowsCellularAccess = false
            config.allowsExpensiveNetworkAccess = false
        }
        val session = NSURLSession.sessionWithConfiguration(config, delegate, NSOperationQueue())
        val request = NSMutableURLRequest.requestWithURL(NSURL.URLWithString(entry.map.url)!!)
        request.setValue(userAgent, forHTTPHeaderField = "User-Agent")
        val task = session.downloadTaskWithRequest(request)
        task.setTaskDescription(entry.id)
        tasks[entry.id] = task
        task.resume()
    }

    override fun remove(id: String) {
        tasks.remove(id)?.cancel()
        entries.remove(id)
        years().forEach { files.removeItemAtPath(packDir(it, id), null) }
        set(id, null)
    }

    /** Whether this connection costs money is not asked yet: be careful and treat it as metered. */
    override fun isMetered(): Boolean = true

    private fun sha256Hex(path: String): String? {
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        return memScoped {
            val md = allocArray<UByteVar>(32)
            CC_SHA256(data.bytes, data.length.convert(), md)
            buildString { for (i in 0 until 32) append(md[i].toInt().toString(16).padStart(2, '0')) }
        }
    }

    private fun finished(id: String, location: NSURL) {
        val entry = entries[id] ?: return
        val temp = location.path ?: return
        if (sha256Hex(temp) != entry.map.sha256) {
            set(id, PackState.Failed(Reason.CHECKSUM))
            return
        }
        files.createDirectoryAtPath(packDir(entry.year, id), true, null, null)
        val target = mapPath(entry.year, id)
        files.removeItemAtPath(target, null)
        if (!files.moveItemAtPath(temp, target, null)) {
            set(id, PackState.Failed(Reason.NO_SPACE))
            return
        }
        // The new year is in place: older ones go.
        years().filter { it < entry.year }.forEach { files.removeItemAtPath(packDir(it, id), null) }
        tasks.remove(id)
        set(id, null)
    }

    private val delegate = object : NSObject(), NSURLSessionDownloadDelegateProtocol {
        override fun URLSession(session: NSURLSession, downloadTask: NSURLSessionDownloadTask, didFinishDownloadingToURL: NSURL) {
            downloadTask.taskDescription?.let { finished(it, didFinishDownloadingToURL) }
        }

        override fun URLSession(
            session: NSURLSession, downloadTask: NSURLSessionDownloadTask, didWriteData: Long,
            totalBytesWritten: Long, totalBytesExpectedToWrite: Long,
        ) {
            val id = downloadTask.taskDescription ?: return
            val total = if (totalBytesExpectedToWrite > 0) totalBytesExpectedToWrite else entries[id]?.map?.bytes ?: 0
            set(id, PackState.Downloading(totalBytesWritten, total))
        }

        override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
            val id = task.taskDescription ?: return
            // A finished download already set its result; an error means it never arrived.
            if (didCompleteWithError != null && transient[id] is PackState.Downloading) set(id, PackState.Failed(Reason.NETWORK))
        }
    }
}
