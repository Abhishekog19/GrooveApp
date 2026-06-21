package com.groove.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.groove.music.core.network.ServiceStatusChecker
import com.groove.music.ui.navigation.GrooveNavHost
import com.groove.music.ui.theme.GrooveTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var serviceStatusChecker: ServiceStatusChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GrooveTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GrooveNavHost(serviceStatusChecker = serviceStatusChecker)
                }
            }
        }
    }
}
