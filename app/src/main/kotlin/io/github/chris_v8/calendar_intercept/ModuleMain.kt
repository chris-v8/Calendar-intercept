// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Chris (chris-v8)

package io.github.chris_v8.calendar_intercept

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Makes Niagara Launcher's date widget open the calendar's main screen when today is tapped.
 *
 * Niagara opens a date with `VIEW content://com.android.calendar/time/<millis>`, which makes the
 * calendar app jump to that exact day. For today only, this module swaps that intent for the
 * launch intent of the app that would have handled it, so the calendar opens on its main screen.
 * Other dates, events and every other launch pass through untouched.
 *
 * Every activity start from an Activity or a Context ends up in
 * `Instrumentation.execStartActivity`, so hooking it catches the widget whichever way Niagara
 * starts it.
 */
class ModuleMain : XposedModule() {

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != NIAGARA || !param.isFirstPackage) return
        // Never let a problem here escape: a crash would take the home screen down with it.
        try {
            hookExecStartActivity()
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Could not install the hook; today's date opens as usual", t)
        }
    }

    private fun hookExecStartActivity() {
        val overloads = Instrumentation::class.java.declaredMethods.filter { method ->
            method.name == "execStartActivity" && Intent::class.java in method.parameterTypes
        }
        check(overloads.isNotEmpty()) { "Instrumentation.execStartActivity not found" }

        for (method in overloads) {
            val intentIndex = method.parameterTypes.indexOf(Intent::class.java)
            hook(method)
                // If the hook itself fails, the original launch goes ahead as if there were no hook.
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val replacement = try {
                        replacementFor(chain.getArg(0) as? Context, chain.getArg(intentIndex) as? Intent)
                    } catch (t: Throwable) {
                        log(Log.ERROR, TAG, "Kept the original intent after an error", t)
                        null
                    }
                    if (replacement == null) {
                        chain.proceed()
                    } else {
                        val args = chain.args.toTypedArray()
                        args[intentIndex] = replacement
                        chain.proceed(args)
                    }
                }
        }
        log(Log.INFO, TAG, "Hooked ${overloads.size} Instrumentation.execStartActivity overload(s)")
    }

    /** Returns the intent to launch instead of [intent], or null to launch it unchanged. */
    private fun replacementFor(context: Context?, intent: Intent?): Intent? {
        if (context == null || intent == null) return null
        val millis = todayTimestampOf(intent) ?: return null

        val pm = context.packageManager
        val calendarPackage = intent.`package`
            ?: intent.component?.packageName
            ?: defaultHandlerOf(pm, intent)
        val generic = if (calendarPackage != null) {
            pm.getLaunchIntentForPackage(calendarPackage) ?: return null
        } else {
            // No default app chosen: let the system ask which calendar app to open.
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
        }
        // Keep Niagara's launch flags (new task, cleared task) and the opening animation's origin.
        generic.flags = intent.flags
        generic.sourceBounds = intent.sourceBounds

        log(Log.INFO, TAG, "Today tapped ($millis): opening ${calendarPackage ?: "a calendar app chooser"}")
        return generic
    }

    /** The time in a `content://com.android.calendar/time/<millis>` VIEW intent, if it is today. */
    private fun todayTimestampOf(intent: Intent): Long? {
        if (intent.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        if (uri.scheme != "content" || uri.authority != CALENDAR_AUTHORITY) return null
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != "time") return null
        val millis = segments[1].toLongOrNull() ?: return null

        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        return millis.takeIf { day == LocalDate.now(zone) }
    }

    /** The package of the app that opens [intent] without asking, or null if the user would be asked. */
    private fun defaultHandlerOf(pm: PackageManager, intent: Intent): String? {
        val activity = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            ?: return null
        // When there is no default the system resolves to its own chooser ("android").
        return activity.packageName.takeIf { it != "android" }
    }

    private companion object {
        const val TAG = "CalendarIntercept"
        const val NIAGARA = "bitpit.launcher"
        const val CALENDAR_AUTHORITY = "com.android.calendar"
    }
}
