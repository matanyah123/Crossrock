# Static block geometry and block-item display compatibility

Historical batch scope: the [state/culling/item continuation](BLOCK_ASSET_STATE_COMPATIBILITY.md) supersedes the remaining-gap statements below for mapped states, world transforms, bounded UV lock, GUI fitting, authored culling and item icon definitions.

Implementation date: 2026-10-13. This is a bounded classic Java baked-model adapter, not a Bedrock gameplay/runtime importer or a claim of cross-engine visual parity.

## What executes

Selected behavior/mixed packs supply the standard `blocks/*.json` `minecraft:block` presentation components. Resource/mixed packs supply `models/blocks/*.json`, `textures/terrain_texture.json`, PNG/TGA images and flipbooks. Geometry identifiers, terrain aliases, images and static block declarations resolve across the selected stack; selected subpack overrides apply first, then higher packs replace lower identities. The translator rebuilds from the selected sources on every resource generation. A higher unsupported declaration does not resurrect a lower generated geometry declaration.

`minecraft:geometry` and `minecraft:material_instances` bind a visual to the **same existing Java block identifier**, including externally registered custom namespaces. No block/item, properties, recipes, collision, loot, world generation or gameplay behavior is registered or replaced. An unknown runtime identifier does not become a playable block. Unbound RP geometry stays inactive. Both companion packs must be enabled; dependency activation is not added here.

The native geometry evaluator supplies static bone hierarchies, cube/bone rotations and pivots, inflate, mirror, box/per-face UVs, signed UV extents, quarter-turn UV rotation and explicit boolean bone visibility. Modern standalone geometry (1.12.0+) and the reserved `minecraft:geometry.full_block` are supported. Quads are passed through a Fabric custom unbaked model into Java's normal atlas, baked block-model parts and item renderer. This preserves arbitrary rotated cube faces without approximating them as axis-aligned boxes.

Materials resolve named instances, references, face names and `*` fallback. Supported render methods are `opaque`, `double_sided`, `alpha_test`, `alpha_test_single_sided` and `blend`. Opaque methods remove texture alpha; alpha-test methods convert alpha to a binary mask; blend retains alpha and selects Java's translucent layer. Double-sided methods add reversed-winding faces. `face_dimming` controls quad shade. Uniform AO 0/1 or historical booleans are supported; boolean AO is rejected for format 1.26.20+. Per-material mixed AO, other AO exponents, isotropic UVs, tint methods and unknown material fields retain fallback. Terrain paths may be scalar or single-element arrays; indexed/weighted selection is not fabricated. Animation metadata follows copied textures and merges by flipbook identity across the selected resource packs.

`minecraft:item_visual` (block format 1.21.60+) supplies independent item geometry/materials. Otherwise block items use the world geometry. Authored `item_display_transforms` on the selected item geometry (geometry format 1.21.30+ in this adapter) execute for GUI, both first-person hands, both third-person hands, ground, fixed and head. Translation, rotation, nonuniform scale and rotation/scale pivots are evaluated in model units. Each authored context selects a pretransformed mesh through Java's `minecraft:display_context`; neutral Java transforms prevent double application and unwanted left-hand mirroring. Unspecified/unsupported contexts use the Java block display fallback. GUI requires explicit `fit_to_frame:false`; automatic fitting is not approximated.

Custom quads are conservatively unculled. Vanilla neighbor-face suppression is bypassed when the neighbor's committed baked model uses this geometry, so a hollow shape does not hide the block behind it. This is a conservative rendering fallback, **not authored Bedrock culling-rule support**. Collision and world light propagation still follow the existing Java block. Pack removal restores original models and neighbor-face behavior.

## Binding example

This companion behavior-pack document is purely a source of static presentation bindings. `example:sculpture` must already exist in Java through another mod/integration to appear in a world.

```json
{
  "format_version": "1.21.60",
  "minecraft:block": {
    "description": { "identifier": "example:sculpture" },
    "components": {
      "minecraft:geometry": "geometry.sculpture",
      "minecraft:material_instances": {
        "*": { "texture": "sculpture_surface", "render_method": "opaque" }
      },
      "minecraft:item_visual": {
        "geometry": "geometry.sculpture_item",
        "material_instances": {
          "*": { "texture": "sculpture_surface", "render_method": "alpha_test_single_sided" }
        }
      }
    }
  }
}
```

## Validation

`BedrockBlockAssetTranslatorTest` covers cross-pack bindings and material references, numerical mesh bounds/rotation, alpha conversion and animation propagation, higher declaration priority, subpack texture selection, removal, independent item visuals/context meshes, material cycles and unsupported permutation fallback, inactive unbound geometry, and numerical display pivots/ranges.

`StaticBlockGeometryChecks` checks real baked quad counts and material layers, double-sided faces, shading/AO, neighbor-face rendering, numerical display bounds across all eight supported contexts, a separately registered test-only custom block/item, source geometry reload, and restoration after removal. The production mod never performs the test-only registration. The existing entity/particle/UI/environment and Actions & Stuff reference regressions run alongside these checks. Diagnostic screenshots are smoke evidence, not a pixel oracle. All 438 root unit tests and 64 EEF tests passed; multi-project build passed. Full standard/reference client suite passed (2m 47s) and final Sodium/reference suite passed (2m 53s). Exact commands and final results are recorded in `BEDROCK_RP_COVERAGE.json` under `static_block_geometry_pass`.

## Remaining gaps / next batch

- Custom block/item gameplay registration and state synchronization are external integration responsibilities.
- State/permutation-driven visuals, `minecraft:transformation`, UV locking, dynamic Molang visibility, n-way rotations, geometry inheritance, all-hidden/empty meshes, poly/texture meshes and authored culling/voxel shapes remain unsupported in this path. Unsupported static bindings publish no replacement, retaining Java/lower resource visuals with diagnostics.
- Automatic GUI fitting, embedded/shelf contexts, older display-transform defaults and independent non-block item definitions/models remain unsupported. Native attachable rendering remains a separate path.
- General aux/weighted atlas selection, built-in Bedrock terrain aliases without authored assets, full material semantics, per-material AO, exact Bedrock lighting and arbitrary shader-pack interoperability are not certified.

Next prioritize a stable Java state-to-Bedrock permutation binding, then world transformation/UV locking and automatic GUI fitting. These should reuse the stack resolver and immutable baked-mesh path rather than inventing pack-specific selectors.

## Official formats consulted

- [Block geometry component](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/blockreference/examples/blockcomponents/minecraftblock_geometry?view=minecraft-bedrock-stable)
- [Material instances](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/blockreference/examples/blockcomponents/minecraftblock_material_instances?view=minecraft-bedrock-stable)
- [Render method and lighting semantics](https://learn.microsoft.com/en-us/minecraft/creator/documents/customblockrenderlighting?view=minecraft-bedrock-stable)
- [Block item visual](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/blockreference/examples/blockcomponents/minecraftblock_item_visual?view=minecraft-bedrock-stable)
- [Authored item display transforms](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/blockreference/examples/itemdisplaytransforms?view=minecraft-bedrock-stable)
- [GUI rotation default correction](https://feedback.minecraft.net/hc/en-us/articles/29461929917069-Minecraft-Beta-Preview-1-21-30-24)
