package com.soordinary.transfer.view.transfer

import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.soordinary.transfer.R
import java.util.regex.Pattern

/**
 * IP添加弹窗：输入IP和密码，通过回调传递数据
 * @param context 上下文
 * @param onIpCreated IP创建成功的回调（传递IP和密码）
 */
class CreateIpDialog(
    private val context: Context,
    private val onIpCreated: (String, String) -> Unit // 回调：传递输入的IP和密码
) : BottomSheetDialog(context) {

    // 控件引用
    private lateinit var etIpAddress: EditText
    private lateinit var etIpPassword: EditText
    private lateinit var btnConfirmIp: Button

    init {
        initView()
        initClickListeners()
    }

    /**
     * 初始化弹窗视图和控件绑定
     */
    private fun initView() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_create_ip, null)
        setContentView(view)

        // 绑定控件
        etIpAddress = view.findViewById(R.id.et_ip_address)
        etIpPassword = view.findViewById(R.id.et_ip_password)
        btnConfirmIp = view.findViewById(R.id.btn_confirm_ip)
    }

    /**
     * 初始化点击事件
     */
    private fun initClickListeners() {
        btnConfirmIp.setOnClickListener {
            createIpItem()
        }
    }

    /**
     * 校验输入并创建IP项（通过回调传递）
     */
    private fun createIpItem() {
        // 1. 获取输入内容并去除首尾空格
        val ipAddress = etIpAddress.text.toString().trim()
        val ipPassword = etIpPassword.text.toString().trim()

        // 2. 输入校验
        // 2.1 校验IP是否为空
        if (ipAddress.isEmpty()) {
            etIpAddress.error = "IP地址不能为空"
            etIpAddress.requestFocus()
            return
        }

        // 2.2 校验IP格式是否合法（可选：增强校验）
        if (!isValidIpAddress(ipAddress)) {
            etIpAddress.error = "IP地址格式不正确"
            etIpAddress.requestFocus()
            Toast.makeText(context, "请输入如192.168.1.100的合法IP", Toast.LENGTH_SHORT).show()
            return
        }

        // 3. 密码为空时默认填充提示文字（可选）
        val finalPassword = if (ipPassword.isEmpty()) "无密码" else ipPassword

        // 4. 通过回调传递IP和密码，关闭弹窗
        onIpCreated.invoke(ipAddress, finalPassword)
        dismiss()
    }

    /**
     * 工具方法：校验IPv4地址格式是否合法
     */
    private fun isValidIpAddress(ip: String): Boolean {
        // IPv4正则表达式：匹配xxx.xxx.xxx.xxx格式，每个段0-255
        val ipPattern = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        )
        return ipPattern.matcher(ip).matches()
    }
}