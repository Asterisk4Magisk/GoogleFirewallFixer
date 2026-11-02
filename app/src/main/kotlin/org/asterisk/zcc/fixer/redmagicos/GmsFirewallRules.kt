// Copyright 2026, Asterisk4Magisk contributors
// SPDX-License-Identifier: GPL-3.0

package org.asterisk.zcc.fixer.redmagicos

/**
 * The decisions the system_server hooks make, kept free of Xposed so they are testable on the
 * JVM. Class and member names come from services.jar of RedMagic OS (ZTE) and are not
 * obfuscated.
 */
internal object GmsFirewallRules {
    const val OPTIMIZER_CLASS = "com.android.server.am.GoogleOptimizer"
    const val SUPER_KILLER_CLASS = "com.android.server.am.SuperKillerAmKillDecision"
    const val SUPER_KILLER_DETECTOR_CLASS = "com.android.server.am.SuperKillerDetector"
    const val PROCESS_RECORD_CLASS = "com.android.server.am.ProcessRecord"
    const val POWER_TRACKER_CLASS = "com.android.server.am.ZtePowerTrackerService"

    /** Inner classes of GoogleOptimizer that call the hooked methods. */
    val OPTIMIZER_INNER_CLASSES = listOf(
        "1", "2", "3", "H", "OptimizerReceiver", "ReachableCheck", "DnsGoogleOptimizeThread",
    )
        .map { name -> "$OPTIMIZER_CLASS\$$name" }

    const val GMS_PACKAGE = "com.google.android.gms"

    /** `optimize(int)` levels: SET adds the zte_fw_gms DROP rules, CLR removes them. */
    const val OPTIMIZE_SET = 0
    const val OPTIMIZE_CLR = 1

    /** SuperKiller ranks: -1 INVALID, 0 KILL, 1 LATER, 2 SELDOM, 3 NEVER. */
    const val RANK_NEVER = 3

    /** GMS processes that hold the FCM/MCS connection and must not be killed. */
    private val PROTECTED_PROCESSES = setOf(GMS_PACKAGE, "$GMS_PACKAGE.persistent")

    /**
     * Turns an `optimize(OPTIMIZE_SET)` call into `optimize(OPTIMIZE_CLR)`. Returns whether it
     * changed anything; any other argument shape is left as it is.
     */
    fun clearInsteadOfSet(args: Array<Any?>): Boolean {
        if (args.isEmpty() || args[0] != OPTIMIZE_SET) return false
        args[0] = OPTIMIZE_CLR
        return true
    }

    /**
     * Returns [RANK_NEVER] when a killable [rank] belongs to a protected GMS process, or null to
     * keep the rank SuperKiller computed. Every other process of the GMS package keeps its rank.
     */
    fun protectedRank(rank: Any?, processName: Any?, packageName: String?): Int? {
        if (rank !is Int || rank < 0 || rank >= RANK_NEVER) return null
        if (packageName != GMS_PACKAGE || processName !in PROTECTED_PROCESSES) return null
        return RANK_NEVER
    }
}
