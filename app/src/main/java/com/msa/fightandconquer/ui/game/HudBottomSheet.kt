package com.msa.fightandconquer.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.ui.UiColors
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The HUD's modal bottom sheet — the glanceable panels' frame since they left the
 * 264 dp side column. Deliberately in-composition rather than material3's
 * ModalBottomSheet: that one opens its own window, which escapes the activity's
 * immersive flags (the system bars would pop back over the board) and stacks
 * above every in-game overlay. Chrome per the handoff spec: opaque surface +
 * hairline + boardLift, top-only 28 dp corners, [UiColors.sheetScrim] behind.
 *
 * Dismissal: scrim tap, system Back, or dragging the handle zone past 30 % of
 * the sheet height (drag lives on the handle only so it never fights the
 * content's own scroll). The scrim consumes every touch — the board is never
 * interactive under an open sheet. [content] scrolls inside a 60 %-of-window
 * cap; [pinned] stays visible below it, mirroring the old side panel's slot.
 */
@Composable
internal fun HudBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    pinned: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val transition = remember { MutableTransitionState(false) }
    transition.targetState = visible
    if (!transition.currentState && !transition.targetState) return

    BackHandler(enabled = visible, onBack = onDismiss)
    val dismissCd = stringResource(R.string.cd_sheet_dismiss)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxSheet = maxHeight * 0.6f
        val scope = rememberCoroutineScope()
        val dragOffset = remember { Animatable(0f) }
        var sheetHeightPx by remember { mutableStateOf(0) }

        AnimatedVisibility(transition, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(UiColors.sheetScrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    )
                    .semantics {
                        contentDescription = dismissCd
                        role = Role.Button
                    },
            )
        }
        AnimatedVisibility(
            transition,
            Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(250)) { it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
        ) {
            Column(
                Modifier
                    .offset { IntOffset(0, dragOffset.value.roundToInt().coerceAtLeast(0)) }
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .heightIn(max = maxSheet)
                    .onSizeChanged { sheetHeightPx = it.height }
                    .hudSurface(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {}, // swallow taps so they never reach the scrim
            ) {
                // 48 dp-tall drag zone; releasing past 30 % of the sheet (or flinging) dismisses.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .pointerInput(sheetHeightPx) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (dragOffset.value > sheetHeightPx * 0.3f) {
                                        onDismiss()
                                        scope.launch { dragOffset.snapTo(0f) }
                                    } else {
                                        scope.launch { dragOffset.animateTo(0f) }
                                    }
                                },
                                onDragCancel = { scope.launch { dragOffset.animateTo(0f) } },
                            ) { _, dragAmount ->
                                scope.launch {
                                    dragOffset.snapTo((dragOffset.value + dragAmount).coerceAtLeast(0f))
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(34.dp, 4.dp)
                            .background(UiColors.hairline, RoundedCornerShape(2.dp)),
                    )
                }
                Column(
                    Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        content = content,
                    )
                    pinned?.invoke(this)
                }
            }
        }
    }
}

/**
 * Keeps the last non-null [value] so sheet content survives the exit animation —
 * the ViewModel's open-state flows null on dismiss, but the sheet still needs
 * something to draw while sliding out.
 */
@Composable
internal fun <T : Any> rememberRetained(value: T?): T? {
    var retained by remember { mutableStateOf(value) }
    if (value != null && value !== retained) retained = value
    return retained
}
