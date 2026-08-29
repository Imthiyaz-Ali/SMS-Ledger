package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TransactionSMS::class, CategoryMapping::class, CustomCategory::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryMappingDao(): CategoryMappingDao
    abstract fun customCategoryDao(): CustomCategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun migrateTo6(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `smsUniqueId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `amount` REAL NOT NULL, `beneficiary` TEXT NOT NULL, `type` TEXT NOT NULL, `category` TEXT NOT NULL, `accountIdentifier` TEXT NOT NULL, `remainingBalance` REAL, `rawSms` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL DEFAULT 0, `sender` TEXT NOT NULL DEFAULT 'Unknown')")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_smsUniqueId` ON `transactions` (`smsUniqueId`)")
            
            try {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `isCompleted` INTEGER NOT NULL DEFAULT 0")
            } catch (e: Exception) {
                // Column may already exist
            }
            try {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `sender` TEXT NOT NULL DEFAULT 'Unknown'")
            } catch (e: Exception) {
                // Column may already exist
            }

            db.execSQL("CREATE TABLE IF NOT EXISTS `category_mappings` (`beneficiary` TEXT NOT NULL, `category` TEXT NOT NULL, `lastUpdated` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`beneficiary`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `custom_categories` (`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`name`))")
        }

        val MIGRATION_1_6 = object : Migration(1, 6) { override fun migrate(db: SupportSQLiteDatabase) { migrateTo6(db) } }
        val MIGRATION_2_6 = object : Migration(2, 6) { override fun migrate(db: SupportSQLiteDatabase) { migrateTo6(db) } }
        val MIGRATION_3_6 = object : Migration(3, 6) { override fun migrate(db: SupportSQLiteDatabase) { migrateTo6(db) } }
        val MIGRATION_4_6 = object : Migration(4, 6) { override fun migrate(db: SupportSQLiteDatabase) { migrateTo6(db) } }
        val MIGRATION_5_6 = object : Migration(5, 6) { override fun migrate(db: SupportSQLiteDatabase) { migrateTo6(db) } }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_ledger_database"
                )
                .addMigrations(MIGRATION_1_6, MIGRATION_2_6, MIGRATION_3_6, MIGRATION_4_6, MIGRATION_5_6)
                .fallbackToDestructiveMigration(true)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
