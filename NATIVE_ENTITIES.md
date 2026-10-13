# Native Bedrock entities — implementation status

This is a native runtime, not EMF/ETF conversion. It is **not complete arbitrary
Bedrock compatibility**. Original packs remain unchanged. Resources are exposed
through the generated virtual pack, not written into the user's resourcepacks folder.

## Connected end to end

- Emissive cutout alpha is distinct from Java blend opacity: nonzero emissive pixels
  remain visible, including 1/255-alpha symbols; zero-alpha cutouts remain transparent.
  Inherited `ALPHA_TEST` is preserved, and generated opacity textures are bounded and
  released on reload. Unit tests and the client renderer fixture cover this regression.

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
  start client-ticked native emitters at animated entity/attachable locators. Imported
  particle definitions and textures share the normal resource reload and pack priority.
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

Native humanoids, including players and armor stands, render equipped helmet, chestplate, leggings and boots using
Java equipment assets attached to the animated semantic head/body/arm/leg bones.
The normal equipment renderer retains dye, trim and glint behavior. Hand item
models follow rightitem/leftitem anchors (including locators), or standard arm
anchors when item anchors are absent. Controller-hidden bones hide their attached
equipment too. No displaced vanilla entity feature layers are enabled. Standard
chain, diamond, gold, iron and netherite Bedrock armor atlases map to Java's humanoid
and leggings textures; leather dye-mask conversion is not implemented.

Client game tests cover all four armor slots, both hands, hidden anchors, and the
original read-only A&S 1.12 archive. This is an equipment attachment foundation,
not complete pack-specific prop parity.

### Native held attachables

`attachables/*.json` are now published independently from client entity definitions,
including selected-subpack and selected-pack priority. Item identifiers and optional
Molang owner conditions resolve the resource-owned model; filenames and pack names
are not selectors. Native entities with `enable_attachables` use the attachable's
own geometry, materials, texture, render controllers and animation graph in world/UI
views. A successful native item replaces, rather than duplicates, the Java hand item.
Missing/unsupported attachable assets are isolated, diagnosed, and retain the Java
hand-item fallback. First-person attachables are not wired into this path yet.

Model `binding` expressions resolve contextual `context.item_slot` through
`query.item_slot_to_bone_name`, using the holder's actual right/left item bones and
handedness. Authored root pivots are rebased once onto those animated anchors, with
the complete hierarchy preserved. Holder mesh visibility does not suppress independent
equipment; zero-scale anchors still collapse their prop branches safely. Each entity/view/slot has its
own animation state; item changes reset that slot, removal clears it, and reloads
invalidate all instances. Evaluated query adapters are released after extraction
to avoid retaining live entities through weak-key cache values.

Tests cover owner/item selection, namespace collisions, selected-pack replacement,
binding pivots/descendants, hidden branches, animation advancement, native-vs-Java
hand exclusivity and unequipping. A real-client fixture renders an animated magenta
prop from an unmodified-format Bedrock attachable on an equipped armor stand.
Holder setup, copied bones, masked materials and worn attachables are supported as
described below. Attachable effects remain unsupported and diagnosed; these tests
are not claims of full A&S prop support.

## Verification

`./gradlew :test :build` runs core regression tests and builds the root mod.
`./gradlew :runClientGameTest` exercises an isolated real client/integrated server:
native living/non-living meshes, arms, sneak cycling, /data synchronization, preserved
Java poses, resource ordering and pack removal. Test assets stay in
`build/run/clientGameTest`.

Armor-stand persistence checks use the real Minecraft entity save/load codecs and
compressed binary NBT, including the compatibility mixins. They preserve all six
Java limb rotations and the independent Bedrock pose across active and inactive
re-saves, restore both possible original arms flags when loading under a disabled
policy, and reactivate saved poses when support returns. Untouched stands must not
gain compatibility NBT. These checks use an integrated server; they do not prove
dedicated-server policy negotiation or a full world/chunk restart round trip.

