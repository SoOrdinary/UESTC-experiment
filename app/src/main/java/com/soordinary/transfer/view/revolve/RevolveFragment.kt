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

        // 首次加载插入示例数据（测试用，正式环境删除）
        //insertSampleData()
    }

    /**
     * 初始化RecyclerView（适配自定义Adapter）
     */
    private fun initRecyclerView() {
        // 初始化适配器（初始空数据）
        revolveAdapter = RevolveAdapter(revolveList = emptyList())

        // 设置Adapter点击/长按回调
        revolveAdapter.setOnItemClickListener { task ->
            Toast.makeText(requireContext(), "点击了：${task.name}", Toast.LENGTH_SHORT).show()
        }

        revolveAdapter.setOnItemLongClickListener { task ->
            showDeleteConfirm(task)
            true // 消费长按事件
        }

        // 配置RecyclerView
        with(binding.revolveList) {
            layoutManager = GridLayoutManager(context, 2)
            adapter = revolveAdapter
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
     * 插入示例数据到数据库（测试用）
     */
    private fun insertSampleData() {
        val sampleList = listOf(
            RevolveEntity(
                name = "打包文档A",
                type = RevolveEntity.TaskType.PACK,
                coverUri = Uri.parse("android.resource://com.soordinary.transfer/drawable/app_icon").toString(),
                value = "2025-12-20 10:00:00"
            ),
            RevolveEntity(
                name = "加密视频B",
                type = RevolveEntity.TaskType.ENCRYPTION,
                coverUri = Uri.parse("android.resource://com.soordinary.transfer/drawable/app_icon").toString(),
                value = "2025-12-20 11:00:00"
            ),
            RevolveEntity(
                name = "传输文件C",
                type = RevolveEntity.TaskType.TRANSFER,
                coverUri = Uri.parse("android.resource://com.soordinary.transfer/drawable/app_icon").toString(),
                value = "2025-12-20 12:00:00"
            )
        )

        // 调用ViewModel插入数据
        viewModel.insertTasks(sampleList) { errorMsg ->
            Toast.makeText(requireContext(), "示例数据插入失败：$errorMsg", Toast.LENGTH_SHORT).show()
        }
    }

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
                    //Toast.makeText(requireContext(), "任务创建成功", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    //Toast.makeText(requireContext(), "创建失败：${e.message}", Toast.LENGTH_SHORT).show()
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