package com.soordinary.transfer.view.revolve

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
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
import com.soordinary.transfer.data.room.entity.RevolveEntity
import com.soordinary.transfer.utils.ZipUtils
import com.soordinary.transfer.utils.encryption.AESUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class RevolveTaskDialog(
    private val context: Context,
    private val viewModel: RevolveViewModel,
    private val task: RevolveEntity,
    private val taskName: String,
    private val pathList: List<String>,
    private val refreshCallback: () -> Unit = {}
) : Dialog(context) {

    // 操作类型枚举
    enum class OperationType {
        NONE, PACK, COMPRESS, DELETE
    }

    private var currentOperation: OperationType = OperationType.NONE
    private lateinit var etCommonInput: EditText       // 父路径输入框
    private lateinit var etPassword: EditText          // 密码输入框
    private lateinit var tvPathsList: TextView          // 路径展示

    // 默认父路径（应用私有目录，无需额外权限）
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
        // 1. 初始化路径列表展示
        tvPathsList = findViewById(R.id.tv_paths_list)
        refreshPathListDisplay(false)

        // 2. 初始化核心控件
        etCommonInput = findViewById(R.id.et_common_input)
        etPassword = findViewById(R.id.et_password)
        val btnOperationSelect: AppCompatButton = findViewById(R.id.btn_operation_select)
        val btnConfirm: AppCompatButton = findViewById(R.id.btn_confirm)

        // 3. 父路径输入框监听（校验路径是否存在）
        etCommonInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (currentOperation == OperationType.PACK || currentOperation == OperationType.COMPRESS) {
                    val parentPath = s.toString().trim()
                    if (parentPath.isNotEmpty()) {
                        val parentFile = File(parentPath)
                        // 路径不存在标红，存在标黑
                        etCommonInput.setTextColor(
                            if (parentFile.exists() && parentFile.isDirectory) Color.BLACK
                            else Color.RED
                        )
                    } else {
                        etCommonInput.setTextColor(Color.BLACK)
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 4. 操作选择下拉菜单
        btnOperationSelect.setOnClickListener {
            val popupMenu = PopupMenu(context, it)
            popupMenu.menu.add(0, 1, 0, "打包")
            popupMenu.menu.add(0, 2, 1, "压缩")
            popupMenu.menu.add(0, 3, 2, "删除任务")

            popupMenu.setOnMenuItemClickListener { menuItem ->
                // 重置输入框样式
                resetEditTextStyle(etCommonInput)
                resetEditTextStyle(etPassword)
                // 隐藏所有输入框和确认按钮
                etCommonInput.visibility = View.GONE
                etPassword.visibility = View.GONE
                btnConfirm.visibility = View.GONE

                when (menuItem.itemId) {
                    1 -> { // 打包
                        currentOperation = OperationType.PACK
                        etCommonInput.hint = "请输入保存父路径"
                        etCommonInput.setText(defaultParentPath)
                        etCommonInput.setSelection(defaultParentPath.length)
                        etCommonInput.visibility = View.VISIBLE

                        // 密码框提示优化：说明为空则不加密
                        etPassword.hint = "请设置打包密码（为空则不加密）"
                        etPassword.visibility = View.VISIBLE

                        refreshPathListDisplay(true) // 标记不存在的文件
                        btnConfirm.visibility = View.VISIBLE
                    }
                    2 -> { // 压缩
                        currentOperation = OperationType.COMPRESS
                        etCommonInput.hint = "请输入保存父路径"
                        etCommonInput.setText(defaultParentPath)
                        etCommonInput.setSelection(defaultParentPath.length)
                        etCommonInput.visibility = View.VISIBLE

                        // 密码框提示优化：说明为空则不加密
                        etPassword.hint = "请设置压缩密码（为空则不加密）"
                        etPassword.visibility = View.VISIBLE

                        refreshPathListDisplay(true)
                        btnConfirm.visibility = View.VISIBLE
                    }
                    3 -> { // 删除任务
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

        // 5. 确认按钮点击事件
        btnConfirm.setOnClickListener {
            val parentPath = etCommonInput.text.toString().trim()
            val password = etPassword.text.toString().trim()

            when (currentOperation) {
                OperationType.PACK -> {
                    // 校验参数（移除密码为空的校验）
                    if (parentPath.isEmpty()) {
                        Toast.makeText(context, "保存父路径不能为空", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val parentFile = File(parentPath)
                    if (!parentFile.exists() || !parentFile.isDirectory) {
                        Toast.makeText(context, "父路径不存在或不是目录", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    // 核心逻辑：过滤有效路径（存在的路径 + 去重）
                    val validPaths = getValidPaths(pathList)
                    if (validPaths.isEmpty()) {
                        Toast.makeText(context, "暂无有效文件/文件夹可打包", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    // 生成自动编号的ZIP路径
                    val fullPath = ZipUtils.generateAutoRenamePath(parentPath, taskName, "zip")
                    // 异步执行打包（仅传参，暂不实现具体逻辑）
                    executePackOperation(fullPath, password, validPaths)
                }
                OperationType.COMPRESS -> {
                    // 校验参数（移除密码为空的校验）
                    if (parentPath.isEmpty()) {
                        Toast.makeText(context, "保存父路径不能为空", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val parentFile = File(parentPath)
                    if (!parentFile.exists() || !parentFile.isDirectory) {
                        Toast.makeText(context, "父路径不存在或不是目录", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    // 核心逻辑：过滤有效路径（存在的路径 + 去重）
                    val validPaths = getValidPaths(pathList)
                    if (validPaths.isEmpty()) {
                        Toast.makeText(context, "暂无有效文件/文件夹可压缩", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    // 生成自动编号的ZIP路径
                    val fullPath = ZipUtils.generateAutoRenamePath(parentPath, "${taskName}_压缩", "zip")
                    // 异步执行压缩（仅传参，暂不实现具体逻辑）
                    executeCompressOperation(fullPath, password, validPaths)
                }
                OperationType.DELETE -> {
                    executeDeleteOperation()
                }
                else -> Toast.makeText(context, "请先选择操作类型", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 过滤有效路径：存在的路径 + 去重（和标红逻辑一致）
     * @param paths 原始路径列表
     * @return 去重后的有效路径（仅包含存在的路径）
     */
    private fun getValidPaths(paths: List<String>): List<String> {
        return paths
            .filter { File(it).exists() } // 过滤掉不存在的路径（和标红逻辑一致）
            .distinct() // 路径去重（基于字符串去重，简单高效）
    }

    /**
     * 执行打包操作（异步）- 仅传参，暂不实现具体逻辑
     * @param targetPath ZIP文件保存路径
     * @param password 加密密码（为空则不加密）
     * @param validPaths 去重后的有效路径列表
     */
    private fun executePackOperation(targetPath: String, password: String, validPaths: List<String>) {
        GlobalScope.launch(Dispatchers.Main) {
            withContext(Dispatchers.IO) {
                // 暂不实现具体打包逻辑，仅打印参数（便于后续调试）
                println("【打包参数】保存路径：$targetPath")
                println("【打包参数】加密密码：${if (password.isEmpty()) "无" else "已设置"}")
                println("【打包参数】有效路径数：${validPaths.size}")
                validPaths.forEachIndexed { index, path ->
                    println("  有效路径$index：$path")
                }
            }

            // 仅提示操作触发，后续可替换为实际打包逻辑
            Toast.makeText(
                context,
                "打包操作已触发，待处理${validPaths.size}个有效路径，保存至$targetPath",
                Toast.LENGTH_LONG
            ).show()
            refreshCallback()
            dismiss()
        }
    }

    /**
     * 执行压缩操作（异步）- 仅传参，暂不实现具体逻辑
     * @param targetPath ZIP文件保存路径
     * @param password 加密密码（为空则不加密）
     * @param validPaths 去重后的有效路径列表
     */
    private fun executeCompressOperation(targetPath: String, password: String, validPaths: List<String>) {
        GlobalScope.launch(Dispatchers.Main) {
            withContext(Dispatchers.IO) {
                // 暂不实现具体压缩逻辑，仅打印参数（便于后续调试）
                println("【压缩参数】保存路径：$targetPath")
                println("【压缩参数】加密密码：${if (password.isEmpty()) "无" else "已设置"}")
                println("【压缩参数】有效路径数：${validPaths.size}")
                validPaths.forEachIndexed { index, path ->
                    println("  有效路径$index：$path")
                }
            }

            // 仅提示操作触发，后续可替换为实际压缩逻辑
            Toast.makeText(
                context,
                "压缩操作已触发，待处理${validPaths.size}个有效路径，保存至$targetPath",
                Toast.LENGTH_LONG
            ).show()
            refreshCallback()
            dismiss()
        }
    }

    /**
     * 执行删除任务操作
     */
    private fun executeDeleteOperation() {
        viewModel.deleteTask(
            task = task,
            onError = { errorMsg ->
                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
            }
        )
        Toast.makeText(context, "任务删除成功", Toast.LENGTH_SHORT).show()
        refreshCallback()
        dismiss()
    }

    /**
     * 生成自动编号的文件路径（兼容旧逻辑，实际调用工具类方法）
     */
    private fun getAutoRenamePath(parentPath: String, fileName: String, extension: String): String {
        return ZipUtils.generateAutoRenamePath(parentPath, fileName, extension)
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

            // 不存在的路径标记红色（和有效路径过滤逻辑一致）
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

    /**
     * 重置输入框样式
     */
    private fun resetEditTextStyle(editText: EditText) {
        editText.isEnabled = true
        editText.setTextColor(Color.BLACK)
        editText.background = editText.context.resources.getDrawable(android.R.drawable.edit_text, null)
    }

    /**
     * 设置删除任务的输入框样式
     */
    private fun setupDeleteEditTextStyle(editText: EditText) {
        editText.isEnabled = false
        editText.setTextColor(Color.RED)
        editText.background = ColorDrawable(Color.TRANSPARENT)
    }

    /**
     * 自定义SpannableStringBuilder（兼容旧逻辑）
     */
    private class SpannableStringBuilder : android.text.SpannableStringBuilder() {
        override fun setSpan(what: Any, start: Int, end: Int, flags: Int) {
            super.setSpan(what, start, end, flags)
        }
    }
}