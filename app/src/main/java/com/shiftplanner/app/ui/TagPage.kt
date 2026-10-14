package com.shiftplanner.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiftplanner.app.R
import com.shiftplanner.app.data.PlannerViewModel
import com.shiftplanner.app.data.TAG_COLORS
import com.shiftplanner.app.data.TagEntity
import com.shiftplanner.app.data.TODO_COLORS
import com.shiftplanner.app.data.TodoTagEntity
import com.shiftplanner.app.data.durationHoursText
import com.shiftplanner.app.data.parseMinutes
import com.shiftplanner.app.data.tagColorOf
import com.shiftplanner.app.data.todoColorOf
import com.shiftplanner.app.data.todoDurationText

// ==================== 标签管理页（上半：班次标签 / 下半：待办标签） ====================

@Composable
fun TagPage(vm: PlannerViewModel) {
    val tags by vm.tags.collectAsState()
    val todoTags by vm.todoTags.collectAsState()

    var editing by remember { mutableStateOf<TagEntity?>(null) }           // 班次标签编辑弹层
    var deleting by remember { mutableStateOf<TagEntity?>(null) }
    var todoEditing by remember { mutableStateOf<TodoTagEntity?>(null) }   // 待办标签编辑弹层
    var todoDeleting by remember { mutableStateOf<TodoTagEntity?>(null) }

    Column(Modifier.fillMaxSize().background(C.bg)) {
        PageHeader(title = "标签", subtitle = "班次与待办模板")

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            // ---- 上半：班次标签 ----
            SectionHeader("班次标签") {
                editing = TagEntity(name = "", startTime = "09:00", endTime = "18:00", colorIndex = 0)
            }
            if (tags.isEmpty()) {
                SectionEmptyHint("还没有班次标签，点击右侧 + 创建，排班时可直接选用")
            } else {
                Column(
                    Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    tags.forEach { tag ->
                        ShiftTagRow(
                            tag,
                            onEdit = { editing = it },
                            onDelete = { deleting = it }
                        )
                    }
                }
            }

            // ---- 下半：待办标签 ----
            SectionHeader("待办标签") {
                todoEditing = TodoTagEntity(name = "", durationValue = 1, durationUnit = 0, colorIndex = 0)
            }
            if (todoTags.isEmpty()) {
                SectionEmptyHint("还没有待办标签，点击右侧 + 创建")
            } else {
                Column(
                    Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    todoTags.forEach { tag ->
                        TodoTagRow(
                            tag,
                            onEdit = { todoEditing = it },
                            onDelete = { todoDeleting = it }
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // 班次标签：编辑 / 新建弹层
    editing?.let { tag ->
        TagEditSheet(
            initial = tag,
            onDismiss = { editing = null },
            onSave = {
                vm.upsertTag(it)
                editing = null
            }
        )
    }

    // 班次标签删除确认（删除标签不影响已生成的排班）
    ConfirmDialog(
        visible = deleting != null,
        title = "删除标签",
        body = "删除「${deleting?.name ?: ""}」后不可恢复，\n已生成的排班不受影响",
        confirmText = "删除",
        danger = true,
        onDismiss = { deleting = null },
        onConfirm = {
            deleting?.let { vm.deleteTag(it.id) }
            deleting = null
        }
    )

    // 待办标签：编辑 / 新建弹层
    todoEditing?.let { tag ->
        TodoEditSheet(
            initial = tag,
            onDismiss = { todoEditing = null },
            onSave = {
                vm.upsertTodoTag(it)
                todoEditing = null
            }
        )
    }

    // 待办标签删除确认
    ConfirmDialog(
        visible = todoDeleting != null,
        title = "删除待办",
        body = "删除「${todoDeleting?.name ?: ""}」后不可恢复",
        confirmText = "删除",
        danger = true,
        onDismiss = { todoDeleting = null },
        onConfirm = {
            todoDeleting?.let { vm.deleteTodoTag(it.id) }
            todoDeleting = null
        }
    )
}

// ==================== 分区标题 / 空态 / 列表行 ====================

/** 分区标题：左侧标题 + 右侧 30dp 新建按钮 */
@Composable
private fun SectionHeader(title: String, onAdd: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = FontM.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
        IconCircleButton(R.drawable.ic_plus, "新建$title", onClick = onAdd, size = 30.dp)
    }
}

/** 分区空态小字 */
@Composable
private fun SectionEmptyHint(text: String) {
    Text(
        text,
        style = FontSm.copy(color = C.fg3),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    )
}

/** 班次标签列表行：色圆 + 名称 + 时间时长 + 编辑/删除 */
@Composable
private fun ShiftTagRow(tag: TagEntity, onEdit: (TagEntity) -> Unit, onDelete: (TagEntity) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(tagColorOf(tag.colorIndex)))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tag.name, style = FontM.copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(2.dp))
                Text(
                    "${tag.startTime} – ${tag.endTime} · ${durationHoursText(tag.startTime, tag.endTime)}",
                    style = FontSm.copy(color = C.fg3)
                )
            }
            IconCircleButton(R.drawable.ic_edit, "编辑", onClick = { onEdit(tag) }, size = 30.dp)
            Spacer(Modifier.width(8.dp))
            IconCircleButton(R.drawable.ic_trash, "删除", onClick = { onDelete(tag) }, size = 30.dp)
        }
    }
}

