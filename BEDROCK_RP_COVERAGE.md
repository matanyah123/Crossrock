# Crossrock Bedrock resource-pack compatibility audit

Audit date: 2026-10-13. Source: current working tree at HEAD `4f7eec71997d5fd5909a88a4cd91db1f67ca882f` **plus existing uncommitted changes**. Updated after the particle, partial Molang, environment, client-entity and partial JSON UI and block/item asset implementation/validation passes; other API families retain the earlier audit findings. See [particle pass](PARTICLE_COMPATIBILITY.md) for the 40-contract evidence matrix and remaining discrepancies.

## Verdict

Crossrock is **not feature-complete for the documented Bedrock resource-pack API**. It is a substantial native entity/animation renderer plus a curated asset translator. Entire presentation families have no active interpreter. Parsing and a working Actions & Stuff scene are not API parity.

The headline inventory covers 182 capability/component contracts. 144 have a functional path, often partial; 137 have mapped targeted unit/client-subset validation. **Zero are certified substantially complete under the requested cross-engine/edge-case standard.** This conservative completeness lower bound does not mean nothing works: it means no full contract has been demonstrated against that standard.

Primary reference: [Minecraft Creator documentation](https://learn.microsoft.com/en-us/minecraft/creator/). The audit also reads its [official source pinned at 4186dc4ec84c](https://github.com/MicrosoftDocs/minecraft-creator/tree/4186dc4ec84c8980fc25d89c26e1414aa6c2fde2/creator), including the [comprehensive pack-content list](https://github.com/MicrosoftDocs/minecraft-creator/blob/4186dc4ec84c8980fc25d89c26e1414aa6c2fde2/creator/Documents/ComprehensivePackContents.md). Pinned source links in the matrix provide reproducible documentation evidence when Learn pages redirect or generated schema descriptions are incomplete.

## Definitions and denominator

- **Implemented**: a real execution/resource-generation/rendering path exists for the named contract. **Partial** also counts toward implemented breadth, but has known missing semantics. **Unsupported** means the active loader/runtime has no path for the named contract. **Unknown** means evidence or documented semantics is insufficient.
- **Validated**: mapped behavioral unit-test assertions passed in this audit for a contract subset. This is targeted validation, not a visual or full-contract pass. Parsed material flags alone do not certify GPU execution. Query/math leaves receive credit only for reviewed explicit runtime/numerical assertions, never blanket identifier matches.
- **Substantially complete**: documented important cases, execution and rendering are independently demonstrated, including differential/visual edge-case coverage. No such blanket certification is available today. The percentage is a verified lower bound; the actual completeness proportion is **Unknown**.
- Every matrix capability/component counts once. Query and math-function leaves are enumerated separately. The primary denominator excludes those leaves so hundreds of queries do not overwhelm absent UI/environment/model APIs. A second unweighted atomic-inventory estimate includes them. These are engineering coverage units, not equally difficult work estimates or the fraction of packs that will work.
- Unknown and unsupported documented units remain in denominators; BP gameplay is excluded. Skin packs, encrypted Marketplace distribution (public contract unverified), and Crossrock-specific persistence are adjacent concerns and explicitly excluded. Nested/versioned schema fields are recorded separately, not double-counted as independent APIs. Additional schema fields not independently audited remain Unknown, so these percentages must not be relabeled “the exact percentage of every Bedrock schema ever published.”

## Coverage

| Category | Implemented breadth | Targeted validation | Certified substantially complete |
|---|---:|---:|---:|
| Packaging | 7/10 (70.0%) | 6/10 (60.0%) | 0/10 (0.0%) |
| Textures | 8/11 (72.7%) | 7/11 (63.6%) | 0/11 (0.0%) |
| Geometry | 8/12 (66.7%) | 8/12 (66.7%) | 0/12 (0.0%) |
| Client entities | 9/11 (81.8%) | 9/11 (81.8%) | 0/11 (0.0%) |
| Animations | 8/9 (88.9%) | 8/9 (88.9%) | 0/9 (0.0%) |
| Controllers | 6/7 (85.7%) | 6/7 (85.7%) | 0/7 (0.0%) |
| Molang language | 6/6 (100.0%) | 5/6 (83.3%) | 0/6 (0.0%) |
| Render controllers and materials | 13/14 (92.9%) | 11/14 (78.6%) | 0/14 (0.0%) |
| Attachables and players | 9/10 (90.0%) | 7/10 (70.0%) | 0/10 (0.0%) |
| Particles | 40/40 (100.0%) | 40/40 (100.0%) | 0/40 (0.0%) |
| JSON UI | 9/9 (100.0%) | 9/9 (100.0%) | 0/9 (0.0%) |
| Audio and localization | 4/10 (40.0%) | 4/10 (40.0%) | 0/10 (0.0%) |
| Environment | 12/17 (70.6%) | 12/17 (70.6%) | 0/17 (0.0%) |
| Block and item assets | 2/7 (28.6%) | 2/7 (28.6%) | 0/7 (0.0%) |
| Advanced rendering | 1/7 (14.3%) | 1/7 (14.3%) | 0/7 (0.0%) |
| Other presentation | 2/2 (100.0%) | 2/2 (100.0%) | 0/2 (0.0%) |
| Molang queries | 192/317 (60.6%) | 91/317 (28.7%) | 0/317 (0.0%) |
| Molang math | 61/61 (100.0%) | 38/61 (62.3%) | 0/61 (0.0%) |

| Category | Implemented breadth | Targeted validation | Certified substantially complete |
|---|---:|---:|---:|
| Core capability/component estimate | 144/182 (79.1%) | 137/182 (75.3%) | 0/182 (0.0%) |
| All enumerated inventory units | 397/560 (70.9%) | 266/560 (47.5%) | 0/560 (0.0%) |

## Validation evidence and limits

Latest validation (2026-10-13 block/item batch): **430 unit tests passed, 0 failures, 0 errors, 0 skipped**. Gradle `:build` and the full client/integrated-server suite passed with generated fixtures and the local read-only Actions & Stuff 1.12 reference pack. Command: `BEDROCK_REFERENCE_PACK='<local Actions and Stuff 1.12.mcpack>' ./gradlew -I /tmp/crossrock-rp-audit-G6didE/audit-build.gradle :test :build :runClientGameTest --console=plain`. Log: `/tmp/crossrock-block-item-final.log`. JSON records every executed unit suite.

The block/item client checks exercise actual baked world and carried cube models, GUI/first-person/third-person/ground/fixed item rendering contexts, loaded icon pixels, missing-alias fallback and pack-removal restoration. Existing environment/entity/UI/particle regressions also pass. See [block/item batch](BLOCK_ITEM_ASSET_COMPATIBILITY.md) for the exact subset and remaining work. These are functional Java-renderer checks, not independent Bedrock visual parity.

The prior water-opacity correction remains recorded separately: RGB tint and sky/fog changes preserve Java alpha without replacing environment shaders. Earlier Sodium/materials-trails runs remain historical evidence; Sodium and Iris were not part of the latest run. Iris remains untested.

## Active implementation boundary

`VirtualCompatibilityPackRuntime.translators()` registers vanilla textures, presentation, beds, sound, language, native entity, environment and registered JSON UI resources; Punchy translation is conditional and adapts attachables, not an independent generic item API. Dormant translators and accepted JSON files are not credited without an active consumer. Native resource ingestion recognizes client entities, attachables, geometry, animations, animation/render controllers, materials, particles and selected biome query metadata. `VanillaTextureTranslator` calls `BedrockBlockModelGenerator`, which consumes a selected vanilla blocks.json subset. There is no general custom block geometry or full PBR execution path. JSON UI now has a bounded decorative and native-interaction execution subset, not full engine/HUD replacement support. Item atlas aliases now execute for mapped vanilla item icons as well as the existing spawn-egg subset. Simple bundled Java cube models additionally consume per-face blocks.json atlas assignments and separate carried textures; unknown/complex shapes retain Java behavior. This remains a bounded asset adapter, not a general item-definition/custom-geometry API. Client-biome/fog subsets and explicitly bounded VV color approximations now execute; see BEDROCK_ENVIRONMENT_COMPATIBILITY.md.

`NATIVE_ENTITIES.md` was checked against code, not accepted as proof. Its statements about missing first-person attachables and rectangular particle rendering are stale: `NativeAttachableRenderer`/`NativePlayerHandsMixin` and the particle rectangle renderer now contain those paths. Attachable particle effects execute; attachable sound effects still explicitly do not. Existing pack-specific adapters such as `SulfurCubeGeometry` cannot be counted as general geometry API support.

## RP versus BP/runtime responsibilities

Pure RP: texture/atlas lookup, geometry/UV interpretation, render controllers/materials, animation evaluation, particle simulation, sounds/text, JSON UI, fog and client-biome visuals, subpacks/settings/priority. These can be implemented without importing BP gameplay.

Runtime-context RP: queries, owner/equipment slots, synced properties, locator transforms, camera/animation clocks, biome identity and vanilla event triggers. Crossrock must adapt Java's available state and explicitly report unavailable contexts. A texture/controller that reads a custom property is not intrinsically unsupported RP syntax; producing that property may require BP/server integration. Do not invent BP state or silently activate variants as a substitute.

Excluded BP gameplay: entity/block/item registration, AI/components, spawning/worldgen, loot/recipes/trades, damage rules, scripts and server functions. Resource-side assets of custom registered objects can still have independent RP support. Skin/persona packs and server camera presets are separate adjacent scopes, not silently counted as core RP gameplay gaps.

## Compatibility matrix

Detailed tables are split into smaller pages for reliable rendering. Coverage counts and limitations are unchanged.

- Packaging: [Packaging](docs/coverage/packaging.md)
- Textures: [Textures](docs/coverage/textures.md)
- Geometry: [Geometry](docs/coverage/geometry.md)
- Client entities: [Client entities](docs/coverage/client-entities.md)
- Animations: [Animations](docs/coverage/animations.md)
- Controllers: [Controllers](docs/coverage/controllers.md)
- Molang language: [Molang language](docs/coverage/molang-language.md)
- Render controllers and materials: [Render controllers and materials](docs/coverage/render-controllers-and-materials.md)
- Attachables and players: [Attachables and players](docs/coverage/attachables-and-players.md)
- Particles: [Particles](docs/coverage/particles.md)
- JSON UI: [JSON UI](docs/coverage/json-ui.md)
- Audio and localization: [Audio and localization](docs/coverage/audio-and-localization.md)
- Environment: [Environment](docs/coverage/environment.md)
- Block and item assets: [Block and item assets](docs/coverage/block-and-item-assets.md)
- Advanced rendering: [Advanced rendering](docs/coverage/advanced-rendering.md)
- Other presentation: [Other presentation](docs/coverage/other-presentation.md)
- Molang queries: [Part 1](docs/coverage/molang-queries-1.md), [Part 2](docs/coverage/molang-queries-2.md), [Part 3](docs/coverage/molang-queries-3.md), [Part 4](docs/coverage/molang-queries-4.md), [Part 5](docs/coverage/molang-queries-5.md), [Part 6](docs/coverage/molang-queries-6.md), [Part 7](docs/coverage/molang-queries-7.md), [Part 8](docs/coverage/molang-queries-8.md)
- Molang math: [Part 1](docs/coverage/molang-math-1.md), [Part 2](docs/coverage/molang-math-2.md)

## Schema-field appendix and unknowns

`BEDROCK_RP_COVERAGE.json` also contains **1803 documented schema-table entries** from current actor, animation, controller, geometry, texture-set, manifest, JSON UI, particle, client-biome and block-culling references, with document/section/JSON-path/line anchors. These entries are **not additional denominator units**: versions and nested properties duplicate matrix contracts. Unsupported-family leaves inherit the verified absent-loader finding. Leaves in implemented families not independently certified remain Unknown; use capability rows for demonstrated subsets. Generated docs contain blank descriptions and `undefined` enum members, so those enum semantics are not guessed. No exact full-schema-completeness claim follows from this inventory.

Known documentation/version uncertainties: latest generated actor fields offer scalar alternatives without explanations; `particle_emitters` semantics are not sufficiently described; preview texture-variation requirements and later removals must be version-gated; flipbook tutorial naming is inconsistent between singular/plural filenames. Historical/persona and preview-only APIs require a separately pinned target/version policy. These are explicit audit limits, not support claims.

## Highest-impact verified gaps

1. No general item atlas or custom block rendering; JSON UI complete expression semantics/factories/engine catalog remain unsupported; input and native slot collections have bounded partial paths; blocks.json only covers selected vanilla shapes/face assignments. Broad classes of cosmetic packs cannot execute, regardless of animation coverage.
2. Query/context coverage is incomplete; some “supported” facts are constants or Java reconstructions. The entity renderer substitutes zero for recognized unsupported-query diagnostics, preserving drawing but potentially selecting incorrect branches. Other contexts and invalid argument handling differ.
3. Particle child context, inherited velocity, audio/log dispatch and facing branches have now been repaired and unit-tested. Remaining high-impact gaps are offscreen owner updates, local-position collisions, UV boundary behavior and independent Bedrock comparisons; see the particle pass.
4. Geometry bounds, render groups and item display transforms are ignored. Shortest-path controller blending remains absent. Client armor/item-lighting controls and legacy controllers now have functional subsets; offscreen policies, versioned selection and full public actor discovery remain open.
5. Sound definition-level attenuation, locale-key/placeholder conversion, fonts and stack merging are incomplete.
6. Client-biome/fog now have partial classic-renderer consumers, but authored colormaps, additional fog media/transitions and full audio remain open. PBR/deferred rendering remains unsupported; VV color adapters are only approximations.

## Beta readiness and feature completeness

**Do not label the current build full Bedrock RP beta or feature-complete.** A clearly scoped classic-renderer entity/texture compatibility preview is defensible if unsupported features are detected and disclosed. A resource-pack beta should have a written target engine/schema version, deterministic pack stacking, clear fallbacks, runtime query/context coverage for common vanilla actors/items, fixed particle child semantics, correct general geometry/attachable transforms, and independent regression packs. Unsupported JSON UI portions and block/item APIs and remaining client-biome/fog fields must either be implemented or explicitly excluded from the advertised beta contract.

Before full documented RP feature completeness: implement all absent core families and documented fields, correct known semantics, adapt runtime context without faking BP gameplay, and validate versioned rendering/audio/effects against Bedrock. Advanced renderer features can be optional for a **named classic-renderer profile**, but cannot be omitted while claiming all documented RP APIs. Unknown rows must be resolved, not counted as complete.

Reasonable beta imperfections: small interpolation/pixel differences, uncommon legacy forms, performance tuning and incompletely sampled device/locale combinations, provided fallback is safe and limitations are documented. Invisible entities, globally wrong transforms, lost textures, wrong variants, stale settings, cross-pack contamination and broken common animation/particle lifecycles are not acceptable as known baseline behavior.

## Independent RP-only regression candidates

- [Mojang bedrock-samples resource_pack](https://github.com/Mojang/bedrock-samples/tree/main/resource_pack): pin a revision and select vanilla client entities, render controllers, sounds, texture atlases and particle assets. The resource-pack subtree is a reference corpus, not a claim that every sample in the repository is RP-only.
- [Microsoft particle examples](https://github.com/microsoft/minecraft-samples): use the particle-example RP portion only; drive it with a test harness rather than enabling companion BP gameplay. Cover billboard modes, emitter shapes, gradients, nested events and owner/world-space attachment.
- [Microsoft RTX sample resource pack](https://learn.microsoft.com/en-us/samples/microsoft/minecraft-samples/rtx-resource-pack/): standalone PBR negative-control today; future positive regression for texture-set channels/capabilities. The official page explicitly identifies a resource-pack-only activation workflow.
- [Vanilla RTX, author site](https://www.minecraftrtx.net/): independent advanced-renderer stress/negative-control; not expected to pass classic rendering and not a substitute for core coverage.
- Add small **pack-neutral conformance RPs**: asymmetrical UV cube; nested/nonuniform reflected locators; same-ID two-pack collision; item atlas/index; grass untinted dirt; font/localization placeholders; fog/biome transition; UI binding/control; shortest-arc controller; child particle isolation/velocity/audio. These proposed fixtures are not existing validated packs. Compare Bedrock and Crossrock captures/state with tolerances.

See `BEDROCK_RP_ROADMAP.md` for prioritized work and acceptance gates. Production fixes remain outside this audit.

## Molang query follow-up (2026-10-12)

This is a **partial implementation pass**, not completion of the requested 317-query goal. See [individual query inventory](MOLANG_QUERY_COMPATIBILITY.md) and `molang_query_pass` in the JSON. Five malformed documentation filename spellings are normalized to their documented API names. Learn's current index lists 306 queries; the official leaf corpus has 317 including 11 legacy/unindexed entries. Four leaves are explicitly BP/server-only and are not missing RP gameplay. The legacy 317 denominator is retained for comparison, with a separate 313 RP-eligible denominator in JSON.

Behavioral tests now exercise common Java state adapters, numerical/argument boundaries, rendered-item durability/tags, block-neighbor coordinates, live particle query contexts and optional property integration. Existing incomplete/constant inputs are still limitations; tests do not certify all supported leaves or every Actions & Stuff animation/morph/facial state. Runtime engine-version gating is still absent.

## Environment follow-up

Environment is now **12/17 functional partial contracts and 12/17 targeted behavioral subsets**; none are certified complete. See [environment matrix](BEDROCK_ENVIRONMENT_COMPATIBILITY.md). Advanced rendering has one bounded atmosphere color approximation; imported lighting rendering is unsupported. Music validation includes live attribute assertions; it is not all unit-tested or visually equivalent to Bedrock. Fog transitions/remaining media, authored colormaps, named music, addition/mood, precipitation, cubemaps, water opacity and advanced graphics remain open. Fresh client/Sodium/material results and logs are recorded separately in JSON.

## Client-entity follow-up

Client entities improved from **4/11 to 9/11 functional partial contracts and targeted-tested subsets**. See [client-entity matrix](CLIENT_ENTITY_COMPATIBILITY.md). Offscreen scheduling remains unsupported and `particle_emitters` Unknown. Public snapshot references and texture/index eggs are bounded subsets; no contract is certified complete. Full standard/Sodium client suites and focused materials/trails pass; no Bedrock visual/audio differential or Iris certification. Detailed test evidence and logs are recorded in JSON.

## JSON UI follow-up

See [real-pack compatibility](BEDROCK_JSON_UI_REAL_PACK_COMPATIBILITY.md). **9/9 broad partial functional and targeted-tested contracts, 0/9 complete**, unchanged denominators. Bounded scalar expressions, static conditionals, variable child arrays, four known primitives, local view bindings and nine-slice now execute. Full standard/Sodium/A&S regressions pass with 414 units and GUI scales 1/2. Recolourful authored layouts remain blocked: nine native fallback screens were exercised, not certified as translated. No Bedrock differential. Exact evidence is in JSON `json_ui_real_pack_pass`; earlier pass records are historical.

## Expression continuation — 2026-10-13

Colon-containing variable/binding identifiers and bounded literal string subtraction now execute; the installed pagination condition passes. The next traced chest blocker is `cache_screen`, with lifecycle/input and item-control dependencies still unsupported. Native fallback and 9/9 partial, 0/9 complete coverage remain unchanged. See [real-pack evidence](BEDROCK_JSON_UI_REAL_PACK_COMPATIBILITY.md) for limits and fresh validation.

## Block/item asset follow-up — 2026-10-13

Mapped item atlas icons and generic simple-cube world/carried texture paths now execute. See [scope and evidence](BLOCK_ITEM_ASSET_COMPATIBILITY.md). This extends three existing Partial contracts; **block/item breadth stays 2/7** and overall totals remain unchanged. Legacy RP item definitions, independent custom item models, Bedrock-authored display transforms and custom block geometry/culling remain unsupported. Next prioritize shared pack-stack atlas resolution and explicit index selection, then bind item presentation definitions to existing runtime identities.
