/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic.ui.composable

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.compose.animation.scene.ElementContentScope
import com.android.systemui.biometrics.AuthController
import com.android.systemui.common.ui.domain.interactor.ConfigurationInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
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
import com.android.systemui.R as AppR
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.util.concurrency.DelayableExecutor
import com.android.systemui.util.wakelock.DelayedWakeLock
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.DisposableHandle

/**
 * Fills the scene-container lockscreen slot [LockscreenElementKeys.AmbientIndicationArea].
 *
 * AOSP's [com.android.systemui.keyguard.ui.composable.elements.AmbientIndicationAreaProvider]
 * contributes an empty element at [ElementSource.STANDARD]. This provider contributes the same key
 * at [ElementSource.VENDOR], which outranks it, and hosts the same [AndroidView] binding the
 * blueprint section uses. With scene container enabled the blueprint section is not composed, so
 * this is the path that actually shows Now Playing.
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
    private val authController: AuthController,
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
        // Scene container anchors this element on the lock-icon edge and ignores the view's
        // layout margins. Pad the side that faces the icon: below the UDFPS circle, or above
        // the rear fingerprint / lock glyph by the stock bottom margin.
        val udfps = authController.isUdfpsSupported
        val iconGap =
            dimensionResource(
                if (udfps) {
                    AppR.dimen.fundamental_ambient_indication_udfps_gap
                } else {
                    R.dimen.ambient_indication_margin_bottom
                }
            )

        AndroidView(
            factory = { ctx ->
                val view =
                    LayoutInflater.from(ctx).inflate(R.layout.ambient_indication, null) as ViewGroup
                setDisposable(
                    KeyguardAmbientIndicationAreaViewBinder.bind(view, viewModel, dependencies)
                )
                view
            },
            onRelease = {
                disposable?.dispose()
                setDisposable(null)
            },
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(
                        top = if (udfps) iconGap else 0.dp,
                        bottom = if (udfps) 0.dp else iconGap,
                    ),
        )
    }
}
