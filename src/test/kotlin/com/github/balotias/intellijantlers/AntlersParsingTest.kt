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
}
