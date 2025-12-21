package com.soordinary.transfer.view.transfer

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.soordinary.transfer.R
import com.soordinary.transfer.utils.NetworkUtil

class TransferFragment : Fragment(R.layout.fragment_transfer) {

    // UI控件
    private lateinit var tvSelfIp: TextView
    private lateinit var btnReceiveData: Button
    private lateinit var rvIpList: RecyclerView // IP列表RecyclerView
    private lateinit var ipItemAdapter: IpItemAdapter // IP列表适配器

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 绑定所有UI控件
        bindViews(view)

        // 2. 初始化：获取并显示本机IP
        initSelfIpDisplay()

        // 3. 初始化IP列表适配器和数据
        initIpListAdapter()

        // 4. 给接收数据按钮添加点击事件
        initReceiveButtonClick()
    }

    /**
     * 绑定所有UI控件
     */
    private fun bindViews(view: View) {
        tvSelfIp = view.findViewById(R.id.tv_self_ip)
        btnReceiveData = view.findViewById(R.id.btn_receive_data)
        rvIpList = view.findViewById<RecyclerView>(R.id.ip_list) // 确保fragment_transfer.xml中有这个ID
    }

    /**
     * 初始化IP列表适配器
     */
    private fun initIpListAdapter() {
        // 初始化适配器，设置Item点击事件
        ipItemAdapter = IpItemAdapter { ipItem ->
            // 处理IP Item的点击事件
            Toast.makeText(
                requireContext(),
                "点击了IP：${ipItem.ip}\n密码：${ipItem.password}",
                Toast.LENGTH_SHORT
            ).show()

            // 可扩展业务逻辑示例：
            // 1. 复制IP到剪贴板
            // 2. 连接该IP传输数据
            // 3. 跳转到详情页面等
        }

        // 设置RecyclerView布局管理器和适配器
        rvIpList.apply {
            layoutManager = LinearLayoutManager(requireContext()) // 线性布局
            adapter = ipItemAdapter
            setHasFixedSize(true) // 优化性能
        }
    }

    /**
     * 获取本机IPv4地址并显示
     */
    private fun initSelfIpDisplay() {
        val selfIp = NetworkUtil.getLocalIpAddress(requireContext())
        tvSelfIp.text = if (selfIp.isNotEmpty()) {
            "本机IP: $selfIp"
        } else {
            "本机IP: 未获取到"
        }
    }

    /**
     * 接收数据按钮点击事件
     */
    private fun initReceiveButtonClick() {
        btnReceiveData.setOnClickListener {
            // 示例：弹出提示，后续可替换为实际接收逻辑
            Toast.makeText(requireContext(), "开始接收数据...", Toast.LENGTH_SHORT).show()

            // TODO: 实际业务逻辑示例（可根据需求实现）
            // 1. 开启后台线程监听指定端口
            // thread {
            //     val serverSocket = java.net.ServerSocket(8888)
            //     val socket = serverSocket.accept()
            //     // 读取数据/文件
            // }
            // 2. 建立Socket连接
            // 3. 接收文件/数据并保存
        }
    }

    fun showCreateIpDialog() {
        // 创建弹窗实例，设置回调
        val createIpDialog = CreateIpDialog(requireContext()) { ip, password ->
            // 弹窗确认后，调用之前的addIpItem方法添加到列表
            addIpItem(ip, password)
        }
        // 显示弹窗
        createIpDialog.show()
    }

    private fun addIpItem(ip: String, password: String) {
        // 1. 校验参数（避免空值或无效IP添加到列表）
        if (ip.isBlank()) {
            Toast.makeText(requireContext(), "IP地址不能为空", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. 获取当前列表的所有数据（转换为可变列表）
        val currentList = ipItemAdapter.currentList.toMutableList()

        // 3. 检查是否已存在相同IP（避免重复添加）
        val isIpExisted = currentList.any { it.ip == ip }
        if (isIpExisted) {
            Toast.makeText(requireContext(), "该IP已存在列表中", Toast.LENGTH_SHORT).show()
            return
        }

        // 4. 添加新的IP项到列表末尾
        currentList.add(IpItem(ip, password))

        // 5. 提交更新后的列表（ListAdapter会自动对比并刷新）
        ipItemAdapter.submitList(currentList)

        // 可选：提示添加成功
        Toast.makeText(requireContext(), "IP添加成功", Toast.LENGTH_SHORT).show()
    }
}