#!/usr/bin/env python3
"""Emit reviewed interaction coverage updates as an apply_patch patch; never edits reports."""
import collections, difflib, json, pathlib, re, xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parents[1]
RUN=pathlib.Path('/tmp/crossrock-rp-audit-G6didE')
logs=['ui-interaction-standard.log','ui-interaction-sodium.log']
for name in logs:
    assert 'BUILD SUCCESSFUL' in (RUN/name).read_text(),name
data=json.loads((ROOT/'BEDROCK_RP_COVERAGE.json').read_text())
base='src/main/java/dev/matanyah/bedrockcompat/bedrock/ui/'
client='src/client/java/dev/matanyah/bedrockcompat/client/ui/json/'
unit='src/test/java/dev/matanyah/bedrockcompat/bedrock/ui/BedrockUiInteractionTest.java'
live='src/gametest/java/dev/matanyah/bedrockcompat/test/JsonUiInteractionChecks.java'
limitations={
 'ui.defs':'Explicit registered JSON/JSONC documents, including non-json extensions, load on reload. Unsafe registrations are independently diagnosed. Built-in vanilla catalog, complete pack-stack precedence and implicit registration remain unsupported.',
 'ui.globals':'Exact scoped variables/inheritance execute; conditional variable blocks and general UI expressions remain unsupported.',
 'ui.controls':'Panel/screen/image/label/stack plus bounded button/grid/native inventory-item renderer execute. Scroll views, sliders, editboxes, toggles, dropdowns, factories and general custom renderers unsupported.',
 'ui.layout':'GUI anchors/percentages/clips/alpha, fixed grids and quantized interactive bounds execute. Content-relative sizing, full transforms/global layering/default semantics and scrolling remain incomplete.',
 'ui.bindings':'Global/always and typed collection/always bindings execute with real native slot indices/counts. View bindings, expressions, factories and complete Bedrock engine data catalog unsupported.',
 'ui.input':'Native widgets execute mouse/keyboard button subset; exact close/back, pause resume/settings actions only. Native inventory transactions remain authoritative. Unknown actions/layouts fail closed. No imported scroll/text/recipe-book/confirm/controller/touch/modal parity.',
 'ui.animation':'Numeric timelines and button-state child selection execute. Interactive triggers/reset/event/wait/flipbook semantics and Bedrock timing parity unsupported.',
 'ui.accessibility':'Java text/localization plus native widget focus/narration hooks execute. Imported fonts/wrapping/TTS controls and hardware narration/controller parity not validated.',
 'ui.hud':'Decorative HUD and exact title/pause/survival inventory/ten container adapters execute. No full HUD replacement/control modifications; creative/modded/recovery screens stay native.'}
def evidence(path):
    lines=(ROOT/path).read_text().splitlines()
    line=next(i+1 for i,s in enumerate(lines) if 'public final class ' in s)
    return dict(path=path,line=line,symbol_or_anchor=pathlib.Path(path).stem)
