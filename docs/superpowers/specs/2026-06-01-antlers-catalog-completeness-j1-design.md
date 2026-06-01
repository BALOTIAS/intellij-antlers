# Antlers Catalog Completeness J1 — Full Tag & Modifier Catalog — Design

**Date:** 2026-06-01
**Status:** Approved (pending spec review)
**Scope:** Sub-project **J1** of "Antlers completeness". Expand the bundled catalog from 25 tags / 30
modifiers to the **full Statamic 6 core set** (~48 top-level tags with sub-tags + parameters, ~190
modifiers), so completion is exhaustive and downstream diagnostics (F) can trust "unknown tag/modifier".
Pure data growth on the existing schema; sourced from `statamic.dev`.

## 1. Background & Goal

The catalog (`catalog/CatalogData.kt`) bundles a Statamic-6 starter set as Kotlin data (no JSON parser on
the plugin runtime classpath). Completion, docs, folding (`isPair`), and tag-method completion all read
it via `AntlersCatalogService`. The starter covers the 25 most common tags and 30 modifiers — so
completion misses most real tags/modifiers, and a future "unknown tag" inspection would mis-flag valid
ones.

J1 brings the catalog to comprehensive Statamic 6 core coverage: **every** documented top-level tag
(with its sub-tags as `methods` and full `parameters`) and **every** documented modifier (with
description and `takesArguments`). No new behavior — existing consumers light up automatically with the
richer data.

## 2. Principles

1. **Pure data, existing schema.** No changes to `TagDef`/`ParamDef`/`ModifierDef` or any consumer.
2. **Completeness is the contract.** Tests assert the full expected name sets are present (Appendices),
   so coverage is verifiable and regression-proof.
3. **Authoritative + linked.** Names, `isPair`/`takesArguments`, descriptions come from `statamic.dev`;
   every entry carries its canonical `docUrl`.
4. **Split by responsibility.** Tags and modifiers move into focused files; the public
   `CatalogData.TAGS`/`MODIFIERS` surface is unchanged.
5. **Runtime-safe.** Kotlin data only — never a serialized format requiring a parser.

## 3. Components

### 3.1 File split (`catalog/CatalogTags.kt`, `catalog/CatalogModifiers.kt`, `catalog/CatalogData.kt`)

```kotlin
// CatalogTags.kt
object CatalogTags { val ALL: List<TagDef> = listOf(/* ~48 tags */) }
// CatalogModifiers.kt
object CatalogModifiers { val ALL: List<ModifierDef> = listOf(/* ~190 modifiers */) }
// CatalogData.kt (thin, backward-compatible)
object CatalogData {
    val TAGS: List<TagDef> = CatalogTags.ALL
    val MODIFIERS: List<ModifierDef> = CatalogModifiers.ALL
}
```

`AntlersCatalogService` and all other consumers keep reading `CatalogData.TAGS`/`MODIFIERS` unchanged.
The current 25 tags / 30 modifiers migrate into the split files (preserving their existing params), then
the lists expand to full coverage.

### 3.2 Tags (`CatalogTags.ALL`)

Every top-level handle in **Appendix A** (~48). Per tag:
- `name` = the handle.
- `isPair` = pair/block per Appendix A (drives folding, brace matching, the block-vs-single insert
  handler, and future inspections).
