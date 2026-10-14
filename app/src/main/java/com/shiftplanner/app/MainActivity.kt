package com.shiftplanner.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import com.shiftplanner.app.data.PlannerViewModel
import com.shiftplanner.app.reminder.SystemCalendarSync
import com.shiftplanner.app.ui.AppNav
import com.shiftplanner.app.ui.CrashScreen
import com.shiftplanner.app.ui.PlannerTheme

class MainActivity : ComponentActivity() {

    private val vm: PlannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 上次崩溃堆栈（仅 debug 包记录），显示后清除
        val lastCrash = CrashRecorder.takeCrash(this)

        setContent {
            val darkTheme by vm.darkTheme.collectAsState()

            LaunchedEffect(Unit) {
                SystemCalendarSync.sync(this@MainActivity,
                    getSharedPreferences("planner", Context.MODE_PRIVATE).getBoolean("remind_enabled", false))
            }

            // 状态栏 / 导航条图标颜色跟随主题
            LaunchedEffect(darkTheme) {
                runCatching {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
            }

            PlannerTheme(darkTheme) {
                if (lastCrash != null) {
                    CrashScreen(lastCrash)
                } else {
                    AppNav(vm)
                }
            }
        }
    }
}
