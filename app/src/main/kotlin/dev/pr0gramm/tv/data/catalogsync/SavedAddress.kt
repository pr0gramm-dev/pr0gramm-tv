package dev.pr0gramm.tv.data.catalogsync

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * The one saved [ServerAddress], as its canonical URL in [file]. [load] reads the small file synchronously, so the
 * process knows its server at start. [load] applies [ServerAddress.parse], so every address that can be saved also
 * loads, http and https in every build.
 */
class SavedAddress internal constructor(private val file: File) {
    /** The saved address, or null when there is none or the file holds no valid one. */
    fun load(): ServerAddress? {
        val text = if (file.isFile) runCatching { file.readText() }.getOrNull() else null
        return text?.let(ServerAddress::parse)
    }

    /** Replaces the saved address with [address] (temporary file, then rename). Throws [IOException] on failure. */
    fun save(address: ServerAddress) {
        val tmp = File(file.path + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(address.manifestUrl.toString().toByteArray())
            out.fd.sync() // the rename below must not outrun the bytes after a power loss
        }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("cannot replace $file")
        }
    }

    companion object {
        /** The address in `filesDir/server-address`. */
        fun create(context: Context): SavedAddress = SavedAddress(File(context.filesDir, "server-address"))
    }
}
