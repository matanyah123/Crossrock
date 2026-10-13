# JSON UI real-pack compatibility pass

## Pre-implementation checklist (2026-10-12)

Read-only target: Recolourful Containers BE 1.4, light/dark subpacks. No pack files or proprietary layout definitions are redistributed.

- [x] Trace registered `.atemp` files, global variables and screen wrappers.
- [x] Separate resolution, compilation, layout and native-interaction failures.
- [x] Add bounded expression evaluation with explicit missing-context failure.
- [x] Resolve variable-provided child arrays and minimal known vanilla primitives.
- [x] Add nine-slice region geometry and connect the existing GUI renderer.
- [x] Validate malformed inputs, precedence, clipping and resize behavior.
- [x] Run complete units and real-pack standard/Sodium client regressions.
- [x] Document remaining blockers and install the verified build.

## Diagnosis

The pack's chest wrapper has no namespace and defines small/large/ender chest, barrel and shulker variants. Crossrock requires an explicit namespace and its chest adapter requests `chest.chest_screen`, not these variant entry points. Removing either guard alone does not make the screen executable.

Container content inherits `container_common.container_details`, then `global.root_panel` and `global.empty_panel`, and supplies children through `$screen_controls`. Screen wrappers reach `global.container_screen` and `pagination._ui_screen_generator`. These are not equivalent to empty panels: they include engine screen lifecycle, cursor and input behavior. Crossrock must not invent these dependencies.

The shared screen-content templates use nine-slice images and `assets/sprites/` / `assets/variants/` paths. Close buttons require additional focus, sound and input mappings; item templates require engine bindings/custom renderers. Unsupported bindings must not silently turn into visible, incorrectly clickable inventory slots.

| Screen | Definitions/dependency family | Current safe outcome |
| --- | --- | --- |
| Chest | chest wrapper → small/double chest → container_common, global, pagination | Native fallback |
| Survival inventory | inventory_screen → shared container/item templates | Native fallback |
| Crafting | crafting_screen → shared container/item templates | Native fallback |
| Furnace | furnace_screen → shared container/item/progress templates | Native fallback |
| Anvil | anvil_screen → shared container/item templates, native text entry | Native fallback |
| Enchanting | enchanting screen → shared container/item templates, engine offer bindings | Native fallback |
| Shulker | chest wrapper → shulker_screen → shared container/item templates | Native fallback |
| Brewing | brewing_stand_screen → shared container/item/progress templates | Native fallback |
| Smithing | smithing_table_screen → shared container/item templates | Native fallback |
| Villager trading | trading screen → shared templates, trade collections/scrolling | No adapter; native fallback |

Exact content entry points inspected: `small_chest_screen.small_chest`, `inventory_screen.screen_base`, `crafting_screen.screen_base`, `furnace_screen.furnace_panel`, `anvil_screen.anvil_panel`, `enchanting_screen.enchanting_panel`, `shulker_screen.shulker_box`, `brewing_stand_screen.brewing_panel`, `smithing_table_screen.smithing_table_panel`, `trading_screen.screen_panel`. Their files are `ui/ashur/<namespace>.atemp`. Inventory/crafting/furnace also reach `recipe_book` templates; chest/shulker/trading use their respective decorated-image namespaces and extension templates. Those dependency names are diagnostic identifiers, not reproduced layouts.

Compilation failure is not an interaction test. The expanded regression exercises native fallback on nine representative screens (including brewing and smithing), not authored real-pack layout fidelity or trading. Villager trading remains untested in this pass.

## References

