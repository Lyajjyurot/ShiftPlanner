package com.shiftplanner.app.ui

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ==================== 设计令牌（照抄原型 CSS 变量） ====================

data class PlannerColors(
    val bg: Color,
    val card: Color,
    val bg3: Color,
    val fg: Color,
    val fg2: Color,
    val fg3: Color,
    val ph: Color,
    val acc: Color,
    val accL: Color,
    val dng: Color,
    val dngL: Color,
    val grn: Color,
    val org: Color,
    val orgL: Color,
    val sep: Color
)

val LightColors = PlannerColors(
    bg = Color(0xFFF6F7F9),
    card = Color(0xFFFFFFFF),
    bg3 = Color(0xFFE9ECF0),
    fg = Color(0xFF18212B),
    fg2 = Color(0xFF46515D),
    fg3 = Color(0xFF7D8792),
    ph = Color(0xFFB7C0C9),
    acc = Color(0xFF276EF1),
    accL = Color(0x14276EF1),
    dng = Color(0xFFD64545),
    dngL = Color(0x14D64545),
    grn = Color(0xFF238A62),
    org = Color(0xFFC47718),
    orgL = Color(0x14C47718),
    sep = Color(0x1A46515D)
)

val DarkColors = PlannerColors(
    bg = Color(0xFF111418),
    card = Color(0xFF1B2026),
    bg3 = Color(0xFF2A3139),
    fg = Color(0xFFF3F5F7),
    fg2 = Color(0xFFD0D7DE),
    fg3 = Color(0xFF8F9AA5),
    ph = Color(0xFF65717D),
    acc = Color(0xFF78A7FF),
    accL = Color(0x2678A7FF),
    dng = Color(0xFFFF7B7B),
    dngL = Color(0x26FF7B7B),
    grn = Color(0xFF61C49A),
    org = Color(0xFFF0B45E),
    orgL = Color(0x26F0B45E),
    sep = Color(0x3D8F9AA5)
)

// ==================== 字号（11/13/15/17/20/28/36） ====================

val FontXs = TextStyle(fontSize = 11.sp)
val FontSm = TextStyle(fontSize = 13.sp)
val FontB = TextStyle(fontSize = 15.sp)
val FontM = TextStyle(fontSize = 17.sp)
val FontL = TextStyle(fontSize = 20.sp)
val FontXL = TextStyle(fontSize = 28.sp)
val Font3XL = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold)

// ==================== 主题 ====================

private val LightScheme = lightColorScheme(
    primary = LightColors.acc,
    onPrimary = Color.White,
    secondary = LightColors.grn,
    background = LightColors.bg,
    surface = LightColors.card,
    onBackground = LightColors.fg,
    onSurface = LightColors.fg,
    error = LightColors.dng,
    outline = LightColors.sep,
    surfaceVariant = LightColors.bg3,
    onSurfaceVariant = LightColors.fg2
)

private val DarkScheme = darkColorScheme(
    primary = DarkColors.acc,
    onPrimary = Color.White,
    secondary = DarkColors.grn,
    background = DarkColors.bg,
    surface = DarkColors.card,
    onBackground = DarkColors.fg,
    onSurface = DarkColors.fg,
    error = DarkColors.dng,
    outline = DarkColors.sep,
    surfaceVariant = DarkColors.bg3,
    onSurfaceVariant = DarkColors.fg2
)

val LocalPlannerColors = staticCompositionLocalOf { LightColors }

/** 当前是否深色模式（供系统弹窗等非 Compose 组件取用） */
val LocalIsDark = staticCompositionLocalOf { false }

/**
 * 系统时间/日期选择器的 context：强制跟随 App 的深浅模式（而非系统）。
 * 深色 → DeviceDefault.Dialog（黑底白字）；浅色 → DeviceDefault.Light.Dialog（白底黑字）。
 */
@Composable
fun pickerContext(): Context {
    val context = LocalContext.current
    val dark = LocalIsDark.current
    return remember(context, dark) {
        if (dark) ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_Dialog)
        else ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_Light_Dialog)
    }
}

/** 全局颜色快捷访问：C.acc / C.fg3 ... */
object C {
    val bg: Color @Composable get() = LocalPlannerColors.current.bg
    val card: Color @Composable get() = LocalPlannerColors.current.card
    val bg3: Color @Composable get() = LocalPlannerColors.current.bg3
    val fg: Color @Composable get() = LocalPlannerColors.current.fg
    val fg2: Color @Composable get() = LocalPlannerColors.current.fg2
    val fg3: Color @Composable get() = LocalPlannerColors.current.fg3
    val ph: Color @Composable get() = LocalPlannerColors.current.ph
    val acc: Color @Composable get() = LocalPlannerColors.current.acc
    val accL: Color @Composable get() = LocalPlannerColors.current.accL
    val dng: Color @Composable get() = LocalPlannerColors.current.dng
    val dngL: Color @Composable get() = LocalPlannerColors.current.dngL
    val grn: Color @Composable get() = LocalPlannerColors.current.grn
    val org: Color @Composable get() = LocalPlannerColors.current.org
    val orgL: Color @Composable get() = LocalPlannerColors.current.orgL
    val sep: Color @Composable get() = LocalPlannerColors.current.sep
}

@Composable
fun PlannerTheme(dark: Boolean, content: @Composable () -> Unit) {
    // 未显式指定颜色的 Text/Icon 默认取 LocalContentColor，
    // 这里强制令牌色，确保深色模式下所有默认文字变白（修复残留黑字）
    val contentColor = if (dark) DarkColors.fg else LightColors.fg
    androidx.compose.runtime.CompositionLocalProvider(
        LocalPlannerColors provides if (dark) DarkColors else LightColors,
        LocalIsDark provides dark,
        androidx.compose.material3.LocalContentColor provides contentColor
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides contentColor
            ) {
                content()
            }
        }
    }
}
