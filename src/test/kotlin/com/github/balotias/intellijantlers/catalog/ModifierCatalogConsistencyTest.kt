package com.github.balotias.intellijantlers.catalog

import org.junit.Assert.assertTrue
import org.junit.Test

class ModifierCatalogConsistencyTest {

    @Test fun everyArgTakingModifierHasParameters() {
        val missing = CatalogModifiers.ALL.filter { it.takesArguments && it.parameters.isEmpty() }.map { it.name }
        assertTrue("modifiers flagged takesArguments but with no ModifierParam data: $missing", missing.isEmpty())
    }

    @Test fun everyModifierWithParametersTakesArguments() {
        val wrong = CatalogModifiers.ALL.filter { it.parameters.isNotEmpty() && !it.takesArguments }.map { it.name }
        assertTrue("modifiers with parameters but takesArguments=false: $wrong", wrong.isEmpty())
    }

    @Test fun optionalParametersSortAfterRequired() {
        val bad = CatalogModifiers.ALL.filter { def ->
            val firstOptional = def.parameters.indexOfFirst { it.optional }
            firstOptional >= 0 && def.parameters.drop(firstOptional).any { !it.optional }
        }.map { it.name }
        assertTrue("required parameters must precede optional ones: $bad", bad.isEmpty())
    }
}
