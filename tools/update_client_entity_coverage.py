#!/usr/bin/env python3
"""Emit apply_patch updates for reviewed client-entity subsets, not parser-name coverage."""
import collections, difflib, json, pathlib, re, xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
RUN = pathlib.Path('/tmp/crossrock-rp-audit-G6didE')
data = json.loads((ROOT / 'BEDROCK_RP_COVERAGE.json').read_text())
for log in ('client-entity-final-fresh.log', 'client-entity-sodium.log', 'client-entity-materials.log'):
    assert 'BUILD SUCCESSFUL' in (RUN / log).read_text(), log
unit = 'src/test/java/dev/matanyah/bedrockcompat/bedrock/entity/BedrockClientEntityCompatibilityTest.java'
eggtest = 'src/test/java/dev/matanyah/bedrockcompat/bedrock/resource/BedrockSpawnEggTest.java'
client = 'src/gametest/java/dev/matanyah/bedrockcompat/test/ClientEntityChecks.java'
definition = 'src/main/java/dev/matanyah/bedrockcompat/bedrock/entity/BedrockClientEntity.java'
runtime = 'src/main/java/dev/matanyah/bedrockcompat/bedrock/entity/BedrockEntityInstance.java'
renderer = 'src/client/java/dev/matanyah/bedrockcompat/client/entity/NativeEntityRenderer.java'
egg = 'src/main/java/dev/matanyah/bedrockcompat/bedrock/resource/BedrockSpawnEgg.java'
reviewed = {
 'entity.aliases': ('Object aliases/cross-file consumers execute. Missing top-level animation entries now isolate; nested failures and scalar/version alternatives remain incomplete.', unit, 'missingAnimationAliasDoesNotDiscardValidClips', False),
 'entity.scripts': ('Per-instance ordered script/controller execution. Render-driven offscreen timing and format-version gates differ.', unit, 'repeatedTimestampDoesNotRerunInitializeOrPreAnimation', True),
 'entity.scale': ('Uniform and lower/uppercase axis overrides execute after pre-animation. Conflicting spellings, negative scales and culling lack Bedrock differential validation.', unit, 'uppercaseAxisScalesExecuteAfterPreAnimationAndOverrideUniform', False),
 'entity.publicvars': ('Declared public variables expose filtered independent Molang snapshots, including arrow evaluation. No automatic actor discovery, target-query binding or live cross-actor update semantics.', unit, 'publicSnapshotsExposeOnlyDeclaredVariablesAndDoNotRetainQueriesOrMutableScope', True),
 'entity.hidearmor': ('Suppresses native armor and Java equipment fallback independently of held items. Other equipment combinations/visual parity not certified.', unit, 'authoredEquipmentControlsRemainIndependent', True),
 'entity.itemlighting': ('Held native attachables and fallback submissions use full-bright when authored; actor/armor lighting unchanged. Live fallback flags asserted, not GPU/pixel parity or all first-person/special-renderer paths.', unit, 'defaultControlsPreserveArmorLightingAndHeldItems', True),
 'entity.spawnegg': ('Authored same-pack item atlas texture/index generates Java presentation. No color masks, built-in/cross-pack atlas fallback, weighted entries, item registration or versioned duplicate selection; higher absent declaration does not remove lower generated egg.', eggtest, 'indexedAtlasTextureReplacesJavaItemPresentationWithoutTint', True),
 'entity.legacycontrollers': ('Legacy object aliases and direct string controller IDs execute in declaration order. Duplicate aliases rejected; coexistence/version behavior lacks Bedrock comparison.', unit, 'legacyDeclarationsExecuteOrderedControllersWithoutScriptsAnimate', True),
 'entity.context': ('Shared actor queries/holder scopes and model-space legacy locators execute; stale geometry anchors clear. BP data remains provider-dependent; offscreen/dedicated multiplayer/lifecycle parity unverified.', unit, 'locatorQueriesFollowGeometrySelectionWithoutStaleAnchors', True),
 'textures.itematlas': ('Narrow same-pack item_texture.json alias/index consumer for spawn_egg only. Not a general item atlas; built-in/cross-pack lookup and weighted entries unsupported.', eggtest, 'omittedIndexDefaultsToZeroAndPriorityReplacesAllPresentationAssets', True),
}

def evidence(path, anchor):
    lines = (ROOT / path).read_text().splitlines()
    return dict(path=path, line=next(i+1 for i,line in enumerate(lines) if anchor in line), symbol_or_anchor=anchor)

