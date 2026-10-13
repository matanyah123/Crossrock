# Crossrock environment compatibility

Scoped implementation pass, 2026-10-12; current-status correction, 2026-10-13. This is a classic Java renderer adapter, **not a Vibrant Visuals renderer or a claim of Bedrock visual parity**.

Latest validation (2026-10-13): 427 unit tests, 426 passed, one reference-pack audit skipped, zero failures/errors. Full generated-fixture client suite passed in 2m 23s (`/tmp/crossrock-water-fix-test.log`). Water/foliage RGB overrides and sky/fog blending preserve Java alpha. Latest run did not include Actions & Stuff, Sodium or Iris; earlier runs below are historical.

## Coverage and counting

Environment breadth increases from **0/17 to 12/17 (70.6%)**, counting a contract once when any documented subset has an executable visual/audio path. All 12 remain **Partial**. Five environment contracts remain unsupported. One advanced-rendering contract has a bounded atmosphere color approximation; no advanced renderer is implemented. Targeted validation counts passing behavioral subsets, not complete contracts or successful parsing. Final run evidence and exact totals are recorded in `BEDROCK_RP_COVERAGE.json`.

Targeted behavioral coverage increases from **0/17 to 12/17**: 11/17 have targeted unit subsets and 10/17 have live client assertions, with overlap. Music relies on live layer assertions; lighting preservation checks receive no implementation credit rather than metadata-only unit credit. Certified complete remains **0/17**.

Historical environment-pass results: **327 unit tests passed**, zero failures/errors/skips. The full client/integrated-server suite with read-only Actions & Stuff passed (2m 22s); the Sodium 0.9.2-alpha.4 mc26.2 full suite passed (2m 10s); the focused Actions & Stuff materials/trails suite passed (1m 33s). Iris was unavailable and its shader-enabled behavior is untested. Noon and underwater diagnostic captures were inspected; no Bedrock visual/audio oracle was available. Initial audio-fixture iterations exposed a missing OGG and a test-thread access error; both were corrected before the final passing run.

Final command:

```sh
BEDROCK_ENVIRONMENT_SCREENSHOTS=/tmp/crossrock-rp-audit-G6didE/environment-captures \
BEDROCK_REFERENCE_PACK='<installed read-only Actions and Stuff 1.12.mcpack>' \
./gradlew -I /tmp/crossrock-rp-audit-G6didE/audit-build.gradle :test :runClientGameTest --rerun-tasks --console=plain
```

Logs: `/tmp/crossrock-rp-audit-G6didE/environment-final.log`, `environment-sodium.log`, `environment-materials.log`. Optional interoperability jars are test runtime dependencies only, never bundled.

## Architecture

`BedrockEnvironmentTranslator` publishes identity-keyed environment resources through the existing generated-pack pipeline. Higher selected packs win, selected subpacks replace base documents, and modern client-biome definitions replace legacy definitions within the same pack. A higher legacy definition also replaces a lower modern definition. Global VV documents use reserved filename identity, not arbitrary authored identifier identity. Definitions are replaced on reload; opening the menu does not parse them.

`BedrockEnvironment` resolves profiles without touching registries. `NativeEnvironment` installs client-only Java environment-attribute layers immediately after Java's biome layer and before its timeline/weather layers. Java's subsequent day/night, weather, water-vision and status-effect behavior remains active. Ground/water tint hooks change only the RGB channels of biome color getters, retaining Java's alpha, block-face tint masks and terrain blending. Sky/fog color blending likewise preserves the original attribute alpha. Java 26.2 consumes ARGB colors; returning a bare Bedrock RGB value made the water tint alpha zero and the surface invisible. Environment adaptation requires no shader replacement. No block, biome, dimension or server registry is registered or mutated. An existing custom Java biome can receive a matching RP profile; the RP cannot create it.

