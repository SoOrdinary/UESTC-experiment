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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.viewModelScope
import com.soordinary.transfer.R
import com.soordinary.transfer.data.room.entity.RevolveEntity
import com.soordinary.transfer.view.revolve.RevolveViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class EditDialog(
    private val context: Context,
    private val filePath: String,
    private val refreshCallback: () -> Unit,
    private val revolveViewModel: RevolveViewModel
) : Dialog(context) {

    // 文件对象
    private val targetFile = File(filePath)
    // 日期格式化工具
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    // 样式常量
    private val LABEL_COLOR = Color.parseColor("#666666")
    private val VALUE_COLOR = Color.parseColor("#2196F3")
    private val LABEL_STYLE = StyleSpan(Typeface.BOLD)
    // 记录当前选中的操作类型
    private var currentOperation: OperationType = OperationType.NONE
    // 保存原文件的后缀
    private val originalFileExtension: String by lazy { getFileExtension(targetFile.name) }
    // 保存原文件的纯前缀
    private val originalFileNameOnly: String by lazy { getFileNameWithoutExtension(targetFile.name) }
    // 原文件所在的文件夹路径
    private val originalFolderPath: String by lazy {
        targetFile.parentFile?.absolutePath ?: ""
    }

    // 判断是否是ZIP文件（用于控制解密选项是否显示）
    private val isZipFile: Boolean by lazy { originalFileExtension.equals("zip", ignoreCase = true) }

    // 中转计划相关
    private lateinit var spTransferPlan: Spinner
    private lateinit var planAdapter: ArrayAdapter<String>
    private var transferPlanList = mutableListOf<RevolveEntity>()
    private var selectedPlan: RevolveEntity? = null

    // 新增：独立的ZIP密码输入框
    private lateinit var etZipPassword: EditText
    // 原有通用输入框（解密时用于输入目标路径）
    private lateinit var etCommonInput: EditText

    // 操作类型枚举：新增DECRYPT类型
    enum class OperationType {
        NONE, RENAME, RESTORE, MIGRATE, DELETE, DECRYPT
    }

    init {
        initDialog()
    }

    private fun initDialog() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_file_edit, null)
        setContentView(view)

        // 原信息区域控件
        val tvName = view.findViewById<TextView>(R.id.tv_name)
        val tvType = view.findViewById<TextView>(R.id.tv_type)
        val tvPath = view.findViewById<TextView>(R.id.tv_path)
        val tvSize = view.findViewById<TextView>(R.id.tv_size)
        val tvModifyTime = view.findViewById<TextView>(R.id.tv_modify_time)

        // 绑定控件：原有通用输入框 + 新增密码输入框
        etCommonInput = view.findViewById(R.id.et_common_input)
        etZipPassword = view.findViewById(R.id.et_zip_password)
        val btnOperationSelect = view.findViewById<AppCompatButton>(R.id.btn_operation_select)
        val btnConfirm = view.findViewById<AppCompatButton>(R.id.btn_confirm)

        // 初始化中转计划选择器
        spTransferPlan = view.findViewById(R.id.sp_transfer_plan)
        planAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item)
        spTransferPlan.adapter = planAdapter
        planAdapter.add("选择中转计划")
        spTransferPlan.setSelection(0, false)

        // Spinner选择监听
        spTransferPlan.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (transferPlanList.isNotEmpty() && position > 0) {
                    selectedPlan = transferPlanList[position - 1]
                } else {
                    selectedPlan = null
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedPlan = null
            }
        }

        // 展示文件信息
        showFileInfo(tvName, tvType, tvPath, tvSize, tvModifyTime)

        // ========== 下拉操作选择按钮（核心修改：新增解密选项） ==========
        btnOperationSelect.setOnClickListener {
            val popupMenu = PopupMenu(context, it)
            // 添加原有选项
            popupMenu.menu.add(0, 1, 0, "重命名")
            popupMenu.menu.add(0, 2, 1, "移动")
            popupMenu.menu.add(0, 3, 2, "中转")
            // 仅当是ZIP文件时，显示「解密ZIP」选项
            if (isZipFile) {
                popupMenu.menu.add(0, 4, 3, "解密ZIP")
                popupMenu.menu.add(0, 5, 4, "删除")
            }else{
                popupMenu.menu.add(0, 5, 4, "删除")
            }

            popupMenu.setOnMenuItemClickListener { menuItem ->
                // 重置所有输入框样式和状态
                resetEditTextStyle(etCommonInput)
                resetEditTextStyle(etZipPassword)
                resetAllInputVisibility()

                when (menuItem.itemId) {
                    1 -> { // 重命名：显示通用输入框
                        currentOperation = OperationType.RENAME
                        etCommonInput.hint = "请输入修改文件名"
                        etCommonInput.setText(originalFileNameOnly)
                        etCommonInput.setSelection(originalFileNameOnly.length)
                        etCommonInput.visibility = View.VISIBLE
                        btnConfirm.visibility = View.VISIBLE
                    }
                    2 -> { // 移动：显示通用输入框（输入目标地址）
                        currentOperation = OperationType.RESTORE
                        etCommonInput.hint = "请输入移动到的文件夹路径"
                        etCommonInput.setText(originalFolderPath)
                        etCommonInput.setSelection(originalFolderPath.length)
                        etCommonInput.visibility = View.VISIBLE
                        btnConfirm.visibility = View.VISIBLE
                    }
                    3 -> { // 中转：显示Spinner
                        currentOperation = OperationType.MIGRATE
                        btnConfirm.visibility = View.VISIBLE
                        spTransferPlan.visibility = View.VISIBLE
                        loadTransferPlans()
                    }
                    4 -> { // 解密ZIP：显示密码输入框 + 通用输入框（目标路径）
                        currentOperation = OperationType.DECRYPT
                        // 密码输入框
                        etZipPassword.hint = "请输入ZIP密码（无密码则留空）"
                        etZipPassword.setText("")
                        etZipPassword.visibility = View.VISIBLE
                        // 通用输入框（目标路径）
                        etCommonInput.hint = "请输入解密后的保存路径"
                        val defaultOutputPath = "$originalFolderPath/${originalFileNameOnly}_decrypted"
                        etCommonInput.setText(defaultOutputPath)
                        etCommonInput.setSelection(defaultOutputPath.length)
                        etCommonInput.visibility = View.VISIBLE
                        // 确认按钮
                        btnConfirm.visibility = View.VISIBLE
                    }
                    5 -> { // 删除：显示通用输入框（提示文本）
                        currentOperation = OperationType.DELETE
                        setupDeleteEditTextStyle(etCommonInput)
                        etCommonInput.setText("确认删除文件？")
                        etCommonInput.visibility = View.VISIBLE
                        btnConfirm.visibility = View.VISIBLE
                    }
                }
                true
            }
            popupMenu.show()
        }

        // ========== 确认按钮（新增解密逻辑） ==========
        btnConfirm.setOnClickListener {
            when (currentOperation) {
                OperationType.RENAME -> {
                    val inputContent = etCommonInput.text.toString().trim()
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
                OperationType.RESTORE -> {
                    val inputContent = etCommonInput.text.toString().trim()
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
                    val plan = selectedPlan ?: run {
                        Toast.makeText(context, "请选择有效的中转计划", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val originalValue = plan.value ?: ""
                    val newPath = targetFile.absolutePath
                    val existingPaths = originalValue.split("|").filter { it.isNotEmpty() }
                    if (existingPaths.contains(newPath)) {
                        Toast.makeText(context, "该路径已在计划中", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val newValue = if (originalValue.isEmpty()) {
                        newPath
                    } else {
                        "$originalValue|$newPath"
                    }
                    revolveViewModel.updateTaskValue(
                        taskId = plan.id,
                        newValue = newValue,
                        onError = { msg ->
                            Toast.makeText(context, "更新失败：$msg", Toast.LENGTH_SHORT).show()
                        }
                    )
                    Toast.makeText(context, "路径已添加到中转计划", Toast.LENGTH_SHORT).show()
                    refreshCallback()
                    dismiss()
                }
                OperationType.DECRYPT -> { // ZIP解密逻辑（读取独立密码输入框 + 通用输入框路径）
                    // 1. 获取目标路径（通用输入框）
                    val targetPath = etCommonInput.text.toString().trim()
                    if (targetPath.isEmpty()) {
                        Toast.makeText(context, "解密保存路径不能为空", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    // 2. 获取密码（空则视为无密码）
                    val password = etZipPassword.text.toString().trim()
                    // 3. 校验是否是ZIP文件
                    if (!isZipFile) {
                        Toast.makeText(context, "当前文件不是ZIP格式，无法解密", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    // 4. 异步解密（避免主线程阻塞）
                    revolveViewModel.viewModelScope.launch(Dispatchers.IO) {
                        val decryptResult = decryptZipFile(password, targetPath)
                        // 主线程更新UI
                        withContext(Dispatchers.Main) {
                            if (decryptResult.first) {
                                Toast.makeText(context, "ZIP解密成功，保存至：${decryptResult.second}", Toast.LENGTH_LONG).show()
                                refreshCallback()
                                dismiss()
                            } else {
                                Toast.makeText(context, "解密失败：${decryptResult.second}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                OperationType.DELETE -> {
                    deleteFile()
                }
                else -> resetAllInputVisibility()
            }
        }
    }

    // ========== 核心工具方法 ==========
    /**
     * 重置所有输入框/Spinner的可见性
     */
    private fun resetAllInputVisibility() {
        etCommonInput.visibility = View.GONE
        etZipPassword.visibility = View.GONE
        spTransferPlan.visibility = View.GONE
        findViewById<AppCompatButton>(R.id.btn_confirm).visibility = View.GONE
        currentOperation = OperationType.NONE
    }

    /**
     * ZIP解密核心逻辑（适配无密码场景 + 自定义保存路径 + 重名自动加编号）
     * @param password ZIP密码（空字符串表示无密码）
     * @param targetPath 用户输入的目标保存路径
     * @return Pair<是否成功, 结果信息（成功返回最终路径，失败返回错误信息）>
     */
    private fun decryptZipFile(password: String, targetPath: String): Pair<Boolean, String> {
        val finalOutputDir = getUniqueDirectoryPath(targetPath)
        if (!finalOutputDir.exists() && !finalOutputDir.mkdirs()) {
            return Pair(false, "无法创建解压目录：${finalOutputDir.absolutePath}")
        }

        try {
            val zipFile = net.lingala.zip4j.ZipFile(targetFile)
            // 有密码则设置密码
            if (password.isNotEmpty()) {
                zipFile.setPassword(password.toCharArray())
            }
            // 解压全部文件（自动处理重名，也可结合我们的唯一路径逻辑）
            zipFile.extractAll(finalOutputDir.absolutePath)
            zipFile.close()
            return Pair(true, finalOutputDir.absolutePath)
        } catch (e: net.lingala.zip4j.exception.ZipException) {
            return Pair(false, when {
                e.message?.contains("password") == true -> "密码错误或文件未加密"
                else -> e.message ?: "ZIP解压异常"
            })
        } catch (e: Exception) {
            return Pair(false, e.message ?: "未知异常")
        }
    }

    /**
     * 获取唯一的文件夹路径（重名则追加编号，如：test -> test(1) -> test(2)）
     */
    private fun getUniqueDirectoryPath(basePath: String): File {
        var newFile = File(basePath)
        var count = 1
        // 如果路径已存在，循环生成新路径直到唯一
        while (newFile.exists()) {
            newFile = File("${basePath}($count)")
            count++
        }
        return newFile
    }

    /**
     * 获取唯一的文件路径（重名则追加编号，如：file.txt -> file(1).txt -> file(2).txt）
     */
    private fun getUniqueFilePath(baseFile: File): File {
        if (!baseFile.exists()) {
            return baseFile
        }

        val parentDir = baseFile.parentFile ?: return baseFile
        val fileName = baseFile.name
        val fileNameWithoutExt = getFileNameWithoutExtension(fileName)
        val extension = getFileExtension(fileName)

        var count = 1
        var newFile: File
        // 生成带编号的文件名
        do {
            val newFileName = if (extension.isNotEmpty()) {
                "$fileNameWithoutExt($count).$extension"
            } else {
                "$fileNameWithoutExt($count)"
            }
            newFile = File(parentDir, newFileName)
            count++
        } while (newFile.exists())

        return newFile
    }

    /**
     * 加载中转计划数据
     */
    private fun loadTransferPlans() {
        revolveViewModel.viewModelScope.launch {
            revolveViewModel.observeAllTasks().collect { planList ->
                transferPlanList.clear()
                transferPlanList.addAll(planList)

                planAdapter.clear()
                planAdapter.add("选择中转计划")
                if (transferPlanList.isNotEmpty()) {
                    transferPlanList.forEach { plan ->
                        planAdapter.add(plan.name)
                    }
                }
                spTransferPlan.setSelection(0, false)
            }
        }
    }

    /**
     * DP转PX
     */
    private fun dp2px(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    /**
     * 重置输入框样式
     */
    private fun resetEditTextStyle(editText: EditText) {
        editText.isEnabled = true
        editText.setTextColor(Color.BLACK)
        editText.background = editText.context.resources.getDrawable(android.R.drawable.edit_text, null)
    }

    /**
     * 设置删除状态输入框样式
     */
    private fun setupDeleteEditTextStyle(editText: EditText) {
        editText.isEnabled = false
        editText.setTextColor(Color.RED)
        editText.background = ColorDrawable(Color.TRANSPARENT)
    }

    /**
     * 展示文件信息
     */
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

    /**
     * 创建带样式的 Spannable 文本
     */
    private fun createSpannableText(label: String, value: String): SpannableString {
        val spannable = SpannableString(label + value)
        val labelEnd = label.length
        spannable.setSpan(LABEL_STYLE, 0, labelEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(ForegroundColorSpan(LABEL_COLOR), 0, labelEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(ForegroundColorSpan(VALUE_COLOR), labelEnd, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannable
    }

    /**
     * 获取无后缀的文件名
     */
    private fun getFileNameWithoutExtension(fileName: String): String {
        val lastDotIndex = fileName.lastIndexOf(".")
        return if (lastDotIndex > 0 && lastDotIndex < fileName.length - 1) {
            fileName.substring(0, lastDotIndex)
        } else {
            fileName
        }
    }

    /**
     * 获取文件后缀
     */
    private fun getFileExtension(fileName: String): String {
        val lastDotIndex = fileName.lastIndexOf(".")
        return if (lastDotIndex > 0 && lastDotIndex < fileName.length - 1) {
            fileName.substring(lastDotIndex + 1).lowercase()
        } else {
            ""
        }
    }

    /**
     * 格式化文件大小
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
     * 重命名文件
     */
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

    /**
     * 移动文件
     */
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

    /**
     * 删除文件（递归删除文件夹）
     */
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