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
import java.io.File

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
            // 验证是否选中中转计划
            selectedPlan ?: run {
                Toast.makeText(context, "请先选择中转计划", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 1. 从数据库重新获取完整的任务数据（确保value字段最新）
            revolveViewModel.getTaskByName(
                taskName = selectedPlan?.name ?: "",
                onResult = { fullTask ->
                    fullTask ?: run {
                        Toast.makeText(context, "该中转计划不存在，无法获取路径", Toast.LENGTH_SHORT).show()
                        return@getTaskByName
                    }

                    // 2. 按|分割value，仅提取有效路径（无提示文本）
                    val originalPaths = parsePathListFromTask(fullTask)

                    // 3. 筛选有效路径（存在+去重）
                    val validPaths = getValidPaths(originalPaths)

                    // 4. 校验有效路径是否为空
                    if (validPaths.isEmpty()) {
                        Toast.makeText(context, "该中转计划下暂无有效文件/文件夹可传输", Toast.LENGTH_SHORT).show()
                        return@getTaskByName
                    }

                    // 5. 执行数据传输逻辑，传入有效路径列表
                    executeDataTransfer(validPaths)
                },
                onError = { errorMsg ->
                    Toast.makeText(context, "查询中转计划失败：$errorMsg", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    /**
     * 路径解析方法：仅按|分割value，返回纯路径列表（无提示文本）
     * @param task 完整的中转计划实体
     * @return 解析后的纯路径列表（空值/无路径时返回空列表）
     */
    private fun parsePathListFromTask(task: RevolveEntity): List<String> {
        // 1. 获取value字段，为空直接返回空列表
        val value = task.value ?: return emptyList()

        // 2. 按|分割路径，过滤空字符串，仅保留有效路径
        return value.split("|")
            .map { it.trim() } // 去除路径首尾空格（避免因空格导致的无效路径）
            .filter { it.isNotEmpty() } // 过滤分割后的空字符串
    }

    /**
     * 过滤有效路径：存在的路径 + 去重（复用第一个Dialog的核心逻辑）
     * @param paths 原始路径列表
     * @return 去重后的有效路径（仅包含存在的路径）
     */
    private fun getValidPaths(paths: List<String>): List<String> {
        return paths
            .filter { it.isNotEmpty() && File(it).exists() } // 过滤空字符串+不存在的路径
            .distinct() // 路径去重（基于字符串去重，简单高效）
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
     * 执行数据传输核心逻辑（传入纯有效路径列表）
     * @param validPaths 筛选后的有效路径列表
     */
    private fun executeDataTransfer(validPaths: List<String>) {
        // 禁用取消、隐藏按钮、显示日志区域
        setCancelable(false)
        binding.spTransferPlan.visibility = View.GONE
        binding.confirm.visibility = View.GONE
        binding.sendLogParent.visibility = View.VISIBLE
        binding.tip.text = "传输数据过程日志（共${validPaths.size}个有效路径）"

        // 初始化数据传输类并执行（可根据需要将validPaths传入DataTransferOld）
        val dataTransferOld = DataTransferOld(
            context,
            8888,
            MD5Util.encryptByMD5(ipItem.password),
            binding.sendLog,
            validPaths,
            {  // 传输完成后恢复可取消状态
                setCancelable(true)
            }
        )

        // 关键：将有效路径列表传递给DataTransferOld（需确保该类支持接收路径参数）
        // 示例：若DataTransferOld有setTransferPaths方法，取消注释下方代码
        // dataTransferOld.setTransferPaths(validPaths)

        // 开启线程执行传输
        Thread {
            dataTransferOld.start(false)
        }.start()
    }
}