for f in data['features']:
    if f['id'] not in limitations:continue
    f.update(status='Partial',implemented=True,validated=True,substantially_complete=False,
        validation_level='targeted_unit_and_client_subset',completeness='Bounded functional subset; not certified complete',
        limitations=[limitations[f['id']]],targeted_unit_behavior=True,visually_verified_against_bedrock=False)
    f['evidence']=[evidence(base+'BedrockUi.java'),evidence(base+'BedrockUiLayout.java'),evidence(client+'NativeJsonUi.java')]
    if f['id']=='ui.input':f['evidence'] += [evidence(base+'BedrockUiInteraction.java'),evidence(client+'NativeJsonUiButton.java')]
    method={'ui.defs':'unknownMappingsRejectRatherThanGuess','ui.globals':'collectionBindingsUseItemScopeNotGlobalFallback','ui.controls':'stateChildrenSelectWithoutDestroyingCommonChildren','ui.layout':'buttonResizeUpdatesActualHitBounds','ui.bindings':'collectionBindingsUseItemScopeNotGlobalFallback','ui.input':'mouseAndKeyboardMappingsAreDistinct','ui.animation':'stateChildrenSelectWithoutDestroyingCommonChildren','ui.accessibility':'disabledAndTransparentButtonsDoNotHit','ui.hud':'noImportedSlotsLeaveNativeUntouched'}[f['id']]
    f['tests']=f.get('tests',[])+[dict(path=unit,method=method,run='passed_json_ui_interaction_pass',note='24 interaction tests; not every field/enum. Earlier tests retained above.'),dict(path=live,method='verify',run='passed_json_ui_interaction_pass',note='Plus fallback/prepareReload: actual menus/input/server state; real pack safe fallback, not Bedrock image parity')]
    if f['id']=='ui.defs':f['tests'][-2].update(path='src/test/java/dev/matanyah/bedrockcompat/bedrock/resource/BedrockUiTranslatorTest.java',method='registeredJsonDocumentsNeedNotUseJsonExtension')
    f['validation_dimensions'].update(automated_subset=True,fresh_in_game=True,cross_engine_differential=False,full_semantic_parity='Not established')
def coverage(rows):
    out=dict(denominator=len(rows))
    for key in ('implemented','validated','substantially_complete'):
        n=sum(bool(f[key]) for f in rows);out[key]=dict(numerator=n,percent=round(100*n/len(rows),1))
    out['status_counts']=dict(collections.Counter(f['status'] for f in rows));return out
eligible=[f for f in data['features'] if f['included_in_rp_denominator']]
data['coverage']['all_inventory_units']=coverage(eligible)
core=coverage([f for f in eligible if f['inventory_kind'] not in ('query','math_function')])
data['coverage']['capabilities_and_components']=core
ui=coverage([f for f in eligible if f['category']=='JSON UI']);data['coverage']['by_category']['JSON UI']=ui
assert ui['implemented']['numerator']==ui['validated']['numerator']==9
results=[]
for p in sorted((RUN/'build/root/test-results/test').glob('TEST-*.xml')):
    r=ET.parse(p).getroot();results.append(dict(suite=r.get('name'),**{k:int(r.get(k,0)) for k in ('tests','failures','errors','skipped')}))
totals={k:sum(r[k] for r in results) for k in ('tests','failures','errors','skipped')}
assert totals==dict(tests=396,failures=0,errors=0,skipped=0),totals
data['validation']['fresh_unit_run']=totals;data['validation']['suite_results']=results
data['json_ui_interaction_pass']=dict(report='BEDROCK_JSON_UI_INTERACTION.md',baseline=dict(implemented=8,validated=8,denominator=9),current=ui,test_results=totals,targeted_new_unit_tests=26,logs={n:str(RUN/n) for n in logs},
    client_result='Standard and Sodium client/integrated-server plus read-only Actions & Stuff and Recolourful fallback passed. Ten native container adapters, relocated-slot splitting/shift/drag/double-click/hotbar/offhand operations and item conservation, imported mouse/keyboard close, focused-widget reload cleanup, vanilla fallback verified.',
    reference_pack_result='Recolourful Containers BE 1.4 custom UI remains unsupported: missing engine templates/expressions/renderers/scroll/nine-slice. Native fallback exercised on seven representative menus; archive equality asserted. No installed pack modified.',
    limits=['9/9 broad partial families is not full property coverage; zero complete.','No independent Bedrock differential, remote multiplayer, Iris, hardware controller/touch/narration or performance benchmark.','Native tooltips/recipe-book/text inputs retained, not replaced or independently pixel-certified.'])
def metric(s,k):return f"{s[k]['numerator']}/{s['denominator']} ({s[k]['percent']:.1f}%)"
md=(ROOT/'BEDROCK_RP_COVERAGE.md').read_text()
for label,s in [('JSON UI',ui),('Core capability/component estimate',core),('All enumerated inventory units',data['coverage']['all_inventory_units'])]:
    md=re.sub(r'^\| '+re.escape(label)+r' \|.*$','| '+label+' | '+' | '.join(metric(s,k) for k in ('implemented','validated','substantially_complete'))+' |',md,flags=re.M)