Animation channels accept scalar, single-component-array and three-component-array
values, including discontinuous keyframe `pre`/`post` values. Each sampled channel
supplies its own `query.key_frame_lerp_time` (and the historical
`global.key_frame_lerp_time` spelling) to expressions; the ratio is scoped and
restored rather than stored as a shared entity query. Unit checks exercise linear
and Catmull-Rom evaluation, per-axis `this`, channel isolation and error cleanup.
The real-client boat fixture verifies a fixed-time keyframe expression reaches
the submitted native mesh without being treated as an unsupported client fact.
Controller-local pose accumulation also carries override metadata through nested
controllers: later clips evaluate `this` against the bind pose plus replacement
deltas, not an already-replaced outer animation. Other bones retain their inherited
transforms. Tests cover position, scale, bind rotation, zero-weight overrides and
the resulting real-client boat mesh. This does not yet prove exhaustive override
semantics during cross-fades between controller states.

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
adapter applies the camera-facing model transform. The hand pass
uses first-person pack controllers and native held attachables without Punchy;
items/maps without an applicable native attachable retain their Java render path.
Native third-person player frames suppress Java model feature layers, as other native
entities do. Third-person native attachables include armor, with Java equipment fallback
on native humanoid bones. UI view extraction is exposed but not yet wired
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

### Armor-stand attachables

Native equipment evaluates resource-authored `parent_setup` in the holder's variable
scope each evaluated frame, after input refresh and before animation and
render-controller selection. Child scripts access the live holder Molang scope via
`c.owning_entity`, scoped to extraction so no entity/world references are retained.
Hands and all four armor slots support explicit bone bindings (including unbound
scaffold roots), legacy copied-holder bones, masked materials, and emissive cutouts.
Explicit bindings align the authored bone pivot with the animated holder anchor.
There is no fixed Y=24 correction: zero-pivot props and independently pivoted tools
use the same pivot-local transform without a global translation.
Copied skeleton bones keep their separate pivot-local
alignment and rest-rotation cancellation. This applies to every native holder and
view, not a list of particular tools or packs.
Texture meshes voxelize alpha-bearing item textures with authored transforms and
bindings, including pixel-depth control. Enchanted armor materials use Java's glint
pass; this does not reproduce arbitrary Bedrock shader programs.
Texture-mesh Y offsets are downward from the owning bone pivot, with extrusion
below the texture plane. Bound texture meshes use the same pivot-local binding as
cube geometry; mixed cube/texture items are layered separately.
Java equipment fallback is suppressed only for slots resolved by a native attachable.
Item-name queries translate edition aliases such as `glow_item_frame`/`glow_frame`;
vanilla armor/tier/harness tags supplement Java registry tags. These are edition
adapters, not rules for particular pack names. Control-name display modes `§§` and
`§§§§` suppress the entire Java nameplate only when the native pack owns the stand.
Control names do not add a world-space height offset: the pack's model and animation
transforms own display placement. Entity position, NBT and hitboxes remain unchanged.
Resource scripts still determine prop selection and react to the synchronized pose.

Legacy block-item names (including Bedrock `web`, `magma`, and `lit_pumpkin`)
resolve against Java items for both prop queries and attachable selection.
Sulfur cubes expose their synchronized BODY item as Bedrock main-hand equipment,
their registered archetype (or `none` when empty), and their fuse state. This lets
authored controllers retain the inner core and choose custom swallowed-block models.
When scripts do not hide held items, swallowed blocks use Java's resolved block
model, including block-state components and resource-pack overrides, at the animated
item anchor. `hide_held_items` prevents duplicating pack-owned replacement geometry.

Native mesh submission preserves authored model X, reflects renderer Y and reverses
winding accordingly. The first-person camera pass adds its own facing transform.
Java fallback equipment and held items use the same corrected anchor space.

Stencil-authored rectangular portal apertures are implemented by clipping geometry
in model space to the eye-to-opening cone. Material inheritance retains stencil
references, read masks, equality tests, replacement writes and color-write suppression.
Masked-reference promotion passes can connect the aperture stamp to interior readers.
Not-equal outline readers subtract the projected silhouettes of preceding matching
body stamps, keeping emissive shells from painting over the textured entity.
Controllers without explicit material rules inherit the client entity or attachable's
default material, including emissive spawn-egg models.
Holdout geometry is not submitted as visible color. Aperture-clipped interior readers
use a depth-independent color pass so terrain behind the opening cannot occlude the
virtual room; this pass does not write virtual-room depth into the world buffer.
This is not a general GPU stencil
interpreter: multiple/disjoint or non-convex apertures, arbitrary stencil arithmetic,
depth-only occlusion effects and asymmetric face tests still need additional support.
The aperture implementation uses the largest authored mask face, not pack identifiers
or bone-name heuristics; each entity's masks are isolated from other mods' draws.

Regression fixtures exercise parent setup, owner-variable reads, copied bones,
binding scaffold roots, item changes, both hands, armor fallback and pose persistence.
The optional reference test covers both special-name modes with stone, diamond sword,
glow item frame, diamond helmet and wheat through all 13 synchronized pose indices. This
targeted matrix is not proof of every item, shader or animation in arbitrary packs.

