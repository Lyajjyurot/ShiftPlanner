package com.shiftplanner.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shiftplanner.app.R
import com.shiftplanner.app.data.PlannerViewModel
import com.shiftplanner.app.data.ShiftEntity
import com.shiftplanner.app.data.TagEntity
import com.shiftplanner.app.data.TodoEntity
import com.shiftplanner.app.data.TodoTagEntity
import com.shiftplanner.app.data.colorMapFor
import com.shiftplanner.app.data.cnText
import com.shiftplanner.app.data.durationMinutes
import com.shiftplanner.app.data.slotsOf
import com.shiftplanner.app.data.toLocalDate
import com.shiftplanner.app.data.todoColorOf
import com.shiftplanner.app.data.weekText
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarPage(
    vm: PlannerViewModel,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onAuto: () -> Unit,
    onRange: () -> Unit,
    onEdit: (Long) -> Unit,
    onSwap: (Long) -> Unit,
    onRemind: () -> Unit
) {
    val shifts by vm.shifts.collectAsState()
    val tags by vm.tags.collectAsState()
    val todoTags by vm.todoTags.collectAsState()
    val todos by vm.todos.collectAsState()
    val today = remember { LocalDate.now() }

    var ym by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    var sheetVisible by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<LocalDate?>(null) }

    // 详情弹层内选标签直接添加班次（满员提示复用 warnFull）
    var sheetPick by remember { mutableStateOf(false) }

    // 普通排版：选中班次标签或待办标签后点日期直接安排
    var pickedShifts by remember { mutableStateOf<List<TagEntity>>(emptyList()) }
    var pickedTodos by remember { mutableStateOf<List<TodoTagEntity>>(emptyList()) }
    var pickerVisible by remember { mutableStateOf(false) }
    var warnFull by remember { mutableStateOf(false) }
    LaunchedEffect(warnFull) {
        if (warnFull) {
            delay(1500)
            warnFull = false
        }
    }

    val colorMap = remember(shifts, dark, tags) { colorMapFor(shifts, dark, tags) }
    val byDay = remember(shifts, ym) {
        shifts.filter { YearMonth.from(it.epochDay.toLocalDate()) == ym }
            .associateBy { it.epochDay }
    }
    val todosByDay = remember(todos) { todos.groupBy { it.day } }
    val monthShifts = byDay.values.toList()

    Column(
        Modifier
            .fillMaxSize()
            .background(C.bg)
    ) {
        PageHeader(
            title = "排班日历",
            subtitle = "${ym.year}年",
            actions = {
                IconCircleButton(R.drawable.ic_sched, "普通排版", onClick = {
                    if (pickedShifts.isNotEmpty() || pickedTodos.isNotEmpty()) {
                        pickedShifts = emptyList()
                        pickedTodos = emptyList()
                    } else pickerVisible = true
                })
                IconCircleButton(R.drawable.ic_auto, "自动排班", onAuto)
                IconCircleButton(R.drawable.ic_trash, "区间重排", onRange)
                IconCircleButton(
                    if (dark) R.drawable.ic_moon else R.drawable.ic_sun,
                    "切换日夜模式", onToggleTheme
                )
            }
        )

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // 普通排版进行中的提示条
            if (pickedShifts.isNotEmpty() || pickedTodos.isNotEmpty()) {
                AlertBar(
                    if (warnFull) "该日已有 3 个班次，部分班次未添加" else
                        "正在安排 ${ (pickedShifts.map { it.name } + pickedTodos.map { it.name }).joinToString("、") }，点日期设置（再次点击取消）",
                    warning = warnFull,
                    actionText = "完成",
                    onAction = { pickedShifts = emptyList(); pickedTodos = emptyList() }
                )
            }

            MonthNav(
                label = "${ym.monthValue}月",
                onPrev = { ym = ym.minusMonths(1) },
                onNext = { ym = ym.plusMonths(1) }
            )

            CalendarGrid(
                ym = ym,
                today = today,
                selectedDay = selected,
                dotColor = { d -> byDay[d.toEpochDay()]?.let { Color(colorMap[it.label] ?: 0xFF007AFF) } },
                onDayClick = { d ->
                    if (pickedShifts.isNotEmpty() || pickedTodos.isNotEmpty()) {
                        vm.addLabelsToDay(d.toEpochDay(), pickedShifts, pickedTodos) {
                            warnFull = true
                        }
                    } else {
                        selected = d
                        sheetVisible = true
                    }
                },
                dayTags = { d ->
                    byDay[d.toEpochDay()]?.let { e ->
                        slotsOf(e).map { s ->
                            DayTag(s.label, colorMap[s.label]?.let { Color(it) })
                        }
                    } ?: emptyList()
                },
                todoDots = { d ->
                    todosByDay[d.toEpochDay()].orEmpty().map { Color(todoColorOf(it.colorIndex)) }
                }
            )

            // 汇总行：有排班时才渲染
            if (monthShifts.isNotEmpty()) {
                val totalMinutes = monthShifts.sumOf { day ->
                    slotsOf(day).sumOf { durationMinutes(it.start, it.end).toLong() }
                }
                Text(
                    "本月 ${monthShifts.size} 天值班 · ${totalMinutes / 60} 小时",
                    style = FontSm.copy(color = C.fg3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            // 空状态
            if (monthShifts.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp, bottom = 48.dp, start = 40.dp, end = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painterResource(R.drawable.ic_tab_cal),
                        contentDescription = null,
                        tint = C.ph,
                        modifier = Modifier.size(46.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "本月还没有排班",
                        style = FontM.copy(color = C.fg2, fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "点上方任意日期即可添加，或用「自动排班」批量生成",
                        style = FontSm.copy(color = C.fg3),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    // 日期操作弹层
    val sel = selected
    if (sel != null) {
        val entity = byDay[sel.toEpochDay()]
        val selDay = sel.toEpochDay()
        val selTodos = todosByDay[selDay].orEmpty()
        BottomSheetOverlay(visible = sheetVisible, onDismiss = { sheetVisible = false }) {
            SheetActions(
                date = sel,
                entity = entity,
                colorMap = colorMap,
                todos = selTodos,
                fullWarn = warnFull,
                onEdit = { sheetVisible = false; onEdit(selDay) },
                onRemind = { sheetVisible = false; onRemind() },
                onSwap = { sheetVisible = false; onSwap(selDay) },
                onDelete = { confirmDelete = sel; sheetVisible = false },
                onDeleteTodo = { vm.deleteTodo(it.id) },
                onAdd = { sheetPick = true }
            )
        }

        // 详情弹层内选标签直接添加班次或待办（详情层保持打开，实时看到新增内容）
        MultiAddPickerSheet(
            visible = sheetPick,
            shiftTags = tags,
            todoTags = todoTags,
            title = "添加班次或待办",
            onDismiss = { sheetPick = false },
            onConfirm = { shiftsToAdd, todosToAdd ->
                vm.addLabelsToDay(selDay, shiftsToAdd, todosToAdd) { warnFull = true }
                sheetPick = false
            }
        )
    }

    ConfirmDialog(
        visible = confirmDelete != null,
        title = "确认删除",
        body = "确认删除 ${confirmDelete?.cnText() ?: ""} 的排班？删除后该日变为无排班。",
        confirmText = "删除",
        onDismiss = { confirmDelete = null },
        onConfirm = {
            confirmDelete?.let { vm.delete(it.toEpochDay()) }
            confirmDelete = null
        }
    )

    // 普通排版：选择班次标签或待办标签
    MultiAddPickerSheet(
        visible = pickerVisible,
        shiftTags = tags,
        todoTags = todoTags,
        onDismiss = { pickerVisible = false },
        onConfirm = { shiftsToAdd, todosToAdd ->
            pickedShifts = shiftsToAdd
            pickedTodos = todosToAdd
            pickerVisible = false
        }
    )
}

@Composable
private fun SheetActions(
    date: LocalDate,
    entity: ShiftEntity?,
    colorMap: Map<String, Long>,
    todos: List<TodoEntity> = emptyList(),
    fullWarn: Boolean = false,
    onEdit: () -> Unit,
    onRemind: () -> Unit,
    onSwap: () -> Unit,
    onDelete: () -> Unit,
    onDeleteTodo: (TodoEntity) -> Unit = {},
    onAdd: () -> Unit
) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(
            "${date.cnText()} ${date.weekText()}",
            style = FontM.copy(fontWeight = FontWeight.SemiBold)
        )
        if (entity != null) {
            Spacer(Modifier.height(4.dp))
            slotsOf(entity).forEach { slot ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(colorMap[slot.label] ?: 0xFF007AFF))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        slot.label,
                        style = FontSm.copy(
                            color = Color(colorMap[slot.label] ?: 0xFF007AFF),
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${slot.start}–${slot.end}",
                        style = FontSm.copy(color = C.fg3)
                    )
                }
                Spacer(Modifier.height(3.dp))
            }
            if (fullWarn) {
                Spacer(Modifier.height(6.dp))
                Text("一天最多安排 3 个班次", style = FontXs.copy(color = C.dng))
            }
            Spacer(Modifier.height(12.dp))
        } else {
            Spacer(Modifier.height(16.dp))
            Text(
                if (todos.isEmpty()) "这一天还没有排班" else "这一天还没有班次",
                style = FontSm.copy(color = C.fg3),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
        }

        // 当天待办列表（提醒日跨天时附日期）
        if (todos.isNotEmpty()) {
            Text("待办", style = FontXs.copy(color = C.fg3, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(6.dp))
            todos.forEach { td ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(todoColorOf(td.colorIndex)))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(td.name, style = FontSm.copy(fontWeight = FontWeight.Medium))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (td.remindDay != date.toEpochDay())
                            "提醒 ${td.remindDay.toLocalDate().cnText()} ${td.remindTime}"
                        else "提醒 ${td.remindTime}",
                        style = FontSm.copy(color = C.fg3)
                    )
                    Spacer(Modifier.weight(1f))
                    IconCircleButton(
                        R.drawable.ic_trash, "删除待办",
                        onClick = { onDeleteTodo(td) }, size = 24.dp
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(6.dp))
        }

        if (entity != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SheetAction(R.drawable.ic_edit, "编辑", C.acc, C.accL, onEdit)
                SheetAction(R.drawable.ic_bell, "提醒", C.acc, C.accL, onRemind)
                SheetAction(R.drawable.ic_swap, "换班", C.acc, C.accL, onSwap)
                SheetAction(R.drawable.ic_plus, "添加", C.grn, C.grn.copy(alpha = 0.12f), onAdd)
                SheetAction(R.drawable.ic_trash, "删除", C.dng, C.dngL, onDelete)
            }
        } else {
            AppButton(
                "添加班次或待办", onAdd,
                modifier = Modifier.fillMaxWidth(),
                icon = R.drawable.ic_plus
            )
        }
    }
}

@Composable
private fun SheetAction(
    icon: Int,
    label: String,
    tint: Color,
    bg: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(bg)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), label, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = FontXs.copy(color = tint, fontWeight = FontWeight.Medium))
    }
}
