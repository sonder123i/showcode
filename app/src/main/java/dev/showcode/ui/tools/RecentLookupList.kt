package dev.showcode.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.UUID

/**
 * 最近查询列表：按时间倒序排列，最多保留 10 条。
 *
 * 这是「粘过单号回来」那一类的补救路径：用户可能从 A App 复制了单号到 B App，
 * 再回到亮码时已经忘了具体是哪一家——这个列表让他能回溯最近的几次操作。
 */
@Composable
fun RecentLookupList(
    history: List<String>,
    onClearAll: () -> Unit,
) {
    var showConfirm by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .width(Double.MAX_VALUE.dp)
                .height(32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "最近查询 (${history.size}/10)",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            if (history.isNotEmpty()) {
                IconButton(onClick = { showConfirm = true }) {
                    Icon(Icons.Default.Clear, contentDescription = "清空")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (history.isEmpty()) {
            Text(
                text = "还没有查询记录",
                color = Color.Gray,
                fontSize = 12.sp,
            )
        } else {
            LazyColumn(
                modifier = Modifier.height(200.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(history.reversed()) { item ->
                    Card(
                        onClick = { /* TODO: 填充该单号 */ },
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = item,
                                fontSize = 13.sp,
                            )
                            IconButton(onClick = { /* TODO: 删除该项 */ }) {
                                Icon(Icons.Default.Clear, contentDescription = "删除")
                            }
                        }
                    }
                }
            }
        }

        if (showConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showConfirm = false },
                title = { Text("确认清空？") },
                text = { Text("这条操作不可撤销。") },
                confirmButton = {
                    androidx.compose.material3.Button(onClick = {
                        onClearAll()
                        showConfirm = false
                    }) {
                        Text("清空")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showConfirm = false }) {
                        Text("取消")
                    }
                },
            )
        }
    }
}
