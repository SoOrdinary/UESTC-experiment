package com.soordinary.transfer.view.folder

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.soordinary.transfer.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class EditDialog(
    private val context: Context,
    private val filePath: String,
    private val refreshCallback: () -> Unit
) : Dialog(context) {

    // 文件对象
    private val targetFile = File(filePath)
    // 日期格式化工具
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    init {
        initDialog()
    }

    private fun initDialog() {
        // 加载布局
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_file_edit, null)
        setContentView(view)

        // 设置弹窗样式（宽高、背景等）
        window?.apply {
            setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(android.R.color.white)
        }

        // 初始化控件
        val tvFileInfo = view.findViewById<TextView>(R.id.tv_file_info)
        val etNewName = view.findViewById<EditText>(R.id.et_new_name)
        val btnRename = view.findViewById<Button>(R.id.btn_rename)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)

        // 展示文件基本信息
        showFileInfo(tvFileInfo)

        // 重命名按钮点击事件
        btnRename.setOnClickListener {
            if (etNewName.visibility == View.GONE) {
                // 第一次点击：显示输入框，填充原名称
                etNewName.visibility = View.VISIBLE
                etNewName.setText(targetFile.name)
                etNewName.setSelection(targetFile.name.length) // 选中文字方便编辑
                btnRename.text = "确认修改"
            } else {
                // 第二次点击：执行重命名
                val newName = etNewName.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(context, "名称不能为空", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (newName == targetFile.name) {
                    Toast.makeText(context, "名称未修改", Toast.LENGTH_SHORT).show()
                    dismiss()
                    return@setOnClickListener
                }

                // 执行重命名
                renameFile(newName)
                // 视图重构
                refreshCallback()
            }
        }

        // 取消按钮点击事件
        btnCancel.setOnClickListener {
            dismiss() // 关闭弹窗
        }
    }

    /**
     * 展示文件基本信息
     */
    private fun showFileInfo(textView: TextView) {
        if (!targetFile.exists()) {
            textView.text = "文件/文件夹不存在"
            return
        }

        // 拼接文件信息
        val info = buildString {
            append("名称：${targetFile.name}\n")
            append("类型：${if (targetFile.isDirectory) "文件夹" else "文件"}\n")
            append("路径：${targetFile.absolutePath}\n")
            append("大小：${formatFileSize(targetFile.length())}\n")
            append("修改时间：${dateFormat.format(Date(targetFile.lastModified()))}\n")
        }
        textView.text = info
    }

    /**
     * 格式化文件大小（B -> KB -> MB -> GB）
     */
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024))
            else -> String.format("%.2f GB", size / (1024.0 * 1024 * 1024))
        }
    }

    /**
     * 执行文件重命名
     */
    private fun renameFile(newName: String) {
        // 构建新文件路径
        val newFile = File(targetFile.parentFile, newName)

        // 检查新名称是否已存在
        if (newFile.exists()) {
            Toast.makeText(context, "该名称已存在", Toast.LENGTH_SHORT).show()
            return
        }

        // 执行重命名操作
        val isSuccess = targetFile.renameTo(newFile)
        if (isSuccess) {
            Toast.makeText(context, "重命名成功", Toast.LENGTH_SHORT).show()
            dismiss() // 关闭弹窗
            // 这里可以添加回调，通知列表刷新数据
        } else {
            Toast.makeText(context, "重命名失败，请检查权限", Toast.LENGTH_SHORT).show()
        }
    }
}