package com.github.balotias.intellijantlers.completion

import com.github.balotias.intellijantlers.catalog.AntlersCatalogService
import com.github.balotias.intellijantlers.catalog.ModifierDef
import com.github.balotias.intellijantlers.catalog.ModifierSignature
import com.github.balotias.intellijantlers.psi.AntlersModifierMixin
import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.psi.util.PsiTreeUtil

/** Ctrl+P signature popup for a modifier call: shows the signature and bolds the current argument. */
class AntlersModifierParameterInfoHandler : ParameterInfoHandler<AntlersModifierMixin, ModifierDef> {

    internal fun modifierAt(context: ParameterInfoContext): AntlersModifierMixin? {
        val el = context.file.findElementAt(context.offset) ?: return null
        return PsiTreeUtil.getParentOfType(el, AntlersModifierMixin::class.java, false)
    }

    internal fun defFor(mod: AntlersModifierMixin): ModifierDef? =
        AntlersCatalogService.getInstance(mod.project).modifiers()
            .firstOrNull { it.name == mod.modifierName && it.parameters.isNotEmpty() }

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): AntlersModifierMixin? {
        val mod = modifierAt(context) ?: return null
        val def = defFor(mod) ?: return null
        context.itemsToShow = arrayOf(def)
        return mod
    }

    override fun showParameterInfo(element: AntlersModifierMixin, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): AntlersModifierMixin? =
        modifierAt(context)

    override fun updateParameterInfo(element: AntlersModifierMixin, context: UpdateParameterInfoContext) {
        val rel = context.offset - element.textRange.startOffset
        context.setCurrentParameter(ModifierArgIndex.indexAt(element.text, rel))
    }

    override fun updateUI(p: ModifierDef, context: ParameterInfoUIContext) {
        if (p.parameters.isEmpty()) {
            context.setupUIComponentPresentation("", -1, -1, false, false, false, context.defaultParameterColor)
            return
        }
        val sb = StringBuilder()
        val ranges = ArrayList<IntRange>()
        p.parameters.forEachIndexed { i, param ->
            if (i > 0) sb.append(", ")
            val start = sb.length
            sb.append(ModifierSignature.paramLabel(param))
            ranges.add(start until sb.length)
        }
        val cur = context.currentParameterIndex
        val clamped = if (cur < 0) -1 else cur.coerceAtMost(p.parameters.size - 1)
        val hs = if (clamped >= 0) ranges[clamped].first else -1
        val he = if (clamped >= 0) ranges[clamped].last + 1 else -1
        context.setupUIComponentPresentation(sb.toString(), hs, he, false, false, false, context.defaultParameterColor)
    }
}
