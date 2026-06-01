package com.github.balotias.intellijantlers.scope

/** A loop-meta variable available inside any iterating Antlers tag. */
data class LoopVariable(val name: String, val description: String)

object LoopVariables {
    val ALL: List<LoopVariable> = listOf(
        LoopVariable("index", "1-based position of the current item in the loop"),
        LoopVariable("count", "1-based count of the current item (alias of index)"),
        LoopVariable("total_results", "Total number of items in the loop"),
        LoopVariable("first", "True on the first iteration"),
        LoopVariable("last", "True on the last iteration"),
        LoopVariable("no_results", "True when the loop produced no items")
    )
}
