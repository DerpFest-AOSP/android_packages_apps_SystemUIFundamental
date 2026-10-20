/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic.ui.composable

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ElementContentScope
import com.android.compose.theme.PlatformTheme
import com.android.systemui.axdynamicbar.ui.AxDynamicBarChipViewModel
import com.android.systemui.axdynamicbar.ui.compose.AxDynamicBarKeyguardChip
import com.android.systemui.biometrics.AuthController
import com.android.systemui.common.ui.domain.interactor.ConfigurationInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.fundamental.ambientmusic.shared.AmbientIndicationMusic
import com.android.systemui.fundamental.ambientmusic.ui.binder.KeyguardAmbientIndicationAreaViewBinder
import com.android.systemui.fundamental.ambientmusic.ui.viewmodel.KeyguardAmbientIndicationViewModel
import com.android.systemui.graphics.ImageLoader
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.ui.viewmodel.KeyguardRootViewModel
import com.android.systemui.media.NotificationMediaManager
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.keyguard.ui.composable.elements.BaseLockscreenElement.ElementSource
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElement
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementProvider
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.util.concurrency.DelayableExecutor
import com.android.systemui.util.wakelock.DelayedWakeLock
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.flow.Flow

/**
 * Fills the scene-container lockscreen slot [LockscreenElementKeys.AmbientIndicationArea].
 *
 * AOSP's [com.android.systemui.keyguard.ui.composable.elements.AmbientIndicationAreaProvider]
 * contributes an empty element at [ElementSource.STANDARD]. This provider contributes the same key
 * at [ElementSource.VENDOR], which outranks it, and hosts the same [AndroidView] binding the
 * blueprint section uses. With scene container enabled the blueprint section is not composed, so
 * this is the path that actually shows Now Playing. The lockscreen dynamic-bar chip lives
 * between the shortcuts, except on a low fingerprint, where that row covers the sensor
 * and the chip is drawn here instead, above the lock icon.
 */
@SysUISingleton
class AmbientIndicationElementProvider
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    private val viewModel: KeyguardAmbientIndicationViewModel,
    powerInteractor: PowerInteractor,
    activityStarter: ActivityStarter,
    wakeLockFactory: DelayedWakeLock.Factory,
    keyguardInteractor: KeyguardInteractor,
    @Main configurationInteractor: ConfigurationInteractor,
    imageLoader: ImageLoader,
    keyguardRootViewModel: KeyguardRootViewModel,
    falsingManager: FalsingManager,
    @Main mainDelayableExecutor: DelayableExecutor,
    @Background backgroundExecutor: Executor,
    notificationMediaManager: NotificationMediaManager,
    statusBarStateController: StatusBarStateController,
    authController: AuthController,
    private val axDynamicBarChipViewModel: AxDynamicBarChipViewModel,
) : LockscreenElementProvider {

    private val dependencies =
        KeyguardAmbientIndicationAreaViewBinder.Dependencies(
            powerInteractor,
            activityStarter,
            wakeLockFactory,
            keyguardInteractor,
            configurationInteractor,
            imageLoader,
            keyguardRootViewModel,
            falsingManager,
            mainDelayableExecutor,
            backgroundExecutor,
            notificationMediaManager,
            statusBarStateController,
            authController,
        )

    override val elements: List<LockscreenElement> by lazy { listOf(AmbientIndicationElement()) }

    private inner class AmbientIndicationElement : LockscreenElement {
        override val key = LockscreenElementKeys.AmbientIndicationArea
        override val context = this@AmbientIndicationElementProvider.context
        override val source = ElementSource.VENDOR

        @Composable
        override fun LockscreenScope<ElementContentScope>.LockscreenElement() {
            AmbientIndication()
        }
    }

    @Composable
    private fun AmbientIndication(modifier: Modifier = Modifier) {
        val (disposable, setDisposable) = remember { mutableStateOf<DisposableHandle?>(null) }
        var chipHeight by remember { mutableIntStateOf(0) }
        val expanded by
            axDynamicBarChipViewModel.isKeyguardExpanded.collectAsStateWithLifecycle()
        val isLowUdfps by axDynamicBarChipViewModel.isLowUdfps.collectAsStateWithLifecycle()

        @Suppress("UNCHECKED_CAST")
        val ambientMusic by
            (viewModel.ambientMusicState as Flow<AmbientIndicationMusic?>)
                .collectAsStateWithLifecycle(initialValue = null)
        @Suppress("UNCHECKED_CAST")
        val devicePlayingMusic by
            (viewModel.devicePlayingMusic as Flow<Boolean>)
                .collectAsStateWithLifecycle(initialValue = false)
        // A search or a recognized song has to be visible even while the low-UDFPS chip
        // is using this slot. Playback on the phone is not that song.
        val showingNowPlaying = ambientMusic != null && !devicePlayingMusic
        val showNowPlayingView =
            if (isLowUdfps) {
                showingNowPlaying || chipHeight == 0
            } else {
                !devicePlayingMusic
            }

        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (isLowUdfps && !showingNowPlaying) {
                PlatformTheme {
                    AxDynamicBarKeyguardChip(
                        viewModel = axDynamicBarChipViewModel,
                        modifier =
                            Modifier.onSizeChanged { chipHeight = it.height }
                                .then(
                                    if (expanded) {
                                        Modifier.fillMaxSize()
                                    } else {
                                        Modifier.widthIn(max = 260.dp)
                                    }
                                ),
                    )
                }
            }
            if (showNowPlayingView) {
                AndroidView(
                    factory = { ctx ->
                        val view =
                            LayoutInflater.from(ctx).inflate(R.layout.ambient_indication, null)
                                as ViewGroup
                        setDisposable(
                            KeyguardAmbientIndicationAreaViewBinder.bind(
                                view,
                                viewModel,
                                dependencies,
                            )
                        )
                        view
                    },
                    onRelease = {
                        disposable?.dispose()
                        setDisposable(null)
                    },
                    modifier = Modifier.fillMaxWidth().hiddenWhenTallerThanSlot(),
                )
            }
        }
    }
}

/**
 * The lockscreen bounds this slot to the free band, so a card that does not fit there takes no
 * space instead of being squashed. The root layout then hides the empty slot as before.
 */
private fun Modifier.hiddenWhenTallerThanSlot(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
    if (placeable.height > constraints.maxHeight) {
        layout(constraints.minWidth, 0) {}
    } else {
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}
