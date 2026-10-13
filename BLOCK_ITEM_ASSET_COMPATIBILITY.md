# Block and item asset compatibility — 2026-10-13

Historical batch scope: the [state/culling/item continuation](BLOCK_ASSET_STATE_COMPATIBILITY.md) supersedes the remaining-gap statements below for mapped states, world transforms, bounded UV lock, GUI fitting, authored culling and item icon definitions.

This batch extends the existing vanilla texture translator with two executable resource-pack paths. It does not register blocks/items or interpret behavior-pack components.

## Implemented subset

- `textures/item_texture.json`: string and single-element string-array `textures` entries resolve authored PNG/TGA paths to existing item texture mappings. Arbitrary source filenames work; the original Java item model, tinting and display behavior remain active. Selected subpack files and higher-priority translated packs override lower ones through the existing pipeline.
- Root `blocks.json`: existing Minecraft blocks whose bundled Java blockstate has one unrotated model and whose model directly inherits untinted `block/cube_all` or `block/cube` can use scalar or per-face atlas assignments. Explicit faces override `side`; `*` supplies unspecified faces. Each alias must resolve a same-pack scalar terrain texture and an image. Complete world assignments generate a six-face Java cube model with its particle texture.
- `carried_textures` on those cubes produces a separate Java item model; the placed block keeps `textures`. Without a carried declaration, the original Java item definition continues to refer to the world block model. Generated cube models inherit Java's normal block display transforms in GUI, hands, ground and frames. Existing flipbook metadata follows copied cube textures.

No asset names or pack names select these paths. Eligibility is determined from bundled Java state/model structure. Missing aliases/images, incomplete faces, indexed terrain definitions, unknown block IDs and unsupported Java model/state shapes produce no new replacement for that assignment. Java state/geometry is retained for unsupported shapes. Stack resolution is still per translated pack: cross-pack atlas/image references and removal of lower generated declarations are not implemented.

## Validation

`BedrockTranslationPipelineTest` adds three tests covering item aliases and aux fallback; cube per-face/wildcard/carried output and unsupported shape fallback; selected subpack plus higher-pack priority. `BlockItemAssetChecks` runs inside the full client suite and checks actual baked world/carried models, GUI/first-person/third-person/ground/fixed item contexts, loaded icon pixels and vanilla fallback for a missing block atlas alias.

See `BEDROCK_RP_COVERAGE.json` → `block_item_asset_pass` for commands, results and logs. Passing model checks establish working Java rendering paths; they do not establish Bedrock visual parity.

## Subsequent static geometry pass

The later [static geometry/material/display batch](BLOCK_GEOMETRY_COMPATIBILITY.md) adds cross-pack static block geometry bindings, material methods, independent block-item visuals and eight authored display contexts. It supersedes the corresponding unsupported statements in the original batch below. General non-block item definitions and atlas index/weighted selectors remain unsupported.

## Original batch remaining work

Multi-index/aux and weighted atlas selectors, shared pack-stack atlas resolution, legacy RP item definitions, custom item geometry and authored `item_display_transforms` remain unsupported. Rotated/multipart/tinted block models, custom block geometry/material bindings, culling, sounds and isotropic rendering are outside this batch. Existing specialized block translators remain available separately.

Next: introduce a shared atlas resolver with explicit index selection and pack-stack provenance, then connect legacy item presentation definitions to already-existing Java item identities. Custom geometry/display transforms need a separate, tested binding path; do not flatten arbitrary Bedrock bones into a vanilla cube.

## Official formats

- [Microsoft blocks.json reference](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/blockreference/examples/blocksjsonfilestructure?view=minecraft-bedrock-stable): face/default assignments and carried textures.
- [Mojang item atlas schema](https://github.com/Mojang/bedrock-schemas/blob/main/schemas/rp/textures/item_texture.schema.json) and [official item atlas examples](https://github.com/Mojang/bedrock-samples/blob/main/resource_pack/textures/item_texture.json): alias-to-image definitions; indexed forms remain bounded as described above.
