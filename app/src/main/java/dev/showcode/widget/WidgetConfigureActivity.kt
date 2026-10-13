package dev.showcode.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.showcode.R
import dev.showcode.app
import dev.showcode.core.PickupPointGroup
import dev.showcode.core.groupByPoint
import dev.showcode.ui.rememberTickingNow
import dev.showcode.ui.theme.Accent
import dev.showcode.ui.theme.AccentTint
import dev.showcode.ui.theme.Canvas
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.InkMuted
import dev.showcode.ui.theme.OnsiteSurface
import dev.showcode.ui.theme.ShowCodeShapes
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.ShowCodeTheme
import dev.showcode.ui.theme.SurfaceCard

/**
 * 添加组件时的配置页（方案丙：绑定点是一等公民，但不强制）。
 * 契约要求：用户中途放弃时结果必须是 RESULT_CANCELED，组件才不会被加上，
 * 所以进 onCreate 就先 setResult(CANCELED)，确认选择时才改 OK。
 */
class WidgetConfigureActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(RESULT_CANCELED)
        setContent {
            ShowCodeTheme {
                ConfigureScreen(onPick = { key, name -> confirm(key, name) })
            }
        }
    }

    private fun confirm(pointKey: String?, pointName: String?) {
        app.repository.prefs.setWidgetBinding(widgetId, pointKey, pointName)
        PickupWidgetProvider.updateAll(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

@Composable
private fun ConfigureScreen(onPick: (String?, String?) -> Unit) {
    val context = LocalContext.current
    val repository = context.app.repository
    val data by repository.data.collectAsState()
    val thresholds by repository.thresholds.collectAsState()
    val now = rememberTickingNow(60_000L)
    val groups = remember(data.parcels, data.aliases, now, thresholds) {
        groupByPoint(data.parcels, now, thresholds, data.aliases)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnsiteSurface)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Text(
            text = stringResource(R.string.widget_configure_title),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
            style = ShowCodeText.Headline,
            color = Ink,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OptionRow(
                    title = stringResource(R.string.widget_configure_general),
                    hint = stringResource(R.string.widget_configure_general_hint),
                    highlighted = true,
                    onClick = { onPick(null, null) },
                )
            }
            items(groups, key = { it.key }) { group ->
                OptionRow(
                    title = stringResource(R.string.widget_configure_bind, group.name),
                    hint = stringResource(R.string.widget_configure_bind_hint),
                    highlighted = false,
                    onClick = { onPick(group.key, group.name) },
                )
            }
        }
    }
}

@Composable
private fun OptionRow(title: String, hint: String, highlighted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlighted) AccentTint else Canvas, ShowCodeShapes.Md)
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = if (highlighted) Accent else Hairline,
                shape = ShowCodeShapes.Md,
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = ShowCodeText.Item, color = Ink)
            Spacer(Modifier.height(2.dp))
            Text(text = hint, style = ShowCodeText.Caption, color = InkMuted)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.widget_configure_add),
            modifier = Modifier
                .background(SurfaceCard, ShowCodeShapes.Sm)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            style = ShowCodeText.Action,
            color = Accent,
        )
    }
}
