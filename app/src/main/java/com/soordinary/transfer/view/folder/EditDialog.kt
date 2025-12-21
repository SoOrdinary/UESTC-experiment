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
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditDialog(
    private val context: Context,
    private val filePath: String,
    private val refreshCallback: () -> Unit,
    // 传入ViewModel获取中转计划
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

    // 新增：中转计划相关
    private lateinit var spTransferPlan: Spinner
    private lateinit var planAdapter: ArrayAdapter<String>
    private var transferPlanList = mutableListOf<RevolveEntity>()
    private var selectedPlan: RevolveEntity? = null

    // 操作类型枚举
    enum class OperationType {
        NONE, RENAME, RESTORE, MIGRATE, DELETE
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

        // 原输入框和按钮
        val etCommonInput = view.findViewById<EditText>(R.id.et_common_input)
        val btnOperationSelect = view.findViewById<AppCompatButton>(R.id.btn_operation_select)
        val btnConfirm = view.findViewById<AppCompatButton>(R.id.btn_confirm)

        // 新增：初始化中转计划选择器
        spTransferPlan = view.findViewById(R.id.sp_transfer_plan)
        planAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item)
        spTransferPlan.adapter = planAdapter
        // 初始化时添加提示项
        planAdapter.add("选择中转计划")
        spTransferPlan.setSelection(0, false) // 默认选中提示项，不触发监听

        // Spinner选择监听
        spTransferPlan.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // 索引0是提示项，索引>0才对应真实计划（计划列表索引 = position -1）
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

        // ========== 下拉操作选择按钮 ==========
        btnOperationSelect.setOnClickListener {
            val popupMenu = PopupMenu(context, it)
            popupMenu.menu.add(0, 1, 0, "重命名")
            popupMenu.menu.add(0, 2, 1, "移动")
            popupMenu.menu.add(0, 3, 2, "中转")
            popupMenu.menu.add(0, 4, 3, "删除")

            popupMenu.setOnMenuItemClickListener { menuItem ->
                resetEditTextStyle(etCommonInput)
                // 重置所有输入区域状态
                etCommonInput.visibility = View.GONE
                spTransferPlan.visibility = View.GONE
                btnConfirm.visibility = View.GONE

                when (menuItem.itemId) {
                    1 -> { // 重命名
                        currentOperation = OperationType.RENAME
                        etCommonInput.hint = "请输入修改文件名"
                        etCommonInput.setText(originalFileNameOnly)
                        etCommonInput.setSelection(originalFileNameOnly.length)
                        etCommonInput.visibility = View.VISIBLE
                        btnConfirm.visibility = View.VISIBLE
                    }
                    2 -> { // 移动
                        currentOperation = OperationType.RESTORE
                        etCommonInput.hint = "请输入移动到的文件夹路径"
                        etCommonInput.setText(originalFolderPath)
                        etCommonInput.setSelection(originalFolderPath.length)
                        etCommonInput.visibility = View.VISIBLE
                        btnConfirm.visibility = View.VISIBLE
                    }
                    3 -> { // 中转 - 核心修改
                        currentOperation = OperationType.MIGRATE
                        btnConfirm.visibility = View.VISIBLE
                        // 隐藏输入框，显示Spinner
                        spTransferPlan.visibility = View.VISIBLE
                        // 加载中转计划数据
                        loadTransferPlans()
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
            popupMenu.show()
        }

        // ========== 确认按钮 ==========
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
                OperationType.MIGRATE -> { // 中转确认逻辑
                    val plan = selectedPlan ?: run {
                        Toast.makeText(context, "请选择有效的中转计划", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    // 追加文件路径：原value|新路径
                    val originalValue = plan.value ?: ""
                    val newPath = targetFile.absolutePath
                    // ========== 修复：精确匹配完整路径，避免子路径误判 ==========
                    // 按|分割所有已存在的路径，过滤空字符串（避免value为空时的空元素）
                    val existingPaths = originalValue.split("|").filter { it.isNotEmpty() }
                    // 精确判断新路径是否在已存在的路径列表中
                    if (existingPaths.contains(newPath)) {
                        Toast.makeText(context, "该路径已在计划中", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    // ========== 原有逻辑 ==========
                    val newValue = if (originalValue.isEmpty()) {
                        newPath
                    } else {
                        "$originalValue|$newPath"
                    }
                    // 调用ViewModel更新计划
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
                OperationType.DELETE -> {
                    deleteFile()
                }
                else -> resetState(etCommonInput, btnConfirm)
            }
        }
    }

    // ========== 新增：加载中转计划数据 ==========
    private fun loadTransferPlans() {
        // 使用 viewModelScope 避免内存泄漏
        revolveViewModel.viewModelScope.launch {
            revolveViewModel.observeAllTasks().collect { planList ->
                // 过滤出中转类型的计划（根据你的TaskType调整，比如 TaskType.TRANSFER）
                transferPlanList.clear()
                transferPlanList.addAll(planList)

                // 清空适配器，重新添加提示项 + 计划列表
                planAdapter.clear()
                planAdapter.add("选择中转计划") // 固定提示项

                if (transferPlanList.isNotEmpty()) {
                    transferPlanList.forEach { plan ->
                        planAdapter.add(plan.name)
                    }
                }
                // 默认选中提示项
                spTransferPlan.setSelection(0, false)
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

    private fun resetState(editText: EditText, confirmBtn: AppCompatButton) {
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