/** 待办标签列表行：色圆 + 名称 + 截止时长 + 编辑/删除 */
@Composable
private fun TodoTagRow(tag: TodoTagEntity, onEdit: (TodoTagEntity) -> Unit, onDelete: (TodoTagEntity) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(todoColorOf(tag.colorIndex)))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tag.name, style = FontM.copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(2.dp))
                Text(
                    if (tag.durationUnit == 1)
                        "截止 ${todoDurationText(tag.durationValue, tag.durationUnit)} · 提醒 ${tag.remindTime}"
                    else
                        "截止 ${todoDurationText(tag.durationValue, tag.durationUnit)} · 提前 ${tag.remindHoursBefore}小时${tag.remindMinutesBefore}分钟提醒",
                    style = FontSm.copy(color = C.fg3)
                )
            }
            IconCircleButton(R.drawable.ic_edit, "编辑", onClick = { onEdit(tag) }, size = 30.dp)
            Spacer(Modifier.width(8.dp))
            IconCircleButton(R.drawable.ic_trash, "删除", onClick = { onDelete(tag) }, size = 30.dp)
        }
    }
}

// ==================== 统一选择弹层（班次 + 待办） ====================

/** 上半班次标签、下半待办标签的统一选择弹层；任一列表为空则隐藏该段 */
@Composable
fun AddPickerSheet(
    visible: Boolean,
    shiftTags: List<TagEntity>,
    todoTags: List<TodoTagEntity>,
    title: String = "选择标签",
    onDismiss: () -> Unit,
    onPickShift: (TagEntity) -> Unit,
    onPickTodo: (TodoTagEntity) -> Unit
) {
    BottomSheetOverlay(visible = visible, onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(title, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(14.dp))

            if (shiftTags.isEmpty() && todoTags.isEmpty()) {
                Text(
                    "还没有可用标签，请先到「标签」页创建",
                    style = FontSm.copy(color = C.fg3)
                )
            } else {
                if (shiftTags.isNotEmpty()) {
                    Text("班次", style = FontXs.copy(color = C.fg3, fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        shiftTags.forEach { tag ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(C.bg3)
                                    .clickable { onPickShift(tag) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color(tagColorOf(tag.colorIndex)))
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(tag.name, style = FontM.copy(fontWeight = FontWeight.SemiBold))
                                    Text(
                                        "${tag.startTime} – ${tag.endTime} · ${durationHoursText(tag.startTime, tag.endTime)}",
                                        style = FontSm.copy(color = C.fg3)
                                    )
                                }
                                Icon(
                                    painterResource(R.drawable.ic_chev), null,
                                    tint = C.ph, modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    if (todoTags.isNotEmpty()) Spacer(Modifier.height(16.dp))
                }
                if (todoTags.isNotEmpty()) {
                    Text("待办", style = FontXs.copy(color = C.fg3, fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        todoTags.forEach { tag ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(C.bg3)
                                    .clickable { onPickTodo(tag) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color(todoColorOf(tag.colorIndex)))
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(tag.name, style = FontM.copy(fontWeight = FontWeight.SemiBold))
                                    Text(
                                        "截止 ${todoDurationText(tag.durationValue, tag.durationUnit)}",
                                        style = FontSm.copy(color = C.fg3)
                                    )
                                }
                                Icon(
                                    painterResource(R.drawable.ic_chev), null,
                                    tint = C.ph, modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun MultiAddPickerSheet(
    visible: Boolean,
    shiftTags: List<TagEntity>,
    todoTags: List<TodoTagEntity>,
    initialShifts: List<TagEntity> = emptyList(),
    initialTodos: List<TodoTagEntity> = emptyList(),
    title: String = "选择标签",
    onDismiss: () -> Unit,
    onConfirm: (List<TagEntity>, List<TodoTagEntity>) -> Unit
) {
    if (!visible) return
    var selectedShifts by remember { mutableStateOf(initialShifts) }
    var selectedTodos by remember { mutableStateOf(initialTodos) }
    val count = selectedShifts.size + selectedTodos.size
    BottomSheetOverlay(visible = true, onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(title, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(4.dp))
            Text("已选择 $count / 3", style = FontSm.copy(color = C.fg3))
            Spacer(Modifier.height(14.dp))
            if (shiftTags.isEmpty() && todoTags.isEmpty()) {
                Text("还没有可用标签，请先到「标签」页创建", style = FontSm.copy(color = C.fg3))
            }
            if (shiftTags.isNotEmpty()) {
                Text("班次", style = FontXs.copy(color = C.fg3, fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(8.dp))
                shiftTags.forEach { tag ->
                    val selected = selectedShifts.any { it.id == tag.id }
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) C.accL else C.bg3)
                            .clickable {
                                selectedShifts = if (selected) selectedShifts.filterNot { it.id == tag.id }
                                else if (count < 3) selectedShifts + tag else selectedShifts
                            }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(tagColorOf(tag.colorIndex))))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tag.name, style = FontM.copy(fontWeight = FontWeight.SemiBold))
                            Text("${tag.startTime} – ${tag.endTime}", style = FontSm.copy(color = C.fg3))
                        }
                        if (selected) Text("✓", style = FontM.copy(color = C.acc, fontWeight = FontWeight.Bold))
                    }
                }
            }
            if (todoTags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("待办", style = FontXs.copy(color = C.fg3, fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(8.dp))
                todoTags.forEach { tag ->
                    val selected = selectedTodos.any { it.id == tag.id }
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) C.accL else C.bg3)
                            .clickable {
                                selectedTodos = if (selected) selectedTodos.filterNot { it.id == tag.id }
                                else if (count < 3) selectedTodos + tag else selectedTodos
                            }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(todoColorOf(tag.colorIndex))))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tag.name, style = FontM.copy(fontWeight = FontWeight.SemiBold))
                            Text("截止 ${todoDurationText(tag.durationValue, tag.durationUnit)}", style = FontSm.copy(color = C.fg3))
                        }
                        if (selected) Text("✓", style = FontM.copy(color = C.acc, fontWeight = FontWeight.Bold))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            AppButton("确认选择", onClick = { onConfirm(selectedShifts, selectedTodos) },
                enabled = count in 1..3, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ==================== 共享小组件 ====================

/** 提醒时间选择行 */
@Composable
private fun RemindTimeRow(time: String, onChange: (String) -> Unit) {
    val pctx = pickerContext()
    ValueRow("提醒时间", time) {
        val h = parseMinutes(time) / 60
        val m = parseMinutes(time) % 60
        TimePickerDialog(pctx, { _, hh, mm -> onChange("%02d:%02d".format(hh, mm)) }, h, m, true).show()
    }
}

@Composable
private fun NumberField(value: String, suffix: String, onValueChange: (String) -> Unit) {
    Row(
        Modifier
            .width(130.dp)
            .height(44.dp)
            .background(C.bg3, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = FontM.copy(color = C.fg),
            cursorBrush = SolidColor(C.acc),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
            decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { inner() } }
        )
        Text(suffix, style = FontSm.copy(color = C.fg3))
    }
}

/** 10 色选择器：5 列均分宽度，任意屏宽都居中不变形 */
@Composable
private fun ColorPickerRow(colors: List<Long>, selected: Int, onSelect: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        for (row in 0 until (colors.size + 4) / 5) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 5) {
                    val idx = row * 5 + col
                    if (idx >= colors.size) break
                    val sel = idx == selected
                    Box(
                        Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(colors[idx]))
                                .then(
                                    if (sel) Modifier.border(
                                        2.dp, C.fg, CircleShape
                                    ) else Modifier
                                )
                                .clickable { onSelect(idx) }
                        )
                    }
                }
            }
        }
    }
}

