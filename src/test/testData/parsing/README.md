# Antlers parser golden tests

The expected-tree `.txt` files are intentionally absent. On the first
`./gradlew test` run, IntelliJ's ParsingTestCase writes each missing
`<name>.txt` (failing that run). Review the generated trees for correctness,
then commit them; subsequent runs compare against them.

Expected structure to sanity-check when reviewing:
- tag.txt: a statement with namePath `collection:blog`, parameters `limit`,
  bound `:sort`, and `as`; then a separate statement with a closingTag.
- modifiers.txt: a statement with namePath `title` and two modifier nodes
  (`upper`, and `truncate` with an arg list).
- condition.txt: a statement whose body is a condition (keyword `if`) followed
  by expression tokens; outer text `yes`; a closing statement.
- mixed.txt: outerHtml `<div>`, a comment, a noparseBlock (raw `{{ x }}` NOT
  tokenised), a statement `user:name`, outerHtml `</div>`.
