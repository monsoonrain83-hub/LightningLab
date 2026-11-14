package com.lightninglab.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lightninglab.Screen

/**
 * Real, Compose-observable navigation state extracted from MainVM.
 * The existing MainActivity and all screens keep using MainVM.screen during
 * migration, while MainVM delegates reads/writes to this single owner.
 */
internal class AppNavigationState(initial: Screen = Screen.HOME) {
    var current: Screen by mutableStateOf(initial)
        private set

    fun navigate(destination: Screen) {
        current = destination
    }

    fun backHome(): Boolean {
        if (current == Screen.HOME) return false
        current = Screen.HOME
        return true
    }
}
