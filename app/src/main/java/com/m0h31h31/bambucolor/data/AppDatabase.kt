package com.m0h31h31.bambucolor.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

@Database(
    entities = [
        ConsumableEntity::class,
        TagConfigEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun consumableDao(): ConsumableDao
    abstract fun tagConfigDao(): TagConfigDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbFile = getExternalDbFile(context)
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    dbFile.absolutePath
                )
                    .fallbackToDestructiveMigration() // 先开发阶段用，后续再做迁移
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private fun getExternalDbFile(context: Context): File {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            return File(dir, "bambu_color.db")
        }
    }
}
