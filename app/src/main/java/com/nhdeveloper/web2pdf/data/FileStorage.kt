package com.nhdeveloper.web2pdf.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.nhdeveloper.web2pdf.model.ConversionRecord
import com.nhdeveloper.web2pdf.model.FileStat
import com.nhdeveloper.web2pdf.utils.FileNameUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Saves, opens, renames and deletes generated PDFs.
 * Destinations: user-chosen folder (SAF) > Downloads/Web2PDF (MediaStore, Android 10+) > app documents folder (Android 8-9).
 * No storage permissions are required.
 */
class FileStorage(private val context: Context) {

    data class Saved(val uri: String, val filePath: String?, val name: String, val size: Long)

    private val resolver get() = context.contentResolver

    // ---------- Saving ----------

    suspend fun save(temp: File, displayName: String, treeUri: String?): Saved = withContext(Dispatchers.IO) {
        val name = FileNameUtils.ensurePdf(displayName)
        val tree = treeUri?.let { Uri.parse(it) }?.takeIf { hasWritePermission(it) }
        if (tree != null) {
            try {
                return@withContext saveToTree(temp, name, tree)
            } catch (e: Exception) {
                // Folder became unavailable; fall back to the default location.
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveToDownloads(temp, name)
        else saveToAppFolder(temp, name)
    }

    private fun hasWritePermission(tree: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }

    private fun saveToTree(temp: File, name: String, tree: Uri): Saved {
        val docId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
        val doc = DocumentsContract.createDocument(resolver, parent, "application/pdf", name.removeSuffix(".pdf"))
            ?: throw IOException("Could not create a file in the selected folder.")
        try {
            val out = resolver.openOutputStream(doc) ?: throw IOException("Could not write to the selected folder.")
            out.use { o -> temp.inputStream().use { it.copyTo(o) } }
        } catch (e: Exception) {
            try {
                DocumentsContract.deleteDocument(resolver, doc)
            } catch (ignored: Exception) {
            }
            throw e
        }
        return Saved(doc.toString(), null, queryName(doc) ?: name, temp.length())
    }

    private fun saveToDownloads(temp: File, name: String): Saved {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Web2PDF")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create a file in Downloads.")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Could not write to Downloads.")
            out.use { o -> temp.inputStream().use { it.copyTo(o) } }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (ignored: Exception) {
            }
            throw e
        }
        return Saved(uri.toString(), null, queryName(uri) ?: name, temp.length())
    }

    private fun saveToAppFolder(temp: File, name: String): Saved {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val dir = File(base, "Web2PDF").apply { mkdirs() }
        val target = uniqueFile(dir, name)
        temp.copyTo(target, overwrite = false)
        return Saved(Uri.fromFile(target).toString(), target.absolutePath, target.name, target.length())
    }

    private fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        if (!file.exists()) return file
        val base = name.removeSuffix(".pdf")
        var i = 1
        while (file.exists()) {
            file = File(dir, "$base ($i).pdf")
            i++
        }
        return file
    }

    private fun queryName(uri: Uri): String? =
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }

    // ---------- Folder permission ----------

    fun takeTree(uri: Uri): Boolean =
        try {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            true
        } catch (e: Exception) {
            false
        }

    fun releaseTree(uri: String) {
        try {
            resolver.releasePersistableUriPermission(
                Uri.parse(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (ignored: Exception) {
        }
    }

    fun treeAvailable(uri: String): Boolean = hasWritePermission(Uri.parse(uri))

    // ---------- Reading ----------

    fun openInput(r: ConversionRecord): InputStream? =
        try {
            if (r.filePath != null) {
                File(r.filePath).takeIf { it.exists() }?.inputStream()
            } else {
                resolver.openInputStream(Uri.parse(r.fileUri))
            }
        } catch (e: Exception) {
            null
        }

    suspend fun stat(r: ConversionRecord): FileStat = withContext(Dispatchers.IO) {
        if (r.filePath != null) {
            val f = File(r.filePath)
            FileStat(f.exists(), if (f.exists()) f.length() else 0L)
        } else {
            try {
                val afd = resolver.openAssetFileDescriptor(Uri.parse(r.fileUri), "r")
                if (afd == null) {
                    FileStat(false, 0L)
                } else {
                    val len = afd.length
                    afd.close()
                    FileStat(true, if (len >= 0) len else r.sizeBytes)
                }
            } catch (e: Exception) {
                FileStat(false, 0L)
            }
        }
    }

    /** Copies the stored PDF into app cache (sub-folder [folder]) and returns it, or null if it is missing. */
    suspend fun copyToCache(r: ConversionRecord, folder: String, cacheName: String): File? =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, folder).apply { mkdirs() }
            val dest = File(dir, FileNameUtils.ensurePdf(cacheName))
            val input = openInput(r) ?: return@withContext null
            try {
                input.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
                dest
            } catch (e: Exception) {
                null
            }
        }

    suspend fun exportTo(r: ConversionRecord, dest: Uri): Boolean = withContext(Dispatchers.IO) {
        val input = openInput(r) ?: return@withContext false
        try {
            val out = resolver.openOutputStream(dest, "w") ?: return@withContext false
            input.use { i -> out.use { o -> i.copyTo(o) } }
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---------- Rename / delete ----------

    suspend fun rename(r: ConversionRecord, newName: String): Saved = withContext(Dispatchers.IO) {
        val name = FileNameUtils.ensurePdf(newName)
        if (r.filePath != null) {
            val old = File(r.filePath)
            if (!old.exists()) throw IOException("The file no longer exists.")
            val dir = old.parentFile ?: throw IOException("Invalid file location.")
            val target = uniqueFile(dir, name)
            if (!old.renameTo(target)) throw IOException("Could not rename the file.")
            Saved(Uri.fromFile(target).toString(), target.absolutePath, target.name, target.length())
        } else {
            val uri = Uri.parse(r.fileUri)
            if (uri.authority == MediaStore.AUTHORITY) {
                val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }
                val n = resolver.update(uri, values, null, null)
                if (n <= 0) throw IOException("Could not rename the file.")
                Saved(r.fileUri, null, queryName(uri) ?: name, r.sizeBytes)
            } else {
                val newUri = DocumentsContract.renameDocument(resolver, uri, name)
                    ?: throw IOException("Could not rename the file.")
                Saved(newUri.toString(), null, queryName(newUri) ?: name, r.sizeBytes)
            }
        }
    }

    /** Returns true when the file is gone afterwards (including when it was already missing). */
    suspend fun delete(r: ConversionRecord): Boolean = withContext(Dispatchers.IO) {
        if (r.filePath != null) {
            val f = File(r.filePath)
            return@withContext !f.exists() || f.delete()
        }
        if (!stat(r).exists) return@withContext true
        val uri = Uri.parse(r.fileUri)
        try {
            if (uri.authority == MediaStore.AUTHORITY) resolver.delete(uri, null, null) > 0
            else DocumentsContract.deleteDocument(resolver, uri)
        } catch (e: Exception) {
            false
        }
    }

    // ---------- Cache ----------

    suspend fun cacheSize(): Long = withContext(Dispatchers.IO) {
        context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        val before = context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        listOf("share", "viewer", "pdf_tmp").forEach { File(context.cacheDir, it).deleteRecursively() }
        before
    }
}
