package com.softmusic.app.ui

import androidx.compose.runtime.compositionLocalOf
import com.softmusic.app.R

internal val LocalHighPerformanceMode = compositionLocalOf { false }

internal val LocalNavigationBackEnabled = compositionLocalOf { true }

internal val LocalSeasonalIcon = compositionLocalOf { R.drawable.favicon }

internal val LocalSeasonalLogo = compositionLocalOf { R.drawable.seasonal_logo_original }
