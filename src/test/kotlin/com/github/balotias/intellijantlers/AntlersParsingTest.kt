package com.github.balotias.intellijantlers

import com.github.balotias.intellijantlers.parser.AntlersParserDefinition
import com.intellij.testFramework.ParsingTestCase

class AntlersParsingTest : ParsingTestCase("parsing", "antlers.html", AntlersParserDefinition()) {

    override fun getTestDataPath(): String = "src/test/testData"
    override fun skipSpaces(): Boolean = false
    override fun includeRanges(): Boolean = true

    // Dump only the primary (Antlers) PSI root. Otherwise, when the full plugin is loaded in the same JVM
    // (by a BasePlatformTestCase), `.antlers.html` resolves to the multi-root template view provider and
    // ParsingTestCase would expect per-root goldens (`<name>.Antlers.txt`) — making this test depend on
    // whether the FileType is registered. Checking only the base root keeps the `<name>.txt` goldens valid
    // regardless, so the whole suite can share one JVM (no per-class forking needed).
    override fun checkAllPsiRoots(): Boolean = false

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
    fun testPhpEchoBlock() = doTest(true)
    fun testPhpTag() = doTest(true)
    fun testPhpEchoTag() = doTest(true)
    fun testNoparseBlock() = doTest(true)
    fun testMultiLineTag() = doTest(true)
    fun testConditionModifier() = doTest(true)
    fun testFrontMatter() = doTest(true)
}
