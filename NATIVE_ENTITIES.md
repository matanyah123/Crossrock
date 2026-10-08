# Native Bedrock entities — implementation status

This is a native runtime, not EMF/ETF conversion. It is **not complete arbitrary
Bedrock compatibility**. Original packs remain unchanged. Resources are exposed
through the generated virtual pack, not written into the user's resourcepacks folder.

## Connected end to end

- Selected pack ordering/subpack overrides, atomic reload snapshots, private native
  resources, PNG/RLE-TGA decoding, material inheritance and entity pack-setting queries.
- Client-entity aliases, initialize/pre-animation/animate scripts, conditional render
  controllers and per-entity variables.
- Render-controller geometry, texture and material arrays resolve in separate resource
  scopes, including reused array names and nested indexed expressions. Selection restores
  caller bindings on success or failure. Unambiguous arrays remain available to visibility
  comparisons; ambiguous names in untyped expressions are not guessed. This does not yet
  establish type inference in such expressions. Bare array members concatenate in order,
  preserving duplicates and lazy per-entity leaf expressions, with forward references and
  empty members. Missing/cyclic references, depth over 64, over 4,096 definitions or
  65,536 raw/expanded members per resource type are isolated as resource errors.
- Molang arithmetic, short-circuit conditions, blocks, assignment, strings, structs,
  seeded random functions, scoped queries and per-channel `this`; bounded lazy
  loops/for_each with break/continue and optional value-only entity-context arrows.
- Hierarchical geometry, bone/cube pivots, inflation, signed/mirrored/rotated UVs,
  animated locators and native hand-item anchors.
- Legacy `geometry.child:geometry.parent` identifiers publish under the canonical
  child ID, then resolve against winning definitions across files/packs at reload.
  Bone fields inherit by name; explicit arrays replace rather than duplicate the
  parent's arrays, and unchanged bones remain available. Mixed legacy/modern texture
  dimensions are normalized before merging. Legacy `neverRender` hides a bone's own
  mesh while keeping its hierarchy and locators usable.
  Missing parents, cycles, depth over 64 and inherited mesh/bone budgets are isolated
  as resource errors. No missing vanilla parent geometry is invented by this resolver.
- Indexed and sequential (`tri_list`/`quad_list`) polygon meshes share the animated
  hierarchy with cubes. Pixel/normalized UVs and per-corner smooth normals are retained;
  inverse-transpose normal transforms handle nonuniform scale. Invalid indices and
  aggregate mesh budgets are checked before rendering.
- Bedrock-handed Euler rotations, entity-relative animation rotations, model-space
  bone TRS queries, and native-only orientation/scale (no duplicate Java sleep pose).
- Animation clocks in seconds, linear/Catmull-Rom keyframes, pre/post discontinuities,
  custom clocks, loop/start delays, weights, overrides and timeline dispatch.
  `query.anim_time` is scoped to the active clip/timeline; entity initialize,
  pre-animation and scale scripts receive zero outside those scopes, not entity age.
- Nested controllers with independent clocks, ordered transitions, entry/exit scripts,
  completion queries, numeric/custom-curve blending and scoped variable remap curves.
  Duplicate extraction timestamps do not repeat effects or transitions.
- Colors, UV transforms, hurt/fire overlays, full brightness, scalar/axis model scale,
  common material alpha/cull/emissive flags and geometry outlines.
- Two-texture `MASKED_MULTITEXTURE` materials compose one texture instead of drawing
  two overlapping meshes. Transparent nonblack mask texels preserve the base;
  transparent black cuts it out; opaque mask pixels replace it. Inherited/custom
  material defines activate this generically. Point-sampled sheets of different
  dimensions share a bounded common sampling grid. Runtime composites are cached
  (256 pairs/16 million pixels maximum), released on reload, and never written into
  source packs. This does not implement three-texture color masks/dye tinting or
  access to dynamic downloaded skin pixels through the resource manager.
- Living and non-living render-state extraction. Untouched entities retain the existing
  renderer. Name tags, leash, shadow and fire submissions remain intact.
