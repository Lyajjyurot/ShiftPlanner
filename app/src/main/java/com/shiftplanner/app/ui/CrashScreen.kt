package com.shiftplanner.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 上次崩溃堆栈整屏展示 + 复制按钮（仅 debug 包） */
@Composable
fun CrashScreen(crash: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.bg)
            .verticalScroll(rememberScrollState())
    ) {
        Column(Modifier.statusBarsPadding().padding(16.dp)) {
            Text(
                "上次运行崩溃了",
                style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = C.dng)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "以下是崩溃堆栈，请复制后反馈：",
                style = FontSm.copy(color = C.fg3)
            )
            Spacer(Modifier.height(16.dp))
            AppButton(
                if (copied) "已复制" else "复制堆栈",
                onClick = {
                    clipboard.setText(AnnotatedString(crash))
                    copied = true
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Text(
                crash,
                style = TextStyle(fontSize = 11.sp, color = C.fg2, lineHeight = 16.sp)
            )
        }
    }
}
