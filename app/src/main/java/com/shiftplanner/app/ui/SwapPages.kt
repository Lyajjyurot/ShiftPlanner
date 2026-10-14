package com.shiftplanner.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiftplanner.app.data.PlannerViewModel
import com.shiftplanner.app.data.ShiftEntity
import com.shiftplanner.app.data.cnText
import com.shiftplanner.app.data.colorMapFor
import com.shiftplanner.app.data.slotsOf
import com.shiftplanner.app.data.toLocalDate
import com.shiftplanner.app.data.weekText
import java.time.LocalDate
import java.time.YearMonth

/** 第一步：选择起点（有排班）与终点（无排班） */
@Composable
fun SwapPage1(
    vm: PlannerViewModel,
    initialFrom: Long?,
    onBack: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    val shifts by vm.shifts.collectAsState()
    val tags by vm.tags.collectAsState()
    val today = remember { LocalDate.now() }
    var ym by remember { mutableStateOf(initialFrom?.let { YearMonth.from(it.toLocalDate()) } ?: YearMonth.from(today)) }
    var from by remember { mutableStateOf(initialFrom?.let { it.toLocalDate() }) }
    var to by remember { mutableStateOf<LocalDate?>(null) }

    val byDay = remember(shifts, ym) {
        shifts.filter { YearMonth.from(it.epochDay.toLocalDate()) == ym }
            .associateBy { it.epochDay }
    }
    val colorMap = remember(shifts, tags) { colorMapFor(shifts, false, tags) }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.bg)
    ) {
        NavHeader("换班", onBack)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            AlertBar("先点【有排班】的一天作为起点，再点【无排班】的一天作为终点")

            // 状态卡
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SwapStatusCard(
                    title = "起点",
                    date = from,
                    label = from?.let { d ->
                        byDay[d.toEpochDay()]?.let { slotsOf(it).joinToString("/") { s -> s.label } }
                    },
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "⇄",
                    style = FontXL.copy(color = C.acc),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                SwapStatusCard(
                    title = "终点",
                    date = to,
                    label = null,
                    modifier = Modifier.weight(1f)
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
                selectedDay = null,
                dotColor = { d ->
                    byDay[d.toEpochDay()]?.let {
                        androidx.compose.ui.graphics.Color(colorMap[it.label] ?: 0xFF007AFF).copy(alpha = 0.75f)
                    }
                },
                onDayClick = { d ->
                    if (byDay.containsKey(d.toEpochDay())) {
                        // 有排班 → 起点（再点同一天取消）
                        from = if (from == d) null else d
                        if (from == null) to = null
                    } else {
                        // 无排班 → 终点（需先选起点）
                        if (from != null) {
                            to = if (to == d) null else d
                        }
                    }
                },
                decor = { d ->
                    when {
                        from == d -> DayDecor.Src
                        to == d -> DayDecor.Dst
                        else -> DayDecor.None
                    }
                }
            )

            Spacer(Modifier.height(32.dp))
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            AppButton(
                "确认换班",
                onClick = {
                    if (from != null && to != null) onConfirm(from!!, to!!)
                },
                enabled = from != null && to != null,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SwapStatusCard(
    title: String,
    date: LocalDate?,
    label: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .border(2.dp, if (date != null) C.acc else C.sep, RoundedCornerShape(18.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = FontXs.copy(color = C.fg3))
        Spacer(Modifier.height(4.dp))
        Text(
            date?.cnText() ?: "未选择",
            style = FontL.copy(
                fontWeight = FontWeight.SemiBold,
                color = if (date != null) C.fg else C.fg3
            )
        )
        if (label != null) {
            Spacer(Modifier.height(4.dp))
            Text(label, style = FontXs.copy(color = C.fg2))
        }
    }
}

/** 第二步：确认换班 */
@Composable
fun SwapPage2(
    vm: PlannerViewModel,
    from: LocalDate?,
    to: LocalDate?,
    onBack: () -> Unit,
    onDone: (String) -> Unit
) {
    val shifts by vm.shifts.collectAsState()
    if (from == null || to == null) { onBack(); return }
    val entity = remember(shifts, from, to) {
        shifts.firstOrNull { it.epochDay == from.toEpochDay() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.bg)
    ) {
        NavHeader("确认换班", onBack)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                "确认将排班移动到新日期",
                style = FontSm.copy(color = C.fg3),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 16.dp),
                textAlign = TextAlign.Center
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConfirmCard(from, entity, true, Modifier.weight(1f))
                Text(
                    "→",
                    style = FontXL.copy(color = C.acc, fontWeight = FontWeight.Light),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                ConfirmCard(to, null, false, Modifier.weight(1f))
            }

            Text(
                "「${entity?.let { slotsOf(it).joinToString("/") { s -> s.label } } ?: "—"}」将移动到 ${to.cnText()}\n${from.cnText()} 变为无排班",
                style = FontSm.copy(color = C.fg3, lineHeight = 22.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                textAlign = TextAlign.Center
            )
        }

        Row(
            Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppButton("取消", onBack, style = BtnStyle.Secondary, modifier = Modifier.weight(1f))
            AppButton(
                "确认交换",
                onClick = {
                    vm.swap(from.toEpochDay(), to.toEpochDay())
                    onDone("${from.cnText()} ⇄ ${to.cnText()} 换班成功")
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ConfirmCard(
    date: LocalDate,
    entity: ShiftEntity?,
    highlight: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .then(
                if (highlight) Modifier.background(C.acc, RoundedCornerShape(18.dp))
                else Modifier.border(2.dp, C.sep, RoundedCornerShape(18.dp))
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            date.cnText(),
            style = FontL.copy(
                fontWeight = FontWeight.SemiBold,
                color = if (highlight) androidx.compose.ui.graphics.Color.White else C.fg
            )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            date.weekText(),
            style = FontXs.copy(
                color = if (highlight) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f) else C.fg2
            )
        )
        Spacer(Modifier.height(8.dp))
        val slots = entity?.let { slotsOf(it) }.orEmpty()
        Text(
            if (slots.isEmpty()) "无排班" else slots.joinToString(" / ") { it.label },
            style = FontXs.copy(
                color = if (highlight) androidx.compose.ui.graphics.Color.White else C.fg2
            )
        )
        if (slots.isNotEmpty()) {
            Text(
                slots.joinToString("\n") { "${it.start}–${it.end}" },
                style = FontXs.copy(
                    color = if (highlight) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f) else C.fg3
                )
            )
        }
    }
}