- Java wood-specific boats/rafts resolve shared Bedrock boat/chest-boat definitions,
  preserving wood variants and Bedrock-facing yaw. Passenger snapshots expose indexed
  rider rotations and vehicle rotations without retaining live entities in submissions.
  Riding-type queries accept edition aliases, including bamboo rafts.
  Partial-tick alpha, time of day, movement distance, body pitch/yaw speed,
  swimming/sprinting, water/lava, invisibility and spectator facts are shared between
  living and non-living extraction rather than silently missing on vehicles/projectiles.
  Vehicles expose the raw hurt countdown in ticks and hurt direction; structural
  integrity is adapted as `40 - Java damage`, retaining fractional damage. Pack
  scripts perform their own partial-tick subtraction, without a second interpolation
  or conversion to seconds in the input adapter.
- Names, biome/tags, equipment names/tags, camera, movement, health and several
  species-specific facts. Unsupported queries are reported and return Molang error
  value zero in the renderer; core evaluators remain strict in tests.
  Cat coats map registry names to Bedrock's eleven variant IDs, not Java's registry
  order. Sitting reads synchronized tameable/fox/general pose state, while sneaking
  includes the entity's crouching pose rather than only a player's shift input.
  Cats expose their synchronized lying flag as `query.is_laying_down`. These are
  read-only adapters: they do not alter vanilla AI or pet interactions.
- Sound timeline/controller actions resolve translated native events. Particle actions
  currently report an unsupported-emitter diagnostic.
  Zero-weight clips cannot erase active bones or emit sounds; entity-local sound
  overrides stay scoped to that species, and volume-zero vanilla events are muted.
- Recognized Java texture and conventional EMF/OptiFine model/properties source-pack
  priorities, without using either mod's API. CEM aliases, fish shapes/puff states,
  age/climate models, equipment layers and vehicle fallbacks are resolved from vanilla
  entity state. A small-only armor-stand model does not suppress an adult native stand.
  CEM/random-texture directories affect priority only when their runtime provider is
  installed; an otherwise inactive Java pack cannot disable native models this way.
  Non-living texture priority uses actual 26.2 boat, minecart, arrow/tipped/spectral
  arrow, trident, crystal, hook, knot, orb, shulker-bullet and wind-charge paths instead
  of guessed entity-ID filenames. Other non-living renderer paths still need adapters.

## Armor stands

A winning pack touching the standard armor-stand texture, geometry or client entity
activates compatibility. Integrated servers follow the host's resolved pack stack.
Dedicated servers aggregate opt-in from connected modded clients, debounced to one
policy update per second. Administrators can override this with
`config/bedrock_compat-server.json`: `{"armor_stands":"auto"}`, `enabled` or `disabled`.
This is a world-wide gameplay policy, not per-client authoritative inventories.
Unmodded servers cannot provide this authority.

Activated stands have server-side arms. Sneak/main-hand interaction cycles 13 indices.
`bedrock_compat:pose_index` is saved separately from Java `Pose`, synchronized to
modded tracking clients, and supplied as `variable.armor_stand.pose_index`.
Java pose values are preserved. Deactivation restores the prior arms flag.
Regular item interactions remain vanilla. Clients cannot assign arbitrary entity IDs,
pose NBT or inventories through this mod's payloads.

## Verification

`./gradlew :test :build` runs core regression tests and builds the root mod.
`./gradlew :runClientGameTest` exercises an isolated real client/integrated server:
native living/non-living meshes, arms, sneak cycling, /data synchronization, preserved
Java poses, resource ordering and pack removal. Test assets stay in
`build/run/clientGameTest`.

Polygon regression tests cover indexed/sequential topology, UV units, hierarchy,
nonuniform/reflected/singular scales, smooth normals, visibility, camera reflection
and invalid input budgets. The real-client fixture renders a double-sided polygon
triangle on a non-living entity alongside a cube-model armor stand; this checks the
resource-to-submission path, not exhaustive historical polygon-pack equivalence.

