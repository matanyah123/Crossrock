# Bedrock JSON UI compatibility

Updated 2026-10-12; see the current [real-pack pass](BEDROCK_JSON_UI_REAL_PACK_COMPATIBILITY.md). Earlier interaction results below remain historical. **9/9 broad contracts have tested partial functional implementations; 0/9 are certified complete.** The preceding decorative pass was 8/9. These are family-level counts, not percentage coverage of individual properties.

The authoritative current property boundary, official documentation links, runtime architecture, screen adapters, real-pack findings, validation and remaining work are in [BEDROCK_JSON_UI_INTERACTION.md](BEDROCK_JSON_UI_INTERACTION.md). Machine-readable individual contract evidence is in `BEDROCK_RP_COVERAGE.json`.

| Contract | Executed subset | Principal gaps |
|---|---|---|
| `ui.defs` | Partial, targeted unit/client subset | Explicit registered JSON/JSONC resources and four known common primitive dependencies execute. No full vanilla catalog, implicit namespace patches, variant root selection or certified cross-file pack precedence. |
| `ui.globals` | Partial, targeted unit/client subset | Scoped variables, inheritance, static ordered requires blocks and variable child arrays execute. Bounded scalar expressions execute; mixed variable/binding expressions, variable template names and runtime conditional trees remain unsupported. |
| `ui.controls` | Partial, targeted unit/client subset | Panel/screen/image/label/stack plus bounded button/grid/native inventory-item renderer execute. Scroll views, sliders, editboxes, toggles, dropdowns, factories and general custom renderers unsupported. |
| `ui.layout` | Partial, targeted unit/client subset | Anchors, percentages, fixed grids/stacks, clipping/alpha and scalar/per-edge nine-slice execute. Undersized nine-slice uses leading-edge priority, not Bedrock-certified behavior. Content-relative sizing, scrolling, vector borders and full layering/default parity remain incomplete. |
| `ui.bindings` | Partial, targeted unit/client subset | Global/collection always bindings plus local view expression bindings execute. Missing data fails closed. Named/sibling-control lookup, arbitrary collection expressions, factories and the full engine data catalog remain unsupported. |
| `ui.input` | Partial, targeted unit/client subset | Native widgets execute mouse/keyboard button subset; exact close/back, pause resume/settings actions only. Native inventory transactions remain authoritative. Unknown actions/layouts fail closed. No imported scroll/text/recipe-book/confirm/controller/touch/modal parity. |
| `ui.animation` | Partial, targeted unit/client subset | Numeric timelines and button-state child selection execute. Interactive triggers/reset/event/wait/flipbook semantics and Bedrock timing parity unsupported. |
| `ui.accessibility` | Partial, targeted unit/client subset | Java text/localization plus native widget focus/narration hooks execute. Imported fonts/wrapping/TTS controls and hardware narration/controller parity not validated. |
| `ui.hud` | Partial, targeted unit/client subset | Decorative HUD and exact title/pause/survival inventory/ten container adapters execute. No full HUD replacement/control modifications; creative/modded/recovery screens stay native. |

## Baseline runtime retained

Registered definitions compile on resource reload, not screen open. Exact scoped variables, qualified inheritance, named-child merging, GUI anchors/percentages/stacks, clipping/alpha, PNG UV images, Java-localized labels and numeric timeline chains remain supported. Unknown fields, missing context and unsafe dependencies reject the affected root. No active root returns before layout/binding work. Active roots still allocate bounded intermediate layouts; no zero-overhead/performance certification.

No implicit Bedrock vanilla catalog, general modifications/full-screen replacement, arbitrary bindings, complete UI expression semantics, nine-slice/font parity or HUD replacement is claimed. Same-file generated override follows the existing resource stack; cross-file namespace/global/registration precedence is not fully certified. Original compile/document/animation safety limits remain; runtime grid expansion adds a shared 16384-node budget and 512-cell per-grid cap.

## Interaction and real-pack results

Buttons use native Java widget focus and input dispatch. Typed native slot grids reposition existing Slot objects only after complete validation; native menus and networking remain authoritative. Unsupported interactive layouts fall back as a whole. Cleanup restores coordinates and clears removed focused widgets.

Recolourful Containers BE 1.4 was inspected and tested from an isolated read-only reference copy. Registered JSONC templates are now discovered, but its custom UI does **not** yet work: missing engine templates, expressions and other documented features still cause safe native fallback. Seven representative native menus were exercised. Actions & Stuff remains a rendering/animation regression, not proof of full JSON UI compatibility.

Fresh validation: **396 unit tests, zero failures/errors/skips**, including **26 new interaction/registration tests**. Standard and Sodium client/integrated-server runs exercise actual native slot operations, ten mapped container close actions, reload cleanup and fallback. Exact successful logs, assertions and remaining limitations are recorded under `json_ui_interaction_pass` in the coverage JSON. Prior `json_ui_pass` records remain historical evidence for the earlier decorative subset.

No Bedrock pixel/timing oracle, remote multiplayer, Iris, hardware controller/touch/narrator or benchmark certification. Tooltips and recipe-book/editbox behavior stay native; imported equivalents remain unsupported. Screenshots are diagnostic captures, not image-diff assertions.

Reproduce with `BEDROCK_REFERENCE_PACK` and `BEDROCK_UI_REFERENCE_PACK` pointing to read-only local archives, then run `./gradlew test runClientGameTest --rerun-tasks` in an isolated output directory. Repeat with `-PnativeEntityCompatibilityMods="<Sodium jar>"`. No installed pack or mod is replaced by these tests.

## Real-pack follow-up

Bounded scalar expressions, static conditional variables, variable child arrays, four common primitives and scalar/per-edge nine-slice now execute. Local view bindings are supported; named-control bindings and scrolling/modifications remain unsupported. **414 unit tests pass**, including 15 new tests. Standard/Sodium client regressions and GUI scales 1/2 pass. Recolourful still falls back on nine exercised screens; trading and authored layout fidelity remain unverified. See the real-pack report for exact dependencies, limits and changed files.

## Expression continuation — 2026-10-13

Colon-containing variable/binding identifiers and bounded literal string subtraction now execute; the installed pagination condition passes. The next traced chest blocker is `cache_screen`, with lifecycle/input and item-control dependencies still unsupported. Native fallback and 9/9 partial, 0/9 complete coverage remain unchanged. See [real-pack evidence](BEDROCK_JSON_UI_REAL_PACK_COMPATIBILITY.md) for limits and fresh validation.