for f in data['features']:
    key = f['id']
    if key in reviewed:
        limitation, testpath, method, live = reviewed[key]
        paths = [(egg,'void translate')] if key in ('textures.itematlas','entity.spawnegg') else [(definition,'BedrockClientEntity parse'),(runtime,'Frame evaluateFrame'),(renderer,'publicContext(Entity')]
        f.update(status='Partial', implemented=True, validated=True, substantially_complete=False,
            validation_level='targeted_unit_and_client_subset' if live else 'targeted_unit_subset',
            completeness='Not certified; executable subset only, no independent Bedrock oracle', limitations=[limitation],
            evidence=[evidence(p,a) for p,a in paths],
            tests=[dict(path=testpath,method=method,run='passed_client_entity_pass',note='Behavioral subset only; not full semantic/visual parity')])
        assert method+'(' in (ROOT / testpath).read_text()
        if live:f['tests'].append(dict(path=client,method='verify',run='passed_client_entity_pass',note='Client state/resource assertions; not independent visual parity'))
        f['validation_dimensions'].update(automated_subset=True,fresh_in_game=live,cross_engine_differential=False,full_semantic_parity='Not established')
        f['targeted_unit_behavior']=True
        f['visually_verified_against_bedrock']=False
    elif key=='entity.offscreen':
        f['limitations']=['Authored offscreen flags diagnosed once on reload, not executed. Render-driven runtime needs an independent bone/effect update scheduler; is_on_screen is not accurate visibility.']
    elif key=='entity.emitters':
        f['limitations']=['No verified consumer; sparse official schema gives insufficient semantics. Reload diagnostic; do not equate this with functional particle_effects aliases.']

for field in data['schema_field_appendix']:
    if 'actor_resource_definition' in field['documentation']:
        field['status']='Unknown'
        field['reason']='Client-entity family has reviewed functional subsets; this versioned/nested leaf is not independently certified. See CLIENT_ENTITY_COMPATIBILITY.md property inventory.'

def coverage(rows):
    result=dict(denominator=len(rows))
    for key in ('implemented','validated','substantially_complete'):
        n=sum(bool(f[key]) for f in rows)
        result[key]=dict(numerator=n,percent=round(n*100/len(rows),1) if rows else None)
    result['status_counts']=dict(collections.Counter(f['status'] for f in rows))
    return result

eligible=[f for f in data['features'] if f['included_in_rp_denominator']]
data['coverage']['all_inventory_units']=coverage(eligible)
data['coverage']['capabilities_and_components']=coverage([f for f in eligible if f['inventory_kind'] not in ('query','math_function')])
for category in ('Client entities','Textures'):
    data['coverage']['by_category'][category]=coverage([f for f in eligible if f['category']==category])
assert data['coverage']['by_category']['Client entities']['implemented']['numerator']==9
results=[]
for p in sorted((RUN / 'build/root/test-results/test').glob('TEST-*.xml')):
    r=ET.parse(p).getroot()
    results.append(dict(suite=r.attrib['name'],**{k:int(r.attrib.get(k,0)) for k in ('tests','failures','errors','skipped')}))
totals={k:sum(r[k] for r in results) for k in ('tests','failures','errors','skipped')}
assert totals==dict(tests=344,failures=0,errors=0,skipped=0),totals
data['validation']['fresh_unit_run']=totals
data['validation']['suite_results']=results
data['client_entity_pass']=dict(baseline=dict(denominator=11,implemented=4,validated=4),
    current=data['coverage']['by_category']['Client entities'],report='CLIENT_ENTITY_COMPATIBILITY.md',
    test_results=totals,logs={name:str(RUN / name) for name in ('client-entity-final-fresh.log','client-entity-sodium.log','client-entity-materials.log')},
    client_result='Passed full client/integrated-server and read-only Actions & Stuff suite, Sodium full suite, focused materials/trails suite.',
    limits=['No independent Bedrock image/audio oracle; no Iris shader-enabled or dedicated remote multiplayer run.',
            'Public snapshot reference subset is not automatic actor discovery. Egg atlas subset is not general item-atlas support.',
            'Offscreen scheduler unsupported; particle_emitters semantics Unknown; version gates/visible bounds remain incomplete.'])

def metric(stats,key):return f"{stats[key]['numerator']}/{stats['denominator']} ({stats[key]['percent']:.1f}%)"
md=(ROOT / 'BEDROCK_RP_COVERAGE.md').read_text()
md=md.replace('partial Molang and environment implementation/validation passes','partial Molang, environment and client-entity implementation/validation passes')
core=data['coverage']['capabilities_and_components']
md=re.sub(r'\d+ have a functional path, often partial; \d+ have mapped targeted unit/client-subset validation\.',f"{core['implemented']['numerator']} have a functional path, often partial; {core['validated']['numerator']} have mapped targeted unit/client-subset validation.",md)
for label,stats in [('Client entities',data['coverage']['by_category']['Client entities']),('Textures',data['coverage']['by_category']['Textures']),('Core capability/component estimate',core),('All enumerated inventory units',data['coverage']['all_inventory_units'])]:
    md=re.sub(r'^\| '+re.escape(label)+r' \|.*$','| '+label+' | '+' | '.join(metric(stats,k) for k in ('implemented','validated','substantially_complete'))+' |',md,flags=re.M)