- `description` = the one-line summary (Appendix A / the tag's page).
- `docUrl = "https://statamic.dev/tags/<handle>"`.
- `methods` = the tag's sub-tags (Appendix A), e.g. `session` → `set,get,has,forget,flush,flash,dump`;
  `user` → `login_form,register_form,profile,can,is,in,logout,…`. Powers `{{ tag:‹caret› }}` completion.
- `parameters` = the full parameter list for that tag, sourced by fetching `statamic.dev/tags/<handle>`
  during implementation and transcribing each as `ParamDef(name, description, type, required)`.

### 3.3 Modifiers (`CatalogModifiers.ALL`)

Every name in **Appendix B** (~190). Per modifier:
- `name`, `description`, and `takesArguments` per Appendix B.
- `docUrl = "https://statamic.dev/modifiers/<name>"` (falls back to the reference page if a name has no
  dedicated page; the URL is informational).

Appendix B was captured via a doc summarizer, so the implementer **fetches `statamic.dev/reference/modifiers`
once** to validate/correct names and arg-flags before transcribing. Bias toward **over-inclusion**: an
extra (or stale) name only means we won't flag it as "unknown" later (a harmless false-negative for F),
whereas a *missing* real modifier would cause a false "unknown" warning — so keep every plausible name.

## 4. File / Package Layout

```
catalog/CatalogTags.kt        (new — TagDef list, ~48 tags with full params)
catalog/CatalogModifiers.kt   (new — ModifierDef list, ~190)
catalog/CatalogData.kt        (slim aliases → CatalogTags.ALL / CatalogModifiers.ALL)
catalog/CatalogModels.kt      (unchanged)
catalog/AntlersCatalogService.kt (unchanged)
```

No `plugin.xml`, grammar, or consumer changes.

## 5. Testing

`BasePlatformTestCase` is unnecessary for pure data — use a JUnit4 unit test (`CatalogCoverageTest`,
like `LexerTest`/`FieldtypePropertiesTest`).

- **Tag coverage:** `CatalogTags.ALL.map { it.name }.toSet()` contains every handle in the test's
  hard-coded expected set (Appendix A); no duplicate names; every `docUrl` starts with
  `https://statamic.dev/tags/`.
- **Modifier coverage:** `CatalogModifiers.ALL` contains every expected name (Appendix B); no duplicates;
  every `description` is non-blank.
- **Param well-formedness:** spot-check rich tags — `collection` params include `from`/`limit`/`sort`/
  `paginate`; `glide` includes `width`/`height`; `nav` includes `handle`/`max_depth`. Every
  `ParamDef.name` is non-blank.
- **Sub-tags:** `tag("session").methods` contains `set`/`has`/`forget`; `tag("user").methods` contains
  `login_form`/`can`; `tag("collection").methods` contains `count`.
- **Backward-compatibility / regression:** `CatalogData.TAGS === CatalogTags.ALL`; the existing
  completion/docs/folding tests stay green (same public surface, richer data).

## 6. Out of Scope (later)

- Per-tag/per-modifier exhaustive prose polish (descriptions are sourced summaries + canonical docUrl).
- Project-scanned custom tags/modifiers already handled by `TagScanner`/`ModifierScanner` (unchanged).
- `nav`/`structure` *scoping* semantics (J2) and deeper augmentation (J3).
- Addon/first-party-addon tags beyond the documented core set.

## 7. Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| ~48 per-tag fetches are slow | Parallelizable during execution; modifiers need zero fetches (Appendix B captured) |
| Description paraphrase drift | Canonical `docUrl` on every entry; tests gate names/flags, not prose |
| Wrong `isPair` → bad fold/brace/inspection | Taken from the reference pair/single column (Appendix A); spot-checked for structural tags |
| Huge single file | Split into `CatalogTags.kt` / `CatalogModifiers.kt` |
| Hyphenated modifier name (`where-in`) | Stored as a literal string; inserted as text — no identifier constraint |
| Statamic renames/adds later | "Done" = documented Statamic 6 core at this date, anchored by docUrls |

---

## Appendix A — Tags (handle · pair/single · sub-tags)

Source: `statamic.dev/tags` and `statamic.dev/reference/tags`. `P`=pair/block, `S`=single.

| Handle | Type | Sub-tags (methods) |
|---|---|---|
| 404 | S | — |
| asset | S | — |
| assets | P | — |
| cache | P | — |
| children | P | — |
| collection | P | count, next, previous |
| cookie | S | — |
| dictionary | P | — |
| dump | S | — |
| foreach | P | — |
| form | P | create, errors, fields, set, submission, submissions, success |
| get_content | S | — |
| get_error | S | — |
| get_errors | P | — |
| get_files | P | — |
| get_site | S | — |
| glide | S | batch, data_url |
| increment | S | — |
| installed | S | — |
| link | S | — |
| locales | P | count |
| loop | P | — |
| markdown | P | indent |
| mix | S | — |
| mount_url | S | — |
| nav | P | breadcrumbs |
| nocache | P | — |
| oauth | S | — |
| obfuscate | P | — |
| parent | P | — |
| partial | S | exists, if_exists |
| protect | P | password_form |
| redirect | S | — |
| route | S | — |
| scope | P | — |
| search | P | — |
| section | P | — |
| session | S | set, get, has, forget, flush, flash, dump |
| svg | S | — |
| switch | S | — |
| taxonomy | P | count |
| trans | S | — |
| user | S | login_form, logout, logout_url, register_form, profile, profile_form, password_form, forgot_password_form, reset_password_form, can, is, in, passkey_form, passkeys, delete_passkey_form, elevated_session_form, two_factor_challenge_form, two_factor_enable_form, two_factor_setup_form, two_factor_enabled, two_factor_recovery_codes, reset_two_factor_recovery_codes_form, disable_two_factor_form |
| user_groups | P | — |
| user_roles | P | — |
| users | P | — |
| vite | S | content |
| yield | S | — |

Parameters for each tag are sourced from `statamic.dev/tags/<handle>` during implementation. Preserve the
existing param sets already authored for `collection`, `nav`, `partial`, `asset`, `assets`, `glide`,
`form`, `user`, `taxonomy`, `cache`, `section`, `search`, etc.

## Appendix B — Modifiers (name · takes-args · description)

Source: `statamic.dev/reference/modifiers`. `Y`=takes arguments, `N`=none. Authoritative implementation list.

add (Y) addition · add_slashes (N) backslash-escape quotes · ampersand_list (N) join with ampersands ·
antlers (N) parse Antlers · ascii (N) to ASCII · as (Y) alias to a variable name · at (Y) item at index ·
attribute (Y) extract HTML attribute · background_position (Y) CSS background position · backspace (Y)
remove trailing chars · bard_html (N) Bard data to HTML · bard_items (N) Bard items · bard_text (N) Bard
plain text · bool_string (N) boolean to string · camelize (N) to camelCase · cdata (N) wrap in CDATA ·
ceil (N) round up · chunk (Y) split into chunks · classes (Y) conditional CSS classes · collapse (N)
flatten one level / drop empties · collapse_whitespace (N) collapse whitespace · compact (N) remove nulls
· contains (Y) value present · contains_all (Y) all values present · contains_any (Y) any value present ·
console_log (N) console.log output · count (N) item count · count_substring (Y) substring occurrences ·
dashify (N) to dash-case · days_ago (N) days since date · decode (N) decode entities · deslugify (N) slug
to text · dl (N) definition-list markup · doesnt_overlap (Y) arrays share nothing · divide (Y) division ·
dump (N) debug output · embed_url (N) embeddable URL · ensure_left (Y) prefix if missing · ensure_right
(Y) suffix if missing · entities (N) encode HTML entities · excerpt (Y) summary excerpt · explode (Y)
string to array · favicon (Y) favicon link tag · filter_empty (N) drop empties · first (Y) first item ·
flatten (N) flatten array · flip (N) swap keys/values · floor (N) round down · format (Y) date format ·
format_number (Y) localized number · format_translated (Y) translated date format · full_urls (Y)
relative to absolute URLs · get (Y) value by key · gravatar (Y) Gravatar URL · group_by (Y) group items ·
headline (N) title-case headline · hex_to_rgb (N) hex to RGB · hours_ago (N) hours since · image (Y)
img tag · in_array (Y) value in array · insert (Y) insert at index · is_after (Y) date after · is_alpha
(N) letters only · is_alphanumeric (N) alphanumeric only · is_array (N) is array · is_before (Y) date
before · is_between (Y) within range · is_blank (N) empty/blank · is_email (N) valid email · is_embeddable
(N) embeddable URL · is_external_url (N) external URL · is_future (N) future date · is_json (N) valid JSON
· is_leap_year (N) leap year · is_lowercase (N) all lowercase · is_numberwang (N) is Numberwang ·
is_numeric (N) numeric · is_past (N) past date · is_today (N) is today · is_tomorrow (N) is tomorrow ·
is_uppercase (N) all uppercase · is_url (N) valid URL · is_weekday (N) weekday · is_weekend (N) weekend ·
is_yesterday (N) is yesterday · iso_format (N) ISO date · join (Y) join with separator · kebab (N) to
kebab-case · key_by (Y) key by property · keys (N) array keys · last (Y) last item · lcfirst (N) lowercase
first char · length (N) length/count · limit (Y) limit items · link (Y) anchor tag · list (N) ul markup ·
lower (N) lowercase · macro (Y) invoke macro · mark (Y) highlight term · markdown (N) Markdown to HTML ·
md5 (N) MD5 hash · minutes_ago (N) minutes since · mod (Y) modulo · modify_date (Y) adjust date ·
months_ago (N) months since · multiply (Y) multiplication · nl2br (N) newlines to <br> · obfuscate (N)
obfuscate text · obfuscate_email (N) obfuscate email · offset (Y) skip items · ol (N) ordered list ·
option_list (N) select options · output (N) asset file contents · overlaps (Y) arrays share a value ·
pad (Y) pad array · parse_url (Y) URL parts · partial (Y) render partial · pathinfo (Y) path info ·
piped (Y) pipe through callable · pluck (Y) pluck property · plural (N) pluralize · random (N) random
item · raw (N) unescaped output · rawurlencode (N) raw URL-encode · rawurlencode_except_slashes (N)
encode except slashes · ray (N) Ray debug · read_time (Y) reading time · regex_mark (Y) highlight regex ·
regex_replace (Y) regex replace · relative (Y) relative date · remove_left (Y) strip prefix · remove_right
(Y) strip suffix · repeat (Y) repeat string · replace (Y) replace text · resolve (N) resolve relations ·
reverse (N) reverse · round (Y) round to precision · safe_truncate (Y) word-safe truncate · sanitize (N)
sanitize HTML · scope (Y) apply query scope · seconds_ago (N) seconds since · segment (Y) URL segment ·
select (Y) select property · sentence_list (N) sentence list · shuffle (N) shuffle · singular (N)
singularize · slugify (N) to slug · smartypants (N) smart typography · snake (N) to snake_case · sort (Y)
sort · spaceless (N) strip inter-tag space · split (Y) split string · str_pad_left (Y) left-pad string ·
strip_tags (N) strip HTML · studly (N) to StudlyCase · substr (Y) substring · subtract (Y) subtraction ·
sum (N) sum values · surround (Y) wrap with delimiters · swap_case (N) swap case · table (N) HTML table ·
tidy (N) tidy HTML · timezone (Y) convert timezone · title (N) Title Case · to_json (N) to JSON · to_qs
(N) to query string · to_spaces (Y) tabs to spaces · to_tabs (Y) spaces to tabs · trim (N) trim
whitespace · truncate (Y) truncate · ucfirst (N) uppercase first char · ul (N) ul markup · underscored (N)
to underscored · unique (N) dedupe · upper (N) uppercase · url (Y) URL of asset/entry · urldecode (N)
URL-decode · urlencode (N) URL-encode · urlencode_except_slashes (N) encode except slashes · values (N)
array values · weeks_ago (N) weeks since · where (Y) filter by criteria · where-in (Y) filter by value
list · widont (N) prevent widows · word_count (N) word count · wrap (Y) wrap with HTML · years_ago (N)
years since
