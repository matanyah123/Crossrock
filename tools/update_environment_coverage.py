#!/usr/bin/env python3
"""Emit a narrow apply_patch for the reviewed environment pass; never write reports directly.

Requires passing isolated unit/client logs. Does not infer semantic support from JSON names.
"""
import collections, difflib, json, pathlib, re, xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
RUN = pathlib.Path('/tmp/crossrock-rp-audit-G6didE')
data = json.loads((ROOT / 'BEDROCK_RP_COVERAGE.json').read_text())
assert 'BUILD SUCCESSFUL' in (RUN / 'environment-final.log').read_text()
unit = 'src/test/java/dev/matanyah/bedrockcompat/bedrock/environment/BedrockEnvironmentTest.java'
translation = 'src/test/java/dev/matanyah/bedrockcompat/bedrock/resource/BedrockEnvironmentTranslatorTest.java'
sound = 'src/test/java/dev/matanyah/bedrockcompat/bedrock/resource/VanillaSoundTranslatorTest.java'
client = 'src/gametest/java/dev/matanyah/bedrockcompat/test/EnvironmentChecks.java'
model = 'src/main/java/dev/matanyah/bedrockcompat/bedrock/environment/BedrockEnvironment.java'
translator = 'src/main/java/dev/matanyah/bedrockcompat/bedrock/resource/BedrockEnvironmentTranslator.java'
runtime = 'src/client/java/dev/matanyah/bedrockcompat/client/environment/NativeEnvironment.java'

# Each tuple is an explicitly reviewed executable subset, its limitations and its assertion evidence.
reviewed = {
 'environment.legacy': ('Sky, water surface color and fog assignment translation only; historical fields and version semantics incomplete.', translation, 'higherLegacyReplacesLowerModern', False),
 'environment.fog': ('Air/water distance+color and fixed/render units. No transition_fog, authored weather/lava/resistance/powder-snow or volumetric renderer. Java fog equations/status effects remain.', unit, 'fogUnitsAndMedia', True),
 'environment.fogstack': ('Biome, legacy default, Java fallback and identity priority. Four-block trilinear spatial blend is approximate; no command stack or authored transition_fog. Incomplete distance pairs do not mix unit systems.', unit, 'missingMediumFallsThroughDefaultStack', True),
 'client_biome.ambient_sounds': ('Imported individual_named_sounds loop only. No addition/mood adaptation; Java loop crossfade/timing. Live event binding and real OGG fixture, not auditory parity.', sound, 'namedAmbientSoundAliasResolvesDefinition', True),
 'client_biome.atmosphere_identifier': ('Profile/global assignment and simplified sky/horizon color approximation; no atmospheric scattering. Bedrock built-in VV profiles/version precedence absent.', unit, 'vvAssignmentAndStandardPrecedence', False),
 'client_biome.biome_music': ('Spatial volume multiplier and explicit empty-string mute execute. Named music definitions, timing and authored fade durations unsupported.', client, 'verify', True),
 'client_biome.dry_foliage_color': ('Direct RGB/hex tint; Java material/blending conventions and no schema-version gating.', unit, 'groundTintsAreSeparate', True),
 'client_biome.fog_appearance': ('Imported fog identifier resolution for air/water. Other media and transitions unsupported; unknown IDs retain default/Java fallback.', unit, 'fogUnitsAndMedia', True),
 'client_biome.foliage_appearance': ('Direct RGB/hex color only; authored foliage colormap objects unsupported, retaining Java fallback.', unit, 'groundTintsAreSeparate', True),
 'client_biome.grass_appearance': ('Direct RGB/hex color only; grass/swamp_grass colormap objects unsupported. Java face tint masks retained.', unit, 'groundTintsAreSeparate', True),
 'client_biome.lighting_identifier': ('Profile/global assignment plus Java sunlight/ambient color approximation. No lux/shadows/moon/orbital/sky-intensity equivalence.', client, 'verify', True),
 'client_biome.sky_color': ('Direct Java sky attribute before weather/time layers. Java sky presence, equations and day/night composition retained, not Bedrock visual parity.', unit, 'skyColorAndNamespace', True),
 'client_biome.water_appearance': ('surface_color only. surface_opacity unsupported; Java opacity/shader behavior retained.', unit, 'groundTintsAreSeparate', True),
 'pbr.atmosphere': ('VV sky_zenith_color -> Java sky and sky_horizon_color -> fog, with color keyframes. Explicit approximation, not Rayleigh/Mie/glare/horizon-stop/volumetric support.', unit, 'vvLinearColorAndCyclicEndpoints', False),
 'pbr.lighting': ('VV sunlight/ambient colors -> Java light-color attributes only. Explicit approximation, no lux, shadow engine, orbital orientation, moon/sky intensity or local lights.', client, 'verify', True),
}
unsupported = {
 'client_biome.color_grading_identifier': 'No imported full-frame color grading or tone mapping consumer; opaque retained JSON is not execution.',
 'client_biome.cubemap_identifier': 'No imported environment cubemap consumer.',
 'client_biome.precipitation': 'No authored precipitation adaptation; Java weather/precipitation remains.',
 'client_biome.water_identifier': 'No VV water-settings consumer; do not substitute flat water tint for wave/optical behavior.',
}

