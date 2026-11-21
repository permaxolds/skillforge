package com.skillforge.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val category: String = "Personal Development",
    val priority: Int = 1,               // 0 low, 1 medium, 2 high
    val startDay: Long,                  // epoch day
    val deadlineDay: Long? = null,       // epoch day; for ONCE it is the due date
    val frequency: String = "DAILY",     // DAILY WEEKLY MONTHLY CUSTOM ONCE
    val customDays: Int = 0,             // bitmask, bit0 = Monday
    val estMinutes: Int = 30,
    val reminderMin: Int? = null,        // minutes after local midnight
    val archived: Boolean = false
)

/** One row per completed (or skipped/rescheduled) occurrence of a goal. */
@Entity(tableName = "completions", primaryKeys = ["goalId", "periodKey"])
data class Completion(val goalId: Long, val periodKey: String, val doneAt: Long, val skipped: Boolean = false)

@Entity(tableName = "chat")
data class ChatMsg(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,                    // user | assistant | error
    val text: String,
    val proposals: String? = null,       // JSON array of {tool,args,state,result}
    val ts: Long = System.currentTimeMillis()
)

@Entity(tableName = "memory")
data class Mem(@PrimaryKey val k: String, val v: String)

@Dao
interface AppDao {
    @Query("SELECT * FROM goals WHERE archived=0 ORDER BY id DESC") fun goals(): Flow<List<Goal>>
    @Query("SELECT * FROM goals WHERE archived=0") suspend fun goalsNow(): List<Goal>
    @Query("SELECT * FROM goals WHERE id=:id") suspend fun goal(id: Long): Goal?
    @Insert suspend fun insert(g: Goal): Long
    @Update suspend fun update(g: Goal)
    @Query("DELETE FROM goals WHERE id=:id") suspend fun deleteGoal(id: Long)
    @Query("DELETE FROM completions WHERE goalId=:id") suspend fun deleteCompletions(id: Long)

    @Query("SELECT * FROM completions") fun completions(): Flow<List<Completion>>
    @Query("SELECT * FROM completions") suspend fun completionsNow(): List<Completion>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addCompletion(c: Completion)
    @Query("DELETE FROM completions WHERE goalId=:id AND periodKey=:k") suspend fun removeCompletion(id: Long, k: String)

    @Query("SELECT * FROM chat ORDER BY id") fun chat(): Flow<List<ChatMsg>>
    @Query("SELECT * FROM chat ORDER BY id DESC LIMIT :n") suspend fun lastChat(n: Int): List<ChatMsg>
    @Insert suspend fun addChat(m: ChatMsg): Long
    @Update suspend fun updateChat(m: ChatMsg)
    @Query("DELETE FROM chat") suspend fun clearChat()

    @Query("SELECT * FROM memory ORDER BY k") fun memory(): Flow<List<Mem>>
    @Query("SELECT * FROM memory") suspend fun memoryNow(): List<Mem>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMem(m: Mem)
    @Query("DELETE FROM memory WHERE k=:k") suspend fun delMem(k: String)
    @Query("DELETE FROM memory") suspend fun clearMem()
}

@Database(entities = [Goal::class, Completion::class, ChatMsg::class, Mem::class], version = 1, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object {
        @Volatile private var inst: Db? = null
        fun get(c: Context): Db = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, Db::class.java, "skillforge.db").build().also { inst = it }
        }
    }
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("sf", Context.MODE_PRIVATE)
    fun url(c: Context): String = sp(c).getString("url", "") ?: ""
    fun token(c: Context): String = sp(c).getString("token", "") ?: ""
    fun set(c: Context, url: String, token: String) = sp(c).edit().putString("url", url).putString("token", token).apply()
}
