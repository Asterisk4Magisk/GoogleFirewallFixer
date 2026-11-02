// Copyright 2026, Asterisk4Magisk contributors
// SPDX-License-Identifier: GPL-3.0

package org.asterisk.zcc.fixer.coloros

import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal sealed interface MethodLookup {
    data class Found(val method: Method) : MethodLookup

    /** No method, or more than one, has the expected signature; nothing is hooked. */
    data class NotUnique(val candidates: List<Method>) : MethodLookup
}

/**
 * Locates the battery's Google restriction methods without Xposed, so the matching rules are
 * testable on the JVM. Method names are obfuscated and change between battery versions, so
 * both methods are recognized by their exact signature instead.
 */
internal object GoogleRestrictionMethods {
    const val CONTROLLER_CLASS = "com.oplus.battery.restrictdynamicfeature.google.GoogleRestrictionController"

    /** `maybeInformListeners(boolean restricted, boolean listChange, int reason)`. */
    fun findInformMethod(controller: Class<*>): MethodLookup {
        return findInstanceMethod(controller, java.lang.Boolean.TYPE, java.lang.Boolean.TYPE, Integer.TYPE)
    }

    /** `changeGmsControlState(boolean restricted, Set userChangedUids)`. */
    fun findControlMethod(controller: Class<*>): MethodLookup {
        return findInstanceMethod(controller, java.lang.Boolean.TYPE, Set::class.java)
    }

    /**
     * Turns a `restricted = true` first argument into `false`. Returns whether it changed
     * anything; any other argument shape is left as it is.
     */
    fun liftRestriction(args: Array<Any?>): Boolean {
        if (args.isEmpty() || args[0] != true) return false
        args[0] = false
        return true
    }

    /**
     * Hooks [lookup]'s method with [hook] when exactly one method matched. A failing hook is
     * logged and reported as null, so one method can never keep the other from being hooked.
     */
    fun installHook(role: String, lookup: MethodLookup, hook: (Method) -> Unit, log: (String) -> Unit): Method? {
        return when (lookup) {
            is MethodLookup.Found -> try {
                hook(lookup.method)
                lookup.method
            } catch (error: Throwable) {
                log("$role hook failed: $error")
                null
            }

            is MethodLookup.NotUnique -> {
                val names = lookup.candidates.joinToString { method -> method.name }
                log("$role method not unique: ${lookup.candidates.size} candidates [$names]")
                null
            }
        }
    }

    private fun findInstanceMethod(controller: Class<*>, vararg parameterTypes: Class<*>): MethodLookup {
        // Static accessors with the same parameters exist beside the targets; only instance
        // methods qualify.
        val candidates = controller.declaredMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(parameterTypes)
        }
        return candidates.singleOrNull()?.let(MethodLookup::Found) ?: MethodLookup.NotUnique(candidates)
    }
}