- [Microsoft UI element reference](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/jsonuireference/examples/jsonuicomponents/ui_element?view=minecraft-bedrock-stable): controls, variables, bindings, UVs and per-edge nine-slice fields. The page contains incomplete enum tables; it is not a complete semantic oracle.
- [Mojang UI common sample](https://github.com/Mojang/bedrock-samples/blob/main/resource_pack/ui/ui_common.json): primitive template semantics and expression usage. Only independently implemented primitive semantics are used, not a copied vanilla catalog.
- [Mojang chest sample](https://github.com/Mojang/bedrock-samples/blob/main/resource_pack/ui/chest_screen.json): screen variant/template dependency structure.
- [Published community JSON UI schema](https://github.com/DJStompZone/MCBE-JSON-UI-Schemas/blob/main/ui.schema.json): secondary field-name inventory (including `nineslice_size`); not authoritative evidence for defaults or runtime parity.

Implementation and validation results follow. Full real-pack compatibility is not yet claimed.

## Implemented generic subset

- `BedrockUiExpression`: cached parsed expressions, arithmetic `+ - * /`, unary signs, numeric comparisons, strict typed equality/inequality, boolean `and/or/not` (and symbolic forms), strings/concatenation, parentheses, `$` variables and `#` binding lookups. Short-circuit logical evaluation. Limits: 4096 characters, 512 parse nodes, depth 48, bounded 1024-entry cache. No script execution or Molang. Missing data, unknown syntax, nonfinite arithmetic and type mismatch fail closed; undocumented coercions are not guessed.
- Static, ordered `variables.requires` blocks and variable-supplied control arrays preserve child-local variable scope. Runtime `#` expressions execute in visibility/enabled/ignored, alpha/layer/text, size/offset/color/size constraints. Local view bindings execute in source order after existing global/collection bindings. Named-control view lookup remains unsupported.
- Independently implemented `common.empty_panel`, `common.empty_image`, `common.horizontal_stack_panel`, `common.vertical_stack_panel` provide only known primitive semantics. Pack definitions replace these defaults. They are available as dependencies, not automatically installed screen roots. No copied vanilla catalog or inferred `global` screen templates.
- `BedrockUiNineSlice`: scalar `nineslice_size` plus per-edge `nine_slice_left/right/top/buttom` (the official reference spells bottom `buttom`). Corners retain GUI-unit border sizes, edges/center stretch independently, UV source coordinates include authored atlas offsets. The existing renderer applies tint/alpha and parent scissor to every region. Unsliced images retain a single blit. Zero-sized targets draw nothing; borders that consume the source center reject. Undersized destinations collapse the center using deterministic leading-edge priority, an explicit approximation pending Bedrock comparison.

## Still blocked / not implemented

- Full vanilla screen catalog, implicit namespace patches, variant root selection, variable template/control names, screen lifecycle and cursor behavior.
- Mixed `$`/`#` expressions and runtime-dependent conditional variable/tree changes; arbitrary object/array expressions, collection operators, coercion/version parity and named/sibling view bindings.
- Modifications, factories, scrolling/wheel/drag/scrollbar input, dynamic lists and content-relative sizing. Existing fixed native-slot collections remain unchanged.
- `assets/` UI images (the renderer still permits only safe `textures/` paths), vector-form nine-slice borders, tiling/filter modes/fonts, custom engine renderers and the full inventory data catalog.
- Recolourful's container-item chain reaches `compatibility_container_item.container_item_bridge`, variable-named controls, `button_mappings.container_item_button` and bundle/storage/durability renderers. It cannot be replaced with a generic empty panel without losing functional semantics.

Thus **Recolourful's authored chest/layout success criterion is not achieved**. This pass removes reusable blockers while retaining safe Java interactions. The installed archive is never edited. No full GUI replacement, Bedrock visual parity, Iris or remote multiplayer support is claimed.

## Changed files in this pass

Production: `BedrockUi.java`, `BedrockUiLayout.java`, `NativeJsonUi.java`; new `BedrockUiExpression.java`, `BedrockUiNineSlice.java`.

Tests: new `BedrockUiRealPackTest.java`, `JsonUiChecks.java`, `JsonUiInteractionChecks.java`. Reports: this file, both existing JSON UI reports and all three RP coverage/roadmap reports. Unrelated existing working-tree changes are preserved.

Report maintenance: `tools/update_json_ui_real_coverage.py` emits reviewed patches only after successful standard/Sodium logs and verifies the fresh unit totals. It does not modify production code or installed packs.

## Validation results

**414 unit tests passed, zero failures/errors/skips**, including 15 new tests in `BedrockUiRealPackTest`. They cover operator behavior/types/short-circuiting, limits, missing data, static condition ordering, child scope/cycles, primitive overrides, dynamic binding values, local view bindings, atlas UVs, corner preservation, asymmetric/small/zero bounds, malformed borders, clipping, alpha and GUI-unit resize.

Full standard client/integrated-server run passed in **2m50s**, and Sodium in **3m05s**. Logs: `/tmp/crossrock-rp-audit-G6didE/ui-real-standard.log` and `ui-real-sodium.log`. Both include read-only Actions & Stuff model/material/animation/player/particle/trail regressions, generated UI fixtures, authoritative native chest transactions/conservation, reload cleanup and the real Recolourful fallback checks. These tests detect their covered regressions; they do not establish that every pack animation or material is correct.

Generated UI fixtures exercise expressions, conditional variables, primitive inheritance and nine-slice through the real renderer. GUI scales 1 and 2 pass, and their screenshots were inspected: the rectangular border retains its thickness in GUI units. The real-pack chest screenshot visibly remains native. Nine fallback screens pass; the trading screen, alternate Recolourful subpack behavior, independent window-size matrix, tooltip pixels and complete per-screen interaction matrix remain unvalidated. Native chest tests cover splitting, shift-click, dragging, double-click collection, hotbar/offhand shortcuts and hover; other fallback screens exercise open/close and captures.

Coverage stays **9/9 broad partial implemented and targeted-tested families, 0/9 complete**. No percentage inflation follows from these additions. Parsing, Java rendering, native interactions and Bedrock visual fidelity remain separate evidence levels.

Installed the verified `bedrock-compat-0.1.0.jar` into the user's Fabric 26.2 test instance; byte equality with the built artifact was checked. Previous jar backup: `/tmp/crossrock-install-backup.rXca83/bedrock-compat-0.1.0.jar`. Minecraft must restart to load the new classes. Installed pack archives were untouched.

## Expression continuation — 2026-10-13

The pagination condition in the read-only installed pack now evaluates. Variable and binding tokens accept a colon after their sigil; string subtraction removes literal substrings without regex evaluation. Numeric subtraction, strict type checks, short circuiting and parser limits remain intact. Concatenation output is capped at 65,536 characters before allocation. Tests cover the pack-shaped containment condition, absent/empty/equal operands, regex metacharacters, repeated matches, numeric subtraction, missing context, type mismatch and output limits.

The regenerated dependency trace records the next chest blocker: `Unsupported control field cache_screen`. Native fallback remains active. No authored chest rendering or interaction success is claimed. Repeated string occurrences use all non-overlapping removal, an explicit approximation pending direct Bedrock comparison; the pack's observed containment condition does not depend on that distinction. No full string coercion semantics are claimed.

Changed production file: `BedrockUiExpression.java`. Tests: `BedrockUiRealPackTest.java`, `BedrockUiDependenciesTest.java`. The archive SHA-256 check still guards against pack modification. Fresh unit validation: **427 tests, zero failures/errors/skips**, with both reference pack environment variables enabled. `test build` passed. Standard client/integrated-server regression passed in **2m57s** (`/tmp/crossrock-rp-audit-G6didE/ui-expression-followup.log`). Historical results above belong to earlier passes.

Sodium client/integrated-server regression also passed in **2m46s** (`/tmp/crossrock-rp-audit-G6didE/ui-expression-sodium.log`), including Actions & Stuff and Recolourful native fallback. These runs do not validate authored chest visual fidelity. This continuation updates workspace sources and builds; it does not install a new jar into the profile.
