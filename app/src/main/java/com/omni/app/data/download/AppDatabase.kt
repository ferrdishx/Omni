package com.omni.app.data.download

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.omni.app.data.favorites.FavoriteDao
import com.omni.app.data.favorites.FavoriteItem

@Database(
    entities = [DownloadedMedia::class, FavoriteItem::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `favorites` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `url` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `author` TEXT NOT NULL DEFAULT '',
                        `duration` TEXT NOT NULL DEFAULT '',
                        `thumbnailUrl` TEXT,
                        `isAudio` INTEGER NOT NULL,
                        `format` TEXT NOT NULL DEFAULT '',
                        `filePath` TEXT NOT NULL,
                        `website` TEXT NOT NULL DEFAULT '',
                        `fileSize` INTEGER NOT NULL DEFAULT 0,
                        `addedAt` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "omni_database"
                )
                .addMigrations(MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
