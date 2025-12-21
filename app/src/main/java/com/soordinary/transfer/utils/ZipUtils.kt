package com.soordinary.transfer.utils

import android.os.Build
import java.io.*
import java.nio.charset.Charset
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * ZIP工具类（纯原生实现，兼容所有Android版本）
 * 支持：STORE(仅打包) / DEFLATE(压缩)，支持文件+文件夹（递归），支持重复文件多次添加
 */
object ZipUtils {

    /**
     * ZIP压缩/打包方法（支持文件+文件夹，支持重复文件多次添加）
     * @param srcPaths 待处理路径列表（文件/文件夹路径均可）
     * @param destZipPath 目标ZIP文件路径
     * @param isCompress 是否开启压缩（true=DEFLATE压缩，false=STORE仅打包）
     * @param compressionLevel 压缩级别（1-9，仅DEFLATE模式生效，6为默认）
     * @return 操作结果（成功/失败+提示信息）
     */
    fun zipFiles(
        srcPaths: List<String>,
        destZipPath: String,
        isCompress: Boolean = true,
        compressionLevel: Int = 6
    ): Pair<Boolean, String> {
        // 校验输入参数
        if (srcPaths.isEmpty()) {
            return Pair(false, "待处理路径列表为空")
        }

        // 收集所有待处理的文件条目（保留重复项，记录原始路径）
        val fileEntries = mutableListOf<FileEntry>()
        srcPaths.forEachIndexed { pathIndex, path ->
            val file = File(path)
            if (!file.exists()) return@forEachIndexed

            if (file.isFile && file.canRead()) {
                // 是文件且可读，直接添加为条目
                fileEntries.add(FileEntry(file, pathIndex, path))
            } else if (file.isDirectory) {
                // 是文件夹，递归遍历所有文件（保留原始文件夹路径）
                collectFilesFromDir(file, pathIndex, path, fileEntries)
            }
        }

        if (fileEntries.isEmpty()) {
            return Pair(false, "无有效文件可处理（文件不存在/不可读）")
        }

        var zipOut: ZipOutputStream? = null
        try {
            // 创建目标ZIP文件的父目录
            val destFile = File(destZipPath)
            val parentDir = destFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs()
            }

            // 初始化ZIP输出流（处理Android N+编码问题）
            val fos = FileOutputStream(destFile)
            val bos = BufferedOutputStream(fos)
            zipOut = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ZipOutputStream(bos, Charset.forName("UTF-8"))
            } else {
                @Suppress("DEPRECATION")
                ZipOutputStream(bos)
            }

            // 记录已使用的ZIP条目名，用于重复命名
            val usedEntryNames = mutableMapOf<String, Int>()

            // 遍历所有文件条目（保留重复项）
            fileEntries.forEach { entry ->
                val file = entry.file
                val rootPath = entry.originalPath
                // 获取基础相对路径
                val baseRelativePath = getRelativePath(file, listOf(File(rootPath).absolutePath))
                // 处理重复条目：添加序号后缀
                val finalEntryName = getUniqueEntryName(baseRelativePath, usedEntryNames)
                // 添加到ZIP
                addFileToZip(file, zipOut, finalEntryName, isCompress, compressionLevel)
            }

