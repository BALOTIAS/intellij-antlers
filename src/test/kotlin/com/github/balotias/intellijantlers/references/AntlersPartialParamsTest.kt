package com.github.balotias.intellijantlers.references

import com.github.balotias.intellijantlers.references.AntlersPartialParams.PartialParam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AntlersPartialParamsTest {

    @Test fun requiredStarOnDirective() =
        assertEquals(
            PartialParam("label", true, "The caption label."),
            AntlersPartialParams.fromDirectiveValue("* label The caption label.")
        )

    @Test fun optionalParam() =
        assertEquals(
            PartialParam("as", false, "The wrapping element. Defaults to `a`."),
            AntlersPartialParams.fromDirectiveValue("as The wrapping element. Defaults to `a`.")
        )

    @Test fun nameOnlyNoDescription() =
        assertEquals(PartialParam("faux", false, ""), AntlersPartialParams.fromDirectiveValue("faux"))

    @Test fun starAttachedToName() =
        assertEquals(PartialParam("label", true, "x"), AntlersPartialParams.fromDirectiveValue("*label x"))

    @Test fun emptyOrStarOnlyIsNull() {
        assertNull(AntlersPartialParams.fromDirectiveValue(""))
        assertNull(AntlersPartialParams.fromDirectiveValue("   "))
        assertNull(AntlersPartialParams.fromDirectiveValue("*"))
    }
}
