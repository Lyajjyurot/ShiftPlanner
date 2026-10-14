package com.shiftplanner.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shiftplanner.app.data.PlannerViewModel
import com.shiftplanner.app.data.TagEntity
import com.shiftplanner.app.data.TodoTagEntity
import com.shiftplanner.app.data.cnText
import com.shiftplanner.app.data.toLocalDate
import java.time.LocalDate
import java.time.YearMonth

/** 自动排班表单（在导航层持有，两步共享） */
data class AutoForm(
    val start: LocalDate = LocalDate.now(),
    val end: LocalDate = LocalDate.now().plusMonths(1).minusDays(1),
    val interval: Int = 2,
    val selectedShifts: List<TagEntity> = emptyList(),
    val selectedTodos: List<TodoTagEntity> = emptyList(),
    val overwrite: Boolean = false
)

fun AutoForm.generatedDays(): List<LocalDate> {
    val days = mutableListOf<LocalDate>()
    var d = start
    while (!d.isAfter(end)) {
        days += d
        d = d.plusDays(interval.toLong())
    }
    return days
}

@Composable
fun AutoPage1(
    vm: PlannerViewModel,
    form: AutoForm,
    onChange: (AutoForm) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    // 系统弹窗 context：强制跟随 App 深浅模式
    val context = pickerContext()
    val tags by vm.tags.collectAsState()
    val todoTags by vm.todoTags.collectAsState()
    var pickerVisible by remember { mutableStateOf(false) }

    fun showDatePicker(initial: LocalDate, onPick: (LocalDate) -> Unit) {
        DatePickerDialog(
            context,
            { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) },
            initial.year, initial.monthValue - 1, initial.dayOfMonth
        ).show()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.bg)
    ) {
        NavHeader("自动排班", onBack)
        StepIndicator(1)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            FieldLabel("首次值班日")
            Column(Modifier.padding(horizontal = 16.dp)) {
                AppCard {
                    ValueRow("开始日期", form.start.cnText()) {
                        showDatePicker(form.start) { onChange(form.copy(start = it)) }
                    }
                }
            }

            FieldLabel("结束日期")
            Column(Modifier.padding(horizontal = 16.dp)) {
                AppCard {
                    ValueRow("结束日期", form.end.cnText()) {
                        showDatePicker(form.end) { onChange(form.copy(end = it)) }
                    }
                }
            }

            FieldLabel("值班间隔")
            Column(Modifier.padding(horizontal = 16.dp)) {
                AppCard {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Stepper(
                            value = form.interval,
                            onChange = { onChange(form.copy(interval = it)) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "每隔 ${form.interval} 天一次",
                        style = FontSm.copy(color = C.fg3)
                    )
                }
            }

            FieldLabel("班次与待办标签（最多 3 个）")
            Column(Modifier.padding(horizontal = 16.dp)) {
                AppCard {
                    ValueRow(
                        "已选标签",
                        (form.selectedShifts.map { it.name } + form.selectedTodos.map { it.name })
                            .joinToString("、").ifEmpty { "请选择" }
                    ) { pickerVisible = true }
                }
            }

            FieldLabel("冲突处理")
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioRow(
                    "跳过已有排班的日期",
                    "保留原来的排班不动",
                    selected = !form.overwrite,
                    onClick = { onChange(form.copy(overwrite = false)) }
                )
                RadioRow(
                    "覆盖已有排班",
                    "用新生成的排班替换",
                    selected = form.overwrite,
                    onClick = { onChange(form.copy(overwrite = true)) }
                )
            }

            Spacer(Modifier.height(24.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                AppButton(
                    "生成预览",
                    onClick = onNext,
                    enabled = form.selectedShifts.isNotEmpty() || form.selectedTodos.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    MultiAddPickerSheet(
        visible = pickerVisible,
        shiftTags = tags,
        todoTags = todoTags,
        initialShifts = form.selectedShifts,
        initialTodos = form.selectedTodos,
        onDismiss = { pickerVisible = false },
        onConfirm = { selectedShifts, selectedTodos ->
            onChange(form.copy(selectedShifts = selectedShifts, selectedTodos = selectedTodos))
            pickerVisible = false
        }
    )
}

@Composable
fun AutoPage2(
    vm: PlannerViewModel,
    form: AutoForm,
    onChange: (AutoForm) -> Unit,
    onBack: () -> Unit,
    onDone: (String) -> Unit
) {
    val shifts by vm.shifts.collectAsState()
    val today = remember { LocalDate.now() }

    val days = remember(form) { form.generatedDays() }
    val existing = remember(shifts) { shifts.map { it.epochDay }.toSet() }
    val conflicts = remember(days, existing) {
        days.filter { existing.contains(it.toEpochDay()) }
    }
    val ym = remember(form) { YearMonth.from(form.start) }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.bg)
    ) {
        NavHeader("排班预览", onBack)
        StepIndicator(2)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                "将在 ${form.start.cnText()} 起，每隔 ${form.interval} 天写入 ${days.size} 天排班",
                style = FontSm.copy(color = C.fg3),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            // 日历预览（冲突日期红浅底红字，将写入日期蓝实心）
            Column(Modifier.padding(horizontal = 16.dp)) {
                AppCard {
                    CalendarGrid(
                        ym = ym,
                        today = today,
                        selectedDay = null,
                        dotColor = { null },
                        onDayClick = { },
                        compact = true,
                        decor = { d ->
                            when {
                                conflicts.contains(d) -> DayDecor.Conflict
                                days.contains(d) -> DayDecor.Planned
                                else -> DayDecor.None
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (conflicts.isNotEmpty()) {
                val conflictText = if (form.overwrite) {
                    "已有排班的日期将被覆盖"
                } else {
                    "已有排班的日期将跳过"
                }
                AlertBar(
                    "${conflicts.size} 天已有排班，${if (form.overwrite) "将被覆盖" else "将跳过"}",
                    warning = true
                )
                if (!form.overwrite) {
                    Text(
                        conflictText,
                        style = FontXs.copy(color = C.org),
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            // 汇总卡
            Column(Modifier.padding(horizontal = 16.dp)) {
                AppCard {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("选中标签", style = FontSm.copy(color = C.fg3), modifier = Modifier.weight(1f))
                        Text(
                            (form.selectedShifts.map { it.name } + form.selectedTodos.map { it.name }).joinToString("、"),
                            style = FontSm.copy(color = C.fg)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("冲突处理", style = FontSm.copy(color = C.fg3), modifier = Modifier.weight(1f))
                        Text(
                            if (form.overwrite) "覆盖模式" else "跳过 ${conflicts.size} 天",
                            style = FontSm.copy(color = if (conflicts.isNotEmpty()) C.dng else C.fg)
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }

        val targetDays = remember(days, existing, form) {
            days.filter { form.overwrite || !existing.contains(it.toEpochDay()) }
        }

        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppButton(
                "上一步", onBack,
                style = BtnStyle.Secondary, modifier = Modifier.weight(1f)
            )
            AppButton(
                "确认生成",
                onClick = {
                    vm.generateWithLabels(
                        targetDays.map { it.toEpochDay() },
                        form.selectedShifts,
                        form.selectedTodos,
                        form.overwrite
                    )
                    onDone("已生成 ${targetDays.size} 天排班")
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
