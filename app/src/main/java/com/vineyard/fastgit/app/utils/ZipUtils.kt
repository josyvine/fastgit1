package com.vineyard.fastgit.app.utils

import android.util.Base64
import java.io.*
import java.util.zip.ZipEntry
import java.io.InputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipUtils {

    /**
     * Zips a single file or directory into a ZipOutputStream.
     */
    fun zipDirectory(sourceDir: File, zipFile: File) {
        val fos = FileOutputStream(zipFile)
        val zos = ZipOutputStream(BufferedOutputStream(fos))
        zipSubFolder(zos, sourceDir, sourceDir.path.length + 1)
        zos.close()
        fos.close()
    }

    private fun zipSubFolder(zos: ZipOutputStream, folder: File, inputPathLength: Int) {
        val files = folder.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                zipSubFolder(zos, file, inputPathLength)
            } else {
                val filePath = file.path
                val entryName = filePath.substring(inputPathLength)
                val ze = ZipEntry(entryName)
                zos.putNextEntry(ze)
                val fis = FileInputStream(file)
                val buffer = ByteArray(8192)
                var count: Int
                while (fis.read(buffer).also { count = it } != -1) {
                    zos.write(buffer, 0, count)
                }
                fis.close()
                zos.closeEntry()
            }
        }
    }

    /**
     * Unzips a ZipInputStream into a destination directory.
     */
    fun unzip(inputStream: InputStream, targetDir: File): List<File> {
        val extractedFiles = mutableListOf<File>()
        val zis = ZipInputStream(BufferedInputStream(inputStream))
        var ze: ZipEntry?
        val buffer = ByteArray(8192)

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        while (zis.nextEntry.also { ze = it } != null) {
            val entry = ze ?: break
            val file = File(targetDir, entry.name)

            // Prevent zip slip vulnerability
            if (!file.canonicalPath.startsWith(targetDir.canonicalPath)) {
                throw SecurityException("Zip entry is outside target dir: ${entry.name}")
            }

            if (entry.isDirectory) {
                file.mkdirs()
            } else {
                file.parentFile?.mkdirs()
                val fos = FileOutputStream(file)
                var count: Int
                while (zis.read(buffer).also { count = it } != -1) {
                    fos.write(buffer, 0, count)
                }
                fos.close()
                extractedFiles.add(file)
            }
            zis.closeEntry()
        }
        zis.close()
        return extractedFiles
    }

    data class ExtractedFileInfo(
        val relativePath: String,
        val contentBase64: String,
        val isText: Boolean
    )

    /**
     * Scans all files in a directory and produces relative paths + Base64 content.
     * Unwraps any single top-level wrapper directory (e.g. repo-main/ or root/) to place files directly at root.
     */
    fun scanDirectory(baseDir: File): List<ExtractedFileInfo> {
        val result = mutableListOf<ExtractedFileInfo>()
        
        // Check if there is a single top-level folder wrapper
        var effectiveDir = baseDir
        var topEntries = baseDir.listFiles()?.filter { 
            it.name != ".git" && it.name != ".gradle" && it.name != "build" && it.name != "__MACOSX" && !it.name.startsWith("._") && it.name != ".DS_Store"
        } ?: emptyList()

        while (topEntries.size == 1 && topEntries[0].isDirectory) {
            effectiveDir = topEntries[0]
            topEntries = effectiveDir.listFiles()?.filter { 
                it.name != ".git" && it.name != ".gradle" && it.name != "build" && it.name != "__MACOSX" && !it.name.startsWith("._") && it.name != ".DS_Store"
            } ?: emptyList()
        }

        val basePath = effectiveDir.canonicalPath

        fun walk(dir: File) {
            val list = dir.listFiles() ?: return
            for (f in list) {
                if (f.isDirectory) {
                    // Skip hidden directories like .git, .gradle, build, __MACOSX
                    if (f.name == ".git" || f.name == ".gradle" || f.name == "build" || f.name == "__MACOSX") continue
                    walk(f)
                } else if (f.isFile) {
                    if (f.name.startsWith("._")) continue // macOS metadata file
                    val relPath = f.canonicalPath.substring(basePath.length + 1).replace('\\', '/')
                    val bytes = f.readBytes()
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    result.add(ExtractedFileInfo(relPath, b64, true))
                }
            }
        }

        walk(effectiveDir)
        return result
    }
}
