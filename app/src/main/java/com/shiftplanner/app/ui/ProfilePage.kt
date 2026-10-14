package com.shiftplanner.app.ui

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.shiftplanner.app.BuildConfig
import com.shiftplanner.app.R
import com.shiftplanner.app.data.PlannerViewModel
import com.shiftplanner.app.reminder.SystemCalendarSync
import kotlinx.coroutines.launch

@Composable
fun ProfilePage(
    vm: PlannerViewModel,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onBackup: () -> Unit
) {
    val shifts by vm.shifts.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.bg)
    ) {
        PageHeader(title = "我的", subtitle = null, actions = {
            IconCircleButton(
                if (dark) R.drawable.ic_moon else R.drawable.ic_sun,
                "切换日夜模式", onToggleTheme
            )
        })

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionTitle("外观")
            SettingsGroup {
                SettingRow(
                    icon = R.drawable.ic_sun,
                    label = "外观模式",
                    rightText = if (dark) "深色" else "浅色",
                    onClick = onToggleTheme
                )
                SettingRow(
                    icon = R.drawable.ic_lang,
                    label = "语言",
                    rightText = "简体中文",
                    onClick = null
                )
            }

            SectionTitle("提醒")
            CalendarReminderSettings(vm)

            SectionTitle("数据")
            SettingsGroup {
                SettingRow(
                    icon = R.drawable.ic_upload,
                    label = "备份与恢复",
                    onClick = onBackup
                )
                if (shifts.isNotEmpty()) {
                    SettingRow(
                        icon = R.drawable.ic_trash,
                        label = "清空全部排班",
                        rightText = "${shifts.size} 条",
                        onClick = { confirmClear = true }
                    )
                }
            }

            SectionTitle("关于")
            SettingsGroup {
                SettingRow(
                    icon = R.drawable.ic_info,
                    label = "版本",
                    rightText = "v${BuildConfig.VERSION_NAME}",
                    onClick = null
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "排班数据保存在本机；开启提醒后会写入所选系统日历，日历账户可能同步到云端。应用未申请网络权限。",
                style = FontXs.copy(color = C.fg3),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))
        }
    }

    ConfirmDialog(
        visible = confirmClear,
        title = "清空全部排班",
        body = "将删除全部 ${shifts.size} 条排班数据，此操作不可恢复。",
        confirmText = "清空",
        onDismiss = { confirmClear = false },
        onConfirm = {
            vm.clearAll()
            confirmClear = false
        }
    )
}

