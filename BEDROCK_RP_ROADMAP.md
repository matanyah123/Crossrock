# Crossrock resource-pack beta roadmap

Audit baseline with particle and partial Molang implementation follow-ups, 2026-10-12. See `BEDROCK_RP_COVERAGE.md` and the JSON feature IDs for evidence. Priorities are reusable API work, not Actions & Stuff-specific conditions. The initial audit made no production changes; subsequent scoped passes are tracked separately.

## Release policy first

Current status reviewed 2026-10-13: core breadth 149/182 (81.9%), targeted validation 142/182 (78.0%). Imported lighting rendering is unsupported; environment colors preserve Java alpha after the invisible-water correction.

Lighting safety: retain Java sunlight colors and ambient brightness until a renderer supports Bedrock's separate ambient color, lux and exposure semantics. Direct ambient RGB-to-lightmap-floor mapping caused fullbright and has been removed. Any future ambient adapter must pass unlit/torch-lit enclosed-room, day/night, dimension, existing-mod-layer and pack-removal regressions.

Declare a target Bedrock engine/schema range and an explicit renderer profile (classic versus Vibrant Visuals/RTX). Record supported/partial/unsupported features at reload. Keep compatibility failures scoped to an asset/definition, with actionable file/field/context diagnostics. Do not turn absent query state into fabricated variants or treat successful parsing as support.

## 1. Missing core APIs

### P0 — General resource identity, atlases and stack resolver

Implement item_texture atlas/index resolution, complete terrain aliases/arrays, weighted deterministic texture variation and flipbook fallback/index/variant semantics. Centralize definition and asset precedence, subpack overlay and cross-pack lookup rather than adding per-item mappings. Enforce dependency version constraints and engine-version policy. Validate disjoint JSON sound/language definitions and same-name collisions across two independent packs. IDs: `textures.itematlas`, `textures.variations`, `textures.flipbook.*`, `manifest.dependencies`, `pack.priority`.

Acceptance: deterministic cold start/reload/reordering tests, reference Bedrock resolutions, missing/invalid dependency diagnostics, no production fixture name checks.

### P1 — Client entity/geometry contracts

Implement authored bounds/culling, render groups and documented offscreen update policies. Block-item item_display_transforms now have a static partial consumer; GUI auto-fit now executes; broader consumers remain open. Extend the now-functional hide_armor, held_item_ignores_lighting, filtered public snapshots and legacy controller subsets with version gates and actor-discovery integration. Resolve uncertain particle_emitters/scalar schema forms from authoritative examples before implementation. Extend authored texture/index spawn eggs with built-in/stack atlas resolution and color masks. IDs: `geometry.bounds`, `geometry.rendergroup`, `geometry.display`, `entity.*`.

Acceptance: asymmetrical geometry and wearable fixtures in every view; transparent layers/reflections/nonuniform scale; offscreen emitters reenter without freeze/replay; correct egg inventory/hand texture.

### P1 — Complete the partial JSON UI runtime

The runtime now has 9/9 broad partial contracts (zero complete), including native Java widgets, scoped actions, fixed typed slot grids and collection bindings. See BEDROCK_JSON_UI_INTERACTION.md. The real-pack pass adds four known primitives, bounded expressions/static conditionals, local view bindings and scalar/per-edge nine-slice. Prioritize engine templates/variant roots/pack-stack semantics, variable template names, factories/named view bindings/renderer catalog, scrolling/content-relative sizing, asset paths, safe modifications and interaction triggers. Recolourful Containers remains native fallback; no pack-specific patches. IDs: `ui.*`.

Acceptance: independent RP-only UI fixture with layout/input/collection/animation/accessibility assertions; safe fallback for unsupported screens and bindings.

### P1/P2 — Block/item presentation

