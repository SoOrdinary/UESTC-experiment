package com.soordinary.transfer.view.transfer

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.soordinary.transfer.R
import com.soordinary.transfer.data.network.socket.DataTransferNew
import com.soordinary.transfer.data.room.database.RevolveDatabase
import com.soordinary.transfer.databinding.DialogDataTransferBinding
import com.soordinary.transfer.databinding.DialogDataTransferReceiveBinding
import com.soordinary.transfer.repository.RevolveRepository
import com.soordinary.transfer.utils.NetworkUtil
import com.soordinary.transfer.utils.encryption.MD5Util
import com.soordinary.transfer.view.revolve.RevolveViewModel
import com.soordinary.transfer.view.user.UserViewModel

class TransferFragment : Fragment(R.layout.fragment_transfer) {

    // UI控件
    private lateinit var tvSelfIp: TextView
    private lateinit var rvIpList: RecyclerView // IP列表RecyclerView
    private lateinit var ipItemAdapter: IpItemAdapter // IP列表适配器
    private val viewModel: UserViewModel by activityViewModels()
    private val revolveViewModel: RevolveViewModel by activityViewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                // 初始化数据库和仓库（确保ApplicationContext，避免内存泄漏）
                val database = RevolveDatabase.getDatabase(requireContext().applicationContext)
                val repository = RevolveRepository(database.revolveDao())
                return RevolveViewModel(repository) as T
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 绑定所有UI控件
        bindViews(view)

        // 2. 初始化：获取并显示本机IP
        initSelfIpDisplay()

        // 3. 初始化IP列表适配器和数据
        initIpListAdapter()

    }

    /**
     * 绑定所有UI控件
     */
    private fun bindViews(view: View) {
        tvSelfIp = view.findViewById(R.id.tv_self_ip)
        rvIpList = view.findViewById<RecyclerView>(R.id.ip_list) // 确保fragment_transfer.xml中有这个ID
    }

    /**
     * 初始化IP列表适配器
     */
    private fun initIpListAdapter() {
        // 初始化适配器，设置Item点击事件
        ipItemAdapter = IpItemAdapter { ipItem ->
            val currentPassword = viewModel.getPasswordLiveData().value
            if (currentPassword.isNullOrEmpty()) {
                Toast.makeText(requireActivity(), "该操作需先为本机设置密码", Toast.LENGTH_LONG).show()
                return@IpItemAdapter
            }
            with(DialogDataTransferBinding.inflate(LayoutInflater.from(requireActivity()))) {
                val dialog = Dialog(requireActivity())
                dialog.setContentView(root)
                dialog.setCancelable(true)

                if (!NetworkUtil.isValid(requireActivity())) {
                    confirmToSend.isEnabled = false
                    confirmToReceive.isEnabled = false
                    internetHint.text = "该操作需开启热点或打开WIFI"
                }

                confirmToSend.setOnClickListener {
                    dialog.dismiss()
                    // 唤起发送设置界面
                    // todo:适配路径、传输内容
                    TransferSendDialog(requireActivity(), ipItem, revolveViewModel).show()
                }

                confirmToReceive.setOnClickListener {
                    dialog.dismiss()
                    // 唤起接收设置界面
                    // todo:适配路径、传输内容
                    TransferReceiveDialog(requireActivity(), ipItem).show()
                }

                dialog.show()
            }
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