md=re.sub(r'\d+ have a functional path, often partial; \d+ have mapped targeted unit/client-subset validation\.',f"{core['implemented']['numerator']} have a functional path, often partial; {core['validated']['numerator']} have mapped targeted unit/client-subset validation.",md)
md=re.sub(r'Fresh isolated unit suite: \*\*\d+ tests, \d+ failures, \d+ errors, \d+ skipped\*\*','Fresh isolated unit suite: **396 tests, 0 failures, 0 errors, 0 skipped**',md)
md=md.replace('JSON UI input/collections/engine renderers remain unsupported','JSON UI general expressions/factories/engine catalog remain unsupported; input and native slot collections have bounded partial paths')
md=md.replace('bounded decorative execution subset, not full engine/input/HUD replacement support','bounded decorative and native-interaction execution subset, not full engine/HUD replacement support')
fs={f['id']:f for f in data['features']}
lines=[]
for line in md.splitlines():
    m=re.search(r'`(ui\.[^`]+)`',line) if line.startswith('|') else None
    if m:
        f=fs[m.group(1)];line=f"| `{f['id']}` — {f['name']} | Partial / targeted_unit_and_client_subset | {limitations[f['id']]} | [Interaction evidence](BEDROCK_JSON_UI_INTERACTION.md) | [Official]({f['documentation'][0]}) |"
    lines.append(line)
md='\n'.join(lines)+'\n'
md=md.split('## JSON UI follow-up')[0]+'## JSON UI follow-up\n\nSee [interaction bridge](BEDROCK_JSON_UI_INTERACTION.md): **9/9 broad partial functional and targeted-tested contracts, 0/9 complete**. Native buttons/actions and typed slot grids now execute, with unchanged authoritative Java transactions. Ten real container adapters, reload cleanup, vanilla fallback and standard/Sodium/A&S regressions pass. Recolourful Containers was tested read-only: its custom UI still falls back because engine templates, expressions and other APIs are missing. No Bedrock differential or full property/parity claim. See JSON `json_ui_interaction_pass` for exact evidence.\n'
road=(ROOT/'BEDROCK_RP_ROADMAP.md').read_text()
start=road.index('A bounded decorative interpreter');end=road.index('\n',start)
road=road[:start]+'The runtime now has 9/9 broad partial contracts (zero complete), including native Java widgets, scoped actions, fixed typed slot grids and collection bindings. See BEDROCK_JSON_UI_INTERACTION.md. Prioritize built-in vanilla templates/pack-stack semantics and bounded conditional expressions, then factories/view bindings/renderer catalog, scrolling/content-relative sizing, nine-slice/fonts, safe modifications and interaction triggers. Recolourful Containers remains native fallback; no pack-specific patches. IDs: `ui.*`.'+road[end:]
road=road.split('## JSON UI pass priorities')[0]+'''## JSON UI pass priorities

1. Missing core: built-in vanilla template catalog, bounded UI expressions/conditional variables, view/factory data and engine renderer registry. These block Recolourful Containers custom UI.
2. Partial: pack-stack/registration semantics, role-specific collections, native navigation/recipe/text actions, scrolling/content-relative sizing, image/text modes and modifications.
3. Incomplete: full layering/clipping/default/trigger semantics, focus/modal/controller/accessibility behavior and per-root invalidation.
4. Validation: independent Bedrock screenshots/timing, larger RP-only pack matrix, creative inventory, GUI-scale/resize matrix, hardware TTS/controller and performance budgets. Current 396 units plus standard/Sodium integrated-server regressions certify bounded subsets only.
5. Optional: newer Bedrock UI engines are separate scope.
'''
compat='''# Bedrock JSON UI compatibility

Updated 2026-10-12 after the interaction bridge. **9/9 broad contracts have tested partial functional implementations; 0/9 are certified complete.** The preceding decorative pass was 8/9. These are family-level counts, not percentage coverage of individual properties.

The authoritative current property boundary, official documentation links, runtime architecture, screen adapters, real-pack findings, validation and remaining work are in [BEDROCK_JSON_UI_INTERACTION.md](BEDROCK_JSON_UI_INTERACTION.md). Machine-readable individual contract evidence is in `BEDROCK_RP_COVERAGE.json`.

| Contract | Executed subset | Principal gaps |
|---|---|---|
'''
for id,lim in limitations.items():compat+=f'| `{id}` | Partial, targeted unit/client subset | {lim} |\n'
compat+='''
## Baseline runtime retained

Registered definitions compile on resource reload, not screen open. Exact scoped variables, qualified inheritance, named-child merging, GUI anchors/percentages/stacks, clipping/alpha, PNG UV images, Java-localized labels and numeric timeline chains remain supported. Unknown fields, missing context and unsafe dependencies reject the affected root. No active root returns before layout/binding work. Active roots still allocate bounded intermediate layouts; no zero-overhead/performance certification.

No implicit Bedrock vanilla catalog, general modifications/full-screen replacement, arbitrary bindings, general UI expressions, nine-slice/font parity or HUD replacement is claimed. Same-file generated override follows the existing resource stack; cross-file namespace/global/registration precedence is not fully certified. Original compile/document/animation safety limits remain; runtime grid expansion adds a shared 16384-node budget and 512-cell per-grid cap.

## Interaction and real-pack results

Buttons use native Java widget focus and input dispatch. Typed native slot grids reposition existing Slot objects only after complete validation; native menus and networking remain authoritative. Unsupported interactive layouts fall back as a whole. Cleanup restores coordinates and clears removed focused widgets.

Recolourful Containers BE 1.4 was inspected and tested from an isolated read-only reference copy. Registered JSONC templates are now discovered, but its custom UI does **not** yet work: missing engine templates, expressions and other documented features still cause safe native fallback. Seven representative native menus were exercised. Actions & Stuff remains a rendering/animation regression, not proof of full JSON UI compatibility.

Fresh validation: **396 unit tests, zero failures/errors/skips**, including **26 new interaction/registration tests**. Standard and Sodium client/integrated-server runs exercise actual native slot operations, ten mapped container close actions, reload cleanup and fallback. Exact successful logs, assertions and remaining limitations are recorded under `json_ui_interaction_pass` in the coverage JSON. Prior `json_ui_pass` records remain historical evidence for the earlier decorative subset.

No Bedrock pixel/timing oracle, remote multiplayer, Iris, hardware controller/touch/narrator or benchmark certification. Tooltips and recipe-book/editbox behavior stay native; imported equivalents remain unsupported. Screenshots are diagnostic captures, not image-diff assertions.

Reproduce with `BEDROCK_REFERENCE_PACK` and `BEDROCK_UI_REFERENCE_PACK` pointing to read-only local archives, then run `./gradlew test runClientGameTest --rerun-tasks` in an isolated output directory. Repeat with `-PnativeEntityCompatibilityMods="<Sodium jar>"`. No installed pack or mod is replaced by these tests.
'''
print('*** Begin Patch')
for name,content in {'BEDROCK_RP_COVERAGE.json':json.dumps(data,indent=2,ensure_ascii=False)+'\n','BEDROCK_RP_COVERAGE.md':md,'BEDROCK_RP_ROADMAP.md':road,'BEDROCK_JSON_UI_COMPATIBILITY.md':compat}.items():
    old=(ROOT/name).read_text()
    if old==content:continue
    print('*** Update File: '+str(ROOT/name))
    for line in list(difflib.unified_diff(old.splitlines(),content.splitlines(),n=3))[2:]:print('@@' if line.startswith('@@') else line)
print('*** End Patch')