Air/water fog colors and distances, sky and simplified light colors use four-block trilinear camera-position sampling. Missing corners use the existing Java value. This is continuous spatial blending, **not Bedrock's exact blending kernel**. Music volume uses the same spatial blend; discrete mute and loop selections use the current biome. Java's loop controller supplies its own crossfade behavior. Replacing a snapshot removes stale definitions and clears tint/attribute caches; normal resource reload rebuilds terrain.

Iris shader-pack detection suspends all environment visual overrides while a shader pack owns rendering. An unavailable Iris API conservatively suspends overrides rather than guessing. Audio remains independent. Shader-state changes invalidate terrain geometry and tint caches. Sodium is tested separately where a compatible local jar is available. Neither compatibility mechanism certifies arbitrary shader packs.

## Standard environment contracts

P = parsed into an active consumer. U = targeted behavioral unit subset. G = live client state/attribute assertions. Screenshots and successful scenes are not independent Bedrock visual comparisons.

| Contract | P | Functional subset | Approximate / unsupported remainder | U | G |
|---|---|---|---|---|---|
| `environment.legacy` | Yes | `biomes_client.json`: sky_color, water_surface_color, fog_identifier; default fog | Other historical fields/version semantics unverified | Yes | Shared modern runtime exercised; legacy-to-runtime visual scene not independently tested |
| `environment.fog` | Yes | `distance.air` and `distance.water`: fog_color, fog_start/end, fixed/render units | Java fog equation/vision/weather differs; water transition_fog, weather/lava/resistance/powder-snow overrides and all volumetric fields unsupported | Yes | Air/water attribute assertions and underwater camera scenario |
| `environment.fogstack` | Yes, subset | Selected biome, legacy data default, Java fallback; missing media fall through; pack priority | No `/fog` push/pop stack or transition_fog; spatial kernel approximated; incomplete distance pairs do not mix units | Yes | Boundary interpolation and removal/reload assertions |
| `client_biome.ambient_sounds` | Yes | Imported `individual_named_sounds` loop event resolved into Java ambient loop controller | Addition and mood unsupported; Bedrock timing/range/crossfade not reproduced | Alias-generation test; metadata-only test not runtime credit | Imported event and loop binding, real OGG fixture; not auditory parity |
| `client_biome.atmosphere_identifier` | Yes | Identifier/global profile resolution and sky/horizon colors | VV scattering replaced with explicitly logged flat-color approximation | Yes | No dedicated assigned atmosphere live scene yet |
| `client_biome.biome_music` | Yes | Spatial volume_multiplier and explicit empty-string music_definition mute | Named music-definition catalog, timing, fade durations and selection unsupported | Metadata-only test not runtime credit | Volume and mute execute through attribute layers |
| `client_biome.color_grading_identifier` | Opaque component retained only | None | No full-frame color grading/tone mapping | No | No |
| `client_biome.cubemap_identifier` | Opaque component retained only | None | No imported environment cubemap consumer | No | No |
| `client_biome.dry_foliage_color` | Yes | Direct RGB/hex color | Java material/biome blend conventions; version gating absent | Yes | Live dry foliage getter |
| `client_biome.fog_appearance` | Yes | Identifier to imported fog profile | Missing/unknown IDs use Java/default; unsupported fog media remain Java | Yes | Air/water profile values |
| `client_biome.foliage_appearance` | Yes | Direct color | Authored colormap objects unsupported; Java fallback remains | Yes | Live foliage getter |
| `client_biome.grass_appearance` | Yes | Direct color | grass/swamp_grass colormap objects unsupported; no new dirt-face tinting | Yes | Live grass getter |
| `client_biome.lighting_identifier` | Data retained only | Profile selection retained for future renderer | No imported lighting rendering; Java sunlight and ambient colors/brightness retained | Selection/constant conversion only | Live no-override lightmap regression |
| `client_biome.precipitation` | Opaque component retained only | None | Java weather/precipitation retained; no authored precipitation adaptation | No | No |
| `client_biome.sky_color` | Yes | Direct RGB/hex Java sky attribute | Java atmosphere/sky presence and day/night/weather composition retained | Yes | Noon sky attribute; time/weather smoke scenarios |
| `client_biome.water_appearance` | Yes | surface_color in Java water biome tint | surface_opacity unsupported; Java opacity preserved | Yes | Live water getter and underwater scene |
| `client_biome.water_identifier` | Opaque component retained only | None | No VV water-settings consumer | No | No |

