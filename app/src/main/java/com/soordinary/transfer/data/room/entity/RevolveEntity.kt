package com.soordinary.transfer.data.room.entity

import androidx.room.*

// 全局注册类型转换器（关键：让Room识别Uri/枚举转换）
@TypeConverters(TaskTypeConverter::class)
// 定义数据库表名
@Entity(tableName = "revolve_task")
data class RevolveEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0, // 主键+默认值，保证无参构造

    val name: String, //

    @ColumnInfo(name = "task_type") // 自定义列名：task_type
    val type: TaskType, // 枚举

    @ColumnInfo(name = "cover_uri") // 自定义列名：cover_uri
    val coverUri: String, // Uri

    @ColumnInfo(name = "task_value", typeAffinity = ColumnInfo.TEXT)
    val value: String // 文本
) {
    // 任务类型枚举
    enum class TaskType(val chineseName: String) {
        PACK("打包"),             //打包
        ENCRYPTION("加密"),       //加密
        TRANSFER("传输"),         //传输
        SYNC("同步")              //同步
    }
}

// 枚举类型转换器
class TaskTypeConverter {
    @TypeConverter
    fun fromTaskType(type: RevolveEntity.TaskType): String {
        return type.name
    }

    @TypeConverter
    fun toTaskType(typeString: String): RevolveEntity.TaskType {
        // 容错：未知类型返回默认值
        return try {
            RevolveEntity.TaskType.valueOf(typeString)
        } catch (e: IllegalArgumentException) {
            RevolveEntity.TaskType.PACK
        }
    }
}