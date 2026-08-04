package com.vineyard.fastgit.app.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.vineyard.fastgit.app.models.FileItem
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DownloadUtils {

    fun downloadSingleFileToCache(context: Context, fileName: String, content: String): File {
        val file = File(context.cacheDir, fileName)
        file.writeText(content)
        return file
    }

    /**
     * Saves a text-based payload (such as source code or build logs) cleanly 
     * inside the public device storage (Environment.DIRECTORY_DOWNLOADS) 
     * using MediaStore on Android 10+ (API 29+) or File API on older versions.
     */
    fun saveTextToDownloads(context: Context, subFolder: String, fileName: String, content: String): File? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = if (subFolder.isEmpty()) "Download/FastGit" else "Download/FastGit/$subFolder"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            val contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val uri = context.contentResolver.insert(contentUri, contentValues)
            if (uri != null) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(content.toByteArray(Charsets.UTF_8))
                    }
                    // Return a representative File reference for state verification
                    return File("/storage/emulated/0/$relativePath", fileName)
                } catch (e: Exception) {
                    // Clean up partially inserted entry on failure
                    context.contentResolver.delete(uri, null, null)
                    throw IOException("Failed to write to MediaStore: ${e.message}", e)
                }
            } else {
                throw IOException("Failed to create MediaStore entry for $fileName")
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val parentDir = if (subFolder.isEmpty()) {
                File(downloadsDir, "FastGit")
            } else {
                File(downloadsDir, "FastGit/$subFolder")
            }
            
            if (!parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    throw IOException("Failed to create directory structure: ${parentDir.absolutePath}")
                }
            }
            
            val targetFile = File(parentDir, fileName)
            targetFile.writeText(content)
            return targetFile
        }
    }

    /**
     * Saves a binary payload (such as a ZIP archive) cleanly 
     * inside the public device storage (Environment.DIRECTORY_DOWNLOADS) 
     * using MediaStore on Android 10+ (API 29+) or File API on older versions.
     */
    fun saveBinaryToDownloads(context: Context, subFolder: String, fileName: String, bytes: ByteArray): File? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = if (subFolder.isEmpty()) "Download/FastGit" else "Download/FastGit/$subFolder"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            val contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val uri = context.contentResolver.insert(contentUri, contentValues)
            if (uri != null) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(bytes)
                    }
                    // Return a representative File reference for state verification
                    return File("/storage/emulated/0/$relativePath", fileName)
                } catch (e: Exception) {
                    // Clean up partially inserted entry on failure
                    context.contentResolver.delete(uri, null, null)
                    throw IOException("Failed to write binary to MediaStore: ${e.message}", e)
                }
            } else {
                throw IOException("Failed to create MediaStore entry for $fileName")
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val parentDir = if (subFolder.isEmpty()) {
                File(downloadsDir, "FastGit")
            } else {
                File(downloadsDir, "FastGit/$subFolder")
            }
            
            if (!parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    throw IOException("Failed to create directory structure: ${parentDir.absolutePath}")
                }
            }
            
            val targetFile = File(parentDir, fileName)
            targetFile.writeBytes(bytes)
            return targetFile
        }
    }

    /**
     * Downloads a FileItem directory structure as a local ZIP file.
     */
    fun createZipFromFolderItems(
        context: Context,
        folderName: String,
        items: List<FileItem>
    ): File {
        val zipFile = File(context.cacheDir, "$folderName.zip")
        val fos = FileOutputStream(zipFile)
        val zos = ZipOutputStream(fos)

        fun zipItem(item: FileItem, prefix: String) {
            val entryPath = if (prefix.isEmpty()) item.name else "$prefix/${item.name}"
            if (item.type == "dir") {
                item.children.forEach { child ->
                    zipItem(child, entryPath)
                }
            } else {
                val ze = ZipEntry(entryPath)
                zos.putNextEntry(ze)
                val bytes = item.byteContent ?: item.content?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
                zos.write(bytes)
                zos.closeEntry()
            }
        }

        items.forEach { item ->
            zipItem(item, "")
        }

        zos.close()
        fos.close()
        return zipFile
    }
}