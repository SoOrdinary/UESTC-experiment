package com.soordinary.transfer.view.revolve

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.soordinary.transfer.R
import com.soordinary.transfer.data.room.database.RevolveDatabase
import com.soordinary.transfer.data.room.entity.RevolveEntity
import com.soordinary.transfer.databinding.FragmentRevolveBinding
import com.soordinary.transfer.repository.RevolveRepository
import kotlinx.coroutines.launch

class RevolveFragment : Fragment(R.layout.fragment_revolve) {

    // ViewBinding 绑定
    private lateinit var binding: FragmentRevolveBinding

    // 适配器实例
    private lateinit var revolveAdapter: RevolveAdapter

    // 共享ViewModel（Activity作用域）
    private val viewModel: RevolveViewModel by activityViewModels {
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
        // 初始化ViewBinding
        binding = FragmentRevolveBinding.bind(view)

        // 初始化RecyclerView
        initRecyclerView()

        // 观察数据库数据变化
        observeTaskData()

        // 移除：删除测试数据插入代码
        // insertSampleData()
    }

    /**
     * 初始化RecyclerView（适配自定义Adapter）
     */
    private fun initRecyclerView() {
        // 初始化适配器（初始空数据）
        revolveAdapter = RevolveAdapter(revolveList = emptyList())

        // ========== 核心修改：移除测试数据，使用真实解析的路径列表 ==========
        revolveAdapter.setOnItemLongClickListener { task ->
            // 从选中的中转计划的value字段解析真实路径列表（按|分割）
            val realPathList = parsePathListFromTask(task)

            // 实例化弹窗并传入真实路径列表
            val dialog = RevolveTaskDialog(
                context = requireContext(),
                viewModel = viewModel,
                task = task,
                taskName = task.name,
                pathList = realPathList,     // 传入真实解析的路径列表
                refreshCallback = {
                    Toast.makeText(requireContext(), "操作完成，已刷新", Toast.LENGTH_SHORT).show()
                    // 刷新列表（可选：重新获取数据更新UI）
                    observeTaskData()
                }
            )
            dialog.show() // 显示弹窗

            true // 消费长按事件
        }

        // 配置RecyclerView
        with(binding.revolveList) {
            layoutManager = GridLayoutManager(context, 2)
            adapter = revolveAdapter
        }
    }

    /**
     * 从RevolveEntity的value字段解析路径列表（按|分割）
     * @param task 选中的中转计划实体
     * @return 解析后的路径列表（空列表则返回提示文本）
     */
    private fun parsePathListFromTask(task: RevolveEntity): List<String> {
        // 1. 获取value字段，为空则返回空列表提示
        val value = task.value ?: return listOf("该计划暂无关联文件路径")

        // 2. 按|分割路径，过滤空字符串（避免分割后出现空元素）
        val pathList = value.split("|").filter { it.isNotEmpty() }

        // 3. 无有效路径时返回提示
        return if (pathList.isEmpty()) {
            listOf("该计划暂无关联文件路径")
        } else {
            pathList
        }
    }

    /**
     * 观察数据库数据，实时更新列表（含空数据提示）
     */
    private fun observeTaskData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.observeAllTasks().collect { taskList ->
                    // 更新适配器数据
                    revolveAdapter.updateData(taskList)
                }
            }
        }
    }

    /**
     * 移除：删除测试数据插入方法
     */
    // private fun insertSampleData() { ... }

    /**
     * 长按删除确认（简化版，可替换为Dialog）
     */
    private fun showDeleteConfirm(task: RevolveEntity) {
        viewModel.deleteTask(task) { errorMsg ->
            if (errorMsg.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "删除成功：${task.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "删除失败：$errorMsg", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun showCreateTaskDialog() {
        CreateRevolveDialog(requireContext()) { name,type,cover,value ->
            // 弹窗回调：在协程中插入数据库（Room操作不能在主线程）
            lifecycleScope.launch {
                try {
                    viewModel.insertTask(name,type,cover,value)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "创建失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.show()
    }

    /**
     * 释放资源，避免内存泄漏
     */
    override fun onDestroyView() {
        super.onDestroyView()
        // 清空Adapter引用，避免内存泄漏
        binding.revolveList.adapter = null
        // 清空Binding引用（可选，强化内存管理）
        binding = FragmentRevolveBinding.inflate(layoutInflater)
    }
}