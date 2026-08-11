package org.shirakawatyu.yamibo.novel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable

@Composable
fun yamiboSwitchColors(): SwitchColors {
    val colors = MaterialTheme.colorScheme
    return SwitchDefaults.colors(
        checkedThumbColor = colors.onPrimary,
        checkedTrackColor = colors.primary,
        checkedBorderColor = colors.primary,
        uncheckedThumbColor = colors.onSurfaceVariant,
        uncheckedTrackColor = colors.surfaceVariant,
        uncheckedBorderColor = colors.outline,
        disabledCheckedThumbColor = colors.onPrimary.copy(alpha = 0.7f),
        disabledCheckedTrackColor = colors.primary.copy(alpha = 0.4f),
        disabledCheckedBorderColor = colors.primary.copy(alpha = 0.4f),
        disabledUncheckedThumbColor = colors.onSurfaceVariant.copy(alpha = 0.5f),
        disabledUncheckedTrackColor = colors.surfaceVariant.copy(alpha = 0.5f),
        disabledUncheckedBorderColor = colors.outline.copy(alpha = 0.5f)
    )
}

@Composable
fun yamiboSliderColors(): SliderColors {
    val colors = MaterialTheme.colorScheme
    return SliderDefaults.colors(
        thumbColor = colors.primary,
        activeTrackColor = colors.primary,
        activeTickColor = colors.primary,
        inactiveTrackColor = colors.primary.copy(alpha = 0.24f),
        inactiveTickColor = colors.primary.copy(alpha = 0.24f),
        disabledThumbColor = colors.onSurface.copy(alpha = 0.38f),
        disabledActiveTrackColor = colors.onSurface.copy(alpha = 0.38f),
        disabledInactiveTrackColor = colors.onSurface.copy(alpha = 0.12f),
        disabledActiveTickColor = colors.surface,
        disabledInactiveTickColor = colors.onSurface.copy(alpha = 0.12f)
    )
}