def evidence(path, anchor):
    lines = (ROOT / path).read_text().splitlines()
    return dict(path=path, line=next(i+1 for i, line in enumerate(lines) if anchor in line), symbol_or_anchor=anchor)

for f in data['features']:
    key = f['id']
    if key in reviewed:
        limitation, testpath, method, ingame = reviewed[key]
        f.update(status='Partial', implemented=True, validated=True, substantially_complete=False,
            validation_level='targeted_client_attribute_subset' if testpath == client else 'targeted_unit_subset',
            completeness='Not certified; bounded subset/approximations, no Bedrock differential oracle', limitations=[limitation],
            evidence=[evidence(translator, 'void translate'), evidence(model, 'public record BedrockEnvironment'), evidence(runtime, 'void addLayers')],
            tests=[dict(path=testpath, method=method, run='passed_environment_pass', note='Behavioral subset only; not full semantic/visual parity')])
        assert re.search(r'\b' + method + r'\s*\(', (ROOT / testpath).read_text())
        if ingame and testpath != client:
            f['tests'].append(dict(path=client, method='verify', run='passed_environment_pass', note='Live client attribute/getter assertions; not independent visual parity'))
        f['validation_dimensions'].update(automated_subset=True, fresh_in_game=ingame, cross_engine_differential=False, full_semantic_parity='Not established')
        f['environment_classification'] = 'Approximation' if key in ('client_biome.atmosphere_identifier','client_biome.lighting_identifier','pbr.atmosphere','pbr.lighting') else 'Direct subset; Java renderer differences remain'
        f['targeted_unit_behavior'] = testpath != client
        f['visually_verified_against_bedrock'] = False
    elif key in unsupported:
        f['limitations'] = [unsupported[key]]
    if key in ('pbr.atmosphere','pbr.lighting','pbr.water','pbr.colorgrading'):
        name = {'pbr.atmosphere':'AtmosphericsCustomization','pbr.lighting':'LightingCustomization','pbr.water':'WaterCustomization','pbr.colorgrading':'ColorGradingToneMappingCustomization'}[key]
        f['documentation'] = [f"https://github.com/MicrosoftDocs/minecraft-creator/blob/{data['official_docs']['commit']}/creator/Documents/VibrantVisuals/{name}.md"]

for field in data['schema_field_appendix']:
    if 'ClientBiomesReference/' in field['documentation'] or 'FogsReference/' in field['documentation']:
        field['status'] = 'Unknown'
        field['reason'] = 'Environment family now has executable subsets; this nested/versioned schema leaf is not independently certified. See BEDROCK_ENVIRONMENT_COMPATIBILITY.md for exact supported/unsupported fields.'

def coverage(rows):
    result = dict(denominator=len(rows))
    for key in ('implemented','validated','substantially_complete'):
        n = sum(bool(f[key]) for f in rows)
        result[key] = dict(numerator=n, percent=round(n*100/len(rows),1) if rows else None)
    result['status_counts'] = dict(collections.Counter(f['status'] for f in rows))
    return result

eligible = [f for f in data['features'] if f['included_in_rp_denominator']]
data['coverage']['all_inventory_units'] = coverage(eligible)
data['coverage']['capabilities_and_components'] = coverage([f for f in eligible if f['inventory_kind'] not in ('query','math_function')])
for category in ('Environment','Advanced rendering'):
    data['coverage']['by_category'][category] = coverage([f for f in eligible if f['category'] == category])
