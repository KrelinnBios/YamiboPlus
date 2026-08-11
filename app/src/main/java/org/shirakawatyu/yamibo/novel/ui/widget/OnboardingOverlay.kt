package org.shirakawatyu.yamibo.novel.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.shirakawatyu.yamibo.novel.util.OnboardingUtil

data class OnboardingStep(
    val title: String,
    val description: String
)

/**
 * 分页面首次进入时弹出的新手引导卡片。同一个 [page] 只展示一次（持久化到 DataStore），
 * [enabled] 用于控制额外的展示条件（如登录态）；条件不满足时不查询、不展示。
 */
@Composable
fun OnboardingOverlay(
    page: OnboardingUtil.Page,
    steps: List<OnboardingStep>,
    enabled: Boolean = true
) {
    var checked by remember(page) { mutableStateOf(false) }
    var visible by remember(page) { mutableStateOf(false) }
    var stepIndex by remember(page) { mutableIntStateOf(0) }

    LaunchedEffect(page, enabled) {
        if (!enabled || checked) return@LaunchedEffect
        OnboardingUtil.hasShown(page) { shown ->
            checked = true
            if (!shown) {
                stepIndex = 0
                visible = true
            }
        }
    }

    if (!visible || steps.isEmpty()) return

    val step = steps[stepIndex]
    val isLastStep = stepIndex == steps.lastIndex

    fun finish() {
        visible = false
        OnboardingUtil.markShown(page)
    }

    AlertDialog(
        onDismissRequest = { finish() },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text(step.title) },
        text = {
            Column {
                Text(step.description)
                if (steps.size > 1) {
                    Row(
                        modifier = Modifier.padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        steps.indices.forEach { i ->
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i == stepIndex) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (steps.size > 1 && stepIndex > 0) {
                    TextButton(onClick = { stepIndex-- }) {
                        Text("上一步")
                    }
                }
                TextButton(onClick = { if (isLastStep) finish() else stepIndex++ }) {
                    Text(if (isLastStep) "我知道了" else "下一步")
                }
            }
        },
        dismissButton = if (steps.size > 1 && !isLastStep) {
            { TextButton(onClick = { finish() }) { Text("跳过") } }
        } else {
            null
        }
    )
}
