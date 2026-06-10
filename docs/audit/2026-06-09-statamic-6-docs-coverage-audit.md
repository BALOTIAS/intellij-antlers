# Statamic 6 Docs Coverage Audit — 2026-06-09

Systematic cross-check of the plugin against the **full** Statamic 6 documentation
(`github.com/statamic/docs` @ `6.x`), not just the Antlers language page. Eight read-only agents fanned
out over the docs (tags, modifiers, conditions/filters, fieldtype augmentation, global variables,
language) and diffed each area against the plugin source. Key claims were re-verified by hand.

**Headline:** core coverage is strong, but there is **one genuine missing language feature (tag
conditions)** plus a batch of **low-risk catalog-data corrections** (wrong/missing tag params, ~7–10
missing modifiers, missing globals, asset augmentation props).

Legend — **CRUCIAL** = common feature absent or actively wrong · **MODERATE** = partial coverage on a
used feature · **MINOR** = niche.

---

## ✅ Source verification (vs `statamic/cms` @ `6.x`) — 2026-06-09

Every doc-derived finding below was re-checked against the **actual Statamic PHP source**, which caught
several doc-only errors. Net: the findings hold, with these corrections baked in:

**Confirmed against source:** C1 conditions (mechanism `params with ':' → field:operator`, in
`Tags/Concerns/QueriesConditions.php`); C2 `foreach`→`Iterate.php` uses `array`/`as`, no `in`; C3 `asset`
uses `params->hasAny(['url','src'])` (so `url` is the real primary, `src` is a valid alias, `id` is **not**
recognised); C4 `redirect` uses `route`/`to`/`url`/`response`; M1 all ten modifiers exist in
`CoreModifiers.php`; M6 `query_scope` is real (the `QueriesScopes` trait on entries/users/terms/assets/
dictionary); M3 `cookie` and `get_site` both have an `index()` (pair) form.

**Corrected — the doc/agent was WRONG, source is authority:**
- **C5:** `size_bytes` and `mime_type` are **real** augmented keys (`AugmentedAsset.php`) — do **not**
  remove them. The full augmented set includes BOTH `size_b` *and* `size_bytes` (plus `size_kb`/
  `size_kilobytes`/`size_mb`/`size_megabytes`/`size_gb`/`size_gigabytes`).
- **M4:** the true global cascade (`View/Cascade.php`) is small. Most of the agent's "missing globals"
  (`datestamp`, `timestamp`, `has_timestamp`, `order_type`, `entries_count`, asset vars) are **contextual**
  variables, **not** cascade globals — adding them to the always-on global list would be wrong. The genuine
  missing globals are only: `get_post`, `cp_url`, `current_date`, `current_full_url`, `logged_out`,
  `today`, `xml_header`.
- **M2 (nav params):** only `handle` was confirmable by quick grep (Nav builds via a TreeBuilder) — verify
  each proposed param name against source before adding.

---

## CRUCIAL