assert data['coverage']['capabilities_and_components']['denominator'] == 182
assert data['coverage']['by_category']['Environment']['implemented']['numerator'] == 13
results = []
for p in (RUN / 'build/root/test-results/test').glob('TEST-*.xml'):
    r = ET.parse(p).getroot()
    results.append(dict(suite=r.attrib['name'], **{k:int(r.attrib.get(k,0)) for k in ('tests','failures','errors','skipped')}))
totals = {k:sum(r[k] for r in results) for k in ('tests','failures','errors','skipped')}
assert totals['tests'] >= 326 and not totals['failures'] and not totals['errors']
data['validation']['fresh_unit_run'] = totals
data['validation']['suite_results'] = results
data['validation']['environment_pass_client_log'] = str(RUN / 'environment-final.log')
data['validation']['environment_pass_client_result'] = 'Passed full client/integrated-server and read-only Actions & Stuff suite. Attribute assertions, not Bedrock visual certification.'
sodium = RUN / 'environment-sodium.log'
data['validation']['environment_sodium'] = dict(log=str(sodium), result='Passed full client/integrated-server suite with local Sodium 0.9.2-alpha.4 mc26.2' if sodium.exists() and 'BUILD SUCCESSFUL' in sodium.read_text() else 'Not successfully verified; see log')
data['validation']['environment_iris'] = 'No local Iris jar; reflection shader-ownership guard code-inspected only. Shader-enabled runtime not tested.'
data['validation']['environment_captures'] = dict(path=str(RUN / 'environment-captures'), review='Noon and underwater diagnostic captures inspected; not an independent Bedrock visual comparison.')
materials = RUN / 'environment-materials.log'
data['validation']['environment_materials'] = dict(log=str(materials), result='Passed focused Actions & Stuff materials/trails suite' if materials.exists() and 'BUILD SUCCESSFUL' in materials.read_text() else 'Not yet freshly passed; full standard Actions & Stuff suite passed')
data['environment_pass'] = dict(baseline=dict(denominator=17,implemented=0,validated=0), current=data['coverage']['by_category']['Environment'],
    report='BEDROCK_ENVIRONMENT_COMPATIBILITY.md', versions='Version/schema gates not implemented; no historical/future semantic parity claim.',
    visual_oracle='No independent Bedrock visual/audio oracle', notes=['13 contracts have executable partial subsets; four unsupported. Two advanced contracts contain explicit color approximations only.',
    'Parsing/metadata-only assertions do not count as runtime unit validation. Music and lighting use targeted live client layer assertions.',
    'Generated fixtures only. Installed packs and registries not modified. Iris shader-enabled interoperability remains untested.'])

def metric(stats,key):return f"{stats[key]['numerator']}/{stats['denominator']} ({stats[key]['percent']:.1f}%)"
md = (ROOT / 'BEDROCK_RP_COVERAGE.md').read_text()
md = md.replace('Updated after the particle and partial Molang implementation/validation passes;', 'Updated after the particle, partial Molang and environment implementation/validation passes;')
core = data['coverage']['capabilities_and_components']
md = md.replace('116 have a functional path, often partial; 109 have mapped targeted unit validation.',
    f"{core['implemented']['numerator']} have a functional path, often partial; {core['validated']['numerator']} have mapped targeted unit/client-subset validation.")
for label, stats in [('Environment',data['coverage']['by_category']['Environment']),('Advanced rendering',data['coverage']['by_category']['Advanced rendering']),('Core capability/component estimate',core),('All enumerated inventory units',data['coverage']['all_inventory_units'])]:
    md = re.sub(r'^\| '+re.escape(label)+r' \|.*$', '| '+label+' | '+' | '.join(metric(stats,k) for k in ('implemented','validated','substantially_complete'))+' |', md, flags=re.M)
md = re.sub(r'Fresh isolated unit suite: \*\*\d+ tests, \d+ failures, \d+ errors, \d+ skipped\*\*', 'Fresh isolated unit suite: **'+', '.join(f'{totals[k]} {k}' for k in ('tests','failures','errors','skipped'))+'**',md)
md = md.replace('and native entity resources; Punchy', 'native entity and environment resources; Punchy')
md = md.replace('There is no general custom block geometry, JSON UI, client-biome visual, fog, PBR or item-atlas execution path.',
    'There is no general custom block geometry, JSON UI, full PBR or item-atlas execution path. Client-biome/fog subsets and explicitly bounded VV color approximations now execute; see BEDROCK_ENVIRONMENT_COMPATIBILITY.md.')
