package com.shiftplanner.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shiftplanner.app.R
import com.shiftplanner.app.data.BackupItem
import com.shiftplanner.app.data.BackupManager
import com.shiftplanner.app.data.PlannerViewModel
import java.time.format.DateTimeFormatter

@Composable
fun BackupPage(
    vm: PlannerViewModel,
    onBack: () -> Unit,
    snack: (String) -> Unit
) {
    val context = LocalContext.current
    val shifts by vm.shifts.collectAsState()

    var backups by remember { mutableStateOf(listOf<BackupItem>()) }
    var refresh by remember { mutableStateOf(0) }

    var confirmRestore by remember { mutableStateOf<BackupItem?>(null) }
    var confirmClearBackups by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) {
        runCatching { backups = BackupManager.list(context) }
    }

    val displayTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    Column(
        Modifier
            .fillMaxSize()
            .background(C.bg)
    ) {
        NavHeader("备份与恢复", onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                AppButton(
                    "新建备份",
                    onClick = {
                        runCatching {
                            BackupManager.create(context, shifts)
                            refresh++
                            snack("备份已创建")
                        }
                    },
                    icon = R.drawable.ic_plus,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "备份文件保存在应用私有目录，不联网、不上传",
                    style = FontXs.copy(color = C.fg3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            Text(
                "历史备份",
                style = FontXs.copy(
                    color = C.fg3,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            if (backups.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp, bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("还没有备份", style = FontM.copy(color = C.fg2, fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.height(6.dp))
                    Text("点上方按钮创建一个", style = FontSm.copy(color = C.fg3))
                }
            } else {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    backups.forEach { item ->
                        AppCard(contentPadding = 12.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(C.accL),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_doc), null,
                                        tint = C.acc, modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.displayName,
                                        style = FontB.copy(color = C.fg),
                                        maxLines = 1
                                    )
                                    Text(
                                        "${item.createdAt.format(displayTime)} · ${item.count} 条",
                                        style = FontXs.copy(color = C.fg3)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "恢复",
                                        style = FontSm.copy(color = C.acc, fontWeight = FontWeight.SemiBold),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { confirmRestore = item }
                                            .padding(vertical = 4.dp, horizontal = 8.dp)
                                    )
                                    Text(
                                        "删除",
                                        style = FontSm.copy(color = C.dng, fontWeight = FontWeight.SemiBold),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                runCatching {
                                                    BackupManager.delete(context, item)
                                                    refresh++
                                                    snack("备份已删除")
                                                }
                                            }
                                            .padding(vertical = 4.dp, horizontal = 8.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            if (backups.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    AppButton(
                        "清空全部备份",
                        onClick = { confirmClearBackups = true },
                        style = BtnStyle.Danger,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    ConfirmDialog(
        visible = confirmRestore != null,
        title = "恢复备份",
        body = "恢复将覆盖当前全部排班（当前 ${shifts.size} 条将被替换）。",
        confirmText = "恢复",
        onDismiss = { confirmRestore = null },
        onConfirm = {
            val item = confirmRestore
            confirmRestore = null
            if (item != null) {
                vm.restoreBackup(context, item) { n ->
                    refresh++
                    snack("已恢复 $n 条排班")
                }
            }
        }
    )

    ConfirmDialog(
        visible = confirmClearBackups,
        title = "清空全部备份",
        body = "确认删除全部 ${backups.size} 个备份文件？此操作不可恢复。",
        confirmText = "清空",
        onDismiss = { confirmClearBackups = false },
        onConfirm = {
            BackupManager.clear(context)
            confirmClearBackups = false
            refresh++
            snack("备份已清空")
        }
    )
}
