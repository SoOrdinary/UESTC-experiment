package com.soordinary.transfer.view.folder

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import com.soordinary.transfer.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditDialog(
    private val context: Context,
    private val filePath: String,
    private val refreshCallback: () -> Unit
) : Dialog(context) {

    // 文件对象
    private val targetFile = File(filePath)
    // 日期格式化工具
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    // 样式常量：保留你当前的配置
    private val LABEL_COLOR = Color.parseColor("#666666")
    private val VALUE_COLOR = Color.parseColor("#2196F3")
    private val LABEL_STYLE = StyleSpan(Typeface.BOLD)
    // 记录当前选中的操作类型
    private var currentOperation: OperationType = OperationType.NONE
    // 保存原文件的后缀（用于自动拼接）
    private val originalFileExtension: String by lazy { getFileExtension(targetFile.name) }
    // 保存原文件的纯前缀（无后缀，用于输入框显示）
    private val originalFileNameOnly: String by lazy { getFileNameWithoutExtension(targetFile.name) }
    // 原文件所在的文件夹路径（用于移动功能默认显示）
    private val originalFolderPath: String by lazy {
        targetFile.parentFile?.absolutePath ?: ""
    }

    // 操作类型枚举（包含删除）
    enum class OperationType {
        NONE, RENAME, RESTORE, MIGRATE, DELETE
    }

    init {
        initDialog()
    }

    private fun initDialog() {
        // 加载布局
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_file_edit, null)
        setContentView(view)

        // 初始化信息区域控件
        val tvName = view.findViewById<TextView>(R.id.tv_name)
        val tvType = view.findViewById<TextView>(R.id.tv_type)
        val tvPath = view.findViewById<TextView>(R.id.tv_path)
        val tvSize = view.findViewById<TextView>(R.id.tv_size)
        val tvModifyTime = view.findViewById<TextView>(R.id.tv_modify_time)

        // 初始化输入框和按钮
        val etCommonInput = view.findViewById<EditText>(R.id.et_common_input)
        val btnOperationSelect = view.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_operation_select)
        val btnConfirm = view.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_confirm)

        // 展示文件基本信息
        showFileInfo(tvName, tvType, tvPath, tvSize, tvModifyTime)

        // ========== 下拉选择按钮点击事件 ==========
        btnOperationSelect.setOnClickListener {
            val popupMenu = PopupMenu(context, it)
            // 添加菜单选项
            popupMenu.menu.add(0, 1, 0, "重命名")
            popupMenu.menu.add(0, 2, 1, "移动")
            popupMenu.menu.add(0, 3, 2, "中转")
            popupMenu.menu.add(0, 4, 3, "删除")

            val menu = popupMenu.menu
            for (i in 0 until menu.size()) {
                val menuItem = menu.getItem(i)
                val title = menuItem.title.toString()
                menuItem.title = title
            }

            // 菜单选项点击事件
            popupMenu.setOnMenuItemClickListener { menuItem ->
                // 每次切换操作前先重置输入框默认样式
                resetEditTextStyle(etCommonInput)

                when (menuItem.itemId) {
                    1 -> { // 重命名
                        currentOperation = OperationType.RENAME
                        etCommonInput.hint = "请输入修改文件名"
                        etCommonInput.setText(originalFileNameOnly)
                        etCommonInput.setSelection(originalFileNameOnly.length)
                        etCommonInput.visibility = View.VISIBLE
                        btnConfirm.visibility = View.VISIBLE
                    }
                    2 -> { // 移动 - 核心修改：默认显示文件夹路径（去掉文件名）
                        currentOperation = OperationType.RESTORE
                        etCommonInput.hint = "请输入移动到的文件夹路径"
                        // 填充原文件所在的文件夹路径，而非完整文件路径
                        etCommonInput.setText(originalFolderPath)
                        // 光标定位到文件夹路径末尾，方便用户直接修改
                        etCommonInput.setSelection(originalFolderPath.length)
                        etCommonInput.visibility = View.VISIBLE
                        btnConfirm.visibility = View.VISIBLE
                    }
                    3 -> { // 中转
                        currentOperation = OperationType.MIGRATE
                        etCommonInput.hint = "请选择中转计划"
                        etCommonInput.setText("")
                        etCommonInput.visibility = View.VISIBLE
                        btnConfirm.visibility = View.VISIBLE
                    }
                    4 -> { // 删除
                        currentOperation = OperationType.DELETE
                        setupDeleteEditTextStyle(etCommonInput)
                        etCommonInput.setText("确认删除文件？")
                        etCommonInput.visibility = View.VISIBLE
                        btnConfirm.visibility = View.VISIBLE
                    }
                }
                true
            }

            // 显示下拉菜单并调整位置
            popupMenu.show()
        }

        // ========== 确认按钮点击事件 ==========
        btnConfirm.setOnClickListener {
            val inputContent = etCommonInput.text.toString().trim()
            when (currentOperation) {
                OperationType.RENAME -> {
                    if (inputContent.isEmpty()) {
                        Toast.makeText(context, "文件名不能为空", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val finalFileName = if (originalFileExtension.isNotEmpty()) {
                        "$inputContent.$originalFileExtension"
                    } else {
                        inputContent
                    }

                    if (finalFileName == targetFile.name) {
                        Toast.makeText(context, "文件名未修改", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    renameFile(finalFileName)
                }
                OperationType.RESTORE -> { // 移动功能
                    if (inputContent.isEmpty()) {
                        Toast.makeText(context, "移动路径不能为空", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val targetDir = File(inputContent)
                    if (!targetDir.exists()) {
                        Toast.makeText(context, "目标路径不存在", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (!targetDir.isDirectory) {
                        Toast.makeText(context, "目标路径必须是文件夹", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    moveFile(targetDir)
                }
                OperationType.MIGRATE -> {
                    if (inputContent.isEmpty()) {
                        Toast.makeText(context, "中转目标路径不能为空", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    Toast.makeText(context, "确认中转：$inputContent", Toast.LENGTH_SHORT).show()
                }
                OperationType.DELETE -> {
                    deleteFile()
                }
                else -> resetState(etCommonInput, btnConfirm)
            }
        }
    }


    private fun dp2px(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    private fun resetState(editText: EditText, confirmBtn: androidx.appcompat.widget.AppCompatButton) {
        editText.visibility = View.GONE
        confirmBtn.visibility = View.GONE
        currentOperation = OperationType.NONE
        editText.setText("")
        resetEditTextStyle(editText)
    }

    private fun resetEditTextStyle(editText: EditText) {
        editText.isEnabled = true
        editText.setTextColor(Color.BLACK)
        editText.background = editText.context.resources.getDrawable(android.R.drawable.edit_text, null)
    }

    private fun setupDeleteEditTextStyle(editText: EditText) {
        editText.isEnabled = false
        editText.setTextColor(Color.RED)
        editText.background = ColorDrawable(Color.TRANSPARENT)
    }

    private fun showFileInfo(
        tvName: TextView,
        tvType: TextView,
        tvPath: TextView,
        tvSize: TextView,
        tvModifyTime: TextView
    ) {
        if (!targetFile.exists()) {
            tvName.text = "文件/文件夹不存在"
            tvType.visibility = View.GONE
            tvPath.visibility = View.GONE
            tvSize.visibility = View.GONE
            tvModifyTime.visibility = View.GONE
            return
        }

        val fileNameWithoutExt = getFileNameWithoutExtension(targetFile.name)
        val fileType = if (targetFile.isDirectory) "文件夹" else getFileExtension(targetFile.name).uppercase()
        val filePath = targetFile.absolutePath
        val fileSize = formatFileSize(targetFile.length())
        val modifyTime = dateFormat.format(Date(targetFile.lastModified()))

        tvName.text = createSpannableText("名称：", fileNameWithoutExt)
        tvType.text = createSpannableText("类型：", fileType)
        tvPath.text = createSpannableText("路径：", filePath)
        tvSize.text = createSpannableText("大小：", fileSize)
        tvModifyTime.text = createSpannableText("修改时间：", modifyTime)

        tvType.visibility = View.VISIBLE
        tvPath.visibility = View.VISIBLE
        tvSize.visibility = View.VISIBLE
        tvModifyTime.visibility = View.VISIBLE
    }

    private fun createSpannableText(label: String, value: String): SpannableString {
        val spannable = SpannableString(label + value)
        val labelEnd = label.length
        spannable.setSpan(LABEL_STYLE, 0, labelEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(ForegroundColorSpan(LABEL_COLOR), 0, labelEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(ForegroundColorSpan(VALUE_COLOR), labelEnd, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannable
    }

    private fun getFileNameWithoutExtension(fileName: String): String {
        val lastDotIndex = fileName.lastIndexOf(".")
        return if (lastDotIndex > 0 && lastDotIndex < fileName.length - 1) {
            fileName.substring(0, lastDotIndex)
        } else {
            fileName
        }
    }

    private fun getFileExtension(fileName: String): String {
        val lastDotIndex = fileName.lastIndexOf(".")
        return if (lastDotIndex > 0 && lastDotIndex < fileName.length - 1) {
            fileName.substring(lastDotIndex + 1).lowercase()
        } else {
            ""
        }
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024))
            else -> String.format("%.2f GB", size / (1024.0 * 1024 * 1024))
        }
    }

    private fun renameFile(newName: String) {
        val newFile = File(targetFile.parentFile, newName)
        if (newFile.exists()) {
            Toast.makeText(context, "该文件名已存在", Toast.LENGTH_SHORT).show()
            return
        }
        val isSuccess = targetFile.renameTo(newFile)
        if (isSuccess) {
            Toast.makeText(context, "重命名成功", Toast.LENGTH_SHORT).show()
            refreshCallback()
            dismiss()
        } else {
            Toast.makeText(context, "重命名失败，请检查权限", Toast.LENGTH_SHORT).show()
        }
    }

    private fun moveFile(targetDir: File) {
        val targetFile = File(targetDir, this.targetFile.name)
        if (targetFile.exists()) {
            Toast.makeText(context, "目标位置已存在同名文件", Toast.LENGTH_SHORT).show()
            return
        }

        val isSuccess = this.targetFile.renameTo(targetFile)
        if (isSuccess) {
            Toast.makeText(context, "文件移动成功", Toast.LENGTH_SHORT).show()
            refreshCallback()
            dismiss()
        } else {
            Toast.makeText(context, "文件移动失败，请检查权限", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteFile() {
        fun deleteRecursively(file: File): Boolean {
            if (file.isDirectory) {
                file.listFiles()?.forEach { child ->
                    if (!deleteRecursively(child)) {
                        return false
                    }
                }
            }
            return file.delete()
        }

        val isSuccess = deleteRecursively(targetFile)
        if (isSuccess) {
            Toast.makeText(context, "文件删除成功", Toast.LENGTH_SHORT).show()
            refreshCallback()
            dismiss()
        } else {
            Toast.makeText(context, "文件删除失败，请检查权限", Toast.LENGTH_SHORT).show()
        }
    }
}