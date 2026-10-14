package com.shiftplanner.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiftplanner.app.data.PlannerViewModel
import com.shiftplanner.app.data.ShiftEntity
import com.shiftplanner.app.data.ShiftSlot
import com.shiftplanner.app.data.cnText
import com.shiftplanner.app.data.durationMinutes
import com.shiftplanner.app.data.parseMinutes
import com.shiftplanner.app.data.slotsOf
import com.shiftplanner.app.data.toLocalDate
import com.shiftplanner.app.data.weekText

@Composable
fun EditPage(
    vm: PlannerViewModel,
    day: Long,
    existing: ShiftEntity?,
    onDone: () -> Unit,
    snack: (String) -> Unit
) {
    val date = remember(day) { day.toLocalDate() }
    val tags by vm.tags.collectAsState()

    // 当天班次槽（最多 3 个）
    var slots by remember(day) { mutableStateOf(existing?.let { slotsOf(it) } ?: emptyList()) }
    // 正在选标签的槽下标（-1 = 关闭弹层；slots.size 表示新增）
    var pickingIndex by remember { mutableStateOf(-1) }
    var confirmDelete by remember { mutableStateOf(false) }

    // 系统弹窗 context：强制跟随 App 深浅模式
    val context = pickerContext()

    fun showTimePicker(initial: String, onPick: (String) -> Unit) {
        val h = parseMinutes(initial) / 60
        val m = parseMinutes(initial) % 60
        TimePickerDialog(
            context,
            { _, hh, mm -> onPick("%02d:%02d".format(hh, mm)) },
            h, m, true
        ).show()
    }

    val totalMinutes = slots.sumOf { durationMinutes(it.start, it.end).toLong() }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.bg)
    ) {
        // 自定义标题栏：取消 / 编辑排班 / 保存
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "取消",
                style = FontM.copy(color = C.acc),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onDone() }
            )
            Text("编辑排班", style = FontM.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "保存",
                style = FontM.copy(color = C.acc, fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        enabled = slots.isNotEmpty()
                    ) {
                        vm.saveSlots(day, slots)
                        snack("已保存")
                        onDone()
                    },
                textAlign = TextAlign.End
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Text(
                "${date.cnText()} ${date.weekText()}",
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )

            Text(
                "一天最多安排 3 个班次",
                style = FontXs.copy(color = C.fg3),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            // ---- 各班次槽 ----
            slots.forEachIndexed { i, slot ->
                FieldLabel("班次 ${i + 1}")
                Column(Modifier.padding(horizontal = 16.dp)) {
                    AppCard {
                        // 点击换标签
                        TagSelectRow(
                            name = slot.label,
                            start = slot.start,
                            end = slot.end,
                            colorIndex = tags.firstOrNull { it.name == slot.label }?.colorIndex ?: 0,
                            onClick = { pickingIndex = i }
                        )
                        Spacer(Modifier.height(10.dp))
                        ValueRow("开始时间", slot.start) {
                            showTimePicker(slot.start) { t ->
                                slots = slots.toMutableList().also { it[i] = slot.copy(start = t) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        ValueRow("结束时间", slot.end) {
                            showTimePicker(slot.end) { t ->
                                slots = slots.toMutableList().also { it[i] = slot.copy(end = t) }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "移除本班次",
                            style = FontSm.copy(color = C.dng),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { slots = slots.filterIndexed { j, _ -> j != i } }
                                .padding(vertical = 6.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ---- 添加班次 ----
            if (slots.size < 3) {
                FieldLabel("班次 ${slots.size + 1}")
                Column(Modifier.padding(horizontal = 16.dp)) {
                    AppCard {
                        TagSelectRow(
                            name = "", start = "", end = "", colorIndex = 0,
                            onClick = { pickingIndex = slots.size }
                        )
                    }
                }
            }

            // ---- 值班时长合计 ----
            if (slots.isNotEmpty()) {
                FieldLabel("值班时长")
                Column(Modifier.padding(horizontal = 16.dp)) {
                    AppCard {
                        val h = totalMinutes / 60
                        val mm = totalMinutes % 60
                        Text(
                            if (mm == 0L) "$h 小时" else "$h 小时 $mm 分钟",
                            style = FontM.copy(fontWeight = FontWeight.Medium)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            slots.joinToString(" + ") { "${it.start}–${it.end}" },
                            style = FontSm.copy(color = C.fg3)
                        )
                    }
                }
            }

            // 底部：删除该日排班（已有排班时）
            if (existing != null) {
                Spacer(Modifier.height(32.dp))
                AppButton(
                    "删除该日排班",
                    onClick = { confirmDelete = true },
                    style = BtnStyle.Danger,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
        }
    }

    // 标签选择弹层
    TagPickerSheet(
        visible = pickingIndex >= 0,
        tags = tags,
        onDismiss = { pickingIndex = -1 },
        onPick = { tag ->
            val i = pickingIndex
            val newSlot = ShiftSlot(tag.name, tag.startTime, tag.endTime)
            slots = when {
                i < slots.size -> slots.toMutableList().also { it[i] = newSlot }
                else -> slots + newSlot
            }
            pickingIndex = -1
        }
    )

    ConfirmDialog(
        visible = confirmDelete,
        title = "删除该日排班",
        body = "确认删除 ${date.cnText()} 的排班？",
        confirmText = "删除",
        onDismiss = { confirmDelete = false },
        onConfirm = {
            vm.delete(day)
            confirmDelete = false
            snack("已删除")
            onDone()
        }
    )
}