Native living rendering preserves the engine's age scale independently of pack scale.
Client-observed position changes drive distance and speed queries, rather than relying
on remote entities' server-side movement counters. The normalized Bedrock day starts
at midnight (Java clock +6000 ticks), and `time_stamp` exposes elapsed world ticks,
independent of `/time set` and a paused daylight cycle. Engine-owned elytra geometry,
materials and default poses satisfy vanilla references without bundling pack assets;
pack-authored replacements retain precedence.
Clip driver clocks remain separate from wrapped sampling phases; old render observations
reuse controller samples without replaying transitions. Molang supports the in/out/in-out
quad, cubic, quart, quint, sine, expo, circ, back, elastic and bounce easing families.
Emissive cutout materials remain depth-writing cutouts, but now preserve the texture's
per-pixel emission mask instead of making the whole mesh full-bright. Transparent-black
emissive texels cut out; colored zero-alpha texels emit. Material inheritance also preserves
source-alpha versus one/one additive blending, culling on translucent draws, depth-write
control, and point/bilinear and repeat/clamp sampling. Dye-mask materials turn alpha into
color coverage rather than cutting away low-alpha face pixels; sheep wool uses its dye color.
Unit regressions cover long absolute clocks and repeated timestamps;
the optional client audit also captures baby villager/zombie rendering.
Complex Molang scripts return zero unless they execute an explicit `return`; this
keeps side-effect-only channel scripts from becoming accidental bone translations.
Sleeping facing omits the standing model's half-turn, leaving the authored lying-down
clip aligned with the bed direction. The reference audit captures a falling right-hand
player holding a native bucket attachable and a sleeping villager.

Player view corrections: head yaw is adapted from Java's relative head/body fields
to Bedrock world yaw, while target yaw remains relative. Inventory entity previews
evaluate after mouse-following rotations, in an isolated UI instance and clock.
Hand state extraction no longer advances the world instance as a side effect.
Pre-animation delta time comes from the individual view's evaluation interval.
Armor-presence queries use Bedrock slot order. Invalid numeric Molang math results
yield zero rather than persisting NaN/Infinity in smoothing variables. Reference
screenshots cover first-person hands, falling, netherite equipment and inventory;
these smoke checks do not establish exhaustive motion or facial-animation parity.

Animation-channel `this` includes the bone's parent-relative bind translation,
including helper bones supplied by additional geometry layers. The renderer still
receives animation deltas, so bind origins are not applied twice. Expression-bearing
bones retain their authored evaluation order for variable dependencies. Synthetic
paired-eye/mouth tests cover neutral placement and asymmetric movement; the optional
reference game test also captures a close-up using `CROSSROCK_TEST_SKIN`.

Downloaded Java skin preprocessing now forces opacity only on actual base-layer
cube faces, preserving the authored alpha in unused atlas padding. Native facial
overlays may sample that padding; Java's rectangular opacity fills previously made
transparent texels black. Visible base faces remain opaque, overlay layers and RGB
data are unchanged, and no pack-specific bone names or settings are overridden.
The client regression exercises the real skin loader with transparent and partially
transparent padding. `CROSSROCK_TEST_SKIN` optionally supplies a local custom skin
for the reference-pack screenshots without bundling it in the mod or test assets.

### Biome-driven entity variants

