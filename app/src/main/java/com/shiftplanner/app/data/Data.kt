package com.shiftplanner.app.data

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.shiftplanner.app.reminder.SystemCalendarSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// ==================== 实体 ====================

/**
 * 一天最多 3 个班次（槽位），epochDay 作为主键天然保证一天一条。
 * 槽 1 为 label/startTime/endTime，槽 2/3 为 label2…（空串 = 未使用）。
 */
@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey val epochDay: Long,
    val label: String,
    val startTime: String,
    val endTime: String,
    val label2: String = "",
    val start2: String = "",
    val end2: String = "",
    val label3: String = "",
    val start3: String = "",
    val end3: String = ""
)

/** 一个班次槽位（名称 + 起止时间） */
data class ShiftSlot(val label: String, val start: String, val end: String)

/** 取出一天里所有已使用的槽位（按 1→2→3 顺序） */
fun slotsOf(e: ShiftEntity): List<ShiftSlot> = buildList {
    if (e.label.isNotBlank()) add(ShiftSlot(e.label, e.startTime, e.endTime))
    if (e.label2.isNotBlank()) add(ShiftSlot(e.label2, e.start2, e.end2))
    if (e.label3.isNotBlank()) add(ShiftSlot(e.label3, e.start3, e.end3))
}

/** 把 ≤3 个槽位组装成一整天的排班记录 */
fun slotsToEntity(day: Long, slots: List<ShiftSlot>): ShiftEntity {
    val s = slots.take(3) + List(3) { ShiftSlot("", "", "") }
    return ShiftEntity(
        epochDay = day,
        label = s[0].label, startTime = s[0].start, endTime = s[0].end,
        label2 = s[1].label, start2 = s[1].start, end2 = s[1].end,
        label3 = s[2].label, start3 = s[2].start, end3 = s[2].end
    )
}

/**
 * 值班标签：预置的班次模板（名称 + 起止时间 + 颜色 + 提醒时间）。
 * 排班时选择标签，把模板内容写入当天。
 */
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startTime: String,
    val endTime: String,
    val colorIndex: Int,      // 0..9，对应 TAG_COLORS
    val remindTime: String = "08:30"   // 值班当天该标签的提醒时刻
)

/** 标签可选的 10 种颜色 */
val TAG_COLORS = listOf(
    0xFF007AFF, 0xFF34C759, 0xFFFF9500, 0xFFFF3B30, 0xFFAF52DE,   // 蓝绿橙红紫
    0xFFFF2D55, 0xFF5AC8FA, 0xFF5856D6, 0xFFA2845E, 0xFF8E8E93    // 粉青靛棕灰
)

fun tagColorOf(index: Int): Long = TAG_COLORS[index.coerceIn(0, TAG_COLORS.size - 1)]

/**
 * 待办标签：预置的待办模板（名称 + 截止时长 + 颜色 + 提醒时间）。
 */
@Entity(tableName = "todo_tags")
data class TodoTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,             // 待办事项名称
    val durationValue: Int = 1,   // 截止时长数值
    val durationUnit: Int = 0,    // 0=小时 1=天
    val colorIndex: Int,          // 0..9，对应 TODO_COLORS
    val remindTime: String = "08:30",
    val remindHoursBefore: Int = 0,
    val remindMinutesBefore: Int = 0
)

/** 待办标签可选的 10 种颜色（与班次标签色系不同） */
val TODO_COLORS = listOf(
    0xFF00BCD4, 0xFF689F38, 0xFFFFC400, 0xFFC2185B, 0xFF6D4C41,   // 青橄榄黄玫红咖啡
    0xFF283593, 0xFF00695C, 0xFF9E9D24, 0xFF455A64, 0xFFEF6C00    // 宝蓝深青绿黄绿蓝灰深橙
)

fun todoColorOf(index: Int): Long = TODO_COLORS[index.coerceIn(0, TODO_COLORS.size - 1)]

