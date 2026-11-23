package dev.pr0gramm.tv.domain.selfupdate

import java.io.File
import java.io.FileOutputStream

/**
 * The versionCode of the own update whose install failed last, kept in [file] across process restarts (review item
 * 15). The file holds the number as text; no file, an empty one or other text is "none". Writes go to a temp file
 * and a rename, so a power loss leaves the old value or the new one. A failed read or write is no error: the
 * update then only loses the mark.
 */
internal class FailedUpdateFile(private val file: File) {
    fun read(): Long? = runCatching { file.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull() }.getOrNull()

    fun write(versionCode: Long?) {
        runCatching {
            if (versionCode == null) {
                file.delete()
                return
            }
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            FileOutputStream(tmp).use { out ->
                out.write(versionCode.toString().toByteArray())
                out.fd.sync()
            }
            check(tmp.renameTo(file)) { "rename failed" }
        }
    }
}
