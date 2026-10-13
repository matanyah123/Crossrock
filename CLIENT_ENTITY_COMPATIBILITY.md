# Client-entity compatibility pass

Date: 2026-10-12. This report describes the current dirty working tree, not a released build.

## Outcome and counting

The rechecked baseline was **4/11 functional contracts and 4/11 targeted-tested contracts**. It is now **9/11 functional partial contracts and 9/11 targeted-tested subsets**. None is certified semantically complete. Offscreen updates remain unsupported; `description.particle_emitters` remains Unknown. These are the existing audit's eleven broad integration contracts, not eleven individual JSON fields or a percentage of packs that work.

Public variables receive partial credit for executable, filtered snapshot references, not automatic actor discovery. Spawn eggs receive partial credit for authored texture/index presentation, not color-mask or built-in atlas support. The separate texture-atlas contract receives narrow partial credit for that same consumer; it does not imply a general item-atlas implementation.

Primary references: Microsoft's [client-entity introduction](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/entityreference/examples/cliententitydocumentation/cliententitydocumentationintroduction?view=minecraft-bedrock-stable), [1.10 schema](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/visualreference/actor_resource_definition.v1.10.0?view=minecraft-bedrock-stable), and official source schemas [1.8](https://github.com/MicrosoftDocs/minecraft-creator/blob/4186dc4ec84c8980fc25d89c26e1414aa6c2fde2/creator/Reference/Content/VisualReference/actor_resource_definition.v1.8.0.md), [1.26](https://github.com/MicrosoftDocs/minecraft-creator/blob/4186dc4ec84c8980fc25d89c26e1414aa6c2fde2/creator/Reference/Content/VisualReference/actor_resource_definition.v1.26.0.md). Generated schema alternatives without sufficient behavioral descriptions are not inferred to work.

## Eleven-contract matrix

“Unit” means assertions about an executable subset. “Client” means client/integrated-server state or resource assertions, not an independent Bedrock visual comparison.

| Contract | Parsing / execution | Unit evidence | Client evidence | Remaining boundary |
|---|---|---|---|---|
| `entity.aliases` | Partial: object aliases resolve through shared runtimes | Existing `BedrockEntityInstanceTest.entityAnimationAliasesDriveNativeMesh`; new missing-alias isolation test | Existing native suite; generated controller fixture | Not all scalar/versioned forms; missing nested aliases can still abort an actor |
| `entity.scripts` | Partial: initialize, pre-animation, conditional ordered animate | Existing script/clock/controller tests; new repeated-timestamp test | Generated public probe/controller state | Render-driven timing; version gates absent |
| `entity.scale` | Partial: uniform, lowercase and uppercase axis overrides execute after pre-animation | New uppercase scale execution test | Scale-one fixture only | No cross-engine negative-scale/culling oracle |
| `entity.offscreen` | Unsupported: authored flags diagnosed at reload, not executed | No execution credit | None | Requires independent bone/effect scheduling, not a fabricated always-visible query |
| `entity.publicvars` | Partial: public declarations filter independent Molang snapshots; renderer exposes snapshot lookup | New privacy, arrow, instance isolation and snapshot mutability tests | Public probe 8/private probe hidden | No automatic actor discovery, target-query integration or live cross-actor variable updates |
| `entity.hidearmor` | Partial: suppresses native armor and Java equipment fallback; held items remain independent | New independent-control/default tests | Zombie/husk armor visibility assertions | Other armor/model combinations and Bedrock comparison remain unverified |
| `entity.itemlighting` | Partial: held native attachables and fallback item submissions use full-bright when authored | New independent-control/default tests | Extracted fallback full-bright flags asserted | No GPU/pixel parity claim; special item renderers and first-person combinations not comprehensively verified |
| `entity.emitters` | Unknown: `particle_emitters` has no verified interpreter; reload diagnostic | None | None | Sparse schema is not sufficient to invent semantics; distinct from functional `particle_effects` aliases |
| `entity.spawnegg` | Partial: same-pack `item_texture.json` alias/index generates Java item presentation | Five new generation/error/fallback tests | Actual generated egg resolves a nonempty Java model | Color masks, built-in/cross-pack atlas fallback, weighted entries and duplicate version selection unsupported |
| `entity.legacycontrollers` | Partial: ordered legacy object aliases and string controller identifiers execute | New ordered execution, string identifier and duplicate tests | Controller entry increments public probe | Coexistence with modern animate/version gates not independently compared to Bedrock |
| `entity.context` | Partial: shared per-instance context, query bindings, geometry anchors and effect routing | Existing clock/holder tests; new locator/switch/reset tests | Animated model-space locator position; stale locator removed | BP data remains provider-dependent; multiplayer/dimension edge cases need more validation |

New unit evidence: [BedrockClientEntityCompatibilityTest](/Users/matanyah/Documents/ChatGPT/MC-Java/src/test/java/dev/matanyah/bedrockcompat/bedrock/entity/BedrockClientEntityCompatibilityTest.java), [BedrockSpawnEggTest](/Users/matanyah/Documents/ChatGPT/MC-Java/src/test/java/dev/matanyah/bedrockcompat/bedrock/resource/BedrockSpawnEggTest.java). Client fixture: [ClientEntityChecks](/Users/matanyah/Documents/ChatGPT/MC-Java/src/gametest/java/dev/matanyah/bedrockcompat/test/ClientEntityChecks.java).

## Property-level inventory

This expands the broad contracts without changing the denominator. “Existing” means inspected functional code, not a new completeness claim.

| Documented property/family | Actual consumer and limitation |
|---|---|
| `format_version`, `min_engine_version` | No historical semantic gating or nearest-compatible duplicate selection. Braced scripts accepted beyond the documented version gate. Last published identity wins rather than selecting against the top RP engine version. |
| `identifier` | Existing namespace-aware native snapshot lookup and Java/Bedrock identity vocabulary. Does not register custom server entities. |
| `textures`, `geometry`, `materials` | Existing object aliases and conditional render-controller selection, including arrays/variants where supported by shared controllers. Missing render references retain actor-level Java fallback; not all schema alternatives execute. |
| `animations`, `render_controllers` | Existing cross-file lookup, conditional entries, ordering, transitions and blending; missing top-level animation alias now diagnoses/skips only that entry. Nested failures still need isolation. |
| `animation_controllers` | New legacy declarations become ordered controller entrypoints, rather than unused parsed metadata. Duplicate aliases rejected. |
| `scripts.initialize`, `pre_animation`, `animate` | Existing per-instance scripts and ordered clip/controller evaluation; repeated timestamp does not rerun scripts. Offscreen behavior differs. |
| `scripts.scale`, `scalex/scaleX`, `scaley/scaleY`, `scalez/scaleZ` | Uniform fallback plus authored axis override, now including uppercase forms. Lowercase takes precedence if both are authored; conflicting forms need a Bedrock oracle. |
| `scripts.hide_held_items` | Existing Molang condition; previously bypassed fallback armed branch now respects it. |
| `scripts.variables` | New `public` declarations with normalized variable names; filtered snapshot contexts expose only declared values. This is a reusable integration boundary, not full actor-query discovery. |
| `scripts.should_update_bones_and_effects_offscreen`, `should_update_effects_offscreen` | Unsupported; explicit reload-only diagnostic. Geometry/render extraction still drives updates. |
| `enable_attachables`, `hide_armor` | Existing equipment entrypoint plus new armor suppression. Armor suppression does not hide ordinary held items. |
| `held_item_ignores_lighting` | New held-only full-bright handling. Does not change actor or armor lighting globally. |
| `queryable_geometry` | Existing bone queries; previous anchors now cleared when geometry source changes. |
| `description.locators` | New legacy bone-associated model-space offsets inherit animated bone transforms without adding the bone pivot twice. Missing bones diagnose/isolate locator. Geometry-defined locators remain shared. |
| `particle_effects`, `sound_effects` | Existing aliases/timeline/controller events. Entity sound positions now use shared world locator transforms; a missing named locator is diagnosed/skipped. Particles retain existing binding/context runtime. Attachable sounds remain explicitly unsupported. |
| `particle_emitters` | Unknown and not executed; reload diagnostic prevents silently presenting it as supported. |
| `spawn_egg.texture`, `texture_index` | New atlas alias lookup, index zero default, bounds checks, PNG/TGA resolution, Java generated model/item/texture output. No item registration or gameplay change. |
| `spawn_egg.base_color`, `overlay_color` | Unsupported without the Bedrock base/overlay masks. Deliberately not recolored from Java species eggs. |
| `item`, `scripts.parent_setup` | Attachable selection/parent context responsibility; existing attachable runtime is reused rather than duplicated in client entities. Full schema/version alternatives not certified. |
| Geometry `visible_bounds_*`, render visibility | Existing controller bone visibility; authored visible bounds are not a full client-culling adapter. Server collision dimensions are untouched. |

Implementation: [BedrockClientEntity](/Users/matanyah/Documents/ChatGPT/MC-Java/src/main/java/dev/matanyah/bedrockcompat/bedrock/entity/BedrockClientEntity.java), [BedrockEntityInstance](/Users/matanyah/Documents/ChatGPT/MC-Java/src/main/java/dev/matanyah/bedrockcompat/bedrock/entity/BedrockEntityInstance.java), [NativeEntityRenderer](/Users/matanyah/Documents/ChatGPT/MC-Java/src/client/java/dev/matanyah/bedrockcompat/client/entity/NativeEntityRenderer.java), [NativeParticles](/Users/matanyah/Documents/ChatGPT/MC-Java/src/client/java/dev/matanyah/bedrockcompat/client/particle/NativeParticles.java), [BedrockSpawnEgg](/Users/matanyah/Documents/ChatGPT/MC-Java/src/main/java/dev/matanyah/bedrockcompat/bedrock/resource/BedrockSpawnEgg.java).

