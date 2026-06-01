package com.github.balotias.intellijantlers.scope

import com.github.balotias.intellijantlers.blueprint.BlueprintNamespace

/** A variable scope around the caret. v1 only models blueprint-backed iterating scopes. */
sealed interface AntlersScope

/** The caret is inside an iterating tag whose entries/terms are described by [namespace]. */
/** [navMeta] is true for nav scopes, which additionally offer nav-tree variables. */
data class BlueprintScope(val namespace: BlueprintNamespace, val navMeta: Boolean = false) : AntlersScope
