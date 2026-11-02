// Copyright 2026, Asterisk4Magisk contributors
// SPDX-License-Identifier: GPL-3.0

@file:Suppress("FunctionName", "UNUSED_PARAMETER", "unused")

package org.asterisk.zcc.fixer.coloros

import org.asterisk.zcc.fixer.coloros.GoogleRestrictionMethods.findControlMethod
import org.asterisk.zcc.fixer.coloros.GoogleRestrictionMethods.findInformMethod
import org.asterisk.zcc.fixer.coloros.GoogleRestrictionMethods.installHook
import org.asterisk.zcc.fixer.coloros.GoogleRestrictionMethods.liftRestriction
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Mirrors the shape of the obfuscated battery controller, static accessors included. */
private class FakeGoogleRestrictionController {
    fun K(restricted: Boolean, listChange: Boolean, reason: Int) {}
    fun C(restricted: Boolean, users: Set<Int>) {}
    fun P() {}
    fun Q(value: Int) {}

    companion object {
        @JvmStatic
        fun w(controller: FakeGoogleRestrictionController, restricted: Boolean, listChange: Boolean, reason: Int) {}

        @JvmStatic
        fun t(controller: FakeGoogleRestrictionController, restricted: Boolean, users: Set<Int>) {}

        @JvmStatic
        fun S(restricted: Boolean, listChange: Boolean, reason: Int) {}
    }
}

private class FakeDuplicateController {
    fun K1(restricted: Boolean, listChange: Boolean, reason: Int) {}
    fun K2(restricted: Boolean, listChange: Boolean, reason: Int) {}
}

private class FakeBoxedController {
    fun K(restricted: Boolean?, listChange: Boolean, reason: Int) {}
    fun C(restricted: Boolean, users: HashSet<Int>) {}
}

private class FakeEmptyController {
    fun P() {}
}

class GoogleRestrictionMethodsTest {
    @Test
    fun `finds the instance inform and control methods`() {
        assertEquals("K", found(findInformMethod(FakeGoogleRestrictionController::class.java)).name)
        assertEquals("C", found(findControlMethod(FakeGoogleRestrictionController::class.java)).name)
    }

    @Test
    fun `ignores static accessors with the same parameters`() {
        val inform = found(findInformMethod(FakeGoogleRestrictionController::class.java))
        val control = found(findControlMethod(FakeGoogleRestrictionController::class.java))
        assertFalse(Modifier.isStatic(inform.modifiers))
        assertFalse(Modifier.isStatic(control.modifiers))
    }

    @Test
    fun `reports no candidates when nothing matches`() {
        assertEquals(MethodLookup.NotUnique(emptyList()), findInformMethod(FakeEmptyController::class.java))
        assertEquals(MethodLookup.NotUnique(emptyList()), findControlMethod(FakeEmptyController::class.java))
    }

    @Test
    fun `reports every candidate when the match is ambiguous`() {
        val lookup = findInformMethod(FakeDuplicateController::class.java) as MethodLookup.NotUnique
        assertEquals(setOf("K1", "K2"), lookup.candidates.map { it.name }.toSet())
    }

    @Test
    fun `rejects boxed boolean and concrete set parameters`() {
        assertEquals(MethodLookup.NotUnique(emptyList()), findInformMethod(FakeBoxedController::class.java))
        assertEquals(MethodLookup.NotUnique(emptyList()), findControlMethod(FakeBoxedController::class.java))
    }

    @Test
    fun `lifts a true restriction`() {
        val args = arrayOf<Any?>(true, false, 0)
        assertTrue(liftRestriction(args))
        assertEquals(false, args[0])
    }

    @Test
    fun `keeps a false restriction`() {
        val args = arrayOf<Any?>(false, setOf<Int>())
        assertFalse(liftRestriction(args))
        assertEquals(false, args[0])
    }

    @Test
    fun `leaves unexpected arguments untouched`() {
        val empty = emptyArray<Any?>()
        assertFalse(liftRestriction(empty))
        assertEquals(0, empty.size)

        val nullFirst = arrayOf<Any?>(null)
        assertFalse(liftRestriction(nullFirst))
        assertArrayEquals(arrayOf<Any?>(null), nullFirst)

        val stringFirst = arrayOf<Any?>("true")
        assertFalse(liftRestriction(stringFirst))
        assertArrayEquals(arrayOf<Any?>("true"), stringFirst)
    }

    @Test
    fun `a failing hook does not stop the other method from being hooked`() {
        val inform = findInformMethod(FakeGoogleRestrictionController::class.java)
        val control = findControlMethod(FakeGoogleRestrictionController::class.java)
        val hooked = mutableListOf<String>()
        val logs = mutableListOf<String>()
        val install = { method: Method ->
            if (method.name == "K") error("rejected")
            hooked += method.name
        }

        assertNull(installHook("inform", inform, install, logs::add))
        assertEquals("C", installHook("control", control, install, logs::add)?.name)
        assertEquals(listOf("C"), hooked)
        assertTrue(logs.single().startsWith("inform hook failed"))
    }

    @Test
    fun `an ambiguous lookup is logged and not hooked`() {
        val logs = mutableListOf<String>()
        val lookup = findInformMethod(FakeDuplicateController::class.java)
        assertNull(installHook("inform", lookup, { error("must not be called") }, logs::add))
        assertTrue(logs.single().startsWith("inform method not unique: 2 candidates"))
    }

    private fun found(lookup: MethodLookup) = (lookup as MethodLookup.Found).method
}