md=re.sub(r'Fresh isolated unit suite: \*\*\d+ tests, \d+ failures, \d+ errors, \d+ skipped\*\*','Fresh isolated unit suite: **344 tests, 0 failures, 0 errors, 0 skipped**',md)
md=md.replace('There is no general custom block geometry, JSON UI, full PBR or item-atlas execution path.','There is no general custom block geometry, JSON UI or full PBR execution path. An item-atlas subset now executes for authored spawn-egg textures only; it is not a general item API.')
md=md.replace('Shortest-path controller blending and several client-entity flags are absent.','Shortest-path controller blending remains absent. Client armor/item-lighting controls and legacy controllers now have functional subsets; offscreen policies, versioned selection and full public actor discovery remain open.')
by_id={f['id']:f for f in data['features']}
lines=[]
for line in md.splitlines():
    m=re.search(r'`([^`]+)`',line) if line.startswith('|') else None
    if m and m.group(1) in set(reviewed)|{'entity.offscreen','entity.emitters'}:
        f=by_id[m.group(1)]
        refs=', '.join(f"[{pathlib.Path(e['path']).stem}]({ROOT / e['path']}:{e['line']})" for e in f['evidence'])
        line=f"| `{f['id']}` — {f['name']} | {f['status']} / {f['validation_level']} | {' '.join(f['limitations']).replace('|','/')} | {refs} | [Official]({f['documentation'][0]}) |"
    lines.append(line)
md='\n'.join(lines)+'\n'
md+='\n## Client-entity follow-up\n\nClient entities improved from **4/11 to 9/11 functional partial contracts and targeted-tested subsets**. See [client-entity matrix](CLIENT_ENTITY_COMPATIBILITY.md). Offscreen scheduling remains unsupported and `particle_emitters` Unknown. Public snapshot references and texture/index eggs are bounded subsets; no contract is certified complete. Full standard/Sodium client suites and focused materials/trails pass; no Bedrock visual/audio differential or Iris certification. Detailed test evidence and logs are recorded in JSON.\n'
roadmap=(ROOT / 'BEDROCK_RP_ROADMAP.md').read_text()
roadmap=roadmap.replace('Implement authored bounds/culling, render groups, geometry item_display_transforms, hide_armor, held_item_ignores_lighting, public-variable visibility and documented offscreen update policies. Add versioned legacy controller declarations. Resolve uncertain particle_emitters/scalar schema forms from authoritative examples before implementation. Implement spawn_egg definitions separately from hardcoded vanilla egg paths.',
    'Implement authored bounds/culling, render groups, geometry item_display_transforms and documented offscreen update policies. Extend the now-functional hide_armor, held_item_ignores_lighting, filtered public snapshots and legacy controller subsets with version gates and actor-discovery integration. Resolve uncertain particle_emitters/scalar schema forms from authoritative examples before implementation. Extend authored texture/index spawn eggs with built-in/stack atlas resolution and color masks.')
roadmap+='\n## Client-entity pass priorities\n\nSee `CLIENT_ENTITY_COMPATIBILITY.md`: 9/11 functional partial contracts and targeted subsets, up from 4/11; zero certified complete.\n\n1. Missing core: independent offscreen bone/effect scheduler and genuine visibility; investigate `particle_emitters` documented semantics before inventing behavior.\n2. Partial APIs: versioned duplicate/engine selection, complete pack-stack/built-in atlas lookup and color egg masks; connect public references to actor discovery/query consumers without exposing private state.\n3. Incorrect/incomplete behavior: client visible-bounds culling, nested missing-reference isolation, higher absent egg declaration versus lower generated assets, effect-context lifecycle stress. Do not alter server collision boxes.\n4. Validation: Bedrock differential timing/blending/scales, locator auditory scenarios, native and fallback equipment combinations, dedicated remote multiplayer, shader-enabled Iris. Existing standard/Sodium/A&S assertions are representative, not universal visual verification.\n'
print('*** Begin Patch')
for name,content in {'BEDROCK_RP_COVERAGE.json':json.dumps(data,indent=2,ensure_ascii=False)+'\n','BEDROCK_RP_COVERAGE.md':md,'BEDROCK_RP_ROADMAP.md':roadmap}.items():
    old=(ROOT / name).read_text()
    if old==content:continue
    print('*** Update File: '+str(ROOT / name))
    for line in list(difflib.unified_diff(old.splitlines(),content.splitlines(),n=3))[2:]:print('@@' if line.startswith('@@') else line)
print('*** End Patch')
