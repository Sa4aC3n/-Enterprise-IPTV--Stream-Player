package com.enterprise.iptv

import android.os.Bundle
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

        setContent {
            EnterpriseIPTVTheme {
                ChannelScreen(viewModel = viewModel)
            }
        }
    }
}
