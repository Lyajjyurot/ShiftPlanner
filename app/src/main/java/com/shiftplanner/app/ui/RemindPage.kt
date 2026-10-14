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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shiftplanner.app.data.PlannerViewModel
import com.shiftplanner.app.data.tagColorOf

@Composable
fun RemindPage(
    vm: PlannerViewModel,
    onBack: () -> Unit
) {
    val enabled by vm.remindEnabled.collectAsState()
    val tags by vm.tags.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(C.bg)
    ) {
        NavHeader("提醒设置", onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))
            CalendarReminderSettings(vm)

            if (enabled) {
                Text(
                    "各班次标签可单独设置提醒时间，值班当天同步到所选日历",
                    style = FontSm.copy(color = C.fg3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 8.dp),
                    textAlign = TextAlign.Center
                )

                FieldLabel("各标签提醒时间")
                Column(Modifier.padding(horizontal = 16.dp)) {
                    if (tags.isEmpty()) {
                        AppCard {
                            Text(
                                "还没有班次标签\n到「班次标签」页创建后即可设置提醒时间",
                                style = FontSm.copy(color = C.fg3),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        AppCard {
                            tags.forEachIndexed { i, tag ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(tagColorOf(tag.colorIndex)))
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        tag.name,
                                        style = FontM.copy(
                                            fontWeight = FontWeight.Medium,
                                            color = C.fg
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        tag.remindTime,
                                        style = FontM.copy(color = C.fg2)
                                    )
                                }
                                if (i < tags.lastIndex) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(0.5.dp)
                                            .background(C.sep)
                                    )
                                }
                            }
                        }
                        Text(
                            "修改提醒时间：到「班次标签」页编辑对应标签",
                            style = FontXs.copy(color = C.fg3),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Text(
                    "提醒同步到系统日历，是否弹出通知由日历应用设置决定",
                    style = FontXs.copy(color = C.fg3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(Modifier.height(16.dp))
                AlertBar("日历提醒已关闭，开启前请授权并选择可写日历", warning = true)
            }
        }
    }
}