            return Pair(true, "成功${if (isCompress) "压缩" else "打包"} ${fileEntries.size} 个文件条目到：$destZipPath")
        } catch (e: Exception) {
            e.printStackTrace()
            return Pair(false, "${if (isCompress) "压缩" else "打包"}失败：${e.message ?: "未知错误"}")
        } finally {
            try {
                zipOut?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 文件条目数据类（记录文件、原始路径索引、原始路径，保留重复项）
     */
    private data class FileEntry(
        val file: File,
        val pathIndex: Int,
        val originalPath: String
    )

    /**
     * 递归收集文件夹下所有文件（保留原始文件夹路径）
     */
    private fun collectFilesFromDir(
        dir: File,
        pathIndex: Int,
        originalDirPath: String,
        fileEntries: MutableList<FileEntry>
    ) {
        val files = dir.listFiles() ?: return
        files.forEach { file ->
            if (file.isFile && file.canRead()) {
                fileEntries.add(FileEntry(file, pathIndex, originalDirPath))
            } else if (file.isDirectory) {
                // 递归遍历子文件夹（保留原始根文件夹路径）
                collectFilesFromDir(file, pathIndex, originalDirPath, fileEntries)
            }
        }
    }

    /**
     * 获取文件相对于源路径的相对路径（保持文件夹层级）
     */
    private fun getRelativePath(file: File, rootPaths: List<String>): String {
        val fileAbsPath = file.absolutePath
        // 找到匹配的根路径
        val matchedRoot = rootPaths.firstOrNull { rootPath ->
            fileAbsPath.startsWith(rootPath)
        } ?: return file.name

        // 计算相对路径
        return if (File(matchedRoot).isDirectory) {
            // 根路径是文件夹，保留层级结构
            fileAbsPath.substring(matchedRoot.length).let {
                if (it.startsWith(File.separator)) it.substring(1) else it
            }
        } else {
            // 根路径是文件，直接用文件名
            file.name
        }
    }

    /**
     * 生成唯一的ZIP条目名（重复时添加序号后缀）
     * 示例：test.jpg → test(1).jpg → test(2).jpg
     */
    private fun getUniqueEntryName(
        baseName: String,
        usedNames: MutableMap<String, Int>
    ): String {
        // 拆分文件名和扩展名
        val dotIndex = baseName.lastIndexOf('.')
        val nameWithoutExt = if (dotIndex > 0) baseName.substring(0, dotIndex) else baseName
        val ext = if (dotIndex > 0) baseName.substring(dotIndex) else ""

        // 检查是否已使用
        return if (!usedNames.containsKey(baseName)) {
            usedNames[baseName] = 1
            baseName
        } else {
            // 生成带序号的名称
            var count = usedNames[baseName] ?: 1
            var newName: String
            do {
                newName = "$nameWithoutExt($count)$ext"
                count++
            } while (usedNames.containsKey(newName))
            usedNames[baseName] = count
            usedNames[newName] = 1
            newName
        }
    }

    /**
     * 将单个文件添加到ZIP流中（支持相对路径，保持文件夹层级）
     */
    private fun addFileToZip(
        file: File,
        zipOut: ZipOutputStream,
        entryRelativePath: String,
        isCompress: Boolean,
        compressionLevel: Int
    ) {
        // 创建ZIP条目（使用唯一名称，避免重复）
        val zipEntry = ZipEntry(entryRelativePath).apply {
            // 设置文件属性
            time = file.lastModified()
            // 标记压缩模式：STORE(仅打包) / DEFLATED(压缩)
            method = if (isCompress) ZipEntry.DEFLATED else ZipEntry.STORED

            // STORE模式必须手动设置size和crc（否则ZIP文件损坏）
            if (method == ZipEntry.STORED) {
                size = file.length()
                crc = calculateFileCRC32(file)
            }
        }

        // 写入文件条目
        zipOut.putNextEntry(zipEntry)

        // 读取文件内容并写入ZIP（根据模式选择是否压缩）
        val buffer = ByteArray(8192) // 8KB缓冲区
        FileInputStream(file).use { fis ->
            BufferedInputStream(fis).use { bis ->
                if (isCompress) {
                    // 压缩模式：通过Deflater设置压缩级别
                    val deflater = Deflater(
                        when {
                            compressionLevel < 1 -> 1
                            compressionLevel > 9 -> 9
                            else -> compressionLevel
                        }, true
                    )
                    DeflaterOutputStream(zipOut, deflater).use { dos ->
                        var bytesRead: Int
                        while (bis.read(buffer).also { bytesRead = it } != -1) {
                            dos.write(buffer, 0, bytesRead)
                        }
                    }
                } else {
                    // 仅打包模式：直接写入原始字节
                    var bytesRead: Int
                    while (bis.read(buffer).also { bytesRead = it } != -1) {
                        zipOut.write(buffer, 0, bytesRead)
                    }
                }
            }
        }

        // 关闭当前条目
        zipOut.closeEntry()
    }

    /**
     * 计算文件的CRC32校验值（STORE模式必须）
     */
    private fun calculateFileCRC32(file: File): Long {
        val crc32 = CRC32()
        val buffer = ByteArray(8192)
        FileInputStream(file).use { fis ->
            BufferedInputStream(fis).use { bis ->
                var bytesRead: Int
                while (bis.read(buffer).also { bytesRead = it } != -1) {
                    crc32.update(buffer, 0, bytesRead)
                }
            }
        }
        return crc32.value
    }

    /**
     * 校验路径是否有效（文件/文件夹）
     */
    fun isPathValid(path: String): Boolean {
        val file = File(path)
        return file.exists() && (file.isFile || file.isDirectory) && file.canRead()
    }

    /**
     * 生成自动编号的ZIP文件路径（避免重复）
     */
    fun generateAutoRenamePath(
        parentPath: String,
        baseFileName: String,
        extension: String = "zip"
    ): String {
        var index = 0
        var targetPath: String
        do {
            val fileName = if (index == 0) {
                "$baseFileName.$extension"
            } else {
                "$baseFileName($index).$extension"
            }
            targetPath = File(parentPath, fileName).absolutePath
            index++
        } while (File(targetPath).exists())
        return targetPath
    }

    /**
     * 读取文件字节数组（用于AES加密）
     */
    fun readFileToBytes(filePath: String): ByteArray? {
        return try {
            val file = File(filePath)
            val fis = FileInputStream(file)
            val bos = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                bos.write(buffer, 0, bytesRead)
            }
            bos.close()
            fis.close()
            bos.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 将字节数组写入文件（用于AES解密/加密后保存）
     */
    fun writeBytesToFile(bytes: ByteArray, filePath: String): Boolean {
        return try {
            val file = File(filePath)
            val fos = FileOutputStream(file)
            fos.write(bytes)
            fos.flush()
            fos.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}