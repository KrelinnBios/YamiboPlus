package org.shirakawatyu.yamibo.novel.ui.widget

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * App 内统一的自定义 Toast。
 *
 * 调用 [YamiboToast.show] 发送消息，在根 Compose 树放置 [YamiboToastHost] 负责展示。
 */
object YamiboToast {
    const val LENGTH_SHORT = 2_000L

    private val nextId = AtomicLong(0L)

    private val _events = MutableSharedFlow<ToastEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = _events.asSharedFlow()

    fun show(
        context: Context? = null,
        message: String,
        durationMillis: Long = LENGTH_SHORT
    ) {
        val cleanMessage = message.trim()
        if (cleanMessage.isEmpty()) return
        _events.tryEmit(
            ToastEvent(
                id = nextId.incrementAndGet(),
                message = cleanMessage,
                durationMillis = durationMillis.coerceAtLeast(800L)
            )
        )
    }

    fun showPersistent(ownerKey: String, message: String) {
        val cleanMessage = message.trim()
        if (cleanMessage.isEmpty()) return
        _events.tryEmit(
            ToastEvent(
                id = nextId.incrementAndGet(),
                message = cleanMessage,
                durationMillis = Long.MAX_VALUE,
                ownerKey = ownerKey
            )
        )
    }

    fun dismiss(ownerKey: String) {
        _events.tryEmit(
            ToastEvent(
                id = nextId.incrementAndGet(),
                message = "",
                durationMillis = 0L,
                ownerKey = ownerKey,
                isDismissal = true
            )
        )
    }
}

data class ToastEvent(
    val id: Long,
    val message: String,
    val durationMillis: Long,
    val ownerKey: String? = null,
    val isDismissal: Boolean = false
)

@Composable
fun YamiboToastPill(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary)
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text(
                text = message,
                modifier = Modifier.padding(start = 10.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun YamiboToastHost(modifier: Modifier = Modifier) {
    var displayedEvent by remember { mutableStateOf<ToastEvent?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var hideJob: Job? = null
        YamiboToast.events.collect { event ->
            if (event.isDismissal) {
                if (displayedEvent?.ownerKey == event.ownerKey) {
                    val dismissedEventId = displayedEvent?.id
                    hideJob?.cancel()
                    visible = false
                    launch {
                        delay(180L)
                        if (displayedEvent?.id == dismissedEventId) {
                            displayedEvent = null
                        }
                    }
                }
            } else {
                hideJob?.cancel()
                displayedEvent = event
                visible = true
                hideJob = launch {
                    delay(event.durationMillis)
                    if (displayedEvent?.id == event.id) {
                        visible = false
                        delay(180L)
                        if (displayedEvent?.id == event.id) {
                            displayedEvent = null
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(220f),
        contentAlignment = Alignment.BottomCenter
    ) {
        val event = displayedEvent
        androidx.compose.animation.AnimatedVisibility(
            visible = visible && event != null,
            enter = fadeIn(animationSpec = tween(200)) +
                    slideInVertically(initialOffsetY = { 30 }),
            exit = fadeOut(animationSpec = tween(500)),
            modifier = Modifier
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp)
        ) {
            event?.let { toast ->
                YamiboToastPill(message = toast.message)
            }
        }
    }
}
