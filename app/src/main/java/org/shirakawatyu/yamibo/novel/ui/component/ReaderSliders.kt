package org.shirakawatyu.yamibo.novel.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shirakawatyu.yamibo.novel.ui.theme.yamiboSliderColors
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ReaderProgressSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null
) {
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = 0,
        modifier = modifier.height(24.dp),
        colors = yamiboSliderColors()
    )
}

@Composable
fun ReaderSettingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            modifier = Modifier.width(82.dp)
        )
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .weight(1f)
                .height(32.dp),
            colors = yamiboSliderColors()
        )
    }
}

@Composable
fun ReaderSettingSlider(
    label: String,
    value: Float,
    options: List<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    require(options.size >= 2) { "ReaderSettingSlider 至少需要两个选项" }
    val selectedIndex = options.indices.minBy { index -> abs(options[index] - value) }
    ReaderSettingSlider(
        label = label,
        value = selectedIndex.toFloat(),
        valueRange = 0f..options.lastIndex.toFloat(),
        steps = (options.size - 2).coerceAtLeast(0),
        onValueChange = { index ->
            onValueChange(options[index.roundToInt().coerceIn(options.indices)])
        },
        modifier = modifier
    )
}
