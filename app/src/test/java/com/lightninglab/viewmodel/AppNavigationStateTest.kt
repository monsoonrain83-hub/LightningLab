package com.lightninglab.viewmodel

import com.lightninglab.Screen
import org.junit.Assert.*
import org.junit.Test

class AppNavigationStateTest {
    @Test fun retainsNavigationInComposeObservableHolder() {
        val navigation = AppNavigationState()
        assertEquals(Screen.HOME, navigation.current)
        navigation.navigate(Screen.VIDEO)
        assertEquals(Screen.VIDEO, navigation.current)
        assertTrue(navigation.backHome())
        assertEquals(Screen.HOME, navigation.current)
        assertFalse(navigation.backHome())
    }

    @Test fun preservesAllExistingDestinations() {
        val navigation = AppNavigationState()
        for (screen in Screen.values()) {
            navigation.navigate(screen)
            assertEquals(screen, navigation.current)
        }
    }
}
