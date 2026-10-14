package com.shiftplanner.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.shiftplanner.app.R
import com.shiftplanner.app.data.Lunar
import java.time.LocalDate
import java.time.YearMonth

// ==================== 页头（主 Tab 页） ====================

@Composable
fun PageHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold))
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = FontSm, color = C.fg3)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

// ==================== 二级页导航条 ====================

@Composable
fun NavHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    right: @Composable () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(52.dp)
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "‹ 返回",
            style = FontM.copy(color = C.acc),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clickable(onClick = onBack),
            fontWeight = FontWeight.Normal
        )
        Text(
            title,
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.align(Alignment.Center)
        )
        Box(Modifier.align(Alignment.CenterEnd)) { right() }
    }
}

// ==================== 按钮 ====================

enum class BtnStyle { Primary, Secondary, Danger, Ghost }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BtnStyle = BtnStyle.Primary,
    enabled: Boolean = true,
    small: Boolean = false,
    @DrawableRes icon: Int? = null
) {
    val h = if (small) 34.dp else 46.dp
    val radius = if (small) 10.dp else 12.dp
    val fontSize = if (small) 13.sp else 17.sp
    val (bg, fg) = when (style) {
        BtnStyle.Primary -> C.acc to Color.White
        BtnStyle.Secondary -> C.bg3 to C.fg
        BtnStyle.Danger -> C.dng to Color.White
        BtnStyle.Ghost -> Color.Transparent to C.acc
    }
    // 按压缩放反馈（去系统水波纹）
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "btnScale")
    Row(
        modifier = modifier
            .scale(scale)
            .height(h)
            .clip(RoundedCornerShape(radius))
            .background(if (style == BtnStyle.Ghost) Color.Transparent else bg)
            .then(
                if (style == BtnStyle.Ghost) Modifier.border(
                    1.5.dp, C.acc, RoundedCornerShape(radius)
                ) else Modifier
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null
            ) { onClick() }
            .padding(horizontal = if (small) 16.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) {
            Icon(
                painterResource(icon), null,
                tint = fg, modifier = Modifier.size(if (small) 14.dp else 18.dp)
            )
        }
        Text(
            text,
            style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.SemiBold, color = fg)
        )
    }
}

// ==================== 圆形图标按钮（34dp 浅底） ====================

@Composable
fun IconCircleButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp
) {
    // 按压缩放反馈（去系统水波纹）
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "iconScale")
    Box(
        modifier = modifier
            .scale(scale)
            .size(size)
            .clip(CircleShape)
            .background(C.accL)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(icon), contentDescription,
            tint = C.acc, modifier = Modifier.size(size * 0.5f)
        )
    }
}

// ==================== 简约开关（44×26） ====================

@Composable
fun SwitchMini(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val knobX by animateDpAsState(if (checked) 18.dp else 2.dp, label = "knob")
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (checked) C.acc else C.bg3)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .padding(start = knobX)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

// ==================== 卡片 ====================

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(C.card)
            .border(1.dp, C.sep, RoundedCornerShape(10.dp))
            .padding(contentPadding),
        content = content
    )
}

// ==================== 月份导航 ====================

@Composable
fun MonthNav(
    label: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            SmallRoundIcon(R.drawable.ic_back, "上个月") { onPrev() }
        }
        Text(label, style = FontL.copy(fontWeight = FontWeight.SemiBold))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            SmallRoundIcon(R.drawable.ic_chev, "下个月") { onNext() }
        }
    }
}

@Composable
private fun SmallRoundIcon(@DrawableRes icon: Int, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(C.card)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(icon), desc, tint = C.acc, modifier = Modifier.size(16.dp))
    }
}

// ==================== 日历网格 ====================

/** 日期装饰（换班/自动排班预览等特殊状态） */
enum class DayDecor { None, Planned, Conflict, Src, Dst }

/** 日期下方的班次标注（名称 + 标签色），一天最多 3 个 */
data class DayTag(val label: String, val color: Color?)

