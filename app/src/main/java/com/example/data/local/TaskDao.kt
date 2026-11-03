package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE user_id = :userId ORDER BY completed ASC, deadline_epoch_millis ASC")
    fun observeTasksForUser(userId: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE user_id = :userId AND status = :status ORDER BY deadline_epoch_millis ASC")
    fun observeTasksByStatus(userId: String, status: TaskStatus): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE user_id = :userId AND priority = :priority ORDER BY deadline_epoch_millis ASC")
    fun observeTasksByPriority(userId: String, priority: TaskPriority): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE user_id = :userId AND category = :category ORDER BY deadline_epoch_millis ASC")
    fun observeTasksByCategory(userId: String, category: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: String): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: Task)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(tasks: List<Task>)

    @Update
    suspend fun updateTask(task: Task)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: String)

    @Query("DELETE FROM tasks WHERE user_id = :userId")
    suspend fun clearTasksForUser(userId: String)
}
