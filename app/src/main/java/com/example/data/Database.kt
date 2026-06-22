package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bacbo_rounds", indices = [Index(value = ["resultado", "numero", "horario"], unique = true)])
data class BacBoRound(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMillis: Long = System.currentTimeMillis(),
    val resultado: String, // "PLAYER", "BANKER", "TIE"
    val numero: Int,
    val horario: String
)

@Dao
interface BacBoDao {
    @Query("SELECT * FROM bacbo_rounds ORDER BY dateMillis DESC")
    fun getAllRounds(): Flow<List<BacBoRound>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRound(round: BacBoRound): Long

    @Query("DELETE FROM bacbo_rounds WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bacbo_rounds")
    suspend fun deleteAll()
}

@Database(entities = [BacBoRound::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bacBoDao(): BacBoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bacbo_monitor_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