@Composable
fun CalendarGrid(
    ym: YearMonth,
    today: LocalDate,
    selectedDay: LocalDate?,
    dotColor: (LocalDate) -> Color?,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    decor: (LocalDate) -> DayDecor = { DayDecor.None },
    dayTags: (LocalDate) -> List<DayTag> = { emptyList() },
    todoDots: (LocalDate) -> List<Color> = { emptyList() }
) {
    val weekHeader = listOf("一", "二", "三", "四", "五", "六", "日")
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // 表头
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            weekHeader.forEach {
                Text(
                    it,
                    style = FontXs.copy(color = C.fg3, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        val first = ym.atDay(1)
        val leading = (first.dayOfWeek.value + 6) % 7 // 周一=0
        var day = 1
        val total = ym.lengthOfMonth()
        while (day <= total) {
            Row(Modifier.fillMaxWidth()) {
                for (w in 0 until 7) {
                    if (day > total || (day == 1 && w < leading)) {
                        Box(Modifier.weight(1f).height(if (compact) 34.dp else 96.dp))
                    } else {
                        val date = ym.atDay(day)
                        DayCell(
                            date = date,
                            today = date == today,
                            selected = date == selectedDay,
                            dot = dotColor(date),
                            compact = compact,
                            decor = decor(date),
                            tags = dayTags(date),
                            todoDots = todoDots(date),
                            onClick = { onDayClick(date) },
                            modifier = Modifier.weight(1f)
                        )
                        day++
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    today: Boolean,
    selected: Boolean,
    dot: Color?,
    compact: Boolean,
    decor: DayDecor,
    tags: List<DayTag>,
    todoDots: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val d = decor
    val filled = today || d == DayDecor.Src || d == DayDecor.Planned
    val strokeSelected = selected || d == DayDecor.Dst
    val circle = if (compact) 30.dp else 34.dp
    val conflictBg = d == DayDecor.Conflict
    val txtColor = when {
        d == DayDecor.Conflict -> C.dng
        filled -> Color.White
        d == DayDecor.Dst -> C.acc
        else -> C.fg
    }
    val ringColor = C.acc
    Column(
        modifier = modifier
            .height(if (compact) 34.dp else 96.dp)
            .then(
                if (conflictBg) Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(C.dngL)
                else Modifier
            )
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .padding(top = if (compact) 2.dp else 5.dp)
                .fillMaxWidth()
                .height(circle)
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(circle)
                    .then(
                        if (filled) Modifier
                            .clip(CircleShape)
                            .background(if (d == DayDecor.Planned) C.acc else C.acc)
                        else Modifier
                    )
                    .then(
                        if (strokeSelected) Modifier
                            .drawBehind {
                                val r = size.minDimension / 2f - 3.dp.toPx()
                                drawCircle(
                                    color = ringColor,
                                    radius = r,
                                    style = Stroke(
                                        width = 2.dp.toPx(),
                                        pathEffect = if (d == DayDecor.Dst) PathEffect.dashPathEffect(
                                            floatArrayOf(4.dp.toPx(), 3.dp.toPx())
                                        ) else null
                                    )
                                )
                            } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    date.dayOfMonth.toString(),
                    style = TextStyle(
                        fontSize = if (compact) 11.sp else 15.sp,
                        fontWeight = if (filled || d == DayDecor.Dst) FontWeight.SemiBold else FontWeight.Medium,
                        color = txtColor
                    )
                )
            }
            // 待办小色点：右上角，最多 3 个
            if (todoDots.isNotEmpty()) {
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    todoDots.take(3).forEach { c ->
                        Box(
                            Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(c)
                        )
                    }
                }
            }
        }
        if (!compact) {
            // 农历小字（初一显示月名，其余显示日名）
            val lunar = remember(date) { Lunar.lunarText(date) }
            if (lunar.isNotEmpty()) {
                Text(
                    lunar,
                    style = TextStyle(fontSize = 9.sp, color = C.fg3),
                    maxLines = 1
                )
            }
        }
        if (!compact && tags.isNotEmpty()) {
            // 日期下方的班次小框：标签色浅底 + 标签色小字（无标签色时用主题蓝），最多 3 个
            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                tags.take(3).forEach { t ->
                    Box(
                        Modifier
                            .padding(top = 1.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(t.color?.copy(alpha = 0.14f) ?: C.accL)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            t.label,
                            style = TextStyle(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                color = t.color ?: C.acc
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else if (!compact && dot != null) {
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(dot.copy(alpha = 0.75f))
            )
        }
    }
}

// ==================== 环形饼图 ====================

data class PieSeg(val color: Color, val value: Float, val label: String)

@Composable
fun DonutChart(
    segments: List<PieSeg>,
    total: Float,
    centerValue: String,
    centerLabel: String,
    modifier: Modifier = Modifier
) {
    Box(modifier.size(104.dp), contentAlignment = Alignment.Center) {
        val trackColor = C.bg3
        androidx.compose.foundation.Canvas(Modifier.size(104.dp)) {
            val stroke = 16.dp.toPx()
            val r = size.minDimension / 2f - stroke / 2f
            drawCircle(color = trackColor, radius = r, style = Stroke(width = stroke))
            var start = -90f
            segments.forEach { seg ->
                val sweep = if (total > 0f) seg.value / total * 360f else 0f
                drawArc(
                    color = seg.color,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                centerValue,
                style = FontL.copy(fontWeight = FontWeight.Bold),
                color = C.fg
            )
            Text(centerLabel, style = TextStyle(fontSize = 10.sp, color = C.fg3))
        }
    }
}

@Composable
fun LegendItem(color: Color, name: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            name, style = FontSm.copy(color = C.fg2),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(value, style = FontSm.copy(color = C.fg3))
    }
}

// ==================== 设置行（我的页） ====================

@Composable
fun SettingRow(
    @DrawableRes icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    rightText: String? = null,
    rightSwitch: Boolean? = null,
    onSwitch: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(painterResource(icon), null, tint = if (enabled) C.fg2 else C.ph, modifier = Modifier.size(19.dp))
        Text(label, style = if (enabled) FontB else FontB.copy(color = C.ph), modifier = Modifier.weight(1f))
        if (rightText != null) {
            Text(rightText, style = FontB.copy(color = C.fg3))
        }
        if (rightSwitch != null) {
            SwitchMini(checked = rightSwitch, onCheckedChange = { onSwitch?.invoke(it) })
        } else {
            Icon(painterResource(R.drawable.ic_chev), null, tint = C.ph, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = FontXs.copy(color = C.fg3, fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(C.card)
    ) {
        content()
    }
}

// ==================== 单选行 ====================

@Composable
fun RadioRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) C.accL else C.card)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(22.dp)
                .border(2.dp, if (selected) C.acc else C.ph, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(C.acc)
                )
            }
        }
        Column {
            Text(title, style = FontM.copy(color = C.fg))
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = FontXs.copy(color = C.fg3))
            }
        }
    }
}

// ==================== 灰底日期/时间行 ====================

@Composable
fun ValueRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(C.bg3)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = FontM.copy(color = C.fg2))
        Spacer(Modifier.weight(1f))
        Text(value, style = FontM.copy(color = C.fg, fontWeight = FontWeight.Medium))
        Spacer(Modifier.width(6.dp))
        Icon(painterResource(R.drawable.ic_chev), null, tint = C.ph, modifier = Modifier.size(14.dp))
    }
}

