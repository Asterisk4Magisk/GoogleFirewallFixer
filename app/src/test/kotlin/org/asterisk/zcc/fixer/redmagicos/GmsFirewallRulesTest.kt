// Copyright 2026, Asterisk4Magisk contributors
// SPDX-License-Identifier: GPL-3.0

package org.asterisk.zcc.fixer.redmagicos

import org.asterisk.zcc.fixer.redmagicos.GmsFirewallRules.GMS_PACKAGE
import org.asterisk.zcc.fixer.redmagicos.GmsFirewallRules.OPTIMIZE_CLR
import org.asterisk.zcc.fixer.redmagicos.GmsFirewallRules.OPTIMIZE_SET
import org.asterisk.zcc.fixer.redmagicos.GmsFirewallRules.RANK_NEVER
import org.asterisk.zcc.fixer.redmagicos.GmsFirewallRules.clearInsteadOfSet
import org.asterisk.zcc.fixer.redmagicos.GmsFirewallRules.protectedRank
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GmsFirewallRulesTest {
    @Test
    fun clearInsteadOfSetRewritesSet() {
        val args = arrayOf<Any?>(OPTIMIZE_SET)

        assertTrue(clearInsteadOfSet(args))
        assertArrayEquals(arrayOf<Any?>(OPTIMIZE_CLR), args)
    }

    @Test
    fun clearInsteadOfSetKeepsOtherLevels() {
        val clear = arrayOf<Any?>(OPTIMIZE_CLR)
        val other = arrayOf<Any?>(2)

        assertFalse(clearInsteadOfSet(clear))
        assertFalse(clearInsteadOfSet(other))
        assertArrayEquals(arrayOf<Any?>(OPTIMIZE_CLR), clear)
        assertArrayEquals(arrayOf<Any?>(2), other)
    }

    @Test
    fun clearInsteadOfSetIgnoresUnexpectedShapes() {
        assertFalse(clearInsteadOfSet(emptyArray()))
        assertFalse(clearInsteadOfSet(arrayOf("0")))
        assertFalse(clearInsteadOfSet(arrayOf(0L)))
        assertFalse(clearInsteadOfSet(arrayOf(null)))
    }

    @Test
    fun protectedRankRaisesGmsMainAndPersistent() {
        for (rank in 0 until RANK_NEVER) {
            assertEquals(RANK_NEVER, protectedRank(rank, GMS_PACKAGE, GMS_PACKAGE))
            assertEquals(RANK_NEVER, protectedRank(rank, "$GMS_PACKAGE.persistent", GMS_PACKAGE))
        }
    }

    @Test
    fun protectedRankKeepsOtherGmsProcesses() {
        assertNull(protectedRank(0, "$GMS_PACKAGE.ui", GMS_PACKAGE))
        assertNull(protectedRank(0, "$GMS_PACKAGE:car", GMS_PACKAGE))
    }

    @Test
    fun protectedRankKeepsSameProcessNameFromOtherPackages() {
        assertNull(protectedRank(0, GMS_PACKAGE, "com.example.fake"))
        assertNull(protectedRank(0, GMS_PACKAGE, null))
    }

    @Test
    fun protectedRankKeepsInvalidNeverAndUnexpectedRanks() {
        assertNull(protectedRank(-1, GMS_PACKAGE, GMS_PACKAGE))
        assertNull(protectedRank(RANK_NEVER, GMS_PACKAGE, GMS_PACKAGE))
        assertNull(protectedRank(4, GMS_PACKAGE, GMS_PACKAGE))
        assertNull(protectedRank(null, GMS_PACKAGE, GMS_PACKAGE))
        assertNull(protectedRank("0", GMS_PACKAGE, GMS_PACKAGE))
    }
}