Boat fixtures exercise ordinary/chest boats and bamboo rafts, variant-dependent
animation, passenger visibility and wood-specific Java texture priority. A Java arrow
texture above/below the virtual pack also checks projectile priority in the real
client. Unit tests distinguish normal/tipped/spectral arrows and shared minecart paths.
This does not provide the missing built-in Bedrock boat paddling animation library or
prove rail-aligned minecart animation parity.
Cat regressions summon all eleven Java coats and verify native texture-array
selection plus variant-driven mesh offsets. Server-set sitting and lying flags are
synchronized to the client, exercised through Molang mesh transforms and then
cleared to check that animations do not retain stale pose state. Crouching is
sampled through an isolated client view (Java AI owns and can reset that pose). The mapping
uses [Mojang's cat variants](https://github.com/Mojang/bedrock-samples/blob/main/behavior_pack/entities/cat.json)
and [texture array order](https://github.com/Mojang/bedrock-samples/blob/main/resource_pack/render_controllers/cat.render_controllers.json).
Unknown modded coat keys produce a diagnostic rather than a fabricated edition ID.
The vehicle damage fixture samples three boats and a minecart at a quarter tick,
with a ten-tick countdown, fractional damage and negative hurt direction. The
minecart fixture computes its countdown in pre-animation like the
[Mojang minecart client entity](https://github.com/Mojang/bedrock-samples/blob/main/resource_pack/entity/minecart.entity.json);
the [minecart animation](https://github.com/Mojang/bedrock-samples/blob/main/resource_pack/animations/minecart.animation.json)
uses `40 - structural_integrity` as damage amplitude. These tests verify inputs
reach mesh transforms; missing engine rail variables/built-in clips remain separate.
The shared-input regression animates a non-living marker using `query.frame_alpha`
at a quarter tick through an independent view clock. Unit tests verify initialize,
pre-animation and post-clip clock isolation while clips and timelines retain their
own times. Query semantics follow the [Molang reference](https://github.com/MicrosoftDocs/minecraft-creator/blob/main/creator/Reference/Content/MolangReference/Examples/MolangConcepts/QueryFunctions.md).
Masked-texture unit/client fixtures cover cutout/preserve/replace pixels, fractional
alpha, custom inherited defines, different sheet dimensions, cache reuse, reload
image disposal and Java projectile texture priority. The masking rule follows the
legacy formula documented in the [OSBES entity shader](https://github.com/jebbyk/OSBES-minecraft-bedrock-edition-shader/blob/develop/shaders/glsl/entity.fragment)
and texture ordering in [Mojang's villager render controllers](https://github.com/Mojang/bedrock-samples/blob/main/resource_pack/render_controllers/villager_v2.render_controllers.json).
This is not yet a pixel-for-pixel comparison against Bedrock's current renderer.
The October 8 reference run also renders A&S 1.12's minecart with EMF/ETF installed;
the native-frame assertion and screenshot verify model loading, not every rail pose.

Legacy inheritance regressions cover cross-file/forward references, property/array
overrides, dimensions, non-mutating resolution, hidden anchors, cycle/missing-parent
isolation and aggregate/depth limits. The client fixture extends a separately loaded
polygon parent with child cubes and verifies that a hidden legacy bone emits no mesh.
Legacy structure follows the [1.8 geometry schema](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/visualreference/geometry.v1.8.0)
and the [GoMint legacy humanoid example](https://github.com/gomint/gomint/blob/master/gomint-server/src/main/resources/geometry.humanoid.custom.json).

CEM naming follows the [OptiFine model-format reference](https://github.com/sp614x/optifine/blob/master/OptiFineDoc/doc/cem_model.txt).
The client priority test also places a small-only CEM above and below the generated
pack: adult stands remain native, small stands defer only when the provider is
installed and its pack is above. Arbitrary `.properties` rule conditions are not
interpreted by this priority adapter yet.

Optional read-only reference inputs:

```
BEDROCK_REFERENCE_PACK=/absolute/path/reference.mcpack
BEDROCK_REFERENCE_ENTITIES=cow,pig,zombie,villager,creeper,skeleton,spider,chicken,sheep
```

`-PnativeEntityCompatibilityMods=/absolute/emf.jar:/absolute/etf.jar` loads existing
development-client dependencies only; neither mod is bundled or required.
In-game tests have passed with EMF 3.2.6 and ETF 7.1.1. The reference A&S armor stand
visibly uses its Bedrock doll model, including a held sword. Nine additional reference
entity types produced native frames. This does not prove every animation or visual
equivalence, nor dedicated-server synchronization.

The October 8 regression run also checks typed/pasted `§`, and captures A&S 1.12's
fisherman hat, sleeping villager, zombie, skeleton and cat with EMF/ETF installed.
The fisherman brim remains at head height and the villager lies on the bed.
These are targeted checks, not exhaustive animation or sound-variant coverage.

## Formatting input

`§` can be typed and pasted into Java text fields (Option+6 on standard Mac layouts).
Only this character is added to the allowed set; control characters stay blocked.
The same check is patched on servers running this mod. Remote unmodded servers or
plugins can still reject formatting, so this does not bypass their validation rules.

## Native players

Winning `minecraft:player` definitions use the same native geometry, Molang,
animation-controller and render-controller pipeline as other entities. Morphs are
render-controller geometry selections, not built-in lists of forms or pack names.
There is no change to player collision, inventory, server entity type or gameplay.

Engine inputs include attack progress, view mode, pitch, swimming/gliding, item-use
duration, flying, crawling, scoping and equipment. Standard Steve/Alex texture aliases
bind the actual Java player's skin. Custom texture aliases remain pack-owned. Wide
and slim procedural humanoid meshes and basic player render controllers fill missing
engine definitions; pack-supplied definitions take precedence. Cape textures can bind
the player's runtime cape. This is not a complete Bedrock built-in animation library.

World, first-person and explicitly requested UI extraction have independent variable
and animation state. Provider inputs are frame-scoped overlays: unregistering a
provider restores pack state rather than leaving a morph stuck active. The camera
adapter reflects Bedrock depth with matching winding/normal changes. The hand pass
uses first-person pack controllers; vanilla held
items/maps remain on their original render path, without duplicate native attachments.
Native third-person player frames suppress Java model feature layers, as other native
entities do. Native armor and custom attachables are still missing, not implicitly
provided by that layer suppression. UI view extraction is exposed but not yet wired
to every inventory/preview screen.

`NativePlayerAnimationApi.register(InputProvider)` lets optional client mods provide
value-only variables and queries (for example `is_emoting`), without choosing pack
models or animations. Close the returned registration to remove it. These callbacks
run during extraction, never during mesh submission. Invalid provider data is isolated
and diagnosed. The API does not invent an emote trigger or synchronize custom states.

Relative-block any/all tag queries observe loaded client blocks within 128 blocks,
add offsets to entity position before flooring, and never load chunks. Tags currently
resolve against Java block tags; arbitrary Bedrock-only tag definitions still need
translation. Camera distance interpolation and root-locator offset queries are also
available. Missing client facts (including unsynchronized kinetic-hit timestamps)
remain diagnosed instead of being claimed as implemented.

Player regression tests exercise actual skin bindings, animated arms, first-person
visibility, provider-driven pack geometry selection, block-tag observations, and
restoration of vanilla players after removing the pack. Reference A&S 1.12 tests run
read-only against the original archive with EMF/ETF installed. Mesh-presence checks
alone do not establish complete player animation or morph parity.

## Still incomplete

- Full Molang/query/species-property coverage and missing referenced vanilla Bedrock
  definitions; exhaustive historical geometry inheritance semantics and voxelized `texture_meshes`.
- Texture compositing/custom material shaders, native armor/custom attachables,
  advanced render layers and particle emitter runtime.
- Exhaustive animation relative-space, event/interpolation and sound-variant parity.
- Arbitrary custom model names/random-rule condition evaluation, complete player/first-person parity, non-living
  orientation adapters and exhaustive coexistence validation.
- Dedicated-server runtime tests, sync for facts not reliably visible to clients,
  and capability reporting for unmodded servers.

Unsupported definitions are isolated and reported instead of aborting resource reload.
Parsing counts and mesh-presence checks are not evidence of complete pack support.
This development milestone must not be described as “100% supported.”
