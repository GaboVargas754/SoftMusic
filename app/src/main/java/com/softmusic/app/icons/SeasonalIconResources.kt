package com.softmusic.app.icons

import androidx.annotation.DrawableRes
import com.softmusic.app.R

@get:DrawableRes
val SeasonalIcon.drawableRes: Int
    get() = when (this) {
        SeasonalIcon.Original -> R.drawable.seasonal_icon_original
        SeasonalIcon.Halloween -> R.drawable.seasonal_icon_halloween
        SeasonalIcon.Christmas -> R.drawable.seasonal_icon_christmas
        SeasonalIcon.DayOfTheDead -> R.drawable.seasonal_icon_day_of_the_dead
        SeasonalIcon.NewYear -> R.drawable.seasonal_icon_new_year
        SeasonalIcon.Valentines -> R.drawable.seasonal_icon_valentines
        SeasonalIcon.Independence -> R.drawable.seasonal_icon_independence
        SeasonalIcon.Easter -> R.drawable.seasonal_icon_easter
        SeasonalIcon.MusicDay -> R.drawable.seasonal_icon_music_day
        SeasonalIcon.Anniversary -> R.drawable.seasonal_icon_anniversary
    }

/** Horizontal wordmark matching the same season as the launcher icon. */
@get:DrawableRes
val SeasonalIcon.logoDrawableRes: Int
    get() = when (this) {
        SeasonalIcon.Original -> R.drawable.seasonal_logo_original
        SeasonalIcon.Halloween -> R.drawable.seasonal_logo_halloween
        SeasonalIcon.Christmas -> R.drawable.seasonal_logo_christmas
        SeasonalIcon.DayOfTheDead -> R.drawable.seasonal_logo_day_of_the_dead
        SeasonalIcon.NewYear -> R.drawable.seasonal_logo_new_year
        SeasonalIcon.Valentines -> R.drawable.seasonal_logo_valentines
        SeasonalIcon.Independence -> R.drawable.seasonal_logo_independence
        SeasonalIcon.Easter -> R.drawable.seasonal_logo_easter
        SeasonalIcon.MusicDay -> R.drawable.seasonal_logo_music_day
        SeasonalIcon.Anniversary -> R.drawable.seasonal_logo_anniversary
    }

/** Keep these component suffixes stable after release so launcher shortcuts can resolve them. */
val SeasonalIcon.launcherAliasSuffix: String
    get() = ".launcher.${name}Icon"
