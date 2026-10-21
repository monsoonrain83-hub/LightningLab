package com.lightninglab

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
fun App(vm: MainVM = viewModel()) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFF4FC3F7), onPrimary = Color(0xFF00212E),
        background = Color(0xFF0B0F17), surface = Color(0xFF121826),
        secondary = Color(0xFF8E7CFF))) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.systemBarsPadding()) {
                BackHandler(enabled = vm.screen != Screen.HOME) { vm.screen = Screen.HOME }
                when (vm.screen) {
                    Screen.HOME -> Home(vm)
                    Screen.RESULTS -> Results(vm)
                    Screen.ABOUT -> About(vm)
                    Screen.PHOTO -> PhotoEditor(vm)
                    Screen.VIDEO -> VideoEditor(vm)
                }
            }
        }
    }
}
