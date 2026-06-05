package com.github.balotias.intellijantlers.completion

import org.junit.Assert.assertEquals
import org.junit.Test

class ModifierArgIndexTest {

    @Test fun parenFirstArg() =
        assertEquals(0, ModifierArgIndex.indexAt("replace('a', 'b')", "replace('a".length))

    @Test fun parenSecondArg() =
        assertEquals(1, ModifierArgIndex.indexAt("replace('a', 'b')", "replace('a', 'b".length))

    @Test fun commaInsideStringIsNotASeparator() =
        assertEquals(0, ModifierArgIndex.indexAt("replace('a,b', '')", "replace('a,b".length))

    @Test fun caretBeforeParenIsOutside() =
        assertEquals(-1, ModifierArgIndex.indexAt("replace('a')", "repl".length))

    @Test fun colonFormSecondArg() =
        assertEquals(1, ModifierArgIndex.indexAt("replace:'a':'b'", "replace:'a':'b".length))

    @Test fun leadingPipePrefixHandled() =
        assertEquals(1, ModifierArgIndex.indexAt("| replace('a', 'b')", "| replace('a', 'b".length))
}