@Composable
internal fun CalendarReminderSettings(vm: PlannerViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull() as? LifecycleOwner
    }
    val scope = rememberCoroutineScope()
    val enabled by vm.remindEnabled.collectAsState()
    var calendars by remember { mutableStateOf<List<SystemCalendarSync.CalendarOption>>(emptyList()) }
    var selectedId by remember { mutableStateOf(SystemCalendarSync.selectedCalendarId(context)) }
    var permissionGranted by remember { mutableStateOf(false) }
    var chooserOpen by remember { mutableStateOf(false) }
    var pendingEnable by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var ownedCount by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** 刷新「本工具已写入日历的事件数」，用于决定删除按钮是否可用 */
    fun refreshOwnedCount() {
        permissionGranted = hasPermission()
        if (!permissionGranted) {
            ownedCount = 0
            return
        }
        scope.launch { ownedCount = SystemCalendarSync.ownedEventCount(context) }
    }

    fun loadCalendars(openChooser: Boolean = false) {
        if (busy) return
        selectedId = SystemCalendarSync.selectedCalendarId(context)
        permissionGranted = hasPermission()
        if (!permissionGranted) {
            calendars = emptyList()
            chooserOpen = false
            pendingEnable = false
            if (openChooser || enabled) {
                status = "请在「日历权限」中授予读写权限；若提醒已开启，可先关闭开关"
                error = true
            }
            return
        }
        busy = true
        scope.launch {
            when (val result = SystemCalendarSync.listCalendars(context)) {
                is SystemCalendarSync.CalendarListResult.Success -> {
                    calendars = result.calendars
                    selectedId = SystemCalendarSync.selectedCalendarId(context)
                    if (calendars.isEmpty()) {
                        status = "没有可写日历，请先在系统日历中创建或启用日历"
                        error = true
                    } else if (openChooser) {
                        chooserOpen = true
                        status = null
                    } else if (selectedId != null && calendars.none { it.id == selectedId }) {
                        status = "已选日历不可写，请在「同步日历」中重新选择"
                        error = true
                    } else if (error) {
                        status = null
                        error = false
                    }
                }
                SystemCalendarSync.CalendarListResult.PermissionDenied -> {
                    permissionGranted = false
                    calendars = emptyList()
                    status = "日历权限已失效，请在「日历权限」中重新授权"
                    error = true
                }
                is SystemCalendarSync.CalendarListResult.Failure -> {
                    calendars = emptyList()
                    status = "读取日历失败：${result.cause.localizedMessage ?: "未知错误"}，请重试"
                    error = true
                }
            }
            busy = false
        }
    }

    /** 一次性删除本工具写入的全部日历日程（忽略导入批次） */
    fun deleteAllSynced() {
        if (busy) return
        permissionGranted = hasPermission()
        if (!permissionGranted) {
            status = "请先在「日历权限」中授予读写权限，再删除日历日程"
            error = true
            return
        }
        busy = true
        status = "正在删除日历日程…"
        error = false
        scope.launch {
            when (val result = SystemCalendarSync.deleteAllSynced(context)) {
                is SystemCalendarSync.Result.Success -> {
                    // 全部日程已清除，关闭提醒开关，避免再次写入
                    vm.setRemindEnabled(false)
                    status = "已删除 ${result.deleted} 条本工具导入的日历日程"
                }
                else -> {
                    if (result == SystemCalendarSync.Result.PermissionDenied) {
                        permissionGranted = false
                        calendars = emptyList()
                    }
                    status = calendarSyncError(result)
                    error = true
                }
            }
            ownedCount = SystemCalendarSync.ownedEventCount(context)
            busy = false
        }
    }

    fun sync(want: Boolean) {
        if (busy) return
        permissionGranted = hasPermission()
        if (!permissionGranted) {
            calendars = emptyList()
            if (!want) {
                vm.setRemindEnabled(false)
                status = "提醒已关闭；未获日历权限，已有日历事件可能仍在。重新授权后点「立即同步」以移除"
            } else {
                status = "请先在「日历权限」中授予读写权限，再开启提醒"
            }
            error = true
            return
        }
        selectedId = SystemCalendarSync.selectedCalendarId(context)
        if (want && selectedId == null) {
            pendingEnable = true
            loadCalendars(openChooser = true)
            return
        }
        busy = true
        status = if (want) "正在同步到日历…" else "正在移除日历提醒…"
        error = false
        scope.launch {
            when (val result = SystemCalendarSync.sync(context, want)) {
                is SystemCalendarSync.Result.Success -> {
                    vm.setRemindEnabled(want)
                    status = if (want) "同步完成：新增 ${result.inserted}、更新 ${result.updated}、移除 ${result.deleted} 条" else "已关闭，日历提醒已移除"
                    ownedCount = SystemCalendarSync.ownedEventCount(context)
                }
                else -> {
                    if (!want && result == SystemCalendarSync.Result.PermissionDenied) {
                        vm.setRemindEnabled(false)
                        permissionGranted = false
                        calendars = emptyList()
                        status = "提醒已关闭；未获日历权限，已有日历事件可能仍在。重新授权后点「立即同步」以移除"
                    } else {
                        if (result == SystemCalendarSync.Result.PermissionDenied) {
                            permissionGranted = false
                            calendars = emptyList()
                        }
                        status = calendarSyncError(result)
                    }
                    error = true
                }
            }
            busy = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionGranted = permissions[Manifest.permission.READ_CALENDAR] == true &&
            permissions[Manifest.permission.WRITE_CALENDAR] == true
        if (permissionGranted) loadCalendars() else {
            calendars = emptyList()
            status = "未获得日历读写权限，请在系统设置中允许后返回；若提醒已开启，可先关闭开关"
            error = true
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loadCalendars()
                refreshOwnedCount()
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    SettingsGroup {
        SettingRow(
            icon = R.drawable.ic_bell,
            label = "日历提醒",
            rightSwitch = enabled,
            onSwitch = { if (!busy) sync(it) }
        )
        SettingRow(
            icon = R.drawable.ic_sched,
            label = "日历权限",
            rightText = if (permissionGranted) "已授权" else "未授权",
            onClick = {
                if (hasPermission()) loadCalendars() else permissionLauncher.launch(
                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                )
            }
        )
        SettingRow(
            icon = R.drawable.ic_tab_cal,
            label = "同步日历",
            rightText = calendars.firstOrNull { it.id == selectedId }?.name ?: "请选择",
            onClick = { loadCalendars(openChooser = true) }
        )
        SettingRow(
            icon = R.drawable.ic_auto,
            label = "立即同步",
            onClick = { if (!busy) sync(enabled) }
        )
        SettingRow(
            icon = R.drawable.ic_trash,
            label = "删除日历日程",
            rightText = if (ownedCount > 0) "$ownedCount 条" else "无可删除记录",
            enabled = ownedCount > 0 && !busy,
            onClick = { if (!busy) confirmDeleteAll = true }
        )
    }
    if (status != null) AlertBar(status!!, warning = error)
    if (ownedCount == 0 && permissionGranted) {
        Text(
            "未找到由本工具导入的日历日程",
            style = FontXs.copy(color = C.fg3),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            textAlign = TextAlign.Center
        )
    }

    ConfirmDialog(
        visible = confirmDeleteAll,
        title = "删除全部日历日程",
        body = "将删除本工具导入的全部排班与待办日程（共 $ownedCount 条），不影响日历中的其他私人日程。此操作不可恢复。",
        confirmText = "删除",
        onDismiss = { confirmDeleteAll = false },
        onConfirm = {
            confirmDeleteAll = false
            deleteAllSynced()
        }
    )

    if (chooserOpen) {
        AlertDialog(
            onDismissRequest = { chooserOpen = false; pendingEnable = false },
            title = { Text("选择可写日历") },
            text = {
                Column {
                    calendars.forEach { calendar ->
                        SettingRow(
                            icon = R.drawable.ic_tab_cal,
                            label = calendar.name,
                            rightText = calendar.accountName,
                            onClick = {
                                chooserOpen = false
                                if (busy) return@SettingRow
                                busy = true
                                scope.launch {
                                    val previous = selectedId
                                    val enableAfterSelection = pendingEnable
                                    pendingEnable = false
                                    when (val result = SystemCalendarSync.selectCalendar(context, calendar.id)) {
                                        SystemCalendarSync.SelectionResult.Success -> {
                                            selectedId = calendar.id
                                            if (enabled || enableAfterSelection) {
                                                when (val synced = SystemCalendarSync.sync(context, true)) {
                                                    is SystemCalendarSync.Result.Success -> {
                                                        if (enableAfterSelection) vm.setRemindEnabled(true)
                                                        status = "同步完成：新增 ${synced.inserted}、更新 ${synced.updated}、移除 ${synced.deleted} 条"
                                                        error = false
                                                    }
                                                    else -> {
                                                        SystemCalendarSync.selectCalendar(context, previous)
                                                        selectedId = previous
                                                        if (synced == SystemCalendarSync.Result.PermissionDenied) {
                                                            permissionGranted = false
                                                            calendars = emptyList()
                                                        }
                                                        status = calendarSyncError(synced)
                                                        error = true
                                                    }
                                                }
                                            } else {
                                                status = "已选择日历：${calendar.name}"
                                                error = false
                                            }
                                        }
                                        SystemCalendarSync.SelectionResult.PermissionDenied -> {
                                            permissionGranted = false
                                            calendars = emptyList()
                                            status = "日历权限已失效，请在「日历权限」中重新授权"
                                            error = true
                                        }
                                        SystemCalendarSync.SelectionResult.NoWritableCalendar -> {
                                            status = "该日历不可写，请在「同步日历」中重新选择"
                                            error = true
                                        }
                                        is SystemCalendarSync.SelectionResult.Failure -> {
                                            status = "选择日历失败：${result.cause.localizedMessage ?: "未知错误"}，请重试"
                                            error = true
                                        }
                                    }
                                    busy = false
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { chooserOpen = false; pendingEnable = false }) { Text("取消") }
            }
        )
    }
}

private fun calendarSyncError(result: SystemCalendarSync.Result): String = when (result) {
    SystemCalendarSync.Result.PermissionDenied -> "日历权限已失效，请在「日历权限」中重新授权后重试"
    SystemCalendarSync.Result.NoCalendarSelected -> "请在「同步日历」中选择可写日历后重试"
    SystemCalendarSync.Result.NoWritableCalendar -> "已选日历不可写，请在「同步日历」中重新选择后重试"
    is SystemCalendarSync.Result.Failure -> "日历同步失败：${result.cause.localizedMessage ?: "未知错误"}，请点「立即同步」重试"
    is SystemCalendarSync.Result.Success -> "同步完成"
}