## Lifecycle, priority and fallback

Native renderer instances are cleared on snapshot generation and world changes; removed actors cannot expose public contexts. Particle owner frames are rebuilt on observation rather than retaining locators from an earlier geometry. Public snapshots cannot retain entity query closures. This does not certify all existing particle closure/lifecycle cases or offscreen despawn timing.

Existing pack selection/subpack and snapshot publication remain in use. Valid authored egg outputs replace lower outputs by generated asset identity; a higher entity definition without an egg declaration does not currently remove an earlier generated egg. Atlas/image lookup is local to the selected pack, not the full Bedrock stack or built-in atlas. These are known priority limitations, not BP dependencies. Missing egg assets report translation failures without discarding a valid entity definition. Actions & Stuff's references to an unshipped built-in item atlas therefore remain unsupported and reported; installed packs are untouched.

No new gameplay registration, server dimensions, scripts or BP state are fabricated. Unavailable properties continue to depend on existing generic query/property providers. No new EMF/ETF dependency or unrelated renderer pipeline was introduced.

## Validation and remaining work

The full unit suite has **344 tests** (17 new tests), with zero failures/errors/skips. Full client/integrated-server regressions pass using generated fixtures and the read-only Actions & Stuff 1.12 pack, including a separate Sodium 0.9.2-alpha.4 run. Exact fresh log paths/results are recorded in `BEDROCK_RP_COVERAGE.json`; focused materials/trails results are recorded separately there. Diagnostic screenshots are not visual certification.

Existing tests exercise representative player animation/face, morph/variant, armor-stand, held-item/attachable, material, particle/trail, controller, reload and vanilla fallback paths. They do not prove every frame of every pack animation. Locator-position unit/client assertions are not auditory verification of sound placement. No independent Bedrock image/audio oracle, dedicated remote multiplayer run or shader-enabled Iris run was performed.

Next priorities:

1. Independent offscreen bone/effect scheduler with real visibility and lifecycle policy.
2. Engine-version/duplicate-resource selection and full pack-stack/built-in atlas resolution; authored color egg masks.
3. Connect public snapshot references to documented actor discovery/query contexts without leaking private state or fabricating BP data.
4. Client visible-bounds culling, nested missing-reference isolation, locator sound auditory scenarios and effect closure-lifecycle stress.
5. Cross-engine comparisons for controller timing, blending, first-person lighting, negative scales and variants; dedicated remote-server and Iris scenarios.

`particle_emitters` remains a documentation investigation, not an invented implementation target. RP beta must advertise these boundaries explicitly rather than claim 11/11 or complete Bedrock parity.

## Files changed in this pass

Paths below are relative to `/Users/matanyah/Documents/ChatGPT/MC-Java`; unrelated pre-existing working-tree edits are not part of this pass.

- `src/main/java/dev/matanyah/bedrockcompat/bedrock/entity/BedrockClientEntity.java`
- `src/main/java/dev/matanyah/bedrockcompat/bedrock/entity/BedrockEntityInstance.java`
- `src/main/java/dev/matanyah/bedrockcompat/bedrock/entity/BedrockEntityIds.java`
- `src/main/java/dev/matanyah/bedrockcompat/bedrock/resource/BedrockSpawnEgg.java` (new)
- `src/main/java/dev/matanyah/bedrockcompat/bedrock/resource/NativeEntityResourceTranslator.java`
- `src/client/java/dev/matanyah/bedrockcompat/client/entity/NativeEntityRenderer.java`
- `src/client/java/dev/matanyah/bedrockcompat/client/entity/NativeEntityResources.java`
- `src/client/java/dev/matanyah/bedrockcompat/client/particle/NativeParticles.java`
- `src/test/java/dev/matanyah/bedrockcompat/bedrock/entity/BedrockClientEntityCompatibilityTest.java` (new)
- `src/test/java/dev/matanyah/bedrockcompat/bedrock/resource/BedrockSpawnEggTest.java` (new)
- `src/gametest/java/dev/matanyah/bedrockcompat/test/ClientEntityChecks.java` (new)
- `src/gametest/java/dev/matanyah/bedrockcompat/test/NativeEntityGameTest.java`
- `tools/update_client_entity_coverage.py` (new, emits reviewed report patches only)
- `CLIENT_ENTITY_COMPATIBILITY.md` (new), `BEDROCK_RP_COVERAGE.md`, `BEDROCK_RP_COVERAGE.json`, `BEDROCK_RP_ROADMAP.md`
