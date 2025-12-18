package com.soordinary.transfer.view.folder

import android.os.Build
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * 适配BackupFileEntity的文件列表加载器
 * 核心：读取指定路径文件，生成对应实体列表，支持上级目录项
 */
object FolderTreeLoader {

    /**
     * 加载指定路径的文件列表
     * @param currentPath 当前目录绝对路径
     * @return 包含上级目录项的FileEntity列表
     */
    fun loadFolderList(currentPath: String): MutableList<FileEntity> {
        val fileList = mutableListOf<FileEntity>()
        val currentFile = File(currentPath)

        // 1. 添加上级目录项（根目录除外）
        if (!isRootPath(currentPath)) {
            val parentPath = currentFile.parent ?: "/"
            fileList.add(
                FileEntity(
                    type = FileEntity.FileType.PARENTDIRECTORY,
                    name = "..",
                    parentPath = currentPath, // 上级项的父路径指向当前目录
                    size = 0,
                    lastModified = 0
                )
            )
        }

        // 2. 读取当前目录下的文件/文件夹，生成对应实体
        currentFile.listFiles()?.forEach { file ->
            val fileType = getFileType(file)
            val extension = if (fileType == FileEntity.FileType.NORMAL) {
                getFileExtension(file.name)
            } else {
                ""
            }

            fileList.add(
                FileEntity(
                    type = fileType,
                    extension = extension,
                    name = file.name,
                    parentPath = currentPath,
                    size = if (file.isFile) file.length() else 0,
                    lastModified = file.lastModified()
                )
            )
        } ?: run {
            // 路径不存在/无权限时返回空列表
            return mutableListOf()
        }

        // 3. 排序：文件夹/上级项在前，文件在后，按名称排序
        fileList.sortWith(
            compareBy(
                { it.type !in listOf(FileEntity.FileType.PARENTDIRECTORY, FileEntity.FileType.DIRECTORY) },
                { it.name }
            )
        )
        return fileList
    }

    /**
     * 判断是否为根目录（避免无限返回上级）
     */
    private fun isRootPath(path: String): Boolean {
        return path == "/" || path == File.separator || path.endsWith(File.separator + "root")
    }

    /**
     * 根据File对象判断文件类型（兼容所有Android版本）
     */
    private fun getFileType(file: File): FileEntity.FileType {
        return when {
            file.isDirectory -> FileEntity.FileType.DIRECTORY
            isSymbolicLinkCompat(file) -> FileEntity.FileType.SYMBOLIC_LINK
            // 硬链接/管道/设备文件等可根据实际需求补充判断逻辑
            else -> FileEntity.FileType.NORMAL
        }
    }

    /**
     * 兼容所有版本的软链接判断（替代Files.isSymbolicLink）
     */
    private fun isSymbolicLinkCompat(file: File): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                java.nio.file.Files.isSymbolicLink(file.toPath())
            } else {
                // 低版本用Os.lstat判断
                val stat = Os.lstat(file.absolutePath)
                (stat.st_mode and OsConstants.S_IFMT) == OsConstants.S_IFLNK
            }
        } catch (e: Exception) {
            false // 异常则判定为非软链接
        }
    }

    /**
     * 获取文件扩展名（如"txt"、"jpg"）
     */
    private fun getFileExtension(fileName: String): String {
        val lastDotIndex = fileName.lastIndexOf(".")
        return if (lastDotIndex > 0 && lastDotIndex < fileName.length - 1) {
            fileName.substring(lastDotIndex + 1).lowercase()
        } else {
            ""
        }
    }
}