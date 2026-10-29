package com.softmusic.app.icons

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

internal class AndroidLauncherIconStore(context: Context) : LauncherIconStore {
    private val packageName = context.packageName
    private val packageManager = context.packageManager
    override val supportsAtomicChanges: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private fun component(icon: SeasonalIcon) = ComponentName(packageName, packageName + icon.launcherAliasSuffix)

    override fun enabledIcons(): Set<SeasonalIcon> = SeasonalIcon.entries.filterTo(mutableSetOf()) { icon ->
        when (packageManager.getComponentEnabledSetting(component(icon))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == SeasonalIcon.Original
            else -> false
        }
    }

    override fun replaceEnabledIcon(icon: SeasonalIcon) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            throw UnsupportedOperationException("Atomic component changes require Android 13")
        }
        packageManager.setComponentEnabledSettings(SeasonalIcon.entries.map { candidate ->
            PackageManager.ComponentEnabledSetting(
                component(candidate),
                if (candidate == icon) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        })
    }

    override fun setEnabled(icon: SeasonalIcon, enabled: Boolean) {
        packageManager.setComponentEnabledSetting(
            component(icon),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
