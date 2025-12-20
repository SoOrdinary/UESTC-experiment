package com.soordinary.transfer.view.folder

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.soordinary.transfer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import android.provider.OpenableColumns

/**
 * 重构后的弹窗：内部实现新建文件夹、复制文件逻辑
 * @param context 上下文
 * @param rootPath 应用根路径
 * @param currentPath 当前目录路径（相对路径）
 * @param refreshCallback 刷新文件列表的回调
 * @param filePickerLauncher 文件选择器Launcher（用于唤起文件选择）
 */
class CreateFolderDialog(
    private val context: Context,
    private val rootPath: String,
    private val currentPath: String,
    private val refreshCallback: () -> Unit,
    private val filePickerLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) : BottomSheetDialog(context) {

    private lateinit var llCreateFolder: LinearLayout
    private lateinit var llBackupFile: LinearLayout

    // 计算完整的目标路径（根路径+当前路径）
    private val targetFullPath: String
        get() = "$rootPath$currentPath"

    init {
        initView()
    }

    private fun initView() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_folder_create, null)
        setContentView(view)

        llCreateFolder = view.findViewById(R.id.ll_create_folder)
        llBackupFile = view.findViewById(R.id.ll_backup_file)

        // 左侧：新建文件夹（内部实现逻辑）
        llCreateFolder.setOnClickListener {
            createUniqueFolder()
            dismiss()
        }

        // 右侧：备份文件（内部唤起选择器+复制逻辑）
        llBackupFile.setOnClickListener {
            launchFilePicker()
            dismiss()
        }
    }

    /**
     * 内部实现：生成不重复的文件夹并创建
     */
    private fun createUniqueFolder() {
        val baseName = "新建文件夹"
        val targetDir = File(targetFullPath)
        var newFolderName = baseName
        var index = 1

        // 循环检查名称唯一性
        while (File(targetDir, newFolderName).exists()) {
            newFolderName = "$baseName($index)"
            index++
        }

        // 子线程创建文件夹
        GlobalScope.launch(Dispatchers.IO) {
            val folderToCreate = File(targetDir, newFolderName)
            val isCreated = folderToCreate.mkdirs()

            withContext(Dispatchers.Main) {
                if (isCreated) {
                    Log.d("FolderCreate", "创建成功：${folderToCreate.absolutePath}")
                    refreshCallback.invoke() // 刷新列表
                } else {
                    Log.e("FolderCreate", "创建失败：${folderToCreate.absolutePath}")
                    Toast.makeText(context, "文件夹创建失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 内部实现：唤起文件选择器
     */
    private fun launchFilePicker() {
        filePickerLauncher.launch(arrayOf("*/*"))
    }

    /**
     * 内部实现：复制文件到目标路径（对外暴露，供Fragment调用）
     */
    fun copyFileToTargetPath(sourceUri: Uri) {
        GlobalScope.launch(Dispatchers.IO) {
            val targetDir = File(targetFullPath)
            // 确保目标目录存在
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                withContext(Dispatchers.Main) {
                    Log.e("FileCopy", "目标目录创建失败：${targetDir.absolutePath}")
                    Toast.makeText(context, "目录创建失败", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // 获取文件名
            val fileName = getFileNameFromUri(sourceUri) ?: "backup_${System.currentTimeMillis()}.tmp"
            val targetFile = File(targetDir, fileName)

            var inputStream: InputStream? = null
            var outputStream: OutputStream? = null
            try {
                inputStream = context.contentResolver.openInputStream(sourceUri)
                    ?: throw IllegalStateException("无法打开文件输入流")

                outputStream = FileOutputStream(targetFile)
                val buffer = ByteArray(4096)
                var bytesRead: Int

                while (true) {
                    val readResult = inputStream.read(buffer)
                    if (readResult == null || readResult == -1) break
                    bytesRead = readResult
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()

                withContext(Dispatchers.Main) {
                    Log.d("FileCopy", "复制成功：${targetFile.absolutePath}")
                    refreshCallback.invoke() // 刷新列表
                }
            } catch (e: Exception) {
                if (targetFile.exists()) targetFile.delete()
                withContext(Dispatchers.Main) {
                    Log.e("FileCopy", "复制失败：${e.message}", e)
                    Toast.makeText(context, "文件复制失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                try { inputStream?.close() } catch (e: Exception) { Log.w("FileCopy", "关闭输入流失败", e) }
                try { outputStream?.close() } catch (e: Exception) { Log.w("FileCopy", "关闭输出流失败", e) }
            }
        }
    }

    /**
     * 内部工具方法：从Uri解析文件名
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = it.getString(nameIndex)
                }
            }
        }
        return fileName
    }
}