The 2026-10-13 batch adds mapped item atlas icons and simple-cube per-face/carried textures (see BLOCK_ITEM_ASSET_COMPATIBILITY.md). The subsequent static geometry pass (BLOCK_GEOMETRY_COMPATIBILITY.md) adds shared stack references, custom static cube/bone geometry/materials, separate block-item visuals and authored display transforms. The state/culling/item pass now implements mapped permutations, world transforms, bounded UV locking, GUI fitting, item icon definitions and authored culling (see BLOCK_ASSET_STATE_COMPATIBILITY.md). Next tackle unmapped state semantics, oblique UV locking, imported voxel occlusion, general atlas index selection, legacy item offset presets and independent Bedrock rendering comparisons. Keep custom object registration/server gameplay outside RP scope, accepting a stable external ID/context when provided. Optional Punchy translates attachables only; it is not an independent generic custom-item route. IDs: `block.*`, `item.*`.

Acceptance: vanilla overrides without BP, and a harness-registered custom object showing that RP geometry/material consumption works independently of gameplay logic.

### P2 — Environment, audio and text

Extend the now-functional client_biome/legacy visual subsets: authored colormaps, full named music and ambient addition/mood, fog transitions and remaining media, precise fog-stack semantics and version gates. Implement locale catalogs/key and placeholder adaptation, glyph fonts, sound category/distance/attenuation and attachable sound dispatch. Particle sound dispatch now executes; context-dependent level-event routing remains partial. Research FSB format/platform feasibility and document a precise unsupported profile if unavailable. IDs: `client_biome.*`, `environment.*`, `sound.*`, `lang.*`, `attachable.audio`, `particle.sound`.

Acceptance: two-biome transition/fog fixtures, locale/format comparisons, positional sound-distance tests, stack conflict fixtures; no BP worldgen required.

## 2. Partially implemented APIs

### P0 — Molang/context conformance

For every `query.*` row, document arguments, return type/units, valid contexts, lifetime and required state source. Replace constant visibility/attachment inputs with actual state where feasible. Separate unsupported context from unsupported syntax. Adapt Java vanilla properties; expose integration boundaries for externally supplied custom synced properties. Add per-function math edge cases and version semantics. Family tests must not automatically certify every query/function.

Acceptance: official-contract tables converted to fixture cases, unsupported-query diagnostics, entity/attachable/particle context isolation and deterministic time/random tests. Do not implement AI or fake BP properties to satisfy RP queries.

### P0/P1 — Rendering and materials

Audit material state recognition against actual GPU application: independent alpha/color/depth write, blending factors, multiple samplers, translucency ordering and UV wrapping. Implement missing multitexture/color-mask semantics through a general material interface. Validate glint, armor dye/trim, elytra and skin layers across first/third/UI views. Keep custom Bedrock shader execution a separate renderer capability, not an accidental acceptance path.

Acceptance: golden captures plus draw-state assertions for cutout/translucent/additive/emissive/glint/stencil/reflection combinations. Parser tests alone fail this gate.

### P1 — Animation/controller composition

Add documented shortest-path blending and locator-positioned animation sound dispatch; validate nested override/weight/order semantics, relative rotation and discontinuous keyframes. Controller particle bind_to_actor now propagates and has unit plus client state assertions; retain it as a regression gate. Compare state entry/exit, transition priority, event-only duration, loop/delay/time jumps against pinned Bedrock behavior. Do not regress actor-local clocks to render frame counters.

Acceptance: numerical pose/variable/event traces at multiple frame rates and long uptime, including UI and first-person duplicate render passes.

## 3. Incorrect or incomplete behavior

### P0 — Particle semantics

Completed in the particle pass: fresh child startup context, inherited world velocity, positional audio/log execution, bind_to_actor propagation, separate facing modes, documented lifetime defaults, distance events and per-render initialization. Targeted production-runtime tests now map to all 40 particle contracts. Remaining: local-position collisions, offscreen actor/locator refresh, exact inherited-speed additive semantics, alpha cutoff/UV boundary parity, all expiry/event ordering combinations and cross-engine distribution/trajectory captures. IDs: `particle.childvelocity`, `particle.facing`, `particle.uv`, component rows. See [particle pass](PARTICLE_COMPATIBILITY.md); do not treat subset tests as full conformance.

Acceptance: trace parent/child variables and velocities, verify no unintended context leakage; side/top camera captures; moving-owner trails and ground decals; sound event counts; cold start/reload/reentry.

### P0 — Runtime state and pack isolation

Resolve hurt_time unit inconsistency, UUID-derived skin_id limitations and water-contact approximation. Ensure pack-specific settings and resources do not leak between instances, variants or views. Review whole-file sound/language output overwrites and native definition priority.

Acceptance: two-pack same-ID/different-ID tests, cross-entity state isolation, runtime state oracle comparisons. A SulfurCubeGeometry workaround is not general mesh correctness.

### P1 — General transforms, not per-pack offsets

Use asymmetric generic fixtures for held items, attachables, sleeping/swimming/gliding, reflections and cube-face winding. Diagnose source/model/view spaces before changing shared transforms. Retest all fixtures for every shared transform/material change.

Acceptance: no special case based on a reference pack UUID, obfuscated animation name, player name or texture filename.

## 4. Untested or insufficiently validated APIs

### P0 — Independent conformance harness

Keep passing unit tests, but add individual official-query/math/component fixtures, state traces and Bedrock-vs-Java comparison captures. Existing Actions & Stuff GameTests and screenshots are a useful stress suite, not an API oracle. Add at least independent vanilla sample resources, a synthetic UI/biome/atlas pack and a different geometry/particle authoring style.

Test matrix: pack order/subpacks/settings persistence; format versions; missing references/malformed files; animation rates/pause/reentry; repeated view extraction; entity unload; resource reload; CPU/GPU budgets; transparency; locale/font/sound fallback; integrated and remote server context.

### P1 — Evidence maintenance

Update stale implementation notes only in a separate requested documentation change. Keep the JSON inventory linked to exact behavioral assertions. Move a row to validated only when the named behavior is asserted; to substantially complete only after important documented cases and a rendering/execution oracle pass. Unknown fields stay Unknown until resolved.

## 5. Optional advanced rendering features

### P2/P3 — Texture sets and PBR

Parse/version texture sets, bind normal/height/MER/MERS and constants, define material semantics and capability negotiation. Use the official RTX RP as a negative-control until the renderer actually supports it. Copying albedo is not PBR.

### P3 — Vibrant Visuals/deferred/RTX profile

Implement/import atmosphere, lighting, water, cubemap, color grading and volumetric fog configs through a real backend or declare them unsupported in the classic profile. Ray tracing is platform/render-backend work, not a quick JSON translator. Full documented RP completeness cannot exclude these silently.

## Beta gates

1. Written version/profile contract and unsupported-family diagnostics.
2. Deterministic resource stack/settings/reload behavior with independent packs.
3. Correct common geometry/player/attachable/material and particle semantics; no invisible common actors or broken baseline transforms.
4. Common runtime query/context adapters with explicit unsupported state, not guessed gameplay.
5. Core claimed atlas, UI, model, sound, localization and biome/fog APIs have execution-level tests. If deferred, the product label must explicitly narrow the beta scope.
6. Independent conformance RPs and reference Bedrock comparisons, not just one commercial pack.
7. No unqualified full-API claim while Unsupported or Unknown inventory rows remain inside the advertised scope.

Low-impact pixel differences, uncommon historical forms and incomplete device sampling may remain beta limitations when documented. Pervasive missing textures, wrong variants, lost settings, invisible actors and broken animation/effect lifecycles may not.

## Molang query pass priorities

The 317-query goal remains open; see `MOLANG_QUERY_COMPATIBILITY.md`. Reusable snapshot integration now exists through `MolangQueryProvider` and `NativeMolangQueries.register`; this is not a claim of BP execution or unavailable-data support.