md = md.replace('6. Client-biome visuals, fog and advanced PBR/deferred APIs have no implementation.',
    '6. Client-biome/fog now have partial classic-renderer consumers, but authored colormaps, additional fog media/transitions and full audio remain open. PBR/deferred rendering remains unsupported; VV color adapters are only approximations.')
md = md.replace('Unsupported JSON UI, blocks/items, client-biome/fog families must either be implemented or explicitly excluded from the advertised beta contract.',
    'Unsupported JSON UI, block/item APIs and remaining client-biome/fog fields must either be implemented or explicitly excluded from the advertised beta contract.')
by_id = {f['id']:f for f in data['features']}
lines = []
for line in md.splitlines():
    match = re.search(r'`([^`]+)`',line) if line.startswith('|') else None
    if match and match.group(1) in set(reviewed)|set(unsupported):
        f = by_id[match.group(1)]
        refs = ', '.join(f"[{pathlib.Path(e['path']).stem}]({ROOT / e['path']}:{e['line']})" for e in f['evidence'])
        line = f"| `{f['id']}` — {f['name']} | {f['status']} / {f['validation_level']} | {' '.join(f['limitations']).replace('|','/')} | {refs} | [Official]({f['documentation'][0]}) |"
    lines.append(line)
md = '\n'.join(lines)+'\n'
md = re.sub(r'\n## Environment follow-up.*?(?=\n## |\Z)','',md,flags=re.S)
md += '\n## Environment follow-up\n\nEnvironment is now **13/17 functional partial contracts and 13/17 targeted behavioral subsets**, not 13 complete contracts. See [environment matrix](BEDROCK_ENVIRONMENT_COMPATIBILITY.md). Advanced rendering has two explicitly bounded atmosphere/light color approximations, not a VV renderer. Music/lighting validation includes live attribute assertions; it is not all unit-tested or visually equivalent to Bedrock. Fog transitions/remaining media, authored colormaps, named music, addition/mood, precipitation, cubemaps, water opacity and advanced graphics remain open. Fresh client/Sodium/material results and logs are recorded separately in JSON.\n'
roadmap = (ROOT / 'BEDROCK_RP_ROADMAP.md').read_text()
roadmap = roadmap.replace('Implement client_biome components and legacy biomes_client visuals, fog definition/stack/transition evaluation,',
    'Extend the now-functional client_biome/legacy visual subsets: authored colormaps, full named music and ambient addition/mood, fog transitions and remaining media, precise fog-stack semantics and version gates. Implement')
roadmap = re.sub(r'\n## Environment pass priorities.*?(?=\n## |\Z)','',roadmap,flags=re.S)
roadmap += '''\n## Environment pass priorities\n\nSee `BEDROCK_ENVIRONMENT_COMPATIBILITY.md`. 13/17 environment contracts have functional partial subsets and targeted assertions; two advanced contracts have explicit color approximations. No contract is certified complete.\n\n1. Core remaining consumers: authored grass/foliage colormaps, named music definitions, ambient addition/mood, client precipitation. Keep Java fallback and external biome identities; do not register server gameplay.\n2. Correct/incomplete behavior: water transition_fog progression, other fog media/weather, command-stack integration, unit-safe incomplete fields, engine/schema gates and actionable per-field diagnostics. Compare spatial interpolation and time/weather composition against Bedrock.\n3. Validation: Bedrock image/audio oracle, authored atmosphere live scenes, legacy-only live scenes, water opacity decisions, dedicated remote-server test, large-profile reload/performance, Iris with enabled shader packs. Sodium smoke/state assertions do not prove every rendering path.\n4. Optional advanced renderer profile: keep scattering, PBR/MER, shadows, cubemaps, color grading/tone mapping, waves/caustics/refraction unsupported until a real backend exists. Maintain explicit bounded color approximation classification; no intensity/lux guessing or pack-specific patches.\n'''
outputs = {'BEDROCK_RP_COVERAGE.json':json.dumps(data,indent=2,ensure_ascii=False)+'\n','BEDROCK_RP_COVERAGE.md':md,'BEDROCK_RP_ROADMAP.md':roadmap}
print('*** Begin Patch')
for name, content in outputs.items():
    old = (ROOT / name).read_text()
    if old == content:continue
    print('*** Update File: '+str(ROOT / name))
    for line in list(difflib.unified_diff(old.splitlines(),content.splitlines(),n=3))[2:]:
        print('@@' if line.startswith('@@') else line)
print('*** End Patch')
