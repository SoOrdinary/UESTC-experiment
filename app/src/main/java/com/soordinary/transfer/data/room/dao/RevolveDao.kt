package com.soordinary.transfer.data.room.dao

import androidx.room.*
import com.soordinary.transfer.data.room.entity.RevolveEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RevolveDao {
    // 插入单条数据（已存在则替换）
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTask(task: RevolveEntity)

    // 插入多条数据
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTasks(tasks: List<RevolveEntity>)

    // 根据ID删除数据（返回删除行数）
    @Delete
    fun deleteTask(task: RevolveEntity): Int

    // 删除所有数据（非suspend，返回Int）
    @Query("DELETE FROM revolve_task")
    fun deleteAllTasks(): Int

    // 根据类型查询所有任务（字段名：task_type）
    @Query("SELECT * FROM revolve_task WHERE task_type = :type")
    fun getTasksByType(type: String): Flow<List<RevolveEntity>>

    // 查询所有任务（返回Flow）
    @Query("SELECT * FROM revolve_task ORDER BY id DESC")
    fun getAllTasks(): Flow<List<RevolveEntity>>

    // 根据名称查询单个任务（修正后可正常解析）
    @Query("SELECT * FROM revolve_task WHERE name = :taskName LIMIT 1")
    fun getTaskByName(taskName: String): RevolveEntity

    // -------------------------- 单独更新方法（字段名适配） --------------------------
    @Query("UPDATE revolve_task SET name = :newName WHERE id = :taskId")
    fun updateTaskName(taskId: Long, newName: String): Int

    @Query("UPDATE revolve_task SET task_type = :newType WHERE id = :taskId")
    fun updateTaskType(taskId: Long, newType: String): Int

    @Query("UPDATE revolve_task SET cover_uri = :newCoverUri WHERE id = :taskId")
    fun updateTaskCoverUri(taskId: Long, newCoverUri: String): Int

    @Query("UPDATE revolve_task SET task_value = :newValue WHERE id = :taskId")
    fun updateTaskValue(taskId: Long, newValue: String): Int

    @Query("UPDATE revolve_task SET name = :newName, task_type = :newType WHERE id = :taskId")
    fun updateTaskNameAndType(taskId: Long, newName: String, newType: String): Int
}