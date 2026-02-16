package com.wfsdiy.wfs_control_2.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

val LocalLanguage = compositionLocalOf { "en" }

@Composable
fun loc(key: String): String {
    LocalLanguage.current // Subscribe to changes for recomposition
    return LocalizationManager.get(key)
}

@Composable
fun loc(key: String, vararg params: Pair<String, String>): String {
    LocalLanguage.current
    return LocalizationManager.get(key, *params)
}

// For non-composable contexts (OscService notifications)
fun locStatic(key: String): String = LocalizationManager.get(key)
