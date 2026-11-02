// Copyright 2026, Asterisk4Magisk contributors
// SPDX-License-Identifier: GPL-3.0

package org.asterisk.zcc.fixer.redmagicos

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps RedMagic OS from cutting Google services off the network.
 *
 * When the subsys process reports `GMS_AVAILABLE_STATE state=false` (China SIM or country code),
 * system_server's `GoogleOptimizer` sets `mGmsAllowed = false` and calls `optimize(0)`, which
 * drops the GMS and Play Store uids in the iptables chain `zte_fw_gms`. This module only keeps
 * that chain clear and keeps SuperKiller away from the GMS processes holding FCM; it does not
 * touch `latchsky_enable`, PMS's own `mGmsAllowed` or any other optimization.
 */
class GoogleOptimizerHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // LSPosed reports system_server as the "android" package in the "android" process.
        if (lpparam.packageName != SYSTEM_PACKAGE || lpparam.processName != SYSTEM_PACKAGE) return
        if (!hooked.compareAndSet(false, true)) return
        try {
            hookSystemServer(lpparam.classLoader)
        } catch (error: Throwable) {
            log("hook failed: $error")
        }
    }

    private fun hookSystemServer(classLoader: ClassLoader) {
        val optimizer = XposedHelpers.findClassIfExists(GmsFirewallRules.OPTIMIZER_CLASS, classLoader)
        if (optimizer == null) {
            log("GoogleOptimizer not found; not a supported RedMagic OS")
            return
        }
        val allowed = XposedHelpers.findFieldIfExists(optimizer, "mGmsAllowed")
            ?.takeIf { field -> field.type == java.lang.Boolean.TYPE }
        if (allowed == null) log("mGmsAllowed not found")
        val optimize = XposedHelpers.findMethodExactIfExists(optimizer, "optimize", Integer.TYPE)
        if (optimize == null) log("optimize(int) not found")

        // system_server is AOT compiled; deoptimize the callers first so calls inlined into
        // them still reach the hooks.
        deoptimizeCallers(classLoader, optimizer)
        // Each hook is installed independently; installHook contains a failure to its own hook.
        val hooks = listOf(
            installHook("updateCheckStrategy") { hookUpdateCheckStrategy(optimizer, allowed) },
            installHook("optimize") { hookOptimize(optimize) },
            installHook("constructor") { hookConstructor(optimizer, optimize) },
            installHook("SuperKiller") { hookSuperKiller(classLoader) },
        )
        log("hooked ${hooks.count { it }}/${hooks.size}")
    }

    /** Before `updateCheckStrategy()`: `mGmsAllowed = true`, so it takes the allowed branch. */
    private fun hookUpdateCheckStrategy(optimizer: Class<*>, allowed: Field?): Boolean {
        if (allowed == null) return false
        val method = XposedHelpers.findMethodExactIfExists(optimizer, "updateCheckStrategy") ?: run {
            log("updateCheckStrategy() not found")
            return false
        }
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    val self = param.thisObject ?: return
                    if (!allowed.getBoolean(self)) {
                        allowed.setBoolean(self, true)
                        log("mGmsAllowed false -> true in updateCheckStrategy")
                    }
                } catch (error: Throwable) {
                    log("mGmsAllowed rewrite failed: $error")
                }
            }
        })
        return true
    }

    /** Fallback: `optimize(0)` (add the DROP rules) is turned into `optimize(1)` (clear them). */
    private fun hookOptimize(optimize: Method?): Boolean {
        if (optimize == null) return false
        XposedBridge.hookMethod(optimize, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    if (GmsFirewallRules.clearInsteadOfSet(param.args)) {
                        log("optimize(0) -> optimize(1)")
                    }
                } catch (error: Throwable) {
                    log("optimize rewrite failed: $error")
                }
            }
        })
        return true
    }

    /**
     * After `GoogleOptimizer(Context)`: post a delayed `optimize(1)` on its own handler, so DROP
     * rules left from before the module took effect are cleared once PMS and LocalServices are up.
     */
    private fun hookConstructor(optimizer: Class<*>, optimize: Method?): Boolean {
        if (optimize == null) return false
        val constructor = XposedHelpers.findConstructorExactIfExists(optimizer, Context::class.java) ?: run {
            log("GoogleOptimizer(Context) not found")
            return false
        }
        XposedBridge.hookMethod(constructor, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val self = param.thisObject
                    val handler = XposedHelpers.getObjectField(self, "mHandler") as? Handler
                    if (handler == null) {
                        log("mHandler is not a Handler; startup clear skipped")
                        return
                    }
                    handler.postDelayed({
                        try {
                            optimize.invoke(self, GmsFirewallRules.OPTIMIZE_CLR)
                            log("startup optimize(1) done, zte_fw_gms cleared")
                        } catch (error: Throwable) {
                            log("startup optimize(1) failed: $error")
                        }
                    }, STARTUP_CLEAR_DELAY_MS)
                } catch (error: Throwable) {
                    log("startup clear scheduling failed: $error")
                }
            }
        })
        return true
    }

    /** SuperKiller: the GMS main and persistent processes, which hold FCM, are ranked NEVER. */
    private fun hookSuperKiller(classLoader: ClassLoader): Boolean {
        val killer = XposedHelpers.findClassIfExists(GmsFirewallRules.SUPER_KILLER_CLASS, classLoader) ?: run {
            log("SuperKillerAmKillDecision not found")
            return false
        }
        val processRecord = XposedHelpers.findClass(GmsFirewallRules.PROCESS_RECORD_CLASS, classLoader)
        // Newer firmware ranks in getProcessKillRank; Android 13 (RedMagic OS 8) in
        // procSwitchToKillRank. Both take the ProcessRecord first and return the rank.
        val method = XposedHelpers.findMethodExactIfExists(
            killer,
            "getProcessKillRank",
            processRecord,
            Integer.TYPE,
            String::class.java,
        ) ?: XposedHelpers.findMethodExactIfExists(
            killer,
            "procSwitchToKillRank",
            processRecord,
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            String::class.java,
        ) ?: run {
            log("getProcessKillRank / procSwitchToKillRank not found")
            return false
        }
        deoptimizeAll(killer)
        deoptimizeAll(XposedHelpers.findClassIfExists(GmsFirewallRules.SUPER_KILLER_DETECTOR_CLASS, classLoader))
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val record = param.args[0] ?: return
                    val processName = XposedHelpers.getObjectField(record, "processName")
                    val info = XposedHelpers.getObjectField(record, "info") as? ApplicationInfo
                    val rank = GmsFirewallRules.protectedRank(param.result, processName, info?.packageName)
                    if (rank != null) {
                        log("SuperKiller rank ${param.result} -> NEVER for $processName uid ${info?.uid}")
                        param.result = rank
                    }
                } catch (error: Throwable) {
                    log("SuperKiller rank rewrite failed: $error")
                }
            }
        })
        log("SuperKiller hooked ${method.name}")
        return true
    }

    private fun installHook(role: String, hook: () -> Boolean): Boolean {
        return try {
            hook()
        } catch (error: Throwable) {
            log("$role hook failed: $error")
            false
        }
    }

    private fun deoptimizeCallers(classLoader: ClassLoader, optimizer: Class<*>) {
        if (deoptimizeMethod == null) {
            log("deoptimizeMethod unavailable; inlined callers may bypass the hooks")
            return
        }
        var count = deoptimizeAll(optimizer)
        GmsFirewallRules.OPTIMIZER_INNER_CLASSES.forEach { name ->
            count += deoptimizeAll(XposedHelpers.findClassIfExists(name, classLoader))
        }
        XposedHelpers.findClassIfExists(GmsFirewallRules.POWER_TRACKER_CLASS, classLoader)
            ?.let { tracker -> XposedHelpers.findMethodExactIfExists(tracker, "onStart") }
            ?.let { onStart -> if (deoptimize(onStart)) count++ }
        log("deoptimized $count methods")
    }

    private fun deoptimizeAll(clazz: Class<*>?): Int {
        return clazz?.declaredMethods?.count(::deoptimize) ?: 0
    }

    private fun deoptimize(member: Member): Boolean {
        val method = deoptimizeMethod ?: return false
        return try {
            method.invoke(null, member)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private companion object {
        const val SYSTEM_PACKAGE = "android"

        /** Delay before the one-shot startup clear, so PMS and LocalServices are up. */
        const val STARTUP_CLEAR_DELAY_MS = 20_000L

        /** system_server may report the package more than once; hook only once. */
        val hooked = AtomicBoolean(false)

        /** LSPosed's `XposedBridge.deoptimizeMethod(Member)`; not part of the API 82 jar. */
        val deoptimizeMethod: Method? by lazy {
            XposedHelpers.findMethodExactIfExists(XposedBridge::class.java, "deoptimizeMethod", Member::class.java)
        }

        fun log(message: String) = XposedBridge.log("[GoogleFirewallFixer] $message")
    }
}
