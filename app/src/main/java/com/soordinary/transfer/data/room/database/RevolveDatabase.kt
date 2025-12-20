package com.soordinary.transfer.data.room.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.soordinary.transfer.data.room.dao.RevolveDao
import com.soordinary.transfer.data.room.entity.RevolveEntity
import com.soordinary.transfer.data.room.entity.TaskTypeConverter

// 全局注册类型转换器
@TypeConverters( TaskTypeConverter::class)
@Database(entities = [RevolveEntity::class], version = 1, exportSchema = false)
abstract class RevolveDatabase : RoomDatabase() {
    abstract fun revolveDao(): RevolveDao

    // 单例模式
    companion object {
        @Volatile
        private var INSTANCE: RevolveDatabase? = null

        fun getDatabase(context: Context): RevolveDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RevolveDatabase::class.java,
                    "revolve_database" // 数据库文件名
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}