package com.soordinary.transfer.view.transfer // 根据你的实际包名调整

import android.app.Activity
import android.app.Dialog
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import com.soordinary.transfer.data.network.socket.DataTransferNew
import com.soordinary.transfer.databinding.DialogDataTransferReceiveBinding
import com.soordinary.transfer.utils.encryption.MD5Util

/**
 * 数据拉取（接收）弹窗
 * 独立封装，默认填充存储路径
 */
class TransferReceiveDialog(
    private val activity: Activity, // 使用Activity而非Context，方便获取文件路径
    private val ipItem: IpItem // 你的IpItem类，确保导入正确
) : Dialog(activity) {

    // ViewBinding实例
    private lateinit var binding: DialogDataTransferReceiveBinding

    // 默认存储路径（按你的要求配置）
    private val defaultReceivePath: String by lazy {
        val basePath = activity.getExternalFilesDir(null)?.path ?: activity.filesDir.path
        "$basePath/storage"
    }

    init {
        initDialog()
    }

    /**
     * 初始化Dialog核心配置
     */
    private fun initDialog() {
        // 初始化ViewBinding
        binding = DialogDataTransferReceiveBinding.inflate(LayoutInflater.from(context))
        setContentView(binding.root)

        // Dialog基础配置
        setCancelable(true)
        window?.setBackgroundDrawableResource(android.R.color.transparent) // 可选，优化背景

        // 初始化输入框（填充默认路径）
        initReceivePathEditText()

        // 设置确认按钮点击事件
        setConfirmClickListener()
    }

    /**
     * 初始化接收路径输入框（填充默认路径）
     */
    private fun initReceivePathEditText() {
        // 填充默认路径
        binding.receivePath.setText(defaultReceivePath)
        // 可选：将光标定位到文本末尾，方便用户修改
        binding.receivePath.setSelection(defaultReceivePath.length)
    }

    /**
     * 设置确认按钮点击逻辑
     */
    private fun setConfirmClickListener() {
        binding.confirm.setOnClickListener {
            // 1. 校验输入路径是否为空
            val receivePath = binding.receivePath.text.toString().trim()
            if (receivePath.isEmpty()) {
                Toast.makeText(context, "接受路径不可为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2. 调整UI状态
            setCancelable(false)
            binding.receivePath.visibility = View.GONE
            binding.confirm.visibility = View.GONE
            binding.receiveLogParent.visibility = View.VISIBLE
            binding.tip.text = "接收数据过程日志"

            // 3. 执行数据拉取逻辑
            executeDataReceive(receivePath)
        }
    }

    /**
     * 执行数据拉取（接收）核心逻辑
     */
    private fun executeDataReceive(receivePath: String) {
        // 初始化数据接收类
        val dataTransferNew = DataTransferNew(
            activity,
            ipItem.ip,
            8888,
            MD5Util.encryptByMD5(ipItem.password), binding.newLog,
            {
                // 接收完成后恢复可取消状态
                setCancelable(true)
            }
        )

        // 开启线程执行拉取操作
        Thread {
            dataTransferNew.start(false)
        }.start()
    }
}