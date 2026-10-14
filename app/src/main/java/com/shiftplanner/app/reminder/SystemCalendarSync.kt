package com.shiftplanner.app.reminder

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import com.shiftplanner.app.data.PlannerDatabase
import com.shiftplanner.app.data.slotsOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object SystemCalendarSync {
    sealed interface Result {
        data class Success(val inserted: Int, val updated: Int, val deleted: Int) : Result
        data object PermissionDenied : Result
        data object NoCalendarSelected : Result
        data object NoWritableCalendar : Result
        data class Failure(val cause: Exception) : Result
    }

    data class CalendarOption(val id: Long, val name: String, val accountName: String)

    sealed interface CalendarListResult {
        data class Success(val calendars: List<CalendarOption>) : CalendarListResult
        data object PermissionDenied : CalendarListResult
        data class Failure(val cause: Exception) : CalendarListResult
    }

    sealed interface SelectionResult {
        data object Success : SelectionResult
        data object PermissionDenied : SelectionResult
        data object NoWritableCalendar : SelectionResult
        data class Failure(val cause: Exception) : SelectionResult
    }

    private data class Event(
        val key: String,
        val title: String,
        val start: Long,
        val end: Long,
        val description: String
    )

    private data class Reminder(val id: Long, val minutes: Int, val method: Int)

    private data class Existing(
        val id: Long,
        val calendarId: Long,
        val event: Event,
        val reminders: List<Reminder>,
        val timezone: String
    )

    private const val KEY_PREFIX = "shiftplanner://calendar-sync/"
    private const val PREFS = "system_calendar_sync"
    private const val SELECTED_ID = "selected_calendar_id"
    private const val OWNED_IDS = "owned_event_ids"
    private val mutex = Mutex()

    fun selectedCalendarId(context: Context): Long? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.contains(SELECTED_ID)) prefs.getLong(SELECTED_ID, -1L) else null
    }

    /** 本地登记的、由本工具写入的系统日历事件 ID，删除时按此精确命中 */
    private fun ownedIds(context: Context): MutableSet<Long> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(OWNED_IDS, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toMutableSet() ?: mutableSetOf()
    }

    private fun saveOwnedIds(context: Context, ids: Set<Long>) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(OWNED_IDS, ids.map { it.toString() }.toHashSet()).apply()
    }

    suspend fun listCalendars(context: Context): CalendarListResult = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!hasPermission(app, false)) return@withContext CalendarListResult.PermissionDenied
        try {
            CalendarListResult.Success(writableCalendars(app))
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            CalendarListResult.PermissionDenied
        } catch (e: Exception) {
            CalendarListResult.Failure(e)
        }
    }

    suspend fun selectCalendar(context: Context, calendarId: Long?): SelectionResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            if (calendarId == null) {
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(SELECTED_ID).apply()
                return@withContext SelectionResult.Success
            }
            if (!hasPermission(app, false)) return@withContext SelectionResult.PermissionDenied
            try {
                if (writableCalendars(app).none { it.id == calendarId }) {
                    return@withContext SelectionResult.NoWritableCalendar
                }
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putLong(SELECTED_ID, calendarId).apply()
                SelectionResult.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                SelectionResult.PermissionDenied
            } catch (e: Exception) {
                SelectionResult.Failure(e)
            }
        }
    }

    suspend fun deleteAllSynced(context: Context): Result = sync(context, enabled = false)

    /**
     * 本工具已写入的系统日历事件数量。
     * 取「本地注册表」与「内容提供者中可识别的旧数据」的并集，
     * 两者都为空才判定为没有可删除记录。
     */
    suspend fun ownedEventCount(context: Context): Int = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!hasPermission(app, false)) return@withContext 0
        val fromRegistry = ownedIds(app)
        val fromProvider = runCatching { scanOwnedEvents(app).map { it.id }.toSet() }
            .getOrDefault(emptySet())
        (fromRegistry + fromProvider).size
    }

    suspend fun sync(context: Context, enabled: Boolean = true): Result = mutex.withLock {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            if (!hasPermission(app, true)) return@withContext Result.PermissionDenied
            try {
                val calendarId = if (enabled) {
                    val selected = selectedCalendarId(app) ?: return@withContext Result.NoCalendarSelected
                    if (writableCalendars(app).none { it.id == selected }) {
                        return@withContext Result.NoWritableCalendar
                    }
                    selected
                } else null
                val resolver = app.contentResolver
                val zone = ZoneId.systemDefault()
                val desired = if (enabled) desiredEvents(app, zone) else emptyMap()
                val existing = scanOwnedEvents(app)
                val registry = ownedIds(app)
                val scanned = existing.map { it.id }.toSet()

                val retained = mutableSetOf<Long>()
                var inserted = 0
                var updated = 0
                var deleted = 0
                var firstDeleteError: Exception? = null
                desired.values.forEach { event ->
                    val old = existing.filter { it.event.key == event.key && it.calendarId == calendarId }
                        .minWithOrNull(compareBy<Existing>({ it.event != event || it.timezone != zone.id }, { it.id }))
                    if (old == null) {
                        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values(app, calendarId!!, zone.id, event))
                            ?: throw IOException("Cannot insert calendar event")
                        val id = ContentUris.parseId(uri)
                        try {
                            insertReminder(app, id)
                        } catch (e: Exception) {
                            try {
                                if (resolver.delete(uri, null, null) != 1) {
                                    e.addSuppressed(IOException("Cannot roll back calendar event $id"))
                                }
                            } catch (cleanup: Exception) {
                                e.addSuppressed(cleanup)
                            }
                            throw e
                        }
                        registry += id
                        inserted++
                    } else {
                        registry += old.id
                        retained += old.id
                        val eventChanged = old.event != event || old.timezone != zone.id
                        val reminderChanged = old.reminders.size != 1 || old.reminders[0].minutes != 0 ||
                            old.reminders[0].method != CalendarContract.Reminders.METHOD_ALERT
                        if (eventChanged || reminderChanged) {
                            val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, old.id)
                            if (eventChanged && resolver.update(eventUri, values(app, calendarId!!, zone.id, event), null, null) != 1) {
                                throw IOException("Cannot update calendar event ${old.id}")
                            }
                            if (reminderChanged) {
                                val keep = old.reminders.filter {
                                    it.minutes == 0 && it.method == CalendarContract.Reminders.METHOD_ALERT
                                }.minByOrNull { it.id }?.id ?: ContentUris.parseId(insertReminder(app, old.id))
                                old.reminders.filter { it.id != keep }.forEach { reminder ->
                                    val reminderUri = ContentUris.withAppendedId(CalendarContract.Reminders.CONTENT_URI, reminder.id)
                                    if (resolver.delete(reminderUri, null, null) != 1) {
                                        throw IOException("Cannot delete calendar reminder ${reminder.id}")
                                    }
                                }
                            }
                            updated++
                        }
                    }
                }
                existing.filter { it.id !in retained }.forEach { old ->
                    val removed = deleteEvent(app, old.id, old.reminders.map { it.id })
                    if (removed >= 0) {
                        // 已删除或本就不存在，都视为已清理
                        if (removed > 0) deleted++
                        registry -= old.id
                    } else {
                        // 单条删除失败不中断，尽量清掉其余日程
                        firstDeleteError = firstDeleteError ?: IOException("Cannot delete calendar event ${old.id}")
                    }
                }
                // 关闭提醒时按本地注册表兜底清除：部分日历会剥离自定义字段，内容查询可能漏掉
                if (!enabled) {
                    (registry - scanned).forEach { id ->
                        if (deleteEvent(app, id, emptyList()) > 0) deleted++
                    }
                    registry.clear()
                }
                saveOwnedIds(app, registry)
                firstDeleteError?.let { throw it }
                Result.Success(inserted, updated, deleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                Result.PermissionDenied
            } catch (e: Exception) {
                Result.Failure(e)
            }
        }
    }

    /** 查询系统日历中所有归本工具所有的事件（含自定义字段被剥离的旧数据） */
    private fun scanOwnedEvents(context: Context): List<Existing> {
        val resolver = context.contentResolver
        val columns = arrayOf(
            CalendarContract.Events._ID, CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.CUSTOM_APP_URI, CalendarContract.Events.CUSTOM_APP_PACKAGE,
            CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND, CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_TIMEZONE, CalendarContract.Events.DELETED
        )
        val out = mutableListOf<Existing>()
        val cursor = resolver.query(CalendarContract.Events.CONTENT_URI, columns, null, null, null)
            ?: throw IOException("Cannot query calendar events")
        cursor.use { rows ->
            while (rows.moveToNext()) {
                if (rows.getInt(9) != 0) continue
                val key = rows.getString(2)
                val pkg = rows.getString(3)
                val title = rows.getString(4) ?: ""
                val description = rows.getString(7) ?: ""
                if (!isOwnedEvent(context.packageName, pkg, key, description)) continue
                val id = rows.getLong(0)
                out += Existing(
                    id, rows.getLong(1),
                    Event(key ?: "", title, rows.getLong(5), rows.getLong(6), description),
                    remindersOf(resolver, id), rows.getString(8) ?: ""
                )
            }
        }
        return out
    }

    private fun remindersOf(resolver: ContentResolver, eventId: Long): List<Reminder> {
        val cursor = resolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(
                CalendarContract.Reminders._ID, CalendarContract.Reminders.MINUTES,
                CalendarContract.Reminders.METHOD
            ),
            "${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()), null
        ) ?: throw IOException("Cannot query calendar reminders")
        return cursor.use { rows ->
            buildList {
                while (rows.moveToNext()) {
                    add(Reminder(rows.getLong(0), rows.getInt(1), rows.getInt(2)))
                }
            }
        }
    }

    /**
     * 删除一个日历事件（先删提醒行，再删事件，避免部分日历残留提醒）。
     * 返回删除的事件行数：>=0 表示无异常（0 表示事件本就不存在），-1 表示失败。
     */
    private fun deleteEvent(context: Context, eventId: Long, reminderIds: List<Long>): Int {
        val resolver = context.contentResolver
        reminderIds.forEach { reminderId ->
            runCatching {
                resolver.delete(
                    ContentUris.withAppendedId(CalendarContract.Reminders.CONTENT_URI, reminderId),
                    null, null
                )
            }
        }
        return try {
            resolver.delete(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                null, null
            )
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            -1
        }
    }

    private suspend fun desiredEvents(context: Context, zone: ZoneId): Map<String, Event> {
        data class GroupKey(val kind: String, val time: LocalDateTime)
        data class GroupBody(val names: MutableList<String>, val lines: MutableList<String>)

        val db = PlannerDatabase.get(context)
        val tagTimes = db.tagDao().all().associate { it.name to it.remindTime }
        val grouped = java.util.TreeMap<GroupKey, GroupBody>(
            compareBy({ it.time }, { it.kind })
        )
        db.shiftDao().all().forEach { shift ->
            val day = LocalDate.ofEpochDay(shift.epochDay)
            slotsOf(shift).forEach { slot ->
                val time = LocalDateTime.of(day, LocalTime.parse(tagTimes[slot.label] ?: "08:30"))
                val body = grouped.getOrPut(GroupKey("shift", time)) {
                    GroupBody(mutableListOf(), mutableListOf())
                }
                body.names += slot.label
                body.lines += "班次：${slot.label} · ${slot.start}–${slot.end}"
            }
        }
        db.todoDao().all().forEach { todo ->
            val time = LocalDateTime.of(LocalDate.ofEpochDay(todo.remindDay), LocalTime.parse(todo.remindTime))
            val body = grouped.getOrPut(GroupKey("todo", time)) {
                GroupBody(mutableListOf(), mutableListOf())
            }
            body.names += todo.name
            body.lines += "待办：${todo.name}（安排日：${LocalDate.ofEpochDay(todo.day)}）"
        }
        return grouped.map { (group, body) ->
            val at = group.time.atZone(zone).toInstant().toEpochMilli()
            // 标题直接使用班次/待办标签名称；同刻多条时合并展示
            val title = body.names.distinct().joinToString("、")
            val event = Event(
                "$KEY_PREFIX${group.kind}/${group.time}", title, at, at + 30 * 60 * 1000L,
                body.lines.sorted().joinToString("\n")
            )
            event.key to event
        }.toMap()
    }

    /**
     * 事件是否由本应用写入。命中任一即认为归本应用所有：
     * 1) CUSTOM_APP_PACKAGE 为本应用包名（正常情况）；
     * 2) CUSTOM_APP_URI 使用本应用专用前缀（部分日历应用会清空 package 字段）；
     * 3) 描述完全由本应用的“班次：/待办：”格式行组成（自定义字段被同步适配器清除的旧数据）。
     */
    private fun isOwnedEvent(packageName: String, pkg: String?, key: String?, description: String): Boolean {
        if (pkg == packageName) return true
        if (key != null && key.startsWith(KEY_PREFIX)) return true
        val lines = description.lines().filter { it.isNotBlank() }
        return lines.isNotEmpty() && lines.all { it.startsWith("班次：") || it.startsWith("待办：") }
    }

    private fun hasPermission(context: Context, write: Boolean): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            (!write || context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED)

    private fun writableCalendars(context: Context): List<CalendarOption> {
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME),
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.SYNC_EVENTS} = 1",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC, ${CalendarContract.Calendars._ID} ASC"
        ) ?: throw IOException("Cannot query calendars")
        return cursor.use { rows ->
            buildList {
                while (rows.moveToNext()) {
                    add(CalendarOption(rows.getLong(0), rows.getString(1) ?: "", rows.getString(2) ?: ""))
                }
            }
        }
    }

    private fun values(context: Context, calendarId: Long, zone: String, event: Event) = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendarId)
        put(CalendarContract.Events.TITLE, event.title)
        put(CalendarContract.Events.DTSTART, event.start)
        put(CalendarContract.Events.DTEND, event.end)
        put(CalendarContract.Events.EVENT_TIMEZONE, zone)
        put(CalendarContract.Events.DESCRIPTION, event.description)
        put(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
        put(CalendarContract.Events.CUSTOM_APP_URI, event.key)
        put(CalendarContract.Events.HAS_ALARM, 1)
    }

    private fun insertReminder(context: Context, eventId: Long) =
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, 0)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }) ?: throw IOException("Cannot insert calendar reminder for $eventId")
}
