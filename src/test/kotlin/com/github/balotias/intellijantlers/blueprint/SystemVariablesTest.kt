package com.github.balotias.intellijantlers.blueprint

import org.junit.Assert.assertTrue
import org.junit.Test

class SystemVariablesTest {

    // Global cascade variables verified present in statamic/cms View/Cascade.php that the list was missing.
    @Test fun coversSourceVerifiedGlobals() {
        val names = SystemVariables.ALL.map { it.name }.toSet()
        for (g in listOf("get_post", "cp_url", "current_date", "current_full_url", "logged_out", "today", "xml_header")) {
            assertTrue("missing global: $g", names.contains(g))
        }
    }

    @Test fun noDuplicates() {
        val names = SystemVariables.ALL.map { it.name }
        assertTrue("duplicate system variables", names.size == names.toSet().size)
    }
}
