package org.shirakawatyu.yamibo.novel.ui.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboComponentColors

@Preview
@Composable
fun TopBar(title: String = "收藏", content: @Composable () -> Unit = {}) {
    val componentColors = yamiboComponentColors()
    val topBarContentColor = componentColors.topBarContent
    Surface(
        color = componentColors.topBarContainer,
        contentColor = topBarContentColor
    ) {
        Row(modifier = Modifier.padding(15.dp, 10.dp).fillMaxWidth().height(36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(color = topBarContentColor, fontSize = 18.sp, text = title)
            content()
        }
    }
}