The loop, mute, volume and lighting live assertions are behavioral validation. Their metadata-only unit assertions are **not** counted as runtime unit coverage. No whole-contract completion is certified.

## Vibrant Visuals classification

| Property family | Classification | Runtime behavior / limit |
|---|---|---|
| Profile identifiers and reserved global filenames | Direct configuration translation | Resolve authored atmosphere/lighting IDs and pack priority; not rendering parity |
| Constant RGB/hex and color time-keyframe decoding | Direct value translation | VV noon = Java tick 6000; RGB linear interpolation; cyclic missing endpoints are an adapter convention |
| atmosphere sky_zenith_color | Approximation | Java SKY_COLOR base, unless a standard sky override exists |
| atmosphere sky_horizon_color | Approximation | Java FOG_COLOR base, unless authored standard air fog color exists |
| lighting directional_lights.orbital.sun.color | Unsupported rendering; data retained | Java SKY_LIGHT_COLOR retained; direct VV color mapping caused an unwanted scene-wide tint |
| lighting ambient.color / illuminance | Unsupported rendering; data retained | Java ambient brightness retained. Bedrock RGB is not an absolute brightness floor; direct mapping caused fullbright even with default 0.02 lux. Requires HDR exposure/light transport, not an arbitrary lux-to-RGB conversion. |
| Sun/moon lux, orbital offset, moon color, sky intensity | Unsupported | Retain Java brightness/orbit; no arbitrary conversion of HDR lux to RGB |
| Rayleigh/Mie scattering, glare, horizon blend stops | Unsupported | No atmospheric scattering renderer |
| Volumetric fog/media coefficients/anisotropy | Unsupported | No volumetric backend |
| Color grading, white balance, exposure, tone mapping | Unsupported | No scene-wide postprocess; fog-only changes are not color grading |
| Water concentrations, waves, caustics, absorption/reflections/refraction | Unsupported | No VV water renderer |
| PBR texture sets, normal/MER maps, local colored lights, shadows | Unsupported | No new material/shadow engine |

Lighting correction: VV sunlight color is no longer applied to Java `SKY_LIGHT_COLOR`, either. It introduced a scene-wide tint without Bedrock exposure/light transport. Pack sky/fog and asset textures remain separate; the lightmap retains vanilla Java colors and brightness. The orange-sun/white-ambient fixture asserts untouched default and pre-existing light layers at noon, midnight, and in all three dimensions.

No JSON-only recognition receives functional credit. Bedrock 1.21.90's built-in vanilla VV biome profiles are not shipped by Crossrock; consequently its reserved global fallback cannot reproduce precedence against those absent profiles. Standard colors take precedence over the VV flat-color approximation intentionally. Full engine/schema-version selection is not implemented; newer/removed/version-specific fields remain unverified. Named identifier resolution is supported; arbitrary definition fields are not.

## Tests and limitations

