package com.msa.fightandconquer.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.ui.HudToast
import com.msa.fightandconquer.ui.ToastKind
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.resolve

/**
 * Top-center toasts, anchored below the measured top chrome. One text style for
 * all three kinds — urgency is carried by the 30% tint wash, not a louder font.
 */
@Composable
internal fun ToastStack(toasts: List<HudToast>, topAnchor: Dp) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = topAnchor + HudSpacing)
            .padding(horizontal = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (toast in toasts) {
            key(toast.id) {
                val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                val wash = when (toast.kind) {
                    ToastKind.INFO -> null
                    ToastKind.WARNING -> UiColors.coin.copy(alpha = 0.3f)
                    ToastKind.ALERT -> UiColors.alert.copy(alpha = 0.3f)
                }
                val urgency = if (toast.kind == ToastKind.ALERT) {
                    LiveRegionMode.Assertive
                } else {
                    LiveRegionMode.Polite
                }
                AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { -it / 2 }) {
                    Text(
                        toast.text.resolve(),
                        Modifier
                            .hudSurface(12.dp)
                            .then(
                                if (wash != null) {
                                    Modifier.background(wash, RoundedCornerShape(12.dp))
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                            .semantics { liveRegion = urgency },
                        color = UiColors.ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
