package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.data.model.TaskItem

/**
 * Priority levels for MCA Task Manager tasks.
 */
enum class TaskPriority(val rank: Int, val displayName: String) {
    LOW(1, "Low"),
    NORMAL(2, "Normal"),
    HIGH(3, "High"),
    CRITICAL(4, "Critical");

    companion object {
        fun fromString(value: String): TaskPriority =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: NORMAL
    }
}

/**
 * Workflow status values for MCA Task Manager tasks.
 */
enum class TaskStatus(val displayName: String) {
    TODO("To Do"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    ARCHIVED("Archived"),
    POSTPONED("Postponed");

    companion object {
        fun fromString(value: String): TaskStatus =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: TODO
    }
}

/**
 * Room Entity defining the local SQLite schema for tasks in MCA Task Manager,
 * including priority levels, deadlines, workflow status, and associated category tags.
 */
@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["priority"]),
        Index(value = ["status"]),
        Index(value = ["deadline_epoch_millis"]),
        Index(value = ["category"])
    ]
)
data class Task(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String = "",

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "notes")
    val notes: String = "",

    @ColumnInfo(name = "priority")
    val priority: TaskPriority = TaskPriority.NORMAL,

    @ColumnInfo(name = "status")
    val status: TaskStatus = TaskStatus.TODO,

    @ColumnInfo(name = "deadline_epoch_millis")
    val deadlineEpochMillis: Long = 0L,

    @ColumnInfo(name = "start_date_epoch_millis")
    val startDateEpochMillis: Long = 0L,

    @ColumnInfo(name = "scheduled_time_slot")
    val scheduledTimeSlot: String = "",

    @ColumnInfo(name = "estimated_minutes")
    val estimatedMinutes: Long = 30L,

    @ColumnInfo(name = "actual_minutes_spent")
    val actualMinutesSpent: Long = 0L,

    @ColumnInfo(name = "category")
    val category: String = "Work",

    @ColumnInfo(name = "category_tags")
    val categoryTags: List<String> = emptyList(),

    @ColumnInfo(name = "project")
    val project: String = "General",

    @ColumnInfo(name = "completed")
    val completed: Boolean = false,

    @ColumnInfo(name = "archived")
    val archived: Boolean = false,

    @ColumnInfo(name = "recurrence")
    val recurrence: String = "NONE",

    @ColumnInfo(name = "reminder_offsets_text")
    val reminderOffsetsText: String = "30m before",

    @ColumnInfo(name = "location_label")
    val locationLabel: String = "",

    @ColumnInfo(name = "location_trigger")
    val locationTrigger: String = "NONE",

    @ColumnInfo(name = "subtasks_text")
    val subtasksText: String = "",

    @ColumnInfo(name = "attachment_uri")
    val attachmentUri: String = "",

    @ColumnInfo(name = "attachment_name")
    val attachmentName: String = "",

    @ColumnInfo(name = "depends_on_task_id")
    val dependsOnTaskId: String = "",

    @ColumnInfo(name = "depends_on_task_title")
    val dependsOnTaskTitle: String = "",

    @ColumnInfo(name = "calendar_synced")
    val calendarSynced: Boolean = false,

    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)

/**
 * Room TypeConverters for persisting [TaskPriority], [TaskStatus], and [List] of category tags.
 */
class TaskTypeConverters {
    @TypeConverter
    fun fromPriority(priority: TaskPriority): String = priority.name

    @TypeConverter
    fun toPriority(value: String): TaskPriority = TaskPriority.fromString(value)

    @TypeConverter
    fun fromStatus(status: TaskStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): TaskStatus = TaskStatus.fromString(value)

    @TypeConverter
    fun fromCategoryTagsList(tags: List<String>): String =
        tags.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")

    @TypeConverter
    fun toCategoryTagsList(raw: String): List<String> =
        if (raw.isBlank()) emptyList()
        else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * Bidirectional mappers between the Room [Task] entity and the Firestore-backed [TaskItem] model.
 */
fun Task.toTaskItem(): TaskItem = TaskItem(
    id = id,
    userId = userId,
    title = title,
    description = description,
    notes = notes,
    category = category,
    project = project,
    priority = priority.name,
    status = status.name,
    completed = completed,
    archived = archived,
    startDateEpochMillis = startDateEpochMillis,
    dueDateEpochMillis = deadlineEpochMillis,
    scheduledTimeSlot = scheduledTimeSlot,
    estimatedMinutes = estimatedMinutes,
    actualMinutesSpent = actualMinutesSpent,
    recurrence = recurrence,
    reminderOffsetsText = reminderOffsetsText,
    locationLabel = locationLabel,
    locationTrigger = locationTrigger,
    subtasksText = subtasksText,
    tagsText = categoryTags.joinToString(","),
    attachmentUri = attachmentUri,
    attachmentName = attachmentName,
    dependsOnTaskId = dependsOnTaskId,
    dependsOnTaskTitle = dependsOnTaskTitle,
    calendarSynced = calendarSynced
)

fun TaskItem.toRoomTask(): Task = Task(
    id = id,
    userId = userId,
    title = title,
    description = description,
    notes = notes,
    priority = TaskPriority.fromString(priority),
    status = TaskStatus.fromString(status),
    deadlineEpochMillis = dueDateEpochMillis,
    startDateEpochMillis = startDateEpochMillis,
    scheduledTimeSlot = scheduledTimeSlot,
    estimatedMinutes = estimatedMinutes,
    actualMinutesSpent = actualMinutesSpent,
    category = category,
    categoryTags = parsedTags(),
    project = project,
    completed = completed,
    archived = archived,
    recurrence = recurrence,
    reminderOffsetsText = reminderOffsetsText,
    locationLabel = locationLabel,
    locationTrigger = locationTrigger,
    subtasksText = subtasksText,
    attachmentUri = attachmentUri,
    attachmentName = attachmentName,
    dependsOnTaskId = dependsOnTaskId,
    dependsOnTaskTitle = dependsOnTaskTitle,
    calendarSynced = calendarSynced,
    updatedAtEpochMillis = updatedAt?.toDate()?.time ?: System.currentTimeMillis()
)