1. Finish remaining RP-feasible query providers: frame statistics, bone AABB/matrix and locator/default-pivot queries, target/head indexing, full equipment/inventory/cooldown contexts, surface particle sampling, client input, missing vanilla actor states and event contexts. Do not label all remaining queries BP-dependent.
2. Add targeted tests to every previously supported leaf; current evidence is still incomplete. Expand positive entity-specific cases beyond non-applicable zero assertions. Test offscreen/reentry, remote synchronization, detached particles, UI/hand/attachable clocks and reload isolation.
3. Add engine/schema context and enforce documented version/removal behavior. Define numerical parity (float precision, epsilon and angle conventions) using an independent Bedrock oracle. Resolve player position origin, position_delta sampling, hurt_time units and armor enum semantics before changing them globally.
4. Correct particle startup statistics and add total-world counters without confusing imported particles with vanilla particles. Keep child-query context and locator transforms under regression coverage.
5. Integrate authoritative external properties only through synchronized snapshot providers. The four explicitly BP/server-only query leaves remain outside RP gameplay completeness.

## Environment pass priorities

See `BEDROCK_ENVIRONMENT_COMPATIBILITY.md`. 12/17 environment contracts have functional partial subsets and targeted assertions; one advanced contract has an atmosphere color approximation. Imported lighting rendering is unsupported. No contract is certified complete.

1. Core remaining consumers: authored grass/foliage colormaps, named music definitions, ambient addition/mood, client precipitation. Keep Java fallback and external biome identities; do not register server gameplay.
2. Correct/incomplete behavior: water transition_fog progression, other fog media/weather, command-stack integration, unit-safe incomplete fields, engine/schema gates and actionable per-field diagnostics. Compare spatial interpolation and time/weather composition against Bedrock.
3. Validation: Bedrock image/audio oracle, authored atmosphere live scenes, legacy-only live scenes, water opacity decisions, dedicated remote-server test, large-profile reload/performance, Iris with enabled shader packs. Sodium smoke/state assertions do not prove every rendering path.
4. Optional advanced renderer profile: keep scattering, PBR/MER, shadows, cubemaps, color grading/tone mapping, waves/caustics/refraction unsupported until a real backend exists. Maintain explicit bounded color approximation classification; no intensity/lux guessing or pack-specific patches.

## Client-entity pass priorities

See `CLIENT_ENTITY_COMPATIBILITY.md`: 9/11 functional partial contracts and targeted subsets, up from 4/11; zero certified complete.

1. Missing core: independent offscreen bone/effect scheduler and genuine visibility; investigate `particle_emitters` documented semantics before inventing behavior.
2. Partial APIs: versioned duplicate/engine selection, complete pack-stack/built-in atlas lookup and color egg masks; connect public references to actor discovery/query consumers without exposing private state.
3. Incorrect/incomplete behavior: client visible-bounds culling, nested missing-reference isolation, higher absent egg declaration versus lower generated assets, effect-context lifecycle stress. Do not alter server collision boxes.
4. Validation: Bedrock differential timing/blending/scales, locator auditory scenarios, native and fallback equipment combinations, dedicated remote multiplayer, shader-enabled Iris. Existing standard/Sodium/A&S assertions are representative, not universal visual verification.

## JSON UI pass priorities

1. Missing core: built-in screen template catalog/variant selection, variable template names, named view/factory data and engine renderer registry. Bounded scalar expressions/static conditionals/local view bindings now execute, but full semantics remain incomplete. These block Recolourful Containers custom UI.
2. Partial: pack-stack/registration semantics, role-specific collections, native navigation/recipe/text actions, scrolling/content-relative sizing, image/text modes and modifications.
3. Incomplete: full layering/clipping/default/trigger semantics, focus/modal/controller/accessibility behavior and per-root invalidation.
4. Validation: independent Bedrock screenshots/timing, larger RP-only pack matrix, creative inventory, GUI-scale/resize matrix beyond tested scales 1/2, hardware TTS/controller and performance budgets. Current 414 units plus standard/Sodium integrated-server regressions certify bounded subsets only.
5. Optional: newer Bedrock UI engines are separate scope.

## Expression continuation — 2026-10-13

Colon-containing variable/binding identifiers and bounded literal string subtraction now execute; the installed pagination condition passes. The next traced chest blocker is `cache_screen`, with lifecycle/input and item-control dependencies still unsupported. Native fallback and 9/9 partial, 0/9 complete coverage remain unchanged. See [real-pack evidence](BEDROCK_JSON_UI_REAL_PACK_COMPATIBILITY.md) for limits and fresh validation.