### C1 — Tag conditions (`field:operator="value"`) — ✅ IMPLEMENTED (MVP, no grammar change)
**Status: COMPLETE.** Completion (operators after `field:`, field-name targets, and right-hand-side
values), operator highlighting, and hover docs shipped for `collection`/`taxonomy`/`users`, detecting the
condition by token pattern. The "deep PSI" value — **go-to-declaration, Find Usages, and Rename of a field
used inside a condition** — was also delivered **without a grammar change**: a scoped blueprint-field
reference (`AntlersBlueprintMemberReference`, in the queried tag's namespace) is attached to the condition
field ident, which the existing field searcher / rename pipeline already cover. No further grammar work
needed for conditions.

The biggest gap. Statamic filters `collection`/`taxonomy`/`users` results with parameter conditions:
`{{ collection:blog title:contains="tao" date:is_after="now" status:is="published" }}`
(`:field:is="x"` for a variable RHS; pipe-separated multi-values; dotted sub-fields `event_date.start:`).

Today this **parses without errors but with wrong structure**: `title` detaches as a loose floating
`T_IDENT`, and `:contains="tao"` is captured by the **bound-parameter** rule (`T_COLON T_DOLLAR? T_IDENT
…`) — so the operator `contains` is mistaken for a *bound parameter name* and the field name is orphaned.
Completion after `title:` offers `collection`'s methods/handles, never the operators. Coloring paints the
operator as a param name.

**Authoritative operator set** (from `Tags/Concerns/QueriesConditions.php`, more complete than the docs):
`is`/`equals` · `not`/`isnt`/`aint` · `is_empty`/`is_blank`/`doesnt_exist`/`not_set`/`isnt_set`/`null` ·
`exists`/`isset` · `contains` · `doesnt_contain` · `in` · `not_in` · `starts_with`/`begins_with` ·
`doesnt_start_with`/`doesnt_begin_with` · `ends_with` · `doesnt_end_with` · `greater_than`/`gt` ·
`less_than`/`lt` · `greater_than_or_equal_to`/`gte` · `less_than_or_equal_to`/`lte` ·
`matches`/`match`/`regex` · `doesnt_match` · `is_alpha` · `is_alpha_numeric` · `is_numeric` · `is_url` ·
`is_embeddable` · `is_email` · `is_after`/`is_future` · `is_before`/`is_past` · `is_numberwang` ·
`overlaps` · `doesnt_overlap`. Taxonomy form: `taxonomy:{handle}:{any|not|all}="term"`.

**Scope to fix (genuine new feature, moderate–high effort):** grammar rule for the
`field(.sub)*:operator="value"` triple (regen) disambiguated from the bound-param + shorthand-handle
rules → new mixin → completion (offer field names in a condition tag's param area, then operators after
`field:`) → coloring → hover docs. *Recommend a dedicated spec/plan, like the blueprint sub-projects.*

### C2 — `foreach` tag advertises a non-existent `in` parameter
Plugin `CatalogTags.foreach` has a single **required** param `in`. The docs have **no `in`** — the array
is the tag part (`{{ foreach:song_reviews }}`) or `array="…"` / `:array="…"`, plus `as="key|value"`.
So the plugin both invents `in` and omits the real params. *Fix: catalog data.*
`{{ foreach:song_reviews as="song|rating" }}{{ song }}: {{ rating }}{{ /foreach:song_reviews }}`

### C3 — `asset` tag has the wrong primary parameter (and probably wrong pair-flag)
Doc: the Asset tag retrieves an asset **by URL** via the `url` param, used as a pair so `{{ url }}`/`{{ alt }}`
resolve inside. Plugin has `isPair=false` and params `id`/`src` (no `url`). *Fix: catalog data — add `url`
(primary), verify pair usage.* `{{ asset url="/img/logo.png" }}<img src="{{ url }}" alt="{{ alt }}">{{ /asset }}`

### C4 — `redirect` tag has zero parameters defined
Plugin `redirect` has an empty `parameters` list; doc documents `url`/`to`, `route`, `response` (status).
*Fix: catalog data.* `{{ redirect to="/" response="301" }}`

### C5 — `assets` fieldtype augmentation: missing props
`FieldtypeProperties` for assets is missing many real augmented vars. **Source-corrected:** the plugin's
existing `size_bytes` and `mime_type` are **valid** (in `AugmentedAsset.php`) — keep them. Genuinely
missing (all confirmed in `AugmentedAsset.php`): `ratio`, `orientation`, `focus`, `focus_css`, `has_focus`,
`is_asset`, `is_image`, `is_svg`, `is_pdf`, `is_audio`, `is_video`, `is_previewable`, `container`, `folder`,
`edit_url`, `blueprint`, `last_modified`, `last_modified_instance`, `last_modified_timestamp`, `path`,
`basename`, `filename`, `permalink`, `api_url`, `duration`/`duration_sec`/`duration_minutes`/`playtime`,
and the size aliases `size_b`/`size_kb`/`size_kilobytes`/`size_mb`/`size_megabytes`/`size_gb`/
`size_gigabytes`. *Fix: catalog data (additions only — remove nothing).*

---

## MODERATE

### M1 — Missing modifiers (verified absent from `CatalogModifiers`)
`merge`, `starts_with`, `ends_with`, `is_empty`, `trans`, `trans_choice`, `format_time`. All common.
(Note `where`/`pluck`/`group_by` already exist as modifiers; these are genuinely new.) *Fix: catalog data
+ coverage-test expected set.*

### M2 — Missing parameters on commonly-used tags (catalog data)
- `nav`: `show_unpublished`, `include_parents`, `select`, `as`
- `users`: `offset`, `as`, `scope`, `filter`/`query_scope`
- `taxonomy`: `min_count`, `collection`, `filter`
- `partial`: `when`, `unless` (conditional include)
- `search`: `site`, `limit`, `offset`, `as`, `supplement_data`, `for`
- `dictionary`: primary param is `handle` (plugin marks `from` required); + `paginate`/`offset`/`as`/…
- `assets`: `folder`, `recursive`, `sort`, `type`, `query_scope`, `offset`, `not_in`

### M3 — `cookie` / `get_site` should be pair tags
Both are documented with a pair form (`{{ cookie }}…{{ /cookie }}`, `{{ get_site:x }}…{{ /get_site:x }}`)
that the plugin currently flags as "never closed" (isPair=false). *Fix: catalog data.* (`installed` has a
minor pair form too.)

### M4 — Missing global variables (`SystemVariables`)
**Source-corrected** against `View/Cascade.php` (the real global cascade). Genuine missing GLOBALS only:
`get_post`, `cp_url`, `current_date`, `current_full_url`, `logged_out`, `today`, `xml_header`.
*(The agent's `datestamp`/`timestamp`/`has_timestamp`/`order_type`/`entries_count`/asset vars are
**contextual** entry/asset/listing variables, NOT cascade globals — they don't belong in the always-on
global list. Core cascade — current_url/uri, csrf_field/token, site/sites, homepage, logged_in,
get/post/old, environment — is already covered.)*

### M5 — `date` fieldtype augmentation
Missing the documented range keys `start`/`end` (`{{ date_range.start }}`). The curated
`day`/`month`/`year`/`timestamp`/`iso` props are **undocumented** (Carbon may expose them, but the doc
formats dates via `format=`/`iso_format=` params / the `format` modifier) — consider trimming.

### M6 — `query_scope` not in the catalog
Documented on `collection`/`taxonomy`/`users` but absent, so it never completes (its sibling `filter`
is present). *Fix: one-line catalog data.*

---

## MINOR

- **Tag params:** `loop:to`, `children:of`, `increment:from/by`, `link:to/in/absolute`,
  `get_files` (depth/ext/sort/…), `get_content:from/site`, `cache:scope/store`, `oauth:redirect`,
  `vite:directory/hot`, `mix:src/in`, `svg:src/sanitize`, `trans:key/count/fallback`.
- **`void` keyword** — `{{ x ? 'a' : void }}` nullifies a param value; `void` lexes as a plain ident, not
  keyword-colored. Cheap to add (annotator).
- **`code` fieldtype** — uncovered; exposes `code` + `mode`.
- **`link` fieldtype** — `element` prop is undocumented (likely speculative).
- **`xml_header`** global (RSS/feed templates only).
- **`collection` methods** — plugin lists `older`/`newer` which aren't in the v6 docs (harmless extras).
- **Missing niche modifiers:** `mailto`, `has_lower_case`, `has_upper_case`.

---

## Verified NON-issues (already handled — do not "fix")

- **`???`** null-only coalescing — already in the lexer `OP` regex; operator-colored as of 1.0.6.
- **`endif`/`endunless`** alternate closers — handled (balance/folding).
- **`:$id`** shorthand bound-param — handled by the grammar (`T_COLON T_DOLLAR? T_IDENT`).
- **`@{ … @}`** brace escape — fixed this round (1.0.6).
- **`\attribute`** backslash raw-param — known limitation, deliberately deferred (needs a lexer change).
- **`bard` / `table` / `video` / `markdown` fieldtypes** — correctly omitted from augmentation props
  (they augment to loops/HTML strings, not dotted scalar properties).
- **Modifiers a–c (28)** — 100% covered, signatures correct.

---

## Recommended sequencing

1. **Catalog-data corrections (one batch, low risk — the J1 pattern):** C2 `foreach`, C3 `asset`,
   C4 `redirect`, C5 assets-augmentation, M1 the 7 missing modifiers, M2 tag params, M3 pair-flags,
   M4 globals, M5 `date` keys, M6 `query_scope`, plus the MINOR params. All pure data + coverage-test
   updates; no parser/grammar risk. High coverage gain.
2. **Tag conditions (C1):** its own spec/plan — grammar regen + mixin + completion + coloring + docs.
   The single substantive language feature still missing.