/** 单位切换胶囊（小时 / 天），选中 accent 底白字 */
@Composable
private fun UnitChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) C.acc else C.bg3)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = FontSm.copy(
                color = if (selected) Color.White else C.fg2,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.padding(horizontal = 18.dp)
        )
    }
}

// ==================== 班次标签编辑弹层 ====================

@Composable
private fun TagEditSheet(
    initial: TagEntity,
    onDismiss: () -> Unit,
    onSave: (TagEntity) -> Unit
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var start by remember(initial.id) { mutableStateOf(initial.startTime) }
    var end by remember(initial.id) { mutableStateOf(initial.endTime) }
    var colorIndex by remember(initial.id) { mutableStateOf(initial.colorIndex) }
    var remind by remember(initial.id) { mutableStateOf(initial.remindTime) }
    var nameError by remember { mutableStateOf(false) }
    val pctx = pickerContext()

    BottomSheetOverlay(visible = true, onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("班次标签", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(16.dp))

            Text("班次名称", style = FontSm.copy(color = C.fg3, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(6.dp))
            BasicTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                textStyle = FontM.copy(color = C.fg),
                cursorBrush = SolidColor(C.acc),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(C.bg3, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) { inner() }
                }
            )
            if (nameError) {
                Spacer(Modifier.height(6.dp))
                Text("请填写班次名称", style = FontSm.copy(color = C.dng))
            }

            Spacer(Modifier.height(18.dp))
            Text("起止时间", style = FontSm.copy(color = C.fg3, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(8.dp))
            ValueRow("开始时间", start) {
                val h = parseMinutes(start) / 60
                val m = parseMinutes(start) % 60
                TimePickerDialog(pctx, { _, hh, mm -> start = "%02d:%02d".format(hh, mm) }, h, m, true).show()
            }
            Spacer(Modifier.height(10.dp))
            ValueRow("结束时间", end) {
                val h = parseMinutes(end) / 60
                val m = parseMinutes(end) % 60
                TimePickerDialog(pctx, { _, hh, mm -> end = "%02d:%02d".format(hh, mm) }, h, m, true).show()
            }

            Spacer(Modifier.height(18.dp))
            RemindTimeRow(remind) { remind = it }

            Spacer(Modifier.height(18.dp))
            Text("标签颜色", style = FontSm.copy(color = C.fg3, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(14.dp))
            ColorPickerRow(TAG_COLORS, colorIndex) { colorIndex = it }

            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton("取消", onDismiss, style = BtnStyle.Secondary, modifier = Modifier.weight(1f))
                AppButton(
                    "保存",
                    onClick = {
                        if (name.isBlank()) nameError = true
                        else onSave(
                            TagEntity(
                                initial.id, name.trim(), start, end, colorIndex, remind
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ==================== 待办标签编辑弹层 ====================

@Composable
private fun TodoEditSheet(
    initial: TodoTagEntity,
    onDismiss: () -> Unit,
    onSave: (TodoTagEntity) -> Unit
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var value by remember(initial.id) { mutableStateOf(initial.durationValue.coerceAtLeast(1).toString()) }
    var unit by remember(initial.id) { mutableStateOf(initial.durationUnit) }
    var colorIndex by remember(initial.id) { mutableStateOf(initial.colorIndex) }
    var remind by remember(initial.id) { mutableStateOf(initial.remindTime) }
    var remindHours by remember(initial.id) { mutableStateOf(initial.remindHoursBefore.toString()) }
    var remindMinutes by remember(initial.id) { mutableStateOf(initial.remindMinutesBefore.toString()) }
    var nameError by remember { mutableStateOf(false) }
    var valueError by remember { mutableStateOf(false) }

    BottomSheetOverlay(visible = true, onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("待办标签", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(16.dp))

            Text("事项名称", style = FontSm.copy(color = C.fg3, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(6.dp))
            BasicTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                textStyle = FontM.copy(color = C.fg),
                cursorBrush = SolidColor(C.acc),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(C.bg3, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) { inner() }
                }
            )
            if (nameError) {
                Spacer(Modifier.height(6.dp))
                Text("请填写事项名称", style = FontSm.copy(color = C.dng))
            }

            Spacer(Modifier.height(18.dp))
            Text("截止时长", style = FontSm.copy(color = C.fg3, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = {
                        value = it.filter { ch -> ch.isDigit() }.take(3)
                        valueError = false
                    },
                    textStyle = FontM.copy(color = C.fg),
                    cursorBrush = SolidColor(C.acc),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(C.bg3, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) { inner() }
                    }
                )
                UnitChip("小时", unit == 0) { unit = 0 }
                UnitChip("天", unit == 1) { unit = 1 }
            }
            if (valueError) {
                Spacer(Modifier.height(6.dp))
                Text("请填写有效的截止时长", style = FontSm.copy(color = C.dng))
            }

            Spacer(Modifier.height(18.dp))
            if (unit == 1) {
                // 天单位：安排日 + 截止天数 的当天按此时刻提醒
                RemindTimeRow(remind) { remind = it }
            } else {
                Text("截止时刻前提醒", style = FontSm.copy(color = C.fg3, fontWeight = FontWeight.Medium))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberField(remindHours, "小时") { remindHours = it.filter(Char::isDigit).take(3) }
                    NumberField(remindMinutes, "分钟") { remindMinutes = it.filter(Char::isDigit).take(2) }
                }
                Spacer(Modifier.height(6.dp))
                Text("例如截止时刻前 1 小时 30 分钟提醒", style = FontSm.copy(color = C.fg3))
            }

            Spacer(Modifier.height(18.dp))
            Text("标签颜色", style = FontSm.copy(color = C.fg3, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(14.dp))
            ColorPickerRow(TODO_COLORS, colorIndex) { colorIndex = it }

            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton("取消", onDismiss, style = BtnStyle.Secondary, modifier = Modifier.weight(1f))
                AppButton(
                    "保存",
                    onClick = {
                        val v = value.toIntOrNull() ?: 0
                        when {
                            name.isBlank() -> nameError = true
                            v <= 0 -> valueError = true
                            else -> onSave(
                                TodoTagEntity(
                                    id = initial.id,
                                    name = name.trim(),
                                    durationValue = v,
                                    durationUnit = unit,
                                    colorIndex = colorIndex,
                                    remindTime = remind,
                                    remindHoursBefore = remindHours.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                                    remindMinutesBefore = remindMinutes.toIntOrNull()?.coerceIn(0, 59) ?: 0
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ==================== 通用：标签选择弹层 ====================

/** 从已有标签中选择一个（排班用）。tags 为空时展示引导文案。 */
@Composable
fun TagPickerSheet(
    visible: Boolean,
    tags: List<TagEntity>,
    title: String = "选择班次标签",
    onDismiss: () -> Unit,
    onPick: (TagEntity) -> Unit
) {
    BottomSheetOverlay(visible = visible, onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(title, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(14.dp))

            if (tags.isEmpty()) {
                Text(
                    "还没有可用标签，请先到「标签」页创建",
                    style = FontSm.copy(color = C.fg3)
                )
                Spacer(Modifier.height(6.dp))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    tags.forEach { tag ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(C.bg3)
                                .clickable { onPick(tag) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color(tagColorOf(tag.colorIndex)))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tag.name, style = FontM.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    "${tag.startTime} – ${tag.endTime} · ${durationHoursText(tag.startTime, tag.endTime)}",
                                    style = FontSm.copy(color = C.fg3)
                                )
                            }
                            Icon(
                                painterResource(R.drawable.ic_chev), null,
                                tint = C.ph, modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** 表单内的标签选择行：选中显示色圆+名称+时间，未选中显示占位文字 */
@Composable
fun TagSelectRow(
    name: String,
    start: String,
    end: String,
    colorIndex: Int,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(C.bg3)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (name.isNotBlank()) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(tagColorOf(colorIndex)))
            )
            Spacer(Modifier.width(10.dp))
            Text(name, style = FontM.copy(color = C.fg, fontWeight = FontWeight.Medium))
            Spacer(Modifier.width(10.dp))
            Text("$start – $end", style = FontSm.copy(color = C.fg3))
        } else {
            Text("选择班次标签", style = FontM.copy(color = C.ph))
        }
        Spacer(Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_chev), null, tint = C.ph, modifier = Modifier.size(14.dp))
    }
}
