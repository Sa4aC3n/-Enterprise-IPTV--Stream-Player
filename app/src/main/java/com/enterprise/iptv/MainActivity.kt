package com.enterprise.iptv

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.enterprise.iptv.presentation.channels.ChannelScreen
import com.enterprise.iptv.presentation.channels.ChannelsUiState
import com.enterprise.iptv.presentation.channels.ChannelsViewModel
import com.enterprise.iptv.presentation.theme.EnterpriseIPTVTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: ChannelsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // تثبيت شاشة البداية الرسمية Android 12+ Splash Screen API
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // استمرار عرض شاشة البداية حتى تكتمل قراءة القنوات الأولية لضمان تجربة انتقال سلسة وبدون وميض
        splashScreen.setKeepOnScreenCondition {
            viewModel.uiState.value is ChannelsUiState.Loading
        }

        handleReminderIntent(intent)

        setContent {
            EnterpriseIPTVTheme {
                ChannelScreen(
                    viewModel = viewModel,
                    onEnterPip = { enterPipMode() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleReminderIntent(intent)
    }

    private fun handleReminderIntent(intent: Intent?) {
        val streamUrl = intent?.getStringExtra("EXTRA_STREAM_URL")
        if (!streamUrl.isNullOrBlank()) {
            val channelName = intent.getStringExtra("EXTRA_CHANNEL_NAME") ?: ""
            viewModel.playStreamDirect(streamUrl, channelName)
        }
    }

    fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val aspectRatio = Rational(16, 9)
                val paramsBuilder = PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    paramsBuilder.setAutoEnterEnabled(true)
                    paramsBuilder.setSeamlessResizeEnabled(true)
                }

                enterPictureInPictureMode(paramsBuilder.build())
            } catch (e: Exception) {
                // Ignore if PiP not supported on specific device
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // الدخول التلقائي في وضع النافذة العائمة عند الخروج للشاشة الرئيسية أثناء تشغيل البث
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (viewModel.playerEngine.exoPlayer.isPlaying) {
                enterPipMode()
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.setPipMode(isInPictureInPictureMode)
    }
}
