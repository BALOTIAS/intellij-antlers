package com.github.balotias.intellijantlers.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class ModifierSignatureTest {

    @Test fun noParamsRendersBareName() =
        assertEquals("upper", ModifierSignature.render(ModifierDef(name = "upper")))

    @Test fun requiredParamsRenderPlain() =
        assertEquals("replace(search, replacement)", ModifierSignature.render(
            ModifierDef(name = "replace", takesArguments = true, parameters = listOf(
                ModifierParam("search"), ModifierParam("replacement")))))

    @Test fun optionalWithDefaultRendersAssignment() =
        assertEquals("truncate(length, ellipsis = '…')", ModifierSignature.render(
            ModifierDef(name = "truncate", takesArguments = true, parameters = listOf(
                ModifierParam("length"), ModifierParam("ellipsis", optional = true, default = "'…'")))))

    @Test fun optionalWithoutDefaultRendersQuestionMark() =
        assertEquals("first(count?)", ModifierSignature.render(
            ModifierDef(name = "first", takesArguments = true, parameters = listOf(
                ModifierParam("count", optional = true)))))

    @Test fun paramLabelIsReused() =
        assertEquals("ellipsis = '…'", ModifierSignature.paramLabel(
            ModifierParam("ellipsis", optional = true, default = "'…'")))
}
