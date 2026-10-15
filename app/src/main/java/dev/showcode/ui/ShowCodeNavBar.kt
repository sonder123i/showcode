package dev.showcode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.showcode.R
import dev.showcode.ui.theme.Accent
import dev.showcode.ui.theme.AccentTint
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.InkMuted
import dev.showcode.ui.theme.ShowCodeShapes
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.SurfaceFloating

/** 底栏本体高度。和它取代的那颗刷新胶囊同一个数——位置、体积、圆角都不变，变的只是里面是三颗键。 */
val NAV_BAR_HEIGHT = 52.dp

private val NAV_BAR_BOTTOM = 12.dp
private val NAV_ICON_SIZE = 20.dp

/**
 * 列表底部要给底栏留的净空 = 底栏高 + 底距 + 一个卡片间距。
 * 少了这一项，滚到底时最后一张卡会被底栏永久压住，而首页那一张往往是最新到的件。
 */
val NAV_BAR_CLEARANCE = NAV_BAR_HEIGHT + NAV_BAR_BOTTOM + 16.dp

/** 底栏上的三个去处。刻意不复用 `ShowCodeRoot` 那个路由枚举——它是文件私有的，而底栏只认这三格。 */
enum class NavBarTab { HOME, IDENTITY, SETTINGS }

/**
 * 底部导航：待取件 / 身份码 / 设置。
 *
 * 它浮在列表上方而不是贴着屏幕边，和它取代的那颗胶囊同一个落点。
 * 选中的那一项**只换颜色不换字重**：加粗会让标签变宽，整排键跟着左右挪——
 * 换个 tab 底栏自己晃一下，是本仓库已经写进回归单的那类错（F2「整行不许左右晃」）。
 */
@Composable
fun ShowCodeNavBar(
    current: NavBarTab,
    onHome: () -> Unit,
    onIdentity: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(bottom = NAV_BAR_BOTTOM)
            .height(NAV_BAR_HEIGHT)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(SurfaceFloating)
            .border(1.dp, Hairline, CircleShape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        NavBarKey(
            label = stringResource(R.string.home_title),
            selected = current == NavBarTab.HOME,
            onClick = onHome,
        ) { tint ->
            Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(NAV_ICON_SIZE), tint = tint)
        }
        NavBarKey(
            label = stringResource(R.string.identity_title),
            selected = current == NavBarTab.IDENTITY,
            onClick = onIdentity,
        ) { tint ->
            // 二维码那个图形不在 material-icons-core 里，是本仓库自己画的一颗（ic_nav_identity）。
            Icon(
                painter = painterResource(R.drawable.ic_nav_identity),
                contentDescription = null,
                modifier = Modifier.size(NAV_ICON_SIZE),
                tint = tint,
            )
        }
        NavBarKey(
            label = stringResource(R.string.settings_title),
            selected = current == NavBarTab.SETTINGS,
            onClick = onSettings,
        ) { tint ->
            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(NAV_ICON_SIZE), tint = tint)
        }
    }
}

/**
 * 一颗键：图形 + 一行标签。图标自己不吃颜色，选中态那一支颜色由这里算好再交给槽——
 * 三个键的选中语义因此只有一份，不会出现「这一颗用底色、那一颗用描边」那种漂移。
 *
 * `contentDescription = null`：旁边那行字就是它的名字，读两遍是噪音。
 */
@Composable
private fun NavBarKey(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val tint = if (selected) Accent else InkMuted
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(ShowCodeShapes.Pill)
            .background(if (selected) AccentTint else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        icon(tint)
        Text(
            text = label,
            style = ShowCodeText.Micro,
            color = if (selected) Accent else InkMuted,
        )
    }
}
