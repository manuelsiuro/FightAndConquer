package com.msa.fightandconquer.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.ui.HudToast
import com.msa.fightandconquer.ui.ToastKind
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.resolve

@Composable
internal fun ToastStack(toasts: List<HudToast>) {
    Column(
        Modifier
            .fillMaxWidth()
            .safeDrawingPadding()
            .padding(top = TopBarHeight + HudGutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (toast in toasts) {
            key(toast.id) {
                val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                val (bg, fg, size) = when (toast.kind) {
                    ToastKind.INFO -> Triple(UiColors.panel, UiColors.ink, 13.sp)
                    ToastKind.WARNING -> Triple(UiColors.toastWarning, UiColors.ink, 13.sp)
                    ToastKind.ALERT -> Triple(UiColors.alert, Color.White, 15.sp)
                }
                val urgency = if (toast.kind == ToastKind.ALERT) {
                    LiveRegionMode.Assertive
                } else {
                    LiveRegionMode.Polite
                }
                AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { -it / 2 }) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = bg,
                        shadowElevation = 4.dp,
                        modifier = Modifier.semantics { liveRegion = urgency },
                    ) {
                        Text(
                            toast.text.resolve(),
                            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = fg,
                            fontSize = size,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
