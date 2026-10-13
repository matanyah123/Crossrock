# Block/item asset expansion — 2026-10-13

This pass adds working paths for the six resource-rendering gaps listed after the static geometry batch. It does **not** certify 100% Bedrock compatibility. Java runtime identities and properties remain authoritative; gameplay registration and unsupported runtime contexts are not fabricated.

## Implemented behavior

### State/permutation visuals

`JavaBlockStates` enumerates the real registered block's states. Java property values retain their Boolean/numeric/string types. Declared Bedrock states map to matching property names (including a namespaced state's local name); the standard `minecraft:cardinal_direction` maps to Java `facing`. Domains are checked. Missing properties or unavailable state values produce a diagnostic and retain the Java/lower resource visual.

`BedrockBlockStates` evaluates ordered permutation conditions against immutable snapshots using the existing Molang evaluator. Later matching permutations replace earlier component values. Constant and state-query expressions can drive bone visibility, including intentionally hiding all geometry. Each real Java state receives its own baked visual; item rendering uses the actual default Java state's presentation. Arbitrary BP states are not registered. Camera/time/entity/world-dependent queries are unavailable in this bake-time path; no invented zero substitutes for them.

### World transformations and UV locking

`minecraft:transformation` executes translation, quarter-turn XYZ rotation, scale (including reflections), and rotation/scale pivots around block center. Translation and pivots use block units, converted to the mesh's sixteenths. Reflections preserve outward winding. World transformations require block format 1.19.80+; pivots require 1.21.0+.

`uv_lock` accepts a Boolean or bone-name array for transformed geometry, with format 1.21.100+. Axis-aligned source faces retain world texture orientation under quarter-turn transformations. Per-face UV rectangles and signed/rotated UVs retain their authored rectangle. Non-square box-UV faces and oblique source faces remain diagnostic fallbacks rather than guessed texture orientations.

### GUI fitting

Authored `gui.fit_to_frame:true` (and omitted/default-true fitting on an authored GUI transform) now centers and scales transformed bounds into the Java GUI frame, while preserving authored translation. Other contexts and explicit `fit_to_frame:false` keep the authored transform. Numerical tests and live item-render bounds verify fitting and translation. This is a Java orthographic fitting adapter; there is no independent Bedrock pixel oracle or certification of historical fitting/version bugs.

### Authored culling

`block_culling/*.json` definitions resolve by identifier across selected resource packs. Bone, cube and face selectors attach rules to their original geometry parts before transforms. Rules support `default`, `same_block`, `same_block_permutation`, `same_culling_layer`, and `cull_against_full_and_opaque`. Neighbor directions rotate with world transforms. Missing bones/cubes and malformed selectors retain fallback with diagnostics.

A Fabric world-aware baked-model wrapper evaluates those rules against actual neighboring states during quad emission. Its geometry cache key includes all six neighbors, so neighbor/state updates do not retain stale culling. Quad metadata uses weak identity keys and follows the lifetime of the baked models. Item rendering uses the full geometry without neighbor culling. Voxel culling shapes/layers that require unimplemented shape semantics are not imported; custom geometry without a known full occlusion shape remains a conservative visible-neighbor fallback.

### Non-block item definitions

Legacy RP and modern BP `items/*.json` definitions can bind existing Java items to `minecraft:icon` atlas entries. String, legacy `icon_name`/`texture`, and modern `textures.default` forms resolve selected-stack aliases and PNG/TGA images. The translated texture is consumed by Java's generated item model and standard item renderer. This is independent of attachables and supports externally registered non-block item identities.

Missing images, unknown identities, multi-index/weighted selectors, dynamic/trim icon channels and unsupported definitions preserve fallback. Legacy `render_offsets` presets retain Java transforms with a diagnostic; they are not claimed as implemented. Gameplay/use components are not imported.

## Validation

`BedrockAssetExpansionTest` verifies ordered state/component selection, numerical world transforms, state-expression visibility, UV locking/culling association, GUI fitting, unavailable state/query fallback, format gates and malformed culling selectors, and non-block item generation/removal. Existing geometry/player reflection regression tests also remain active after adding original cube/face metadata to quads.

`BlockAssetExpansionChecks` uses a test-only registered Boolean/facing block and a separate non-block item. It checks actual baked state bounds, later permutation replacement, a fully hidden state, GUI fitting/translation versus unfitted fixed context, legacy/modern rendered icons, all four neighbor culling conditions, changed-neighbor emission counts, and cache-key invalidation. Pack-removal checks restore vanilla/external fixture item models. Existing geometry source-reload, material, entity, particle, UI and environment assertions run in the same suite.

Commands, final test counts, standard/reference and Sodium/reference results, and the installed artifact hash are recorded in `BEDROCK_RP_COVERAGE.json` under `block_asset_state_pass`. The first client launch stalled at Fabric's startup barrier before tests began and was restarted; it is not counted as a pass.

## Remaining limits

- Server-side registration, gameplay, arbitrary state synchronization and automatic creation of new custom IDs remain external.
- Non-state runtime Molang contexts, general state-name/domain conversions, inherited/poly/texture block meshes, mixed per-material AO and full lighting parity remain unsupported.
- General aux/weighted atlases, legacy offset presets, trim/dynamic icon channels, voxel culling shapes, oblique/non-square box UV locking, embedded/shelf display contexts and old-version semantics are not certified.
- No independent Bedrock differential rendering oracle or exhaustive pack/engine-version matrix is available. Breadth percentages count functional partial contracts; they are not a completion percentage.

## Official references

- [Block permutations](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/blockreference/examples/blockstatesandpermutations?view=minecraft-bedrock-stable)
- [World transformation](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/blockreference/examples/blockcomponents/minecraftblock_transformation?view=minecraft-bedrock-stable)
- [Geometry and UV locking](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/blockreference/examples/blockcomponents/minecraftblock_geometry?view=minecraft-bedrock-stable)
- [Culling rules and conditions](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/blockcullingreference/examples/blockcullingrules/block_culling?view=minecraft-bedrock-stable)
- [Geometry display transforms](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/visualreference/geometry.v1.21.0?view=minecraft-bedrock-stable)
- [Item icons](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/itemreference/examples/itemcomponents/minecraft_icon?view=minecraft-bedrock-stable)
- [Official item/atlas example](https://learn.microsoft.com/en-us/minecraft/creator/documents/custom_projectiles?view=minecraft-bedrock-stable)
