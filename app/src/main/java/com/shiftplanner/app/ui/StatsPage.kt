package com.shiftplanner.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiftplanner.app.R
import com.shiftplanner.app.data.PlannerViewModel
import com.shiftplanner.app.data.colorMapFor
import com.shiftplanner.app.data.durationMinutes
import com.shiftplanner.app.data.labelText
import com.shiftplanner.app.data.slotsOf
import com.shiftplanner.app.data.toLocalDate
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun StatsPage(
    vm: PlannerViewModel,
    dark: Boolean,
    onToggleTheme: () -> Unit
) {
    val shifts by vm.shifts.collectAsState()
    val tags by vm.tags.collectAsState()
    val today = remember { LocalDate.now() }
    var ym by remember { mutableStateOf(YearMonth.from(today)) }

    val colorMap = remember(shifts, dark, tags) { colorMapFor(shifts, dark, tags) }
    val monthShifts = remember(shifts, ym) {
        shifts.filter { YearMonth.from(it.epochDay.toLocalDate()) == ym }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.bg)
    ) {
        PageHeader(
            title = "统计",
            subtitle = ym.labelText(),
            actions = {
                IconCircleButton(
                    if (dark) R.drawable.ic_moon else R.drawable.ic_sun,
                    "切换日夜模式", onToggleTheme
                )
            }
        )

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            MonthNav(
                label = ym.labelText(),
                onPrev = { ym = ym.minusMonths(1) },
                onNext = { ym = ym.plusMonths(1) }
            )

            if (monthShifts.isEmpty()) {
                // 无数据：不渲染任何数字和图表
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp, start = 40.dp, end = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "本月暂无排班数据",
                        style = FontM.copy(color = C.fg2, fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "录入排班后，这里会自动生成统计",
                        style = FontSm.copy(color = C.fg3)
                    )
                }
            } else {
                // ---- 卡片一：值班天数 ----（一天多个班次时，各标签分别计 1 天）
                val byLabel = HashMap<String, Int>()
                monthShifts.forEach { e ->
                    slotsOf(e).map { it.label }.distinct().forEach { l ->
                        byLabel[l] = (byLabel[l] ?: 0) + 1
                    }
                }
                val totalDays = monthShifts.size
                val pieTotalDays = byLabel.values.sum()

                StatsCard(
                    title = "值班天数",
                    bigValue = totalDays,
                    unit = "天"
                ) {
                    PieBody(
                        segments = byLabel.entries
                            .sortedBy { it.key }
                            .map { PieSeg(colorMap[it.key]!!.let { c -> androidx.compose.ui.graphics.Color(c) }, it.value.toFloat(), it.key) },
                        total = pieTotalDays.toFloat(),
                        centerValue = totalDays.toString(),
                        centerLabel = "天"
                    ) { name, days ->
                        val pct = if (pieTotalDays > 0) days * 100 / pieTotalDays else 0
                        "$days 天 · $pct%"
                    }
                }

                // ---- 卡片二：值班时长 ----（一天多个班次分别累加）
                val hourByLabel = HashMap<String, Int>()
                monthShifts.forEach { e ->
                    slotsOf(e).forEach { s ->
                        val h = durationMinutes(s.start, s.end)
                        hourByLabel[s.label] = (hourByLabel[s.label] ?: 0) + h
                    }
                }
                val totalMinutes = hourByLabel.values.sum()

                StatsCard(
                    title = "值班时长",
                    bigValue = totalMinutes / 60,
                    unit = "小时"
                ) {
                    PieBody(
                        segments = hourByLabel.entries
                            .sortedBy { it.key }
                            .map { PieSeg(androidx.compose.ui.graphics.Color(colorMap[it.key] ?: 0xFF007AFF), it.value.toFloat(), it.key) },
                        total = totalMinutes.toFloat(),
                        centerValue = (totalMinutes / 60).toString(),
                        centerLabel = "小时"
                    ) { name, minutes ->
                        val h = minutes / 60
                        val pct = if (totalMinutes > 0) minutes * 100 / totalMinutes else 0
                        "$h 小时 · $pct%"
                    }
                }

                // ---- 卡片三：本月概览 ----
                AppCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("本月概览", style = FontB.copy(fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.height(12.dp))
                    val avg = totalMinutes / totalDays
                    val restDays = ym.lengthOfMonth() - totalDays
                    val topLabel = byLabel.maxByOrNull { it.value }
                    OverviewRow("日均时长", "${String.format("%.1f", avg / 60f)} 小时/班")
                    OverviewRow("休息天数", "$restDays 天")
                    OverviewRow(
                        "出现最多的班次",
                        topLabel?.let { "${it.key}（${it.value}天）" } ?: "—"
                    )
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun StatsCard(
    title: String,
    bigValue: Int,
    unit: String,
    body: @Composable () -> Unit
) {
    AppCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(title, style = FontB.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    bigValue.toString(),
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"   // 表格数字：等宽对齐
                    ),
                    color = C.fg
                )
                Text(
                    " $unit",
                    style = FontSm.copy(color = C.fg3),
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.sep))
        Spacer(Modifier.height(12.dp))
        body()
    }
}

@Composable
private fun PieBody(
    segments: List<PieSeg>,
    total: Float,
    centerValue: String,
    centerLabel: String,
    valueText: (String, Int) -> String
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DonutChart(
            segments = segments,
            total = total,
            centerValue = centerValue,
            centerLabel = centerLabel,
            modifier = Modifier.padding(end = 20.dp)
        )
        Column(
            Modifier.weight(1f),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(11.dp)
        ) {
            segments.forEach { seg ->
                LegendItem(
                    color = seg.color,
                    name = seg.label,
                    value = valueText(seg.label, seg.value.toInt())
                )
            }
        }
    }
}

@Composable
private fun OverviewRow(label: String, value: String) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(label, style = FontSm.copy(color = C.fg3), modifier = Modifier.weight(1f))
            Text(value, style = FontSm.copy(color = C.fg))
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.sep))
    }
}
