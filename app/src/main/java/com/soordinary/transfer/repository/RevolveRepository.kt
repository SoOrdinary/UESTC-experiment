package com.soordinary.transfer.repository

import com.soordinary.transfer.data.room.dao.RevolveDao
import com.soordinary.transfer.data.room.entity.RevolveEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RevolveRepository(private val revolveDao: RevolveDao) {

    // ==================== 插入操作（DAO 无 suspend，需手动切换线程） ====================
    /**
     * 插入单个任务
     */
    suspend fun insertTask(
        name: String,
        type: RevolveEntity.TaskType,
        coverUri: String,
        value: String
    ) {
        // 适配实体类：coverUri 是 String 类型，需将 Uri 转为 String
        val task = RevolveEntity(
            name = name,
            type = type,
            coverUri = coverUri, // 关键修正：Uri → String
            value = value
        )
        withContext(Dispatchers.IO) {
            revolveDao.insertTask(task)
        }
    }

    /**
     * 插入单个任务（直接传入实体类）
     */
    suspend fun insertTask(task: RevolveEntity) {
        withContext(Dispatchers.IO) {
            revolveDao.insertTask(task)
        }
    }

    /**
     * 插入多个任务
     */
    suspend fun insertTasks(tasks: List<RevolveEntity>) {
        withContext(Dispatchers.IO) {
            revolveDao.insertTasks(tasks)
        }
    }

    // ==================== 删除操作（DAO 无 suspend，手动切换线程） ====================
    /**
     * 删除单个任务（返回删除行数）
     */
    suspend fun deleteTask(task: RevolveEntity): Int {
        return withContext(Dispatchers.IO) {
            revolveDao.deleteTask(task)
        }
    }

    /**
     * 删除所有任务（返回删除行数）
     */
    suspend fun deleteAllTasks(): Int {
        return withContext(Dispatchers.IO) {
            revolveDao.deleteAllTasks()
        }
    }

    // ==================== 查询操作（Flow 无需切换线程，Room 自动处理） ====================
    /**
     * 观察所有任务（实时监听数据变化）
     */
    fun observeAllTasks(): Flow<List<RevolveEntity>> {
        return revolveDao.getAllTasks()
    }

    /**
     * 根据类型观察任务
     */
    fun observeTasksByType(type: RevolveEntity.TaskType): Flow<List<RevolveEntity>> {
        return revolveDao.getTasksByType(type.name)
    }

    /**
     * 根据名称查询单个任务（返回可空类型，适配无数据场景）
     */
    suspend fun getTaskByName(taskName: String): RevolveEntity? {
        return withContext(Dispatchers.IO) {
            try {
                revolveDao.getTaskByName(taskName)
            } catch (e: Exception) {
                // 无数据时返回 null，避免崩溃
                null
            }
        }
    }

    // ==================== 更新操作（DAO 无 suspend，手动切换线程） ====================
    /**
     * 更新任务名称
     * @return 更新的行数（>0 代表更新成功）
     */
    suspend fun updateTaskName(taskId: Long, newName: String): Int {
        return withContext(Dispatchers.IO) {
            revolveDao.updateTaskName(taskId, newName)
        }
    }

    /**
     * 更新任务类型
     * @return 更新的行数
     */
    suspend fun updateTaskType(taskId: Long, newType: RevolveEntity.TaskType): Int {
        return withContext(Dispatchers.IO) {
            revolveDao.updateTaskType(taskId, newType.name)
        }
    }

    /**
     * 更新任务封面Uri
     * @return 更新的行数
     */
    suspend fun updateTaskCoverUri(taskId: Long, newCoverUri: String): Int {
        return withContext(Dispatchers.IO) {
            revolveDao.updateTaskCoverUri(taskId, newCoverUri) // Uri → String
        }
    }

    /**
     * 更新任务value（长文本）
     * @return 更新的行数
     */
    suspend fun updateTaskValue(taskId: Long, newValue: String): Int {
        return withContext(Dispatchers.IO) {
            revolveDao.updateTaskValue(taskId, newValue)
        }
    }

    /**
     * 批量更新名称和类型
     * @return 更新的行数
     */
    suspend fun updateTaskNameAndType(taskId: Long, newName: String, newType: RevolveEntity.TaskType): Int {
        return withContext(Dispatchers.IO) {
            revolveDao.updateTaskNameAndType(taskId, newName, newType.name)
        }
    }
}