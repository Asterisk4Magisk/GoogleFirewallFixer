// Copyright 2026, Asterisk4Magisk contributors
// SPDX-License-Identifier: GPL-3.0

package org.asterisk.zcc.fixer.coloros

import android.app.AndroidAppHelper
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps the ColorOS battery component from cutting Google services off the network.
 *
 * After a failed Google connectivity probe the battery's `GoogleRestrictionController` sets
 * Google Play services, Play Store and ConfigUpdater to reject-all networking. Both of its
 * restriction entry points take `restricted` as their first argument; forcing it to `false`
 * makes the controller clear the policy instead, while the rest of its logic, including the
 * packages a user changed manually, runs unchanged.
 */
class BatteryGoogleRestrictionHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // The battery shares the com.oplus.athena process and is not its first package, so
        // isFirstApplication is deliberately not checked.
        if (lpparam.packageName != BATTERY_PACKAGE) return
        if (!hooked.compareAndSet(false, true)) return
        try {
            hookController(lpparam.classLoader)
        } catch (error: Throwable) {
            log("hook failed: $error")
        }
    }

    private fun hookController(classLoader: ClassLoader) {
        val controller = XposedHelpers.findClassIfExists(GoogleRestrictionMethods.CONTROLLER_CLASS, classLoader)
        if (controller == null) {
            log("GoogleRestrictionController not found; not a supported ColorOS battery")
            return
        }
        // Each method is hooked independently; installHook contains a failure to its own method.
        val inform = GoogleRestrictionMethods.installHook(
            "inform",
            GoogleRestrictionMethods.findInformMethod(controller),
            ::hookMethod,
            ::log,
        )
        val control = GoogleRestrictionMethods.installHook(
            "control",
            GoogleRestrictionMethods.findControlMethod(controller),
            ::hookMethod,
            ::log,
        )
        if (inform == null && control == null) return
        val version = batteryVersionName()?.let { " battery=$it" }.orEmpty()
        log("hooked inform=${inform?.name ?: "none"} control=${control?.name ?: "none"}$version")
    }

    private fun hookMethod(method: Method) {
        XposedBridge.hookMethod(method, LiftRestrictionHook)
    }

    private fun batteryVersionName(): String? {
        return try {
            AndroidAppHelper.currentApplication()?.packageManager?.getPackageInfo(BATTERY_PACKAGE, 0)?.versionName
        } catch (_: Throwable) {
            null
        }
    }

    private object LiftRestrictionHook : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            try {
                if (GoogleRestrictionMethods.liftRestriction(param.args)) {
                    log("restricted=true -> false in ${param.method.name}")
                }
            } catch (error: Throwable) {
                log("restriction rewrite failed: $error")
            }
        }
    }

    private companion object {
        const val BATTERY_PACKAGE = "com.oplus.battery"

        /** The battery package may be loaded more than once in a process; hook only once. */
        val hooked = AtomicBoolean(false)

        fun log(message: String) = XposedBridge.log("[GoogleFirewallFixer] $message")
    }
}
