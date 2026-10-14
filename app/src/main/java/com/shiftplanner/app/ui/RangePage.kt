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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shiftplanner.app.data.PlannerViewModel
import com.shiftplanner.app.data.ShiftEntity
import com.shiftplanner.app.data.cnText
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun RangePage(
    vm: PlannerViewModel,
    onBack: () -> Unit,
    onDone: (String) -> Unit
) {
    // 系统弹窗 context：强制跟随 App 深浅模式
    val context = pickerContext()
    val tags by vm.tags.collectAsState()

    var start by remember { mutableStateOf(LocalDate.now()) }
    var end by remember { mutableStateOf(LocalDate.now().plusDays(9)) }
    var mode by remember { mutableStateOf(false) } // false=清空区间 true=按隔N天重排
    var interval by remember { mutableStateOf(2) }
    var tagName by remember { mutableStateOf("") }
    var tagStart by remember { mutableStateOf("09:00") }
    var tagEnd by remember { mutableStateOf("18:00") }
    var tagColorIndex by remember { mutableStateOf(0) }
    var pickerVisible by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    val dayCount = ChronoUnit.DAYS.between(start, end).toInt() + 1
    val overLimit = dayCount > 10

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
        NavHeader("区间重排", onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "最多选择连续 10 天",
                style = FontSm.copy(color = C.fg3),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            FieldLabel("起始日期")
            Column(Modifier.padding(horizontal = 16.dp)) {
                AppCard {
                    ValueRow("开始", start.cnText()) {
                        showDatePicker(start) { start = it; if (end.isBefore(start)) end = start }
                    }
                }
            }

            FieldLabel("结束日期")
            Column(Modifier.padding(horizontal = 16.dp)) {
                AppCard {
                    ValueRow("结束", end.cnText()) {
                        showDatePicker(end) { end = it; if (end.isBefore(start)) start = end }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text("已选 ", style = FontSm.copy(color = C.fg3))
                Text(
                    dayCount.toString(),
                    style = FontSm.copy(
                        color = if (overLimit) C.dng else C.fg,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                )
                Text(" 天", style = FontSm.copy(color = C.fg3))
            }

            if (overLimit) {
                AlertBar("超过 10 天，请缩小范围", warning = true)
            }

            FieldLabel("重排模式")
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioRow(
                    "清空区间",
                    "删除区间内所有排班",
                    selected = !mode,
                    onClick = { mode = false }
                )
                RadioRow(
                    "按隔 N 天重排",
                    "在区间内按固定间隔重新排班",
                    selected = mode,
                    onClick = { mode = true }
                )
            }

            if (mode) {
                FieldLabel("间隔天数")
                Column(Modifier.padding(horizontal = 16.dp)) {
                    AppCard {
                        Row {
                            Stepper(value = interval, onChange = { interval = it })
                        }
                    }
                }

                FieldLabel("班次标签")
                Column(Modifier.padding(horizontal = 16.dp)) {
                    AppCard {
                        TagSelectRow(
                            name = tagName,
                            start = tagStart,
                            end = tagEnd,
                            colorIndex = tagColorIndex,
                            onClick = { pickerVisible = true }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                AppButton(
                    "执行重排",
                    onClick = { confirm = true },
                    enabled = !overLimit && (!mode || tagName.isNotBlank()),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    ConfirmDialog(
        visible = confirm,
        title = if (mode) "确认重排区间" else "确认清空区间",
        body = if (mode) {
            "将删除 ${start.cnText()}–${end.cnText()} 共 $dayCount 天排班\n并按每隔 $interval 天写入「${tagName}」\n${tagStart}–${tagEnd}"
        } else {
            "将删除 ${start.cnText()}–${end.cnText()}共 $dayCount 天排班\n区间外不受影响"
        },
        confirmText = if (mode) "重排" else "确认删除",
        onDismiss = { confirm = false },
        onConfirm = {
            val from = start.toEpochDay()
            val to = end.toEpochDay()
            if (mode) {
                val items = mutableListOf<ShiftEntity>()
                var d = start
                while (!d.isAfter(end)) {
                    items += ShiftEntity(d.toEpochDay(), tagName, tagStart, tagEnd)
                    d = d.plusDays(interval.toLong())
                }
                vm.applyRange(from, to, items)
                onDone("已重排 $dayCount 天区间")
            } else {
                vm.applyRange(from, to, emptyList())
                onDone("已清空区间排班")
            }
            confirm = false
        }
    )

    // 标签选择弹层
    TagPickerSheet(
        visible = pickerVisible,
        tags = tags,
        onDismiss = { pickerVisible = false },
        onPick = { tag ->
            tagName = tag.name
            tagStart = tag.startTime
            tagEnd = tag.endTime
            tagColorIndex = tag.colorIndex
            pickerVisible = false
        }
    )
}
