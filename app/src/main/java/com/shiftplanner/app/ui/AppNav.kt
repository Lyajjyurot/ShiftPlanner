package com.shiftplanner.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.shiftplanner.app.R
import com.shiftplanner.app.data.PlannerViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate

// ==================== 路由 ====================

sealed interface Route {
    data object Calendar : Route
    data object Stats : Route
    data object Tags : Route
    data object Profile : Route
    data class Edit(val day: Long) : Route
    data object Swap1 : Route
    data class Swap2(val from: LocalDate, val to: LocalDate) : Route
    data object Auto1 : Route
    data object Auto2 : Route
    data object Range : Route
    data object Remind : Route
    data object Backup : Route
}

@Composable
fun AppNav(vm: PlannerViewModel) {
    val dark by vm.darkTheme.collectAsState()
    val shifts by vm.shifts.collectAsState()

    val backStack = remember { mutableStateListOf<Route>(Route.Calendar) }
    val current = backStack.last()

    // ---- Snackbar ----
    var snackMsg by remember { mutableStateOf<String?>(null) }
    fun snack(m: String) { snackMsg = m }
    LaunchedEffect(snackMsg) {
        if (snackMsg != null) {
            delay(1800)
            snackMsg = null
        }
    }

    // ---- 二级页共享状态 ----
    var autoForm by remember { mutableStateOf(AutoForm()) }
    var swapInitial by remember { mutableStateOf<Long?>(null) }

    fun nav(r: Route) { backStack.add(r) }
    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
    fun navTab(r: Route) {
        backStack.clear()
        backStack.add(r)
    }

    BackHandler(enabled = backStack.size > 1) { back() }

    val showTabs = current is Route.Calendar || current is Route.Stats ||
            current is Route.Tags || current is Route.Profile

    Box(Modifier.fillMaxSize().background(C.bg)) {
        // ==================== 页面 ====================
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = 0.985f, animationSpec = tween(220))) togetherWith
                        (fadeOut(tween(150)) + scaleOut(targetScale = 1.015f, animationSpec = tween(150)))
            },
            label = "page"
        ) { route ->
            when (route) {
            Route.Calendar -> CalendarPage(
                vm = vm,
                dark = dark,
                onToggleTheme = { vm.toggleTheme() },
                onAuto = { autoForm = AutoForm(); nav(Route.Auto1) },
                onRange = { nav(Route.Range) },
                onEdit = { day -> nav(Route.Edit(day)) },
                onSwap = { day -> swapInitial = day; nav(Route.Swap1) },
                onRemind = { nav(Route.Remind) }
            )
            Route.Stats -> StatsPage(
                vm = vm,
                dark = dark,
                onToggleTheme = { vm.toggleTheme() }
            )
            Route.Tags -> TagPage(vm = vm)
            Route.Profile -> ProfilePage(
                vm = vm,
                dark = dark,
                onToggleTheme = { vm.toggleTheme() },
                onBackup = { nav(Route.Backup) }
            )
            is Route.Edit -> EditPage(
                vm = vm,
                day = route.day,
                existing = shifts.firstOrNull { it.epochDay == route.day },
                onDone = { back() },
                snack = { snack(it) }
            )
            Route.Swap1 -> SwapPage1(
                vm = vm,
                initialFrom = swapInitial,
                onBack = { back() },
                onConfirm = { from, to -> nav(Route.Swap2(from, to)) }
            )
            is Route.Swap2 -> SwapPage2(
                vm = vm,
                from = route.from,
                to = route.to,
                onBack = { back() },
                onDone = { msg ->
                    navTab(Route.Calendar)
                    snack(msg)
                }
            )
            Route.Auto1 -> AutoPage1(
                vm = vm,
                form = autoForm,
                onChange = { autoForm = it },
                onBack = { back() },
                onNext = { nav(Route.Auto2) }
            )
            Route.Auto2 -> AutoPage2(
                vm = vm,
                form = autoForm,
                onChange = { autoForm = it },
                onBack = { back() },
                onDone = { msg ->
                    navTab(Route.Calendar)
                    snack(msg)
                }
            )
            Route.Range -> RangePage(
                vm = vm,
                onBack = { back() },
                onDone = { msg ->
                    navTab(Route.Calendar)
                    snack(msg)
                }
            )
            Route.Remind -> RemindPage(vm = vm, onBack = { back() })
            Route.Backup -> BackupPage(
                vm = vm,
                onBack = { back() },
                snack = { snack(it) }
            )
            }
        }

        // ==================== 底部 Tab（仅主页面显示） ====================
        if (showTabs) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(C.card)
            ) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.sep))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(50.dp)
                ) {
                    TabItem(R.drawable.ic_tab_cal, "排班日历", current is Route.Calendar,
                        Modifier.weight(1f)) { navTab(Route.Calendar) }
                    TabItem(R.drawable.ic_tab_stats, "统计", current is Route.Stats,
                        Modifier.weight(1f)) { navTab(Route.Stats) }
                    TabItem(R.drawable.ic_tab_tag, "标签", current is Route.Tags,
                        Modifier.weight(1f)) { navTab(Route.Tags) }
                    TabItem(R.drawable.ic_tab_user, "我的", current is Route.Profile,
                        Modifier.weight(1f)) { navTab(Route.Profile) }
                }
            }
        }

        // ==================== Snackbar ====================
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = if (showTabs) 70.dp else 24.dp)
        ) {
            AppSnackbar(snackMsg)
        }
    }
}

@Composable
private fun TabItem(
    icon: Int,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // 按压缩放反馈（去系统水波纹）
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "tabScale")
    Column(
        modifier
            .scale(scale)
            .fillMaxWidth()
            .height(50.dp)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(width = 52.dp, height = 28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (active) C.accL else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(icon), label,
                tint = if (active) C.acc else C.fg3,
                modifier = Modifier.size(21.dp)
            )
        }
        Text(
            label,
            style = TextStyle(fontSize = 10.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium),
            color = if (active) C.acc else C.fg3
        )
    }
}
