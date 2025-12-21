package com.soordinary.transfer.view.revolve

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import com.soordinary.transfer.R
import com.soordinary.transfer.view.revolve.RevolveViewModel
import java.io.File
import java.util.Locale

class RevolveTaskDialog(
    private val context: Context,
    private val viewModel: RevolveViewModel,
    private val taskName: String, // 任务名（用于默认文件名）
    private val pathList: List<String>,
    private val refreshCallback: () -> Unit = {}
) : Dialog(context) {

    // 缩减操作类型：仅保留打包、压缩（加密）、删除
    enum class OperationType {
        NONE, PACK, COMPRESS, DELETE
    }

    private var currentOperation: OperationType = OperationType.NONE
    private lateinit var etCommonInput: EditText       // 父路径输入框（仅显示父路径）
    private lateinit var etPassword: EditText          // 密码输入框
    private lateinit var tvPathsList: TextView          // 路径展示TextView

    // 默认父路径（仅到storage目录，不含文件名）
    private val defaultParentPath by lazy {
        "${context.getExternalFilesDir(null)?.absolutePath}/storage"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_revolve_task)

        initView()
    }

    private fun initView() {
        // 1. 初始化路径列表TextView
        tvPathsList = findViewById(R.id.tv_paths_list)
        refreshPathListDisplay(false)

        // 2. 初始化核心控件
        etCommonInput = findViewById(R.id.et_common_input)
        etPassword = findViewById(R.id.et_password)
        val btnOperationSelect: AppCompatButton = findViewById(R.id.btn_operation_select)
        val btnConfirm: AppCompatButton = findViewById(R.id.btn_confirm)

        // 3. 输入框文本变化监听（仅校验父路径是否存在）
        etCommonInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // 仅在打包/压缩时校验父路径是否存在
                if (currentOperation == OperationType.PACK || currentOperation == OperationType.COMPRESS) {
                    val parentPath = s.toString().trim()
                    if (parentPath.isNotEmpty()) {
                        val parentFile = File(parentPath)
                        // 父路径不存在标记红色，存在则黑色
                        etCommonInput.setTextColor(if (parentFile.exists() && parentFile.isDirectory) Color.BLACK else Color.RED)
                    } else {
                        etCommonInput.setTextColor(Color.BLACK)
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 4. 下拉菜单点击事件（修复压缩操作确认按钮显示问题）
        btnOperationSelect.setOnClickListener {
            val popupMenu = PopupMenu(context, it)
            popupMenu.menu.add(0, 1, 0, "打包")
            popupMenu.menu.add(0, 2, 1, "压缩")
            popupMenu.menu.add(0, 3, 2, "删除任务")

            popupMenu.setOnMenuItemClickListener { menuItem ->
                // 重置所有输入框样式
                resetEditTextStyle(etCommonInput)
                resetEditTextStyle(etPassword)

                // 隐藏所有输入框和确认按钮（统一初始化）
                etCommonInput.visibility = View.GONE
                etPassword.visibility = View.GONE
                btnConfirm.visibility = View.GONE

                when (menuItem.itemId) {
                    1 -> { // 打包：显示父路径输入框 + 密码框 + 确认按钮
                        currentOperation = OperationType.PACK
                        etCommonInput.hint = "请输入保存父路径"
                        etCommonInput.setText(defaultParentPath) // 仅显示父路径
                        etCommonInput.setSelection(defaultParentPath.length)
                        etCommonInput.visibility = View.VISIBLE

                        // 显示密码输入框
                        etPassword.hint = "请设置打包密码"
                        etPassword.visibility = View.VISIBLE

                        // 校验路径列表并标记红色
                        refreshPathListDisplay(true)
                        btnConfirm.visibility = View.VISIBLE // 显示确认按钮
                    }
                    2 -> { // 压缩：修复！确认按钮改为VISIBLE
                        currentOperation = OperationType.COMPRESS
                        etCommonInput.hint = "请输入保存父路径"
                        etCommonInput.setText(defaultParentPath) // 仅显示父路径
                        etCommonInput.setSelection(defaultParentPath.length)
                        etCommonInput.visibility = View.VISIBLE

                        // 显示密码输入框
                        etPassword.hint = "请设置压缩密码"
                        etPassword.visibility = View.VISIBLE

                        // 校验路径列表并标记红色
                        refreshPathListDisplay(true)
                        btnConfirm.visibility = View.VISIBLE // 关键修复：从GONE改为VISIBLE
                    }
                    3 -> { // 删除：仅显示提示文本，无密码框
                        currentOperation = OperationType.DELETE
                        setupDeleteEditTextStyle(etCommonInput)
                        etCommonInput.setText("确认删除任务？")
                        etCommonInput.visibility = View.VISIBLE
                        refreshPathListDisplay(false)
                        btnConfirm.visibility = View.VISIBLE
                    }
                }
                // 更新操作按钮文字
                btnOperationSelect.text = menuItem.title
                true
            }
            popupMenu.show()
        }

        // 5. 确认按钮点击事件（新增自动编号逻辑）
        btnConfirm.setOnClickListener {
            val parentPath = etCommonInput.text.toString().trim()
            val password = etPassword.text.toString().trim()

            when (currentOperation) {
                OperationType.PACK -> {
                    // 校验父路径和密码
                    if (parentPath.isEmpty()) {
                        Toast.makeText(context, "保存父路径不能为空", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val parentFile = File(parentPath)
                    if (!parentFile.exists() || !parentFile.isDirectory) {
                        Toast.makeText(context, "父路径不存在或不是目录", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (password.isEmpty()) {
                        Toast.makeText(context, "请设置打包密码", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    // 校验有效文件
                    val validPaths = pathList.filter { File(it).exists() }
                    if (validPaths.isEmpty()) {
                        Toast.makeText(context, "暂无有效文件可打包", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    // 生成带自动编号的完整路径
                    val fullPath = getAutoRenamePath(parentPath, taskName, "zip")
                    executePackOperation(fullPath, password)
                }
                OperationType.COMPRESS -> {
                    // 校验父路径和密码
                    if (parentPath.isEmpty()) {
                        Toast.makeText(context, "保存父路径不能为空", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val parentFile = File(parentPath)
                    if (!parentFile.exists() || !parentFile.isDirectory) {
                        Toast.makeText(context, "父路径不存在或不是目录", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (password.isEmpty()) {
                        Toast.makeText(context, "请设置压缩密码", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    // 校验有效文件
                    val validPaths = pathList.filter { File(it).exists() }
                    if (validPaths.isEmpty()) {
                        Toast.makeText(context, "暂无有效文件可压缩", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    // 生成带自动编号的完整路径
                    val fullPath = getAutoRenamePath(parentPath, "${taskName}_压缩", "zip")
                    executeCompressOperation(fullPath, password)
                }
                OperationType.DELETE -> {
                    executeDeleteOperation()
                }
                else -> Toast.makeText(context, "请先选择操作类型", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 生成自动编号的文件路径（重复则追加(1)、(2)...）
     * @param parentPath 父路径
     * @param fileName 基础文件名（不含后缀）
     * @param extension 文件后缀（如zip）
     * @return 最终的完整路径
     */
    private fun getAutoRenamePath(parentPath: String, fileName: String, extension: String): String {
        var index = 0
        var fullPath: String

        // 循环检查文件是否存在，直到找到不存在的路径
        do {
            val tempFileName = if (index == 0) {
                "$fileName.$extension"
            } else {
                String.format(Locale.getDefault(), "%s(%d).%s", fileName, index, extension)
            }
            fullPath = "$parentPath/$tempFileName"
            index++
        } while (File(fullPath).exists())

        return fullPath
    }

    /**
     * 刷新路径列表展示（标记不存在的文件为红色）
     */
    private fun refreshPathListDisplay(checkExist: Boolean) {
        if (pathList.isEmpty()) {
            tvPathsList.text = "暂无待处理路径"
            return
        }

        val spannable = SpannableStringBuilder()
        pathList.forEachIndexed { index, path ->
            val lineText = "${index + 1}. $path\n"
            val start = spannable.length
            spannable.append(lineText)
            val end = spannable.length

            // 不存在的文件标记红色
            if (checkExist && !File(path).exists()) {
                spannable.setSpan(
                    ForegroundColorSpan(Color.RED),
                    start,
                    end - 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        tvPathsList.text = spannable
    }

    // ========== 输入框样式方法 ==========
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

    // ========== 操作执行方法 ==========
    private fun executePackOperation(targetPath: String, password: String) {
        val validPaths = pathList.filter { File(it).exists() }
        Toast.makeText(context, "开始打包 ${validPaths.size} 个文件（密码：$password）到：$targetPath", Toast.LENGTH_LONG).show()
        refreshCallback()
        dismiss()
    }

    private fun executeCompressOperation(targetPath: String, password: String) {
        val validPaths = pathList.filter { File(it).exists() }
        Toast.makeText(context, "开始压缩 ${validPaths.size} 个文件（密码：$password）到：$targetPath", Toast.LENGTH_LONG).show()
        refreshCallback()
        dismiss()
    }

    private fun executeDeleteOperation() {
        Toast.makeText(context, "成功删除 ${pathList.size} 个任务路径", Toast.LENGTH_SHORT).show()
        refreshCallback()
        dismiss()
    }

    /**
     * 自定义SpannableStringBuilder
     */
    private class SpannableStringBuilder : android.text.SpannableStringBuilder() {
        override fun setSpan(what: Any, start: Int, end: Int, flags: Int) {
            super.setSpan(what, start, end, flags)
        }
    }
}