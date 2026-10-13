#!/usr/bin/env python3
"""Emit apply_patch updates from the reviewed, behaviorally tested JSON UI subset."""
import collections, difflib, json, pathlib, re, xml.etree.ElementTree as ET

ROOT=pathlib.Path(__file__).resolve().parents[1]
RUN=pathlib.Path('/tmp/crossrock-rp-audit-G6didE')
logs=('json-ui-final-fresh.log','json-ui-sodium.log','json-ui-materials.log')
for name in logs:
    assert 'BUILD SUCCESSFUL' in (RUN/name).read_text(),name
data=json.loads((ROOT/'BEDROCK_RP_COVERAGE.json').read_text())
compiler='src/main/java/dev/matanyah/bedrockcompat/bedrock/ui/BedrockUi.java'
layout='src/main/java/dev/matanyah/bedrockcompat/bedrock/ui/BedrockUiLayout.java'
translator='src/main/java/dev/matanyah/bedrockcompat/bedrock/resource/BedrockUiTranslator.java'
client='src/client/java/dev/matanyah/bedrockcompat/client/ui/json/NativeJsonUi.java'
unit='src/test/java/dev/matanyah/bedrockcompat/bedrock/ui/BedrockUiTest.java'
resource_test='src/test/java/dev/matanyah/bedrockcompat/bedrock/resource/BedrockUiTranslatorTest.java'
live='src/gametest/java/dev/matanyah/bedrockcompat/test/JsonUiChecks.java'
reviewed={
 'ui.defs':('Registered UI files compile on reload with bounded source diagnostics. No implicit vanilla template catalog; registration removal/union, namespace collisions across different files and version gates incomplete.',resource_test,'onlyRegisteredFilesAndGlobalsPublish',translator,'void publish'),
 'ui.globals':('Exact variable substitution, scoped defaults/overrides, qualified inheritance and named child merges execute. Conditional variables, general expressions and full pack-stack merging unsupported.',unit,'globalsDefaultsAndInstanceVariablesHaveScopedValues',compiler,'Node node'),
 'ui.controls':('Panel/screen/image/label/stack trees execute; unknown types/fields reject affected root. Buttons, grids, scroll controls, collections, factories and custom renderers unsupported.',unit,'namedChildrenOverrideWithoutFlatteningOrDroppingSiblings',compiler,'Node node'),
 'ui.layout':('GUI-unit anchors, parent percentages, offsets, constraints, stacks, sibling layers, clipping and alpha execute. Content-relative dimensions, grids, global subtree layering and exact Bedrock defaults/parity incomplete.',unit,'clippingAndAlphaPropagateWithoutLeakingToSiblings',layout,'void visit'),
 'ui.bindings':('Global/always binding subset and explicit Java player-name/HUD/XP sources execute. View/collection/factory/condition/expression, health/inventory/custom-renderer data unsupported; unavailable context rejects, never fabricates.',unit,'bindingUpdatesVisibilityAndTextWithoutRecompile',layout,'void visit'),
 'ui.animation':('Numeric alpha/offset/size/color timelines, four easings and next chains/loops execute with screen-local reset. Trigger/reset-name/event/wait/flipbook semantics unsupported; no Bedrock timing oracle.',unit,'chainedLoopAndEasingAreDeterministicAtLongUptime',layout,'void sample'),
 'ui.accessibility':('Label text/localization uses Java font, alignment, shadow and color. Imported fonts/scales/wrapping/TTS/narration unsupported; text-only subset is not accessibility parity.',unit,'bindingUpdatesVisibilityAndTextWithoutRecompile',client,'void draw'),
 'ui.hud':('Decorative HUD/root composition executes below native foreground; exact title/pause/survival inventory/container adapters preserve native widgets. No HUD replacement, modification injection or engine custom renderer; container only code-inspected, creative/modded/recovery screens unadapted.',unit,'emptyProgramHasZeroDrawsAndNoVanillaMutation',client,'void register'),
}

def evidence(path,anchor):
    return dict(path=path,line=next(i+1 for i,s in enumerate((ROOT/path).read_text().splitlines()) if anchor in s),symbol_or_anchor=anchor)

docs='https://github.com/MicrosoftDocs/minecraft-creator/blob/4186dc4ec84c8980fc25d89c26e1414aa6c2fde2/creator/Reference/Content/JsonUiReference/Examples/'
for f in data['features']:
    if f['id'] in reviewed:
        limitation,testpath,method,path,anchor=reviewed[f['id']]
        assert method+'(' in (ROOT/testpath).read_text()
        f.update(status='Partial',implemented=True,validated=True,substantially_complete=False,
            validation_level='targeted_unit_and_client_subset',completeness='Not certified; bounded functional subset only',
            limitations=[limitation],evidence=[evidence(path,anchor),evidence(client,'void render')],
            tests=[dict(path=testpath,method=method,run='passed_json_ui_pass',note='Behavioral subset, not all fields'),
                   dict(path=live,method='verify',run='passed_json_ui_pass',note='Java draw callbacks/native widgets/Escape, not Bedrock differential')],
            documentation=[docs+'JsonUiList.md',docs+'JsonUiComponents/ui_element.md'],
            targeted_unit_behavior=True,visually_verified_against_bedrock=False)
        f['validation_dimensions'].update(automated_subset=True,fresh_in_game=True,cross_engine_differential=False,full_semantic_parity='Not established')
    elif f['id']=='ui.input':
        f.update(limitations=['Imported input/action/focus/modal/scroll/controller contracts unsupported. Unknown interactive controls reject; native Java widgets and Escape preserved, not counted as imported-input support.'],
                 evidence=[evidence(compiler,'Set<String> TYPES'),evidence(client,'String adapter')])
for field in data['schema_field_appendix']:
    if 'JsonUiReference' in field['documentation']:
        field.update(status='Unknown',reason='Family has reviewed partial execution; this schema leaf/enum/default is not independently certified. See BEDROCK_JSON_UI_COMPATIBILITY.md property-level boundary.')

def coverage(rows):
    out=dict(denominator=len(rows))
    for key in ('implemented','validated','substantially_complete'):
        n=sum(bool(f[key]) for f in rows);out[key]=dict(numerator=n,percent=round(n*100/len(rows),1) if rows else None)
    out['status_counts']=dict(collections.Counter(f['status'] for f in rows));return out

eligible=[f for f in data['features'] if f['included_in_rp_denominator']]
data['coverage']['all_inventory_units']=coverage(eligible)
data['coverage']['capabilities_and_components']=coverage([f for f in eligible if f['inventory_kind'] not in ('query','math_function')])
ui=coverage([f for f in eligible if f['category']=='JSON UI']);data['coverage']['by_category']['JSON UI']=ui
assert ui['implemented']['numerator']==ui['validated']['numerator']==8
results=[]
for p in sorted((RUN/'build/root/test-results/test').glob('TEST-*.xml')):
    r=ET.parse(p).getroot();results.append(dict(suite=r.get('name'),**{k:int(r.get(k,0)) for k in ('tests','failures','errors','skipped')}))
totals={k:sum(r[k] for r in results) for k in ('tests','failures','errors','skipped')}
assert totals==dict(tests=370,failures=0,errors=0,skipped=0),totals
data['validation']['fresh_unit_run']=totals;data['validation']['suite_results']=results
data['json_ui_pass']=dict(baseline=dict(denominator=9,implemented=0,validated=0),current=ui,
    report='BEDROCK_JSON_UI_COMPATIBILITY.md',test_results=totals,targeted_new_unit_tests=26,
    logs={n:str(RUN/n) for n in logs},
    client_result='Passed standard and Sodium full client/integrated-server/read-only Actions & Stuff regressions and focused materials/trails. Generated HUD/title/pause/survival inventory draw assertions, native widgets/Escape and pack removal verified.',
    diagnostic_captures=dict(generated_names=['json-ui-title','json-ui-pause','json-ui-inventory'],
        note='Diagnostic screenshots generated by standard/Sodium runs; test harness clears screenshots on later runs. Pause/inventory inspected during this pass; no Bedrock image comparison.'),
    limits=['No independent Bedrock image/timing oracle, UI benchmark, Iris, remote multiplayer, hardware controller/touch/TTS run.',
            '8/9 means broad contracts with subsets, not individual property coverage. Zero certified complete.',
            'Input unsupported; exact full pack-stack resolution, engine bases, expressions, collections, custom renderers and HUD replacement remain open.'])

def metric(stats,key):return f"{stats[key]['numerator']}/{stats['denominator']} ({stats[key]['percent']:.1f}%)"
md=(ROOT/'BEDROCK_RP_COVERAGE.md').read_text()
md=md.replace('environment and client-entity implementation/validation passes','environment, client-entity and partial JSON UI implementation/validation passes')
core=data['coverage']['capabilities_and_components']
md=re.sub(r'\d+ have a functional path, often partial; \d+ have mapped targeted unit/client-subset validation\.',f"{core['implemented']['numerator']} have a functional path, often partial; {core['validated']['numerator']} have mapped targeted unit/client-subset validation.",md)
for label,stats in [('JSON UI',ui),('Core capability/component estimate',core),('All enumerated inventory units',data['coverage']['all_inventory_units'])]:
    md=re.sub(r'^\| '+re.escape(label)+r' \|.*$','| '+label+' | '+' | '.join(metric(stats,k) for k in ('implemented','validated','substantially_complete'))+' |',md,flags=re.M)
md=re.sub(r'Fresh isolated unit suite: \*\*\d+ tests, \d+ failures, \d+ errors, \d+ skipped\*\*','Fresh isolated unit suite: **370 tests, 0 failures, 0 errors, 0 skipped**',md)
md=md.replace('There is no general custom block geometry, JSON UI or full PBR execution path.','There is no general custom block geometry or full PBR execution path. JSON UI now has a bounded decorative execution subset, not full engine/input/HUD replacement support.')
md=md.replace('language native entity and environment resources','language, native entity, environment and registered JSON UI resources')
md=md.replace('No general item atlas, custom block rendering or JSON UI interpreter;','No general item atlas or custom block rendering; JSON UI input/collections/engine renderers remain unsupported;')
md=md.replace('Unsupported JSON UI, block/item APIs','Unsupported JSON UI portions and block/item APIs')
features={f['id']:f for f in data['features']}
lines=[]
for line in md.splitlines():
    m=re.search(r'`([^`]+)`',line) if line.startswith('|') else None
    if m and m.group(1).startswith('ui.') and m.group(1) in features:
        f=features[m.group(1)];refs=', '.join(f"[{pathlib.Path(e['path']).stem}]({ROOT/e['path']}:{e['line']})" for e in f['evidence'])
        line=f"| `{f['id']}` — {f['name']} | {f['status']} / {f['validation_level']} | {' '.join(f['limitations']).replace('|','/')} | {refs} | [Official]({f['documentation'][0]}) |"
    lines.append(line)
md='\n'.join(lines)+'\n'
if '## JSON UI follow-up' not in md:
    md+='\n## JSON UI follow-up\n\nSee [JSON UI compatibility](BEDROCK_JSON_UI_COMPATIBILITY.md): **0/9 → 8/9 functional partial and targeted-tested subsets; 0/9 certified complete**. Eight broad contracts with a narrow subset are not 89% individual property coverage. Imported input remains unsupported. Registered decorative trees, labels/images/stacks, scoped variables, layout, explicit bindings and numeric animation execute during native GUI extraction. Exact title/pause/survival inventory/HUD fixtures and standard/Sodium/A&S regressions pass; containers are not live-validated. Native widgets/Escape and configuration/recovery UI remain native. No Bedrock visual differential, full screen modification engine, built-in template catalog, conditional expression/collection renderer, TTS or benchmark certification. Exact logs and limitations are in JSON.\n'
road=(ROOT/'BEDROCK_RP_ROADMAP.md').read_text()
road=road.replace('### P1 — JSON UI interpreter','### P1 — Complete the partial JSON UI runtime')
road=road.replace('Build namespace/template inheritance, variable scopes, element/control tree, layout, textures/nine-slice/text, input/focus, bindings/collections/factories and animation scheduling. Define supported HUD/screens and Java data-source adaptations explicitly. Cosmetic sprite copying is not a substitute. IDs: `ui.*`.',
    'A bounded decorative interpreter now executes registered definitions, namespace/template inheritance, exact variable scopes, five control types, parent-relative layout, images/text, explicit Java bindings and numeric timelines. See BEDROCK_JSON_UI_COMPATIBILITY.md (8/9 broad partial contracts, zero certified complete). Next implement exact pack-stack/built-in template resolution, conditional expressions, native interactive/focus/accessibility adapters, grids/scrolling/collections/factories, custom HUD/item renderers, nine-slice/fonts, safe modifications and triggered animation/reset semantics. Do not confuse decoration with HUD replacement or native input support. IDs: `ui.*`.')
if '## JSON UI pass priorities' not in road:
    road+='\n## JSON UI pass priorities\n\n1. Missing core: bounded expressions/conditional variables, native interactive actions/focus/accessibility, view/collection/factory data and engine custom renderers.\n2. Partial: exact pack-stack/template/registration behavior, grids/scrolling/content-relative sizing, text/image modes, modifications and triggers.\n3. Incorrect/incomplete: complete subtree layering/clipping/default semantics; per-root dependency invalidation rather than global animation dirtiness.\n4. Validation: independent Bedrock screenshots/timing, real RP-only UI packs, containers/creative inventory, live resize/GUI scale, input/controller/TTS and performance budgets. Existing 26 new units and standard/Sodium/A&S client passes certify subsets only.\n5. Optional: newer Bedrock UI engines remain separate scope, not silently implemented by JSON UI parsing.\n'
print('*** Begin Patch')
for name,content in {'BEDROCK_RP_COVERAGE.json':json.dumps(data,indent=2,ensure_ascii=False)+'\n','BEDROCK_RP_COVERAGE.md':md,'BEDROCK_RP_ROADMAP.md':road}.items():
    old=(ROOT/name).read_text()
    if old==content:continue
    print('*** Update File: '+str(ROOT/name))
    for line in list(difflib.unified_diff(old.splitlines(),content.splitlines(),n=3))[2:]:print('@@' if line.startswith('@@') else line)
print('*** End Patch')
