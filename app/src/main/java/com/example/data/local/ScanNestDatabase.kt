package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FolderEntity::class,
        DocumentEntity::class,
        PageEntity::class,
        SignatureEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ScanNestDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun documentDao(): DocumentDao
    abstract fun pageDao(): PageDao
    abstract fun signatureDao(): SignatureDao

    companion object {
        @Volatile
        private var INSTANCE: ScanNestDatabase? = null

        fun getInstance(context: Context): ScanNestDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScanNestDatabase::class.java,
                    "scannest.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
