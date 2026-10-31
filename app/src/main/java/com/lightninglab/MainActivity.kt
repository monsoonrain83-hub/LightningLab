package com.lightninglab

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
fun App(vm: MainVM = viewModel()) {
    LightningTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.systemBarsPadding()) {
                BackHandler(enabled = vm.screen != Screen.HOME) { vm.screen = Screen.HOME }
                Box(Modifier.weight(1f)) {
                    Crossfade(targetState = vm.screen, animationSpec = tween(200), label = "screen") { sc ->
                        when (sc) {
                            Screen.HOME -> Home(vm)
                            Screen.DETECT -> DetectionScreen(vm)
                            Screen.RESULTS -> Results(vm)
                            Screen.ABOUT -> About(vm)
                            Screen.PHOTO -> PhotoEditor(vm)
                            Screen.VIDEO -> VideoEditor(vm)
                            Screen.WEATHER -> WeatherScreen(vm)
                            Screen.PREPARE -> PrepareScreen(vm)
                        }
                    }
                }
                // Navigation is centralized on the home dashboard.
            }
        }
    }
}
