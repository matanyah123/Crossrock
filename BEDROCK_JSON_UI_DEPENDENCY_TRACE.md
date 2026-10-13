# JSON UI dependency tracing — Recolourful Containers BE 1.4

## Outcome and evidence boundary

The authored chest is **not yet functional**. Native Java fallback remains intentional. This pass fixes two loader defects and adds reusable dependency diagnostics; it does not substitute an empty template for unsupported inventory behavior.

The companion `BEDROCK_JSON_UI_DEPENDENCY_TRACE.json` is generated from the installed archive, read-only, by `BedrockUiDependenciesTest.realPackRegistrationResolvesPackOwnedPaginationWithoutChangingArchive`. It includes archive SHA-256, registration failures, source files, JSON pointers, definitions, reference edges, binding/texture/collection observations, potential chest reachability and actual compiler failures. It contains metadata, not a redistributed pack or its assets.

**Static graph is not runtime reachability:** both branches of conditional variables are inventoried, unresolved `$` references remain dynamic, and dotted variable values are only template candidates. Optional compatibility branches must not be mistaken for active chest requirements. Missing ownership is `unknown`, not guessed to be engine-owned. This is an authored dependency inventory, not a complete scope-evaluated execution graph. Global variables are recorded separately. Log summaries remain capped at 256; root-indexed compiler failures are retained up to the compiler's 4096-definition limit so a late root is not hidden by earlier failures.

## First failing chain, before and after this pass

```text
ui/chest_screen.json (implicit vanilla file patch; namespace chest)
  chest.small_chest_screen
    -> global.container_screen       ui/ashur/common/global.atemp
       -> pagination._ui_screen_generator   update-logs.txt
```

Before: the chest patch was not published because it is not listed in `_ui_defs`; `pagination` was absent because the loader rejected root-relative registration paths outside `ui/`. The pagination document uses Unicode-escaped JSON keys and is valid JSONC after its leading comment. Its filename does not determine its content type.

After: both documents are loaded. `BedrockUiTemplates` supplies the verified namespace identity of the vanilla chest file, without inventing its parent templates. The previous compiler failure was `Expected ) at UI expression offset 14` from the condition at:

`update-logs.txt#/_ui_screen_generator/variables/requires`

That condition uses the variable name `$_pack:_` and string subtraction. The 2026-10-13 follow-up accepts colons in variable/binding tokens and evaluates literal string removal. The installed pack condition now executes; the next chest failure is `Unsupported control field cache_screen`. Screen lifecycle and input contracts still require implementation before activation. Repeated-match removal currently removes all non-overlapping literal matches; this edge case remains an explicit approximation without a Bedrock differential oracle. Numeric subtraction and strict mixed-type rejection are preserved.

Two registered files are actually absent from the archive:

- `ui/ashur/common/compatibility/bridges/beveled_buttons_bridge.atemp`
- `ui/ashur/horse_screen.atemp`

These are reported as missing registrations, not synthesized. Valid registrations still publish when another registration fails.

## Chest dependency graph beyond the first failure

```text
global.container_screen
  -> global.screen_base_input_container -> compatibility_variables._client_variables
  -> screen_content.screen_background
  -> safezone.screen_holder_safezone
       -> root_screen_panel@$screen_content
          -> small_chest_screen.small_chest -> container_common.container_details
             -> recolourful_chest.chest -> screen_content.image_element
                  -> global.close_button
             -> rcc-extensions.small_chest_screen (conditional compatibility branches)
             -> container_items grid (9 x 3)
             -> container_common.container_bottom_section
                  -> inventory_items grid (9 x 3)
                  -> hotbar_items grid (9 x 1)
             -> jc_container_item.container_item
                  -> compatibility_container_item.container_item_bridge
                  -> variable-selected renderer/button/count/durability/storage controls
       -> stack_splitting_overlay
       -> selected-item factory and bundle tooltip controls
```

`global`, `safezone`, `compatibility_variables`, `rcc-extensions` and `pagination` are pack namespaces. They are not missing engine templates. Engine `common.*` entrance/exit animations are referenced, but whether each is active depends on conditional variables. The four supported common primitives remain independently implemented; no full vanilla catalog is copied.

## Remaining blockers and required generic work

