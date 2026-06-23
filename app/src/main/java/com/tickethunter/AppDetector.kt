package com.tickethunter

import android.content.pm.PackageManager

object AppDetector {
    fun isInstalled(pm: PackageManager, platform: Platform): Boolean {
        return platform.packageNames.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    fun launchIntent(pm: PackageManager, platform: Platform) =
        platform.packageNames.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
}