- `BedrockEnvironmentTest`: color validation, absent contexts, isolated tint channels, unsupported colormap fallback, fog units/media/defaults/malformed ranges, no mixed-unit fallback, VV time/curves/profile selection, recoverable malformed documents and replacement snapshots.
- `BedrockEnvironmentTranslatorTest`: identity priority, selected subpack, legacy/modern cross-pack conflicts, malformed document isolation and reserved global identity.
- `VanillaSoundTranslatorTest.namedAmbientSoundAliasResolvesDefinition`: event target, gain and pitch; not sound-engine playback by itself.
- `EnvironmentChecks`: actual live tint getters and Java attribute layers, air/water values, sky, music mute/volume, ambient event binding, simplified lighting values, continuous two-biome midpoint, camera underwater, Overworld/Nether/End, and removal fallback. Weather/time screenshots are smoke coverage, not asserted Bedrock numerical equivalence.
- Ambient-floor regression: default white / 0.02 lux fixture must preserve zero and nonzero pre-existing Java ambient layers at noon, midnight, and across dimensions. Enclosed zero-sky/zero-block-light room and torch-lit room captures check darkness versus normal illumination; no claim of Bedrock lux parity. See [official ambient lighting semantics](https://learn.microsoft.com/en-us/minecraft/creator/documents/vibrantvisuals/lightingcustomization).
- Full client suite retains entity, particle, model/material, GUI and read-only Actions & Stuff regression checks. Generated fixture assets are separate from installed packs. Existing warnings are recorded rather than hidden.

Remaining work: authored colormaps, full music and addition/mood adaptation, fog transition curves and remaining media, precise version/schema diagnostics, independent Bedrock captures and audio comparisons, shader-pack interoperability, remote-server validation, performance measurements on large biome/profile sets, and terrain reload visual comparisons. Global malformed JSON is reported; malformed individual color/range values currently fall back without comprehensive field-level diagnostics. No claim is made for full multiplayer visual parity, exhaustive A&S animation coverage, or every Sodium/Iris release.

## Official references

Primary documentation was checked on Learn and against official source commit `4186dc4ec84c8980fc25d89c26e1414aa6c2fde2`:

- [Client biomes](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/clientbiomesreference/examples/clientbiomesoverview?view=minecraft-bedrock-stable) and [component inventory](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/clientbiomesreference/examples/components/client_biome_components?view=minecraft-bedrock-stable).
- [Fog settings and stack](https://learn.microsoft.com/en-us/minecraft/creator/documents/foginresourcepacks?view=minecraft-bedrock-stable).
- [VV biome customization and precedence](https://learn.microsoft.com/en-us/minecraft/creator/documents/vibrantvisuals/biomecustomization?view=minecraft-bedrock-stable).
- [Atmospherics](https://learn.microsoft.com/en-us/minecraft/creator/documents/vibrantvisuals/atmosphericscustomization?view=minecraft-bedrock-stable), [lighting](https://learn.microsoft.com/en-us/minecraft/creator/documents/vibrantvisuals/lightingcustomization?view=minecraft-bedrock-stable), [water](https://learn.microsoft.com/en-us/minecraft/creator/documents/vibrantvisuals/watercustomization?view=minecraft-bedrock-stable), [color grading](https://learn.microsoft.com/en-us/minecraft/creator/documents/vibrantvisuals/colorgradingtonemappingcustomization?view=minecraft-bedrock-stable), [keyframes](https://learn.microsoft.com/en-us/minecraft/creator/documents/vibrantvisuals/keyframejsonsyntax?view=minecraft-bedrock-stable).

## Files changed by this pass

The workspace already contained unrelated changes; this list does not attribute their prior edits to this pass.

- New runtime: `bedrock/environment/BedrockEnvironment.java`, `client/environment/NativeEnvironment.java`.
- New translator: `bedrock/resource/BedrockEnvironmentTranslator.java`.
- New mixins: `NativeEnvironmentAttributesMixin.java`, `NativeEnvironmentTintMixin.java`.
- Wiring: `BedrockCompatClient.java`, `VirtualCompatibilityPackRuntime.java`, `NativeEntityResources.java`, `bedrock_compat.client.mixins.json`.
- Audio alias: `VanillaSoundTranslator.java`.
- Tests: `BedrockEnvironmentTest.java`, `BedrockEnvironmentTranslatorTest.java`, `VanillaSoundTranslatorTest.java`, `EnvironmentChecks.java`, `NativeEntityGameTest.java`.
- Reports: this document, all three RP coverage/roadmap reports, `tools/update_environment_coverage.py`.