| Source / path anchor | Contract / failure | Ownership | Required work |
|---|---|---|---|
| `update-logs.txt`, `/_ui_screen_generator/variables/requires` | Resolved 2026-10-13: colon tokens and literal string removal | Pack expression / engine language | Repeated-match semantics still need Bedrock differential validation |
| `global.atemp`, `/container_screen@pagination._ui_screen_generator` | Screen lifecycle, `$screen`, input mappings, animation references | Pack template / engine runtime | Native lifecycle adapter and explicit supported mapping semantics |
| `safezone.atemp`, `screen_holder_safezone` descendants | `root_screen_panel@$screen_content`; safe-zone binding data | Pack template / engine state | Scope-aware variable template names and real safe-zone context |
| `small_chest_screen.atemp`, `/small_chest@container_common.container_details` | Native adapter still names `chest.chest_screen`, not Bedrock small/large variants | Crossrock | Menu-specific variant selection; do not use titles to guess container identity |
| `recolourful_chest.atemp`, `/chest@screen_content.image_element/$texture` | `assets/` image rejected by current `textures/`-only renderer | Pack asset | Safe pack-relative UI image publication/resolution, with dimensions and missing-image checks |
| Same definition, `/variables` | Container-title context and conditional defaults | Pack / engine context | Correct runtime `$container_title` and documented default precedence |
| `jc_container_item.atemp`, `/container_item@compatibility_container_item.container_item_bridge/controls` | Variable-selected names/templates; per-item metadata, buttons and renderers | Pack / engine inventory state | Preserve collection scope and delegate only semantically equivalent Java inventory operations |
| `container_common.atemp`, `container_details` carried-item subtree | Cursor following, selected-item bindings, counts, durability/storage renderers | Engine state consumed by pack | Native cursor/tooltip/carried-stack integration without duplicate item rendering |
| `safezone.atemp`, selected hover/bundle subtrees | Factories, content-relative size, view bindings and tooltip collections | Engine state consumed by pack | Real providers and layout semantics, or explicit safe native delegation |

All exact authored reference pointers and observed binding names are in the JSON report. Unresolved optional extension references are **not** automatically blockers for the default chest. No modification operation was established as the first failing dependency. The fixed chest grids themselves do not require scrolling; bundle/conditional subtrees must be evaluated before deciding whether scrolling is required for the complete screen. No unrelated scrolling/modification implementation is claimed.

## Slot alignment, not fake slots

The authored standard panel is 176×166 GUI units. The top grid begins after a 17-unit spacer and a 7-unit left spacer; centered 16-pixel item renderers in 18-unit cells would place item origins at (8,18). The bottom section is 162×83, giving player inventory origins at (8,84) and hotbar at (8,142). These are **derived expected coordinates**, not successful real-pack render measurements.

Existing `BedrockUiSlots.plan` validates 16×16 renderers, unique authoritative slot IDs, clipping, panel bounds and overlap before applying positions. Java retains menu slot identity, input handling and server synchronization. Nothing in this pass changes slot contents, click transactions or models/rendering outside UI loading and diagnostics.

## Implemented reusable components

- Safe root-relative `_ui_defs` registration, including non-JSON extensions; rejects absolute paths, traversal, empty segments, URI/drive syntax and backslashes. Existing byte/count limits remain.
- Verified implicit chest-file publication and namespace identity. Other unregistered screen patches remain unsupported rather than guessed from filenames.
- `BedrockUiTemplates`: stable namespaced primitive registry, pack overrides use existing compiler inheritance/composition. No new version parity is claimed.
- Per-root compiler expansion budget instead of a cumulative budget across unrelated definitions.
- `BedrockUiDependencies`: bounded static source/pointer/ownership/reference inventory with cycle-safe reachability. Cached on resource reload; no per-frame scans.
- Existing Add-ons error screen now reads cached UI execution/fallback and structured parse/reference/safety diagnostics. Opening it does not persist errors or inspect pack contents for this feature. Runtime errors retain the existing bounded de-duplication. Layout/binding/interaction failures still use the existing textual execution diagnostics; full structured phase-specific runtime provenance remains unfinished.

## Validation and remaining work

See the current coverage report follow-up for fresh unit/client results. New tests cover root-relative path safety, implicit chest registration, namespace identity, missing/dynamic reference provenance, pack override ownership, cycles, depth limits and avoiding false missing-template claims for dotted action variables. The opt-in real-pack test verifies loading and SHA-256 stability. Client regressions assert that both previously missed documents reached the live reload before exercising native fallback.

Primary success criterion remains **unmet**: no authored chest visual fidelity, full native-control delegation, real-pack GUI-scale/window-size matrix, or shulker success is claimed. A working synthetic grid or passing native fallback is not a working Recolourful interface. Next work should resolve the expression/context chain, then the real item-control subtree, before enabling a new root adapter or suppressing native backgrounds.

## References

- [Microsoft UI element contracts](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/jsonuireference/examples/jsonuicomponents/ui_element?view=minecraft-bedrock-stable): controls, bindings, input and layout fields; incomplete enum/default documentation is not treated as an implementation oracle.
- [Mojang chest reference](https://github.com/Mojang/bedrock-samples/blob/main/resource_pack/ui/chest_screen.json): verified `chest` namespace, small/large variant identities and slot-grid relationships. Accessed 2026-10-12; no source catalog bundled.
- Pack-specific dependencies above are observed in the user's installed archive, not inferred from Actions & Stuff.

## Files changed in this pass

Production: `BedrockUiTranslator.java`, `BedrockUi.java`, new `BedrockUiTemplates.java`, new `BedrockUiDependencies.java`, `client/ui/json/NativeJsonUi.java`, and `client/ui/AddonsScreen.java`.

Validation: `BedrockUiTranslatorTest.java`, new `BedrockUiDependenciesTest.java`, and the `uiReference` checks in `NativeEntityGameTest.java`.

Reports: this file and its JSON companion, the three existing JSON UI reports, all three RP coverage/roadmap files, and `tools/update_json_ui_dependency_coverage.py`. The maintenance script emits reviewed patches only after verifying successful logs and fresh XML totals. Unrelated preexisting working-tree changes and installed pack archives were preserved.
