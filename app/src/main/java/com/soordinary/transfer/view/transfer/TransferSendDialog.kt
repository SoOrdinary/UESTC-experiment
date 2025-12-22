package com.soordinary.transfer.view.transfer // 根据你的实际包名调整

import android.app.Activity
import android.app.Dialog
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.soordinary.transfer.data.network.socket.DataTransferOld
import com.soordinary.transfer.data.room.entity.RevolveEntity
import com.soordinary.transfer.databinding.DialogDataTransferSendBinding
import com.soordinary.transfer.utils.encryption.MD5Util
import com.soordinary.transfer.view.revolve.RevolveViewModel
import kotlinx.coroutines.launch

/**
 * 数据传输弹窗（包含中转计划选择Spinner）
 * 独立封装，复用性更高
 */
class TransferSendDialog(
    private val context: Activity,
    private val ipItem: IpItem, // 你的IpItem类，确保导入正确
    private val revolveViewModel: RevolveViewModel // 传入ViewModel，避免类内初始化
) : Dialog(context) {

    // 绑定类（使用ViewBinding）
    private lateinit var binding: DialogDataTransferSendBinding
    
    // Spinner相关
    private lateinit var planAdapter: ArrayAdapter<String>
    private val transferPlanList = mutableListOf<RevolveEntity>()
    private var selectedPlan: RevolveEntity? = null

    init {
        initDialog()
    }

    /**
     * 初始化Dialog核心逻辑
     */
    private fun initDialog() {
        // 初始化ViewBinding
        binding = DialogDataTransferSendBinding.inflate(LayoutInflater.from(context))
        setContentView(binding.root)
        
        // 设置Dialog基础属性（可选，根据需要调整）
        setCancelable(true)
        window?.setBackgroundDrawableResource(android.R.color.transparent) // 可选，去除默认背景

        // 1. 初始化Spinner
        initSpinner()

        // 2. 设置确认按钮点击事件
        setConfirmClickListener()
    }

    /**
     * 初始化Spinner（核心逻辑复用）
     */
    private fun initSpinner() {
        // 初始化适配器
        planAdapter = ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item)
        // 配置Spinner
        binding.spTransferPlan.apply {
            adapter = planAdapter
            // 添加默认提示项
            planAdapter.add("选择中转计划")
            // 默认选中提示项，不触发选择事件
            setSelection(0, false)

            // 设置选择监听
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
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
            visibility = View.VISIBLE
            loadTransferPlans()
        }
    }

    /**
     * 设置确认按钮点击事件
     */
    private fun setConfirmClickListener() {
        binding.confirm.setOnClickListener {
            // 显示Spinner并加载中转计划数据

            // 验证是否选中中转计划（可选，根据业务调整）
            selectedPlan ?: run {
                Toast.makeText(context, "请先选择中转计划", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 执行数据传输逻辑
            executeDataTransfer()
        }
    }

    /**
     * 加载中转计划数据
     */
    private fun loadTransferPlans() {
        revolveViewModel.viewModelScope.launch {
            revolveViewModel.observeAllTasks().collect { planList ->
                transferPlanList.clear()
                transferPlanList.addAll(planList)

                // 刷新适配器
                planAdapter.clear()
                planAdapter.add("选择中转计划")
                if (transferPlanList.isNotEmpty()) {
                    transferPlanList.forEach { plan ->
                        planAdapter.add(plan.name)
                    }
                }
                binding.spTransferPlan.setSelection(0, false)
            }
        }
    }

    /**
     * 执行数据传输核心逻辑
     */
    private fun executeDataTransfer() {
        // 禁用取消、隐藏按钮、显示日志区域
        setCancelable(false)
        binding.spTransferPlan.visibility=View.GONE
        binding.confirm.visibility = View.GONE
        binding.sendLogParent.visibility = View.VISIBLE
        binding.tip.text = "传输数据过程日志"

        // 初始化数据传输类并执行
        val dataTransferOld = DataTransferOld(
            context,
            8888,
            MD5Util.encryptByMD5(ipItem.password),
            logView = binding.oldLog,
            {  // 传输完成后恢复可取消状态
                setCancelable(true)
            }
        )

        // 开启线程执行传输
        Thread {
            dataTransferOld.start(false)
        }.start()
    }
}