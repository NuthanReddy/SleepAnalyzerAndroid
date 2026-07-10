package tech.future.sleepanalyzer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import tech.future.sleepanalyzer.data.db.dao.AlarmConfigDao
import tech.future.sleepanalyzer.data.db.dao.AudioRecordingDao
import tech.future.sleepanalyzer.data.db.dao.SleepGoalDao
import tech.future.sleepanalyzer.data.db.dao.SleepNoteDao
import tech.future.sleepanalyzer.data.db.dao.SleepProgramDao
import tech.future.sleepanalyzer.data.db.dao.SleepSessionDao
import tech.future.sleepanalyzer.data.db.dao.UserAccountDao
import tech.future.sleepanalyzer.data.db.dao.UserProfileDao
import tech.future.sleepanalyzer.data.db.dao.VoiceProfileDao
import tech.future.sleepanalyzer.data.db.dao.WearableDeviceDao
import tech.future.sleepanalyzer.data.db.dao.WearableSampleDao
import tech.future.sleepanalyzer.data.db.dao.WearableSleepStageDao
import tech.future.sleepanalyzer.data.db.entity.AlarmConfig
import tech.future.sleepanalyzer.data.db.entity.AudioRecording
import tech.future.sleepanalyzer.data.db.entity.SleepGoal
import tech.future.sleepanalyzer.data.db.entity.SleepNote
import tech.future.sleepanalyzer.data.db.entity.SleepProgram
import tech.future.sleepanalyzer.data.db.entity.SleepSession
import tech.future.sleepanalyzer.data.db.entity.UserAccount
import tech.future.sleepanalyzer.data.db.entity.UserProfile
import tech.future.sleepanalyzer.data.db.entity.VoiceProfile
import tech.future.sleepanalyzer.data.db.entity.WearableDevice
import tech.future.sleepanalyzer.data.db.entity.WearableSample
import tech.future.sleepanalyzer.data.db.entity.WearableSleepStage

@Database(
    entities = [
        SleepSession::class,
        SleepNote::class,
        AlarmConfig::class,
        AudioRecording::class,
        SleepGoal::class,
        SleepProgram::class,
        VoiceProfile::class,
        UserProfile::class,
        UserAccount::class,
        WearableSample::class,
        WearableSleepStage::class,
        WearableDevice::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sleepSessionDao(): SleepSessionDao
    abstract fun sleepNoteDao(): SleepNoteDao
    abstract fun alarmConfigDao(): AlarmConfigDao
    abstract fun audioRecordingDao(): AudioRecordingDao
    abstract fun sleepGoalDao(): SleepGoalDao
    abstract fun sleepProgramDao(): SleepProgramDao
    abstract fun voiceProfileDao(): VoiceProfileDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun userAccountDao(): UserAccountDao
    abstract fun wearableSampleDao(): WearableSampleDao
    abstract fun wearableSleepStageDao(): WearableSleepStageDao
    abstract fun wearableDeviceDao(): WearableDeviceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS voice_profiles (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "label TEXT NOT NULL DEFAULT 'me', " +
                        "sampleFilePath TEXT, " +
                        "pitchMeanHz REAL NOT NULL DEFAULT 0, " +
                        "pitchStdHz REAL NOT NULL DEFAULT 0, " +
                        "spectralCentroidMean REAL NOT NULL DEFAULT 0, " +
                        "spectralCentroidStd REAL NOT NULL DEFAULT 0, " +
                        "zeroCrossingRate REAL NOT NULL DEFAULT 0, " +
                        "rmsMean REAL NOT NULL DEFAULT 0, " +
                        "bandEnergyMeans TEXT NOT NULL DEFAULT '', " +
                        "createdAt INTEGER NOT NULL DEFAULT 0, " +
                        "isActive INTEGER NOT NULL DEFAULT 1)"
                )
                db.execSQL("ALTER TABLE audio_recordings ADD COLUMN attributedTo TEXT NOT NULL DEFAULT 'unknown'")
                db.execSQL("ALTER TABLE audio_recordings ADD COLUMN matchConfidence REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE audio_recordings ADD COLUMN pitchHz REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE audio_recordings ADD COLUMN croppedFromMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE audio_recordings ADD COLUMN croppedToMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS user_profile (" +
                        "id INTEGER PRIMARY KEY NOT NULL, " +
                        "displayName TEXT, " +
                        "dateOfBirth INTEGER, " +
                        "biologicalSex TEXT, " +
                        "heightCm REAL, " +
                        "weightKg REAL, " +
                        "activityLevel TEXT, " +
                        "units TEXT NOT NULL DEFAULT 'metric', " +
                        "sleepConditions TEXT NOT NULL DEFAULT '', " +
                        "medications TEXT, " +
                        "typicalCaffeineCutoffHour INTEGER, " +
                        "shiftWorkSchedule TEXT, " +
                        "updatedAt INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS wearable_samples (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "sessionId INTEGER, " +
                        "timestamp INTEGER NOT NULL, " +
                        "metric TEXT NOT NULL, " +
                        "value REAL NOT NULL, " +
                        "unit TEXT NOT NULL DEFAULT '', " +
                        "deviceId TEXT, " +
                        "sourceProvider TEXT NOT NULL DEFAULT 'health_connect')"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_wearable_samples_timestamp ON wearable_samples(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_wearable_samples_sessionId ON wearable_samples(sessionId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_wearable_samples_deviceId_metric_timestamp ON wearable_samples(deviceId, metric, timestamp)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS wearable_devices (" +
                        "id TEXT PRIMARY KEY NOT NULL, " +
                        "displayName TEXT NOT NULL, " +
                        "type TEXT NOT NULL DEFAULT 'unknown', " +
                        "sourceProvider TEXT NOT NULL, " +
                        "isActive INTEGER NOT NULL DEFAULT 1, " +
                        "lastSyncMs INTEGER NOT NULL DEFAULT 0, " +
                        "capabilities TEXT NOT NULL DEFAULT '')"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarm_configs ADD COLUMN useSmartWake INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS wearable_sleep_stages (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "sessionId INTEGER, " +
                        "startTime INTEGER NOT NULL, " +
                        "endTime INTEGER NOT NULL, " +
                        "stage TEXT NOT NULL, " +
                        "sourceProvider TEXT NOT NULL DEFAULT 'health_connect', " +
                        "deviceId TEXT)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_wearable_sleep_stages_startTime ON wearable_sleep_stages(startTime)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_wearable_sleep_stages_sessionId ON wearable_sleep_stages(sessionId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_wearable_sleep_stages_deviceId_startTime_endTime ON wearable_sleep_stages(deviceId, startTime, endTime)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS user_account (" +
                        "id INTEGER PRIMARY KEY NOT NULL, " +
                        "uid TEXT, " +
                        "phoneNumber TEXT, " +
                        "email TEXT, " +
                        "displayName TEXT, " +
                        "photoUrl TEXT, " +
                        "provider TEXT, " +
                        "createdAt INTEGER NOT NULL DEFAULT 0, " +
                        "lastSyncMs INTEGER NOT NULL DEFAULT 0, " +
                        "syncEnabled INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audio_recordings ADD COLUMN transcript TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        const val DATABASE_NAME = "sleep_analyzer_database"

        /** Checkpoints the WAL so the primary .db file holds the full, up-to-date dataset. */
        fun checkpoint(context: Context) {
            val db = getDatabase(context)
            db.query("PRAGMA wal_checkpoint(TRUNCATE)", emptyArray()).use { it.moveToFirst() }
        }

        /** Closes the open database so its files can be safely overwritten during a restore. */
        fun closeDatabase() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
