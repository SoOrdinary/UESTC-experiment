package com.soordinary.transfer.view.revolve

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soordinary.transfer.data.room.entity.RevolveEntity
import com.soordinary.transfer.repository.RevolveRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class RevolveViewModel(private val repository: RevolveRepository) : ViewModel() {

    // ==================== 插入操作（保留原有逻辑，无返回值） ====================
    /**
     * 插入单个任务
     * @param onError 可选的错误回调，供UI层处理异常
     */
    fun insertTask(
        name: String,
        type: RevolveEntity.TaskType,
        coverUri: String,
        value: String,
        onError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                repository.insertTask(name, type, coverUri, value)
            } catch (e: Exception) {
                onError?.invoke(e.message ?: "插入任务失败")
            }
        }
    }

    /**
     * 插入多个任务
     * @param onError 可选的错误回调
     */
    fun insertTasks(tasks: List<RevolveEntity>, onError: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.insertTasks(tasks)
            } catch (e: Exception) {
                onError?.invoke(e.message ?: "批量插入任务失败")
            }
        }
    }

    // ==================== 删除操作（适配仓库层返回行数） ====================
    /**
     * 删除单个任务
     * @param onError 可选的错误回调（包含“无数据删除”的场景）
     */
    fun deleteTask(task: RevolveEntity, onError: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val deleteCount = repository.deleteTask(task)
                // 判断是否删除成功（行数=0代表无数据被删除）
                if (deleteCount == 0) {
                    onError?.invoke("该任务不存在，删除失败")
                }
            } catch (e: Exception) {
                onError?.invoke(e.message ?: "删除任务失败")
            }
        }
    }

    /**
     * 删除所有任务
     * @param onError 可选的错误回调（包含“无数据删除”的场景）
     */
    fun deleteAllTasks(onError: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val deleteCount = repository.deleteAllTasks()
                if (deleteCount == 0) {
                    onError?.invoke("暂无任务可删除")
                }
            } catch (e: Exception) {
                onError?.invoke(e.message ?: "清空任务失败")
            }
        }
    }

    // ==================== 查询操作（保留原有逻辑） ====================
    /**
     * 观察所有任务（实时监听数据变化）
     */
    fun observeAllTasks(): Flow<List<RevolveEntity>> {
        return repository.observeAllTasks()
    }

    /**
     * 根据类型观察任务
     */
    fun observeTasksByType(type: RevolveEntity.TaskType): Flow<List<RevolveEntity>> {
        return repository.observeTasksByType(type)
    }

    /**
     * 根据名称查询任务
     * @param onResult 查询结果回调
     * @param onError 错误回调
     */
    fun getTaskByName(
        taskName: String,
        onResult: (RevolveEntity?) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                val task = repository.getTaskByName(taskName)
                onResult(task)
            } catch (e: Exception) {
                onError?.invoke(e.message ?: "查询任务失败")
                onResult(null)
            }
        }
    }

    // ==================== 更新操作（适配仓库层返回行数） ====================
    /**
     * 更新任务名称
     * @param onError 可选的错误回调（包含“无数据更新”的场景）
     */
    fun updateTaskName(taskId: Long, newName: String, onError: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val updateCount = repository.updateTaskName(taskId, newName)
                if (updateCount == 0) {
                    onError?.invoke("任务ID不存在，更新名称失败")
                }
            } catch (e: Exception) {
                onError?.invoke(e.message ?: "更新任务名称失败")
            }
        }
    }

    /**
     * 更新任务value
     * @param onError 可选的错误回调（包含“无数据更新”的场景）
     */
    fun updateTaskValue(taskId: Long, newValue: String, onError: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val updateCount = repository.updateTaskValue(taskId, newValue)
                if (updateCount == 0) {
                    onError?.invoke("任务ID不存在，更新内容失败")
                }
            } catch (e: Exception) {
                onError?.invoke(e.message ?: "更新任务内容失败")
            }
        }
    }

    /**
     * 更新任务封面Uri
     * @param onError 可选的错误回调（包含“无数据更新”的场景）
     */
    fun updateTaskCoverUri(taskId: Long, newCoverUri: String, onError: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val updateCount = repository.updateTaskCoverUri(taskId, newCoverUri)
                if (updateCount == 0) {
                    onError?.invoke("任务ID不存在，更新封面失败")
                }
            } catch (e: Exception) {
                onError?.invoke(e.message ?: "更新封面失败")
            }
        }
    }

    /**
     * 更新任务类型
     * @param onError 可选的错误回调（包含“无数据更新”的场景）
     */
    fun updateTaskType(
        taskId: Long,
        newType: RevolveEntity.TaskType,
        onError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                val updateCount = repository.updateTaskType(taskId, newType)
                if (updateCount == 0) {
                    onError?.invoke("任务ID不存在，更新类型失败")
                }
            } catch (e: Exception) {
                onError?.invoke(e.message ?: "更新任务类型失败")
            }
        }
    }
}