package com.kasirkita.pos.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kasirkita.pos.core.database.dao.ProductDao
import com.kasirkita.pos.core.database.entity.ProductEntity

@Database(
    entities = [ProductEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
}