// ==================== 加减器 ====================

@Composable
fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    min: Int = 1,
    max: Int = 30
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        RoundMinusButton { if (value > min) onChange(value - 1) }
        Text(
            value.toString(),
            style = FontXL.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.width(34.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        RoundMinusButton { if (value < max) onChange(value + 1) }
    }
}

@Composable
private fun RoundMinusButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .border(1.5.dp, C.sep, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(R.drawable.ic_plus), null, tint = C.acc, modifier = Modifier.size(16.dp))
    }
}

// ==================== 两步进度指示器 ====================

@Composable
fun StepIndicator(step: Int) {
    Row(
        Modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(2) { i ->
            val active = i == step - 1
            val done = i < step - 1
            Box(
                Modifier
                    .then(
                        if (active) Modifier.width(24.dp) else Modifier.size(8.dp)
                    )
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            active || done -> C.acc
                            else -> C.ph
                        }
                    )
            )
        }
    }
}

// ==================== 提示条 ====================

@Composable
fun AlertBar(
    text: String,
    warning: Boolean = false,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (warning) C.orgL else C.accL)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = FontSm.copy(
                color = if (warning) C.org else C.acc,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.weight(1f)
        )
        if (actionText != null && onAction != null) {
            Text(
                actionText,
                style = FontSm.copy(color = if (warning) C.org else C.acc, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.clickable { onAction() }
            )
        }
    }
}

// ==================== 底部弹层 ====================
// 用 Dialog（独立 window 层），保证覆盖底部 Tab 栏

@Composable
fun BottomSheetOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!visible) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.LaunchedEffect(Unit) {
            runCatching {
                (view.parent as? androidx.compose.ui.window.DialogWindowProvider)
                    ?.window?.setDimAmount(0.35f)
            }
        }
        Box(Modifier.fillMaxSize()) {
            // 点击空白处关闭（透明层覆盖 sheet 之外的全部区域）
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.94f)      // 屏宽 94%：两侧留边，不贴物理边缘
                    .widthIn(max = 520.dp)    // 平板/超宽屏限宽居中
                    .heightIn(max = 640.dp)   // 内容再多也不超出屏幕
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.card)
                    .border(1.dp, C.sep, RoundedCornerShape(14.dp))
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp)
            ) {
                // 顶部小横条
                Box(
                    Modifier
                        .padding(top = 12.dp, bottom = 12.dp)
                        .size(width = 36.dp, height = 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(C.ph)
                        .align(Alignment.CenterHorizontally)
                )
                content()
            }
        }
    }
}

// ==================== 确认弹窗（iOS 风格） ====================

@Composable
fun ConfirmDialog(
    visible: Boolean,
    title: String,
    body: String,
    confirmText: String = "确认",
    danger: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (!visible) return
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(270.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(C.card)
        ) {
            Column(
                Modifier.padding(top = 20.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = FontM.copy(fontWeight = FontWeight.SemiBold, color = C.fg))
                Spacer(Modifier.height(8.dp))
                Text(
                    body,
                    style = FontB.copy(color = C.fg2, lineHeight = 22.sp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.sep))
            Text(
                "取消",
                style = FontM.copy(color = C.acc, fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
                    .padding(vertical = 13.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.sep))
            Text(
                confirmText,
                style = FontM.copy(color = if (danger) C.dng else C.acc),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onConfirm() }
                    .padding(vertical = 13.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ==================== 顶部浮动提示（原型 Snackbar） ====================

@Composable
fun AppSnackbar(message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(tween(200)) { it } + fadeIn(),
        exit = slideOutVertically(tween(200)) { it } + fadeOut(),
        modifier = modifier
    ) {
        Text(
            message ?: "",
            style = FontSm.copy(color = C.bg, fontWeight = FontWeight.Medium),
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(C.fg)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// ==================== 表单标签 ====================

@Composable
fun FieldLabel(text: String) {
    Text(
        text,
        style = FontSm.copy(color = C.fg3, fontWeight = FontWeight.Medium),
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp)
    )
}
