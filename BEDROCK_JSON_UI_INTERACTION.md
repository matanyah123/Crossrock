# Bedrock JSON UI interaction bridge

Follow-up: [JSON UI real-pack compatibility](BEDROCK_JSON_UI_REAL_PACK_COMPATIBILITY.md) is the current boundary. This report records the earlier interaction pass. The follow-up adds bounded scalar expressions, static conditional variables, variable child arrays, four known common primitives, local view bindings and nine-slice rendering. Scrolling, named-control bindings, modifications and Recolourful authored layouts remain unsupported; native slot/input behavior is preserved. Nine real-pack fallback screens (now including brewing/smithing) and generated fixture GUI scales 1/2 are exercised in the new runs. Earlier test counts and missing-feature statements below are historical.

Reviewed 2026-10-12. **9/9 broad JSON UI contracts now have partial functional paths and targeted tests; 0/9 are certified complete.** This is contract breadth, not 100% property compatibility. The previous decorative pass remains documented in the coverage JSON history.

## Sources and semantic boundary

Primary reference: [Microsoft UI element reference](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/jsonuireference/examples/jsonuicomponents/ui_element?view=minecraft-bedrock-stable), pinned [official documentation](https://github.com/MicrosoftDocs/minecraft-creator/tree/4186dc4ec84c8980fc25d89c26e1414aa6c2fde2/creator/Reference/Content/JsonUiReference/Examples). Undefined generated enum tables do not establish defaults. Official Mojang examples at commit `46ba6ea985fb5a92d79a9419198f10dda14c199d`: [button templates](https://github.com/Mojang/bedrock-samples/blob/46ba6ea985fb5a92d79a9419198f10dda14c199d/resource_pack/ui/ui_common.json), [pause actions](https://github.com/Mojang/bedrock-samples/blob/46ba6ea985fb5a92d79a9419198f10dda14c199d/resource_pack/ui/pause_screen.json), [chest collections](https://github.com/Mojang/bedrock-samples/blob/46ba6ea985fb5a92d79a9419198f10dda14c199d/resource_pack/ui/chest_screen.json).

These are resource-side interfaces. Server-defined actions/data are not fabricated. Bedrock UI expressions are not Molang. No full Bedrock UI engine or built-in vanilla template catalog is claimed.

## Implemented paths

| Area | Executed subset | Boundary |
|---|---|---|
| Buttons | Native Java widgets, mouse hover/press/release, keyboard focus and Enter/Space/keypad activation, enabled/visible/alpha checks, clipped GUI-pixel hit testing | Mouse `menu_select/pressed`, keyboard `menu_ok/focused` only; duplicate/non-consuming/unknown source mappings reject |
| States | Named default/hover/pressed child selection; common children remain | Not full interactive animation triggers, sounds, modal behavior or controller/touch parity |
| Actions | `menu_exit`/`menu_cancel` on mapped non-title screens; pause `menu_continue` and `menu_settings` | Exact screen scope; unknown actions reject the root, never guessed from labels; no generic confirm, recipe-book, textbox or external navigation actions |
| Collections | Typed real native slot collections; exact-sized row-major grids, qualified templates, collection index and always collection bindings | `container_items`, `inventory_items`, `hotbar_items`, `equipment_items`; no factories, arbitrary lists, expressions or Bedrock engine data catalog |
| Slots | Atomic 16×16 positioning of existing native Slot objects, with panel/clip/uniqueness/overlap checks | No fake slots, rescaling, cropped slots or movement outside the existing native panel; all native transaction behavior retained |
| Registration | Explicitly registered JSON documents need not end in `.json`; JSONC templates are read on reload | Safe `ui/` paths only; not arbitrary files, implicit registrations or complete pack-stack merging |
| Lifecycle | Reinit/removal/reload restores original slot coordinates, removes imported widgets and clears focus/captured input | Invalid roots fall back as a whole; decorative nodes never register input listeners |

`BedrockUiInteraction` is the pure input/action policy. `BedrockUiLayout` expands typed collections and button-state children with a shared 16384-node runtime budget (grid cap 512 cells). `BedrockUiSlots` validates the complete presentation plan before mutation. `NativeJsonUiButton` participates in Java's normal widget/focus dispatch. `NativeJsonUi` owns scoped sessions, native data adapters and cleanup. Slot accessors change only x/y; identity, index, contents, menu handler and networking remain Java's.

Runtime inventory categories are bounded adapters, not a complete Bedrock inventory model: non-player slots currently share `container_items`, including output/crafting slots where present. Unsupported templates/bindings must fail closed rather than assume their role. Native recipe-book controls, text fields and tooltips remain native; imported equivalents are not implemented.

## Exact screen adapters

Title and pause remain supported alongside HUD decoration and survival inventory. Container adapters now cover chest/generic ContainerScreen, crafting table, furnace, smoker, blast furnace, anvil, enchanting, brewing stand, smithing and shulker box. Each maps an exact Java class to its corresponding Bedrock root. Creative inventory, modded subclasses, pack/configuration/recovery screens remain native. The bridge does not remove the native background or perform general control modifications/full-screen replacement.

## Recolourful Containers BE 1.4

Tested the user-provided archive read-only via an isolated copy. Its manifest reports 1.4.0, min engine 1.26.40, PBR and light/dark subpacks. Registered JSONC `.atemp` templates and hyphenated namespaces now reach compilation; one unsafe/outside-UI registration no longer suppresses later safe registrations.

**Its custom screens do not yet execute.** Missing vanilla/global/common templates, conditional expressions/variables, scrolling, renderer bindings, sizing and nine-slice features still prevent reliable mapping. The tested result is safe native fallback, not visual compatibility. Chest, crafting, furnace, survival inventory, anvil, enchanting and shulker menus were exercised. The source archive was not edited or bundled; byte equality with the isolated reference copy is asserted. No pack-specific action or template hacks were added.

## Validation

- `BedrockUiInteractionTest`: 24 targeted behavioral tests for mapping/scoping, visibility/enabled/clip/rounding, state selection, resize, collection scope/count/index, bounded expansion, atomic slot plans/swaps, overlap rejection and unchanged native fallback.
- `BedrockUiTranslatorTest`: two additional registration tests for generic non-JSON extensions and independent safe registrations. Existing 26 decorative/translation tests remain.
- `JsonUiInteractionChecks`: generated independent fixtures open ten real integrated-server container menus; imported mouse/keyboard close actions synchronize through normal Java behavior. Relocated chest slots retain identity/index. Pack removal while a button is focused restores slots and disables/removes the stale widget.
- Native inventory tests use actual screen input, not fabricated transactions. Results for splitting, shift-clicking, dragging, collection and keyboard swaps are recorded in `json_ui_interaction_pass` in the coverage JSON after successful runs.
- Standard and Sodium runs include read-only Actions & Stuff regression plus Recolourful fallback. Screenshots are diagnostic Java captures, not Bedrock pixel comparisons. Existing shader/platform/particle-atlas warnings do not constitute full pack parity.

Final results: **396 units, 0 failures/errors/skips** (26 new interaction/registration tests). Standard full run passed in **2m52s** (`/tmp/crossrock-rp-audit-G6didE/ui-interaction-standard.log`); Sodium full run passed in **2m56s** (`ui-interaction-sodium.log`). Both exercised splitting, shift-click, double-click collection, quick-craft dragging, hotbar/offhand swaps, native hover and server-side conservation, in relocated and fallback chest layouts. Native Java intentionally returns false after processing hotbar shortcuts; tests assert server state instead. Render-extracted hover waits for the actual target rather than assuming a server tick implies a rendered frame.

Inspected generated chest and real-pack fallback chest captures in `build/run/clientGameTest/screenshots`; the latter visibly remains vanilla, as reported. The harness clears these diagnostic captures on subsequent runs. Overall inventory breadth is now **399/560 functional partial-or-better, 268/560 targeted-validated**, versus 398/560 and 267/560 before this interaction pass. Core contracts are **146/182 functional, 139/182 targeted-validated**. These counts do not certify full semantic parity.

No independent Bedrock differential, remote multiplayer, Iris, hardware controller/touch/narrator, performance benchmark or full GUI-scale/resize matrix is certified. Unit resizing/rounding checks are distinct from visual verification. Native hover is asserted; tooltip content and pixel correctness remain inherited behavior, not independently image-tested.

## Remaining priorities

1. Exact built-in vanilla template catalog and pack-stack resolution, with bounded UI expressions/conditional variables. These block ordinary UI replacement packs, including Recolourful.
2. Explicit renderer/data-source registry, view bindings, factories and content-relative sizing; role-specific menu collections.
3. Nine-slice/text modes, scrolling and safe control modifications; supported recipe-book/text-input/navigation actions only after reliable native adapters exist.
4. Interactive animation triggers, focus ordering, modal/input propagation and controller/accessibility parity.
5. Independent Bedrock visual/timing fixtures, measured reload/frame budgets and larger native screen/GUI-scale regression matrix.

## Files changed by this interaction pass

Production: `BedrockUi.java`, `BedrockUiLayout.java`, new `BedrockUiInteraction.java`, new `BedrockUiSlots.java`, `BedrockUiTranslator.java`, `BedrockPackContentReader.java`, `NativeJsonUi.java`, new `NativeJsonUiButton.java`, new `NativeJsonUiSlotAccess.java`, new `NativeJsonUiContainerAccess.java`, client mixin registration.

Validation: new `BedrockUiInteractionTest.java`, `BedrockUiTranslatorTest.java`, new `JsonUiInteractionChecks.java`, `JsonUiChecks.java`, `NativeEntityGameTest.java`. Reports: this file, `BEDROCK_JSON_UI_COMPATIBILITY.md`, the three RP reports, and the interaction coverage updater. Existing unrelated workspace changes are preserved. No installed mod or pack is replaced by this pass.

## Expression continuation — 2026-10-13

Colon-containing variable/binding identifiers and bounded literal string subtraction now execute; the installed pagination condition passes. The next traced chest blocker is `cache_screen`, with lifecycle/input and item-control dependencies still unsupported. Native fallback and 9/9 partial, 0/9 complete coverage remain unchanged. See [real-pack evidence](BEDROCK_JSON_UI_REAL_PACK_COMPATIBILITY.md) for limits and fresh validation.