Entity biome identifier/tag queries now bridge Java's biome registry and Bedrock's
legacy identifiers and semantic tags (for example `mesa`, `jungle`, `cold`, and
`nether`). Original namespaced Java/datapack tags remain available, and custom
namespaces are never classified just by a biome's name. Imported `minecraft:biome`
tag metadata joins the query tags without registering/changing world biomes.
The mappings follow the vanilla vocabulary documented in
[Microsoft's biome tag reference](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/entityreference/examples/definitions/nestedtables/biome_filter?view=minecraft-bedrock-stable).

Textures, geometry and animation selection still run through the pack's render
controllers and scripts, including its own setting conditions. No variant setting
or pack-specific variable is forced on. Packs that select a spawn variant in
`initialize` retain that choice; packs using `pre_animation` can update on biome
changes. Tests exercise warm/default textures and geometry with settings on/off,
and a real client fixture changes biome and verifies imported tag metadata.

### Native particles

Particle JSON and atlas sprites are imported during resource reload, not menu open.
Animation/controller events start bounded, independently ticked emitters; locators
inherit animated bones and attachable bindings. Supported components include once,
looping and expression lifetimes, instant/steady rates, point/box/sphere/disc/custom/
entity-AABB shapes, initial speed/spin, dynamic and parametric motion, local space,
block expiry, basic collision/bounce, kill planes, Molang curves, color gradients,
flipbooks, lighting and additive/alpha/opaque billboards. Lifetime events can run
expressions and spawn child effects. Effects without billboard appearance remain
invisible while still running their events. Emitters and particles have safety caps
and reset on world change/resource reload.

Known Java particle IDs map to their Bedrock counterpart only when that definition
is imported. A higher-priority Java particle declaration keeps priority; otherwise
the imported effect replaces the Java particle rather than layering a duplicate.
An entity's semantic `fire` alias suppresses the Java flame overlay only while its
native emitter is active; extinguishing removes that emitter. Missing definitions
leave vanilla behavior intact. No resource-pack-specific identifiers are built in.

Remaining parity limitations: rectangular billboard aspect ratios currently use
Java's square-quad batch; manual emission, custom particle shaders and every historical facing-mode nuance
are not fully implemented. Native first-person locators use entity world space,
not a separate camera-space particle pass. Parsing coverage is not visual parity.

- Full Molang/query/species-property coverage and missing referenced vanilla Bedrock
  definitions; exhaustive historical geometry inheritance semantics.
- Texture compositing/custom material shaders, native player armor/custom attachables,
  advanced render layers and exhaustive particle component parity.
- Exhaustive animation relative-space, event/interpolation and sound-variant parity.
- Arbitrary custom model names/random-rule condition evaluation, complete player/first-person parity, non-living
  orientation adapters and exhaustive coexistence validation.
- Dedicated-server runtime tests, sync for facts not reliably visible to clients,
  and capability reporting for unmodded servers.

Unsupported definitions are isolated and reported instead of aborting resource reload.
Parsing counts and mesh-presence checks are not evidence of complete pack support.
This development milestone must not be described as “100% supported.”

The engine supplies a fallback `animation.player.sleeping` when a pack references
that vanilla clip without providing it. Bed orientation is owned by the native
living-renderer frame; pack-authored sleep clips override the fallback. Living
entity queries also expose `sleep_rotation` for custom clips.

Player position and relative-block queries share Bedrock's fixed standing origin
(Java feet + 1.62), independently of camera height and rendering transforms.
Bedrock axe/hoe/pickaxe/shovel destructibility tags map to Java mineable tags.
The imported balancing trigger is tested on fences, walls, horizontal end rods
and iron bars, including returning to a full block and disabling the pack setting.

Slime queries expose Java's actual slime size as the Bedrock variant, and Wither
queries expose its spawn invulnerability timer and shield state. Attachable bone
TRS queries include current holder bindings so physics clips observe the same
coordinate space as the rendered item. Enchanted attachables receive both the
query and engine variable; material GLINT defines support inheritance/removal.
Equal-depth material passes preserve masks rather than exposing hidden variant
geometry (including the unnamed Warden's William-only parts).
Bedrock explosion sheets are split into Java's sixteen explosion sprites; the
ordinary Java explosion emitter then uses the imported animation frames.

Masked villager and zombie-villager head/hat materials use alpha testing, including
their inherited fancy/non-fancy variants, so transparent black pixels are cut out.
Disabled animation branches do not execute physics scripts or mutate shared Molang
temporaries. Input-driven controller transitions select the incoming state before
sampling its clips; alternating physics states integrate once per rendered frame.
Fresh clips receive the actual frame delta even when their state clock starts at zero.
Replacing a Molang struct clears old flat field defaults, allowing bone TRS snapshots
to replace initialized zero coordinates. The imported-pack game test checks bell
pitch response, finite chain meshes, and unemployed/farmer hats in four biome types.

Resource Packs now opens Minecraft's original Java pack screen with Java/Bedrock
tabs. Both tabs retain pending edits; navigation and pack configuration do not
reload resources. Done or Escape commits the combined session, with at most one
reload when its selection or Bedrock configuration changed. The title-screen
Add-ons shortcut is removed. Pack settings use separate labels, checkboxes for
toggles/multi-select choices, numeric sliders, and keyboard-accessible dropdowns
for single choices/subpacks. Client tests cover navigation, retained Java edits,
one-reload commits, responsive labels, and saved configuration choices.
Generation runs when the resource repository opens its complete selected pack
list for reload, not on individual `Pack.open` calls (which vanilla also makes
to display icons). Both primary and full individual opens reuse the last snapshot.
