package com.github.balotias.intellijantlers

import com.github.balotias.intellijantlers.parser.AntlersParserDefinition
import com.intellij.testFramework.ParsingTestCase

class AntlersParsingTest : ParsingTestCase("parsing", "antlers.html", AntlersParserDefinition()) {

    override fun getTestDataPath(): String = "src/test/testData"
    override fun skipSpaces(): Boolean = false
    override fun includeRanges(): Boolean = true

    fun testTag() = doTest(true)
    fun testModifiers() = doTest(true)
    fun testCondition() = doTest(true)
    fun testMixed() = doTest(true)
    fun testUnclosedTag() = doTest(true)
    fun testNestedTags() = doTest(true)
    fun testModifierChain() = doTest(true)
    fun testBoundParam() = doTest(true)
    fun testBracketAccess() = doTest(true)
    fun testPhpBlock() = doTest(true)
    fun testNoparseBlock() = doTest(true)
    fun testMultiLineTag() = doTest(true)
    fun testConditionModifier() = doTest(true)
}