/** 待办截止时长文案：如 "24 小时" / "3 天" */
fun todoDurationText(value: Int, unit: Int): String =
    if (unit == 1) "$value 天" else "$value 小时"

/**
 * 待办实例：安排到某天的一个待办（来自待办标签）。
 * 提醒点在添加时推算并存库：
 * 小时单位 = 安排日的当前时刻 + 截止时长（跨天自动进位，自动生成不可改）；
 * 天单位   = 安排日 + 截止天数的当天，按标签设置的提醒时刻。
 */
@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,              // 安排在日历的哪一天（epochDay）
    val name: String,
    val colorIndex: Int,        // 0..9，对应 TODO_COLORS
    val remindDay: Long,        // 提醒日（epochDay）
    val remindTime: String     // 提醒时刻 HH:mm
)

// ==================== 时长计算（算出来的，不存库） ====================

fun parseMinutes(hhmm: String): Int {
    val p = hhmm.split(":")
    return (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
}

/** 跨天班：end == start 时得 24 小时 */
fun durationMinutes(start: String, end: String): Int {
    val s = parseMinutes(start)
    val e = parseMinutes(end)
    return if (e > s) e - s else (24 * 60 - s) + e
}

fun durationHoursText(start: String, end: String): String {
    val m = durationMinutes(start, end)
    val h = m / 60
    val mm = m % 60
    return if (mm == 0) "$h 小时" else "$h 小时 $mm 分钟"
}

// ==================== DAO ====================

@Dao
abstract class ShiftDao {

    @Query("SELECT * FROM shifts ORDER BY epochDay ASC")
    abstract fun observeAll(): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts ORDER BY epochDay ASC")
    abstract suspend fun all(): List<ShiftEntity>

    @Query("SELECT * FROM shifts WHERE epochDay = :day LIMIT 1")
    abstract suspend fun find(day: Long): ShiftEntity?

    @Upsert
    abstract suspend fun upsert(entity: ShiftEntity)

    @Upsert
    abstract suspend fun upsertAll(list: List<ShiftEntity>)

    @Query("DELETE FROM shifts WHERE epochDay = :day")
    abstract suspend fun delete(day: Long)

    @Query("DELETE FROM shifts WHERE epochDay BETWEEN :from AND :to")
    abstract suspend fun deleteRange(from: Long, to: Long)

    @Query("DELETE FROM shifts")
    abstract suspend fun clear()

    /** 交换两天：起点搬到终点，若终点原有排班则搬到起点 */
    @Transaction
    open suspend fun swap(a: Long, b: Long) {
        val x = find(a)
        val y = find(b)
        if (x != null) delete(a)
        if (y != null) delete(b)
        if (x != null) upsert(ShiftEntity(b, x.label, x.startTime, x.endTime))
        if (y != null) upsert(ShiftEntity(a, y.label, y.startTime, y.endTime))
    }
}

// ==================== 数据库 ====================

@Dao
abstract class TagDao {

    @Query("SELECT * FROM tags ORDER BY id ASC")
    abstract fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY id ASC")
    abstract suspend fun all(): List<TagEntity>

    @Upsert
    abstract suspend fun upsert(entity: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    abstract suspend fun delete(id: Long)
}

@Dao
abstract class TodoTagDao {

    @Query("SELECT * FROM todo_tags ORDER BY id ASC")
    abstract fun observeAll(): Flow<List<TodoTagEntity>>

    @Upsert
    abstract suspend fun upsert(entity: TodoTagEntity)

    @Query("DELETE FROM todo_tags WHERE id = :id")
    abstract suspend fun delete(id: Long)
}

@Dao
abstract class TodoDao {

    @Query("SELECT * FROM todos ORDER BY id ASC")
    abstract fun observeAll(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos ORDER BY id ASC")
    abstract suspend fun all(): List<TodoEntity>

    @Upsert
    abstract suspend fun upsert(entity: TodoEntity)

    /** 某天安排的指定待办（同日同名去重用） */
    @Query("SELECT * FROM todos WHERE day = :day AND name = :name LIMIT 1")
    abstract suspend fun findOnDay(day: Long, name: String): TodoEntity?

    /** 提醒日的指定待办（闹钟触发后定位通知内容用） */
    @Query("SELECT * FROM todos WHERE remindDay = :day AND name = :name LIMIT 1")
    abstract suspend fun findRemind(day: Long, name: String): TodoEntity?

    @Query("DELETE FROM todos WHERE id = :id")
    abstract suspend fun delete(id: Long)
}

@Database(entities = [ShiftEntity::class, TagEntity::class, TodoTagEntity::class, TodoEntity::class], version = 6, exportSchema = false)
abstract class PlannerDatabase : RoomDatabase() {
    abstract fun shiftDao(): ShiftDao
    abstract fun tagDao(): TagDao
    abstract fun todoTagDao(): TodoTagDao
    abstract fun todoDao(): TodoDao

    companion object {
        @Volatile private var instance: PlannerDatabase? = null

        /** 表结构升级：1 → 2 新增标签表（不影响已有排班数据） */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tags` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`startTime` TEXT NOT NULL, " +
                        "`endTime` TEXT NOT NULL, " +
                        "`colorIndex` INTEGER NOT NULL)"
                )
            }
        }

        /** 表结构升级：2 → 3 一天最多 3 个班次（新增槽 2/3 列）+ 标签提醒时间 */
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `shifts` ADD COLUMN `label2` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `shifts` ADD COLUMN `start2` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `shifts` ADD COLUMN `end2` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `shifts` ADD COLUMN `label3` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `shifts` ADD COLUMN `start3` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `shifts` ADD COLUMN `end3` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `tags` ADD COLUMN `remindTime` TEXT NOT NULL DEFAULT '08:30'")
            }
        }

        /** 表结构升级：3 → 4 新增待办标签表（不影响已有数据） */
        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `todo_tags` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`durationValue` INTEGER NOT NULL DEFAULT 1, " +
                        "`durationUnit` INTEGER NOT NULL DEFAULT 0, " +
                        "`colorIndex` INTEGER NOT NULL, " +
                        "`remindTime` TEXT NOT NULL DEFAULT '08:30')"
                )
            }
        }

        /** 表结构升级：4 → 5 新增待办实例表（不影响已有数据） */
        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `todos` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`day` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`colorIndex` INTEGER NOT NULL, " +
                        "`remindDay` INTEGER NOT NULL, " +
                        "`remindTime` TEXT NOT NULL)"
                )
            }
        }

        /** 表结构升级：5 → 6 新增待办标签的提前提醒配置 */
        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `todo_tags` ADD COLUMN `remindHoursBefore` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `todo_tags` ADD COLUMN `remindMinutesBefore` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): PlannerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlannerDatabase::class.java,
                    "shifts.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { instance = it }
            }
    }
}

// ==================== 备份管理 ====================

data class BackupItem(
    val file: File,
    val displayName: String,
    val createdAt: LocalDateTime,
    val count: Int
)

object BackupManager {

    private const val DIR = "backups"
    private val TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun dir(context: Context): File = File(context.filesDir, DIR)

    fun list(context: Context): List<BackupItem> {
        val d = dir(context)
        val files = d.listFiles { f -> f.isFile && f.name.endsWith(".bak") } ?: return emptyList()
        return files
            .mapNotNull { f ->
                // 文件名 backup-yyyyMMdd-HHmmss.bak
                val stem = f.name.removePrefix("backup-").removeSuffix(".bak")
                val time = runCatching {
                    LocalDateTime.parse(stem, TS)
                }.getOrNull()
                val count = runCatching {
                    f.readLines().count { it.isNotBlank() }
                }.getOrDefault(0)
                BackupItem(f, f.name, time ?: LocalDateTime.of(1970, 1, 1, 0, 0), count)
            }
            .sortedByDescending { it.createdAt }
    }

    /** 新建备份，返回文件名 */
    fun create(context: Context, all: List<ShiftEntity>): String {
        val d = dir(context)
        if (!d.exists()) d.mkdirs()
        val name = "backup-${LocalDateTime.now().format(TS)}.bak"
        val body = all.joinToString("\n") {
            // 每槽 label 放该槽末位，避免 label 内含分隔符导致解析错乱
            "${it.epochDay}|${it.startTime}|${it.endTime}|${it.label}|" +
                "${it.start2}|${it.end2}|${it.label2}|" +
                "${it.start3}|${it.end3}|${it.label3}"
        }
        File(d, name).writeText(body)
        return name
    }

    /** 恢复：覆盖当前全部排班（兼容旧版单槽格式） */
    fun restore(context: Context, item: BackupItem): List<ShiftEntity> {
        val lines = item.file.readLines()
        val list = mutableListOf<ShiftEntity>()
        for (line in lines) {
            if (line.isBlank()) continue
            val parts = line.split("|", limit = 10)
            val day = parts[0].toLongOrNull() ?: continue
            list += when {
                // 新格式：day|s1|e1|l1|s2|e2|l2|s3|e3|l3
                parts.size >= 10 -> ShiftEntity(
                    day, parts[3], parts[1], parts[2],
                    parts[6], parts[4], parts[5],
                    parts[9], parts[7], parts[8]
                )
                // 旧格式：day|start|end|label
                parts.size >= 4 -> ShiftEntity(day, parts[3], parts[1], parts[2])
                else -> continue
            }
        }
        return list
    }

    fun delete(context: Context, item: BackupItem) {
        runCatching { item.file.delete() }
    }

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { runCatching { it.delete() } }
    }
}

// ==================== ViewModel ====================

class PlannerViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = PlannerDatabase.get(app).shiftDao()
    private val tagDao = PlannerDatabase.get(app).tagDao()
    private val todoTagDao = PlannerDatabase.get(app).todoTagDao()
    private val todoDao = PlannerDatabase.get(app).todoDao()

    /** 全部排班，UI 自动刷新 */
    val shifts: StateFlow<List<ShiftEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 全部值班标签，UI 自动刷新 */
    val tags: StateFlow<List<TagEntity>> = tagDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 全部待办标签，UI 自动刷新 */
    val todoTags: StateFlow<List<TodoTagEntity>> = todoTagDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 全部待办实例，UI 自动刷新 */
    val todos: StateFlow<List<TodoEntity>> = todoDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- 标签 ----
    fun upsertTag(tag: TagEntity) {
        viewModelScope.launch {
            runCatching { tagDao.upsert(tag) }
            syncCalendar()
        }
    }

    fun deleteTag(id: Long) {
        viewModelScope.launch {
            runCatching { tagDao.delete(id) }
            syncCalendar()
        }
    }

    // ---- 待办标签 ----
    fun upsertTodoTag(tag: TodoTagEntity) {
        viewModelScope.launch {
            runCatching { todoTagDao.upsert(tag) }
        }
    }

    fun deleteTodoTag(id: Long) {
        viewModelScope.launch {
            runCatching { todoTagDao.delete(id) }
        }
    }

    // ---- 待办实例 ----
    private val hhMm = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * 安排待办到某天：同日同名再点一次 = 移除。
     * 提醒点推算：小时单位 = 安排日的当前时刻 + 截止时长（跨天进位，自动生成）；
     * 天单位 = 安排日 + 截止天数 的当天，按标签设置的提醒时刻。
     */
    fun addTodoToDay(day: Long, tag: TodoTagEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val exist = todoDao.findOnDay(day, tag.name)
            if (exist != null) {
                todoDao.delete(exist.id)
            } else {
                val (rDay, rTime) = if (tag.durationUnit == 0) {
                        val at = LocalDateTime.of(day.toLocalDate(), LocalTime.now())
                            .plusHours(tag.durationValue.toLong())
                            .minusHours(tag.remindHoursBefore.toLong())
                            .minusMinutes(tag.remindMinutesBefore.toLong())
                        at.toLocalDate().toEpochDay() to hhMm.format(at)
                    } else {
                    (day + tag.durationValue) to tag.remindTime
                }
                todoDao.upsert(
                    TodoEntity(
                        day = day, name = tag.name, colorIndex = tag.colorIndex,
                        remindDay = rDay, remindTime = rTime
                    )
                )
            }
            syncCalendar()
        }
    }

    private fun todoForDay(day: Long, tag: TodoTagEntity): TodoEntity {
        val (remindDay, remindTime) = if (tag.durationUnit == 0) {
            val at = LocalDateTime.of(day.toLocalDate(), LocalTime.now())
                .plusHours(tag.durationValue.toLong())
                .minusHours(tag.remindHoursBefore.toLong())
                .minusMinutes(tag.remindMinutesBefore.toLong())
            at.toLocalDate().toEpochDay() to hhMm.format(at)
        } else {
            (day + tag.durationValue) to tag.remindTime
        }
        return TodoEntity(
            day = day, name = tag.name, colorIndex = tag.colorIndex,
            remindDay = remindDay, remindTime = remindTime
        )
    }

    fun addLabelsToDay(
        day: Long,
        selectedShifts: List<TagEntity>,
        selectedTodos: List<TodoTagEntity>,
        onFull: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = dao.find(day)?.let(::slotsOf).orEmpty().toMutableList()
            var full = false
            selectedShifts.forEach { tag ->
                val index = current.indexOfFirst { it.label == tag.name }
                if (index >= 0) current.removeAt(index)
                else if (current.size < 3) current.add(ShiftSlot(tag.name, tag.startTime, tag.endTime))
                else full = true
            }
            if (selectedShifts.isNotEmpty()) {
                if (current.isEmpty()) dao.delete(day) else dao.upsert(slotsToEntity(day, current))
            }
            selectedTodos.forEach { tag ->
                val existing = todoDao.findOnDay(day, tag.name)
                if (existing != null) todoDao.delete(existing.id)
                else todoDao.upsert(todoForDay(day, tag))
            }
            syncCalendar()
            if (full) withContext(Dispatchers.Main) { onFull() }
        }
    }

    fun generateWithLabels(
        days: List<Long>,
        selectedShifts: List<TagEntity>,
        selectedTodos: List<TodoTagEntity>,
        overwrite: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            days.forEach { day ->
                val existing = dao.find(day)
                if (!overwrite && existing != null) return@forEach
                if (selectedShifts.isNotEmpty()) {
                    dao.upsert(slotsToEntity(day, selectedShifts.map {
                        ShiftSlot(it.name, it.startTime, it.endTime)
                    }))
                }
                selectedTodos.forEach { tag ->
                    if (todoDao.findOnDay(day, tag.name) == null) {
                        todoDao.upsert(todoForDay(day, tag))
                    }
                }
            }
            syncCalendar()
        }
    }

    fun deleteTodo(id: Long) {
        viewModelScope.launch {
            runCatching { todoDao.delete(id) }
            syncCalendar()
        }
    }

    private val prefs = app.getSharedPreferences("planner", Context.MODE_PRIVATE)

    // ---- 外观 ----
    private val _darkTheme = MutableStateFlow(prefs.getBoolean("dark_theme", false))
    val darkTheme: StateFlow<Boolean> = _darkTheme

    fun toggleTheme() {
        _darkTheme.value = !_darkTheme.value
        prefs.edit().putBoolean("dark_theme", _darkTheme.value).apply()
    }

    // ---- 提醒 ----
    private val _remindEnabled = MutableStateFlow(prefs.getBoolean("remind_enabled", false))
    val remindEnabled: StateFlow<Boolean> = _remindEnabled

    fun setRemindEnabled(value: Boolean) {
        _remindEnabled.value = value
        prefs.edit().putBoolean("remind_enabled", value).apply()
    }

    private suspend fun syncCalendar() {
        SystemCalendarSync.sync(getApplication(), prefs.getBoolean("remind_enabled", false))
    }

    // ---- 排班操作 ----

    /** 批量写入完整排班（自动排班 / 区间重排确认） */
    fun saveAll(items: List<ShiftEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            if (items.isNotEmpty()) dao.upsertAll(items)
            syncCalendar()
        }
    }

    /** 编辑页保存：整日多槽覆盖（全部为空则删除该日） */
    fun saveSlots(day: Long, slots: List<ShiftSlot>) {
        viewModelScope.launch(Dispatchers.IO) {
            if (slots.isEmpty()) dao.delete(day)
            else dao.upsert(slotsToEntity(day, slots))
            syncCalendar()
        }
    }

    /** 普通排版：点日期加一个班次槽；同标签已存在则移除；满 3 个回调 onFull */
    fun addTagToDay(day: Long, name: String, start: String, end: String, onFull: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val e = dao.find(day)
            if (e == null) {
                dao.upsert(ShiftEntity(day, name, start, end))
            } else {
                val cur = slotsOf(e)
                val idx = cur.indexOfFirst { it.label == name }
                when {
                    idx >= 0 -> {
                        // 再点一次同标签：移除该槽，后面的前移
                        val rest = cur.filterIndexed { i, _ -> i != idx }
                        if (rest.isEmpty()) dao.delete(day)
                        else dao.upsert(slotsToEntity(day, rest))
                    }
                    cur.size < 3 -> dao.upsert(slotsToEntity(day, cur + ShiftSlot(name, start, end)))
                    else -> withContext(Dispatchers.Main) { onFull() }
                }
            }
            syncCalendar()
        }
    }

    fun delete(day: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.delete(day)
            syncCalendar()
        }
    }

    fun swap(a: Long, b: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.swap(a, b)
            syncCalendar()
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clear()
            syncCalendar()
        }
    }

    /** 自动排班 / 区间重排：先删区间再写入 */
    fun applyRange(from: Long, to: Long, items: List<ShiftEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteRange(from, to)
            if (items.isNotEmpty()) dao.upsertAll(items)
            syncCalendar()
        }
    }

    fun restoreBackup(context: Context, item: BackupItem, onDone: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = BackupManager.restore(context, item)
            dao.clear()
            if (list.isNotEmpty()) dao.upsertAll(list)
            syncCalendar()
            withContext(Dispatchers.Main) { onDone(list.size) }
        }
    }
}

// ==================== 颜色分配 ====================

/** 按全部备注去重排序分配颜色；标签班次优先用标签自带色，其余走调色板 */
fun colorMapFor(
    all: List<ShiftEntity>,
    dark: Boolean,
    tags: List<TagEntity> = emptyList()
): Map<String, Long> {
    val palette = if (dark) {
        listOf(0xFF0A84FF, 0xFF30D158, 0xFFFF9F0A, 0xFFBF5AF2, 0xFF64D2FF)
    } else {
        listOf(0xFF007AFF, 0xFF34C759, 0xFFFF9500, 0xFFAF52DE, 0xFF5AC8FA)
    }
    val tagColors = tags.associate { it.name to tagColorOf(it.colorIndex) }
    val labels = all.flatMap { listOf(it.label, it.label2, it.label3) }
        .filter { it.isNotBlank() }.distinct().sorted()
    return labels.withIndex().associate { (i, l) ->
        l to (tagColors[l] ?: palette[i % palette.size])
    }
}

// ==================== 日期工具 ====================

fun YearMonth.labelText(): String = "${year}年${monthValue}月"

fun LocalDate.cnText(): String = "${monthValue}月${dayOfMonth}日"

fun LocalDate.weekText(): String {
    val names = listOf("日", "一", "二", "三", "四", "五", "六")
    return "星期${names[dayOfWeek.value % 7]}"
}

fun Long.toLocalDate(): LocalDate = LocalDate.ofEpochDay(this)
