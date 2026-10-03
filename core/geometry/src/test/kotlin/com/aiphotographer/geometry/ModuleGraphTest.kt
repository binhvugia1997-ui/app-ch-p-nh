package com.aiphotographer.geometry

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ModuleGraphTest {
    @Test fun coreIsJvmOnlyAndDependencyDirectionsAreEnforced() {
        val rows = File(requireNotNull(System.getProperty("moduleGraph"))).readLines().map { it.split('\t') }
        assertTrue(rows.any { it[0] == ":core:geometry" })
        for ((module, kind, target) in rows) {
            if (module.startsWith(":core:")) {
                if (kind == "plugin") assertEquals("jvm", target)
                if (kind == "dependency") {
                    assertFalse("$module -> $target", target.startsWith(":feature:") || target.startsWith(":perception:") || target == ":app")
                    assertFalse("Android SDK dependency $target", target.startsWith("external:androidx.") || target.startsWith("external:com.android"))
                }
            }
            if (module.startsWith(":perception:") && kind == "dependency") assertFalse(target.startsWith(":feature:"))
        }
    }
}
