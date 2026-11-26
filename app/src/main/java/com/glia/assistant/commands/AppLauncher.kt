package com.glia.assistant.commands

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** Abre aplicativos instalados a partir do nome falado. */
class AppLauncher(context: Context) {
    private val appContext = context.applicationContext

    fun launch(query: String): AppLaunch {
        val match = AppMatcher.bestMatch(query, installedApps())
            ?: return AppLaunch(
                spoken = "Nao encontrei o aplicativo $query. Tente falar o nome como aparece no aparelho.",
                launched = false,
            )
        val intent = appContext.packageManager.getLaunchIntentForPackage(match.packageName)
            ?: return AppLaunch("Nao consegui abrir ${match.label}.", launched = false)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            AppLaunch("Abrindo ${match.label}.", launched = true)
        } catch (_: Exception) {
            AppLaunch("Nao consegui abrir ${match.label}.", launched = false)
        }
    }

    private fun installedApps(): List<AppEntry> {
        val pm = appContext.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return resolved.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            if (activity.packageName == appContext.packageName) return@mapNotNull null
            val label = runCatching { info.loadLabel(pm).toString() }.getOrNull().orEmpty().trim()
            if (label.isEmpty()) null else AppEntry(label, activity.packageName)
        }.distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}

data class AppLaunch(val spoken: String, val launched: Boolean)
