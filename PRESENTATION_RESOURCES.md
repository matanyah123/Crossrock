# Presentation resources (Minecraft 26.2)

All output is generated in Crossrock's virtual resource pack. Original add-ons are read-only.
Pack ordering and selected subpacks use the existing selection system; changes reload when
leaving the Add-ons screen.

## Supported

- Generic textures/ui/title.png replaces Java's title intact, including integrated edition/
  add-on branding. Artwork is fitted into Java's 256x44 visible window on a padded 256x64
  texture (with high-resolution scaling), so the lower part is not cropped. The extra Java Edition overlay becomes transparent unless the pack supplies
  textures/ui/edition.png. No pack-specific text detection or artwork cropping is used.
- Common Bedrock button artwork maps to Java's normal/highlighted/disabled buttons. Checkboxes,
  slider tracks/handles, close buttons, book/recipe page arrows and recipe filters are mapped.
  Bedrock base_size/nineslice_size metadata is parsed into Java gui.scaling metadata; source
  PNG resolution is retained. Bright generic button backgrounds receive luminance-based
  contrast correction for Java's white/gray labels; dark artwork and other widgets are unchanged.
  Fixed widgets use stretch scaling. Java keeps its own layout,
  input, text and accessibility behavior; arbitrary UI JSON is still not executed.

- Double-slab assignments in Bedrock blocks.json: modern *_double_slab and double_*_slab names,
  including copper naming and normal_stone/smooth_stone aliases. Generated blockstates replace
  only type=double. Java's original bottom/top models are preserved. The bundled state index
  comes from Minecraft 26.2's blockstate assets, including waxed-copper model references.
- Root splashes.json or texts/splashes.json, string entries, UTF-8/format codes, and canMerge.
  Merging appends to lower-priority generated splashes (or vanilla bundled splashes when no
  lower Bedrock pack provided them). It does not merge third-party Java packs' splash files.
  Empty replacement lists intentionally disable ordinary splash texts.
- textures/ui/panorama_0.png through panorama_5.png and panorama_overlay.png.
  Individual faces may override vanilla; PNG panorama faces must be square. Partial overrides
  and full sets are normalized to vanilla 26.2's 1024px cubemap size to avoid a fatal
  mismatched-face reload when multiple Bedrock packs contribute faces.
- Bedrock HUD images: normal, poison, wither, frozen, absorption and vehicle hearts;
  armor, food/hunger, air, experience/jump bars, hotbar selection, optional crosshair image,
  and nine hotbar slot images plus end caps. Experience bars translate Bedrock nine-slice
  metadata; vanilla slice defaults apply when the pack only overrides the PNG.
  High-resolution sprites retain crisp pixels. No implicit animation metadata is generated.
- Static bed client-entity geometry (modern or legacy, with resolved geometry inheritance),
  per-color textures including silver/light_gray, cube/bone transforms whose baked faces remain
  axis aligned, mirrored/per-face UVs, and geometry crossing the head/foot boundary.
  Models target Java 26.2's ordinary color_bed_head/foot resources. The full source sheet is
  used directly; split-face UVs are interpolated instead of stretching the whole bed onto each half.
  Texture-only bed packs retain the existing bed texture splitter.

## Deliberate limits

This is not arbitrary Bedrock UI JSON execution. Touch controls, boss-bar layouts, custom UI
logic, hardcore-only HUD decorations, HDR/alternate panorama selection and platform-conditional
splashes are not implemented. Conditional splash entries are reported, not passed to Java.
Unknown Java slab IDs are skipped.

Beds currently use static geometry and the default geometry alias. Animated geometry, non-axis-
aligned faces, custom material instances and models outside Java's block-model coordinate range
are reported instead of publishing malformed models. Geometry and referenced textures must be
resolvable within the selected Bedrock pack/subpack; cross-pack bed-definition inheritance is not
yet supported. Java blockstate facing/part behavior remains vanilla.

Nine-slice defaults verified against Mojang's bedrock-samples resource_pack/textures/ui/
experiencebarempty.json and experiencebarfull.json. No copyrighted pack content is bundled.

## Test in-game

1. Restart after installing the updated JAR; enable the generated Crossrock resource pack.
2. Select a Bedrock pack/subpack that contains these resources. Exit the Add-ons screen.
3. Check the title panorama and splash. Check HUD health, hunger, armor, air, XP,
   mounted jump charge, hotbar slots and selection.
4. Place one slab, then stack another of the same type. Only the double should use its
   double-slab assignment. Test copper/waxed copper separately if the pack defines both.
5. Place beds in all four directions; inspect both halves, underneath and their item rendering.
   Unsupported bed geometry should produce an Errors entry without breaking resource reload.

Automated coverage includes selected-subpack metadata discovery, splash priority, nine-slice
end-cap preservation, high-resolution hotbars, malformed input isolation, unchanged slab halves,
bed seam UVs/exterior faces, rejection of unsupported geometry, and real-client model baking.
