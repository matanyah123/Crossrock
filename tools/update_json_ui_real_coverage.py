#!/usr/bin/env python3
"""Emit an apply_patch update after successful real-pack regression runs; never write reports."""
import difflib, json, pathlib, re, xml.etree.ElementTree as ET
root = pathlib.Path(__file__).resolve().parents[1]
run = pathlib.Path('/tmp/crossrock-rp-audit-G6didE')
logs = ['ui-real-standard.log', 'ui-real-sodium.log']
for name in logs:
    assert 'BUILD SUCCESSFUL' in (run / name).read_text(), name
data = json.loads((root / 'BEDROCK_RP_COVERAGE.json').read_text())
limitations = {
    'ui.defs': 'Explicit registered JSON/JSONC resources and four known common primitive dependencies execute. No full vanilla catalog, implicit namespace patches, variant root selection or certified cross-file pack precedence.',
    'ui.globals': 'Scoped variables, inheritance, static ordered requires blocks and variable child arrays execute. Bounded scalar expressions execute; mixed variable/binding expressions, variable template names and runtime conditional trees remain unsupported.',
    'ui.layout': 'Anchors, percentages, fixed grids/stacks, clipping/alpha and scalar/per-edge nine-slice execute. Undersized nine-slice uses leading-edge priority, not Bedrock-certified behavior. Content-relative sizing, scrolling, vector borders and full layering/default parity remain incomplete.',
    'ui.bindings': 'Global/collection always bindings plus local view expression bindings execute. Missing data fails closed. Named/sibling-control lookup, arbitrary collection expressions, factories and the full engine data catalog remain unsupported.'
}
methods = {
    'ui.defs': 'knownVanillaPrimitivesResolveAndPackOverrideWins',
    'ui.globals': 'variableChildArraysPreserveLocalScopeAndConditionalOrder',
    'ui.layout': 'nineSlicePreservesCornersAndAtlasUv',
    'ui.bindings': 'localViewBindingsExecuteInOrderAndNamedControlsFailClosed'
}
base = 'src/main/java/dev/matanyah/bedrockcompat/bedrock/ui/'
test = 'src/test/java/dev/matanyah/bedrockcompat/bedrock/ui/BedrockUiRealPackTest.java'
for feature in data['features']:
    if feature['id'] not in limitations:
        continue
    feature['limitations'] = [limitations[feature['id']]]
    for name in {'ui.defs':['BedrockUi.java'], 'ui.globals':['BedrockUiExpression.java'], 'ui.layout':['BedrockUiNineSlice.java'], 'ui.bindings':['BedrockUiExpression.java']}[feature['id']]:
        path = base + name
        line = next(i+1 for i, s in enumerate((root/path).read_text().splitlines()) if 'public final class' in s)
        feature['evidence'].append(dict(path=path, line=line, symbol_or_anchor=name[:-5]))
    feature['tests'].append(dict(path=test, method=methods[feature['id']], run='passed_json_ui_real_pack_pass', note='15 new targeted unit tests; bounded subset, not all Bedrock properties or differential parity.'))
    feature['documentation'].append('https://learn.microsoft.com/en-us/minecraft/creator/reference/content/jsonuireference/examples/jsonuicomponents/ui_element?view=minecraft-bedrock-stable')
results = []
for path in sorted((run/'build/root/test-results/test').glob('TEST-*.xml')):
    suite = ET.parse(path).getroot()
    results.append(dict(suite=suite.get('name'), **{k:int(suite.get(k,0)) for k in ['tests','failures','errors','skipped']}))
totals = {k:sum(s[k] for s in results) for k in ['tests','failures','errors','skipped']}
assert totals == dict(tests=414, failures=0, errors=0, skipped=0), totals
data['validation']['fresh_unit_run'] = totals
data['validation']['suite_results'] = results
data['json_ui_real_pack_pass'] = dict(report='BEDROCK_JSON_UI_REAL_PACK_COMPATIBILITY.md', test_results=totals, targeted_new_unit_tests=15,
    logs={name:str(run/name) for name in logs},
    implemented_subset=['Four independently implemented common primitive templates', 'Variable child arrays and static ordered conditional variables', 'Bounded scalar UI expression AST and local view bindings', 'Scalar/per-edge nine-slice renderer'],
    client_result='Standard and Sodium full client/integrated-server regressions pass, including Actions & Stuff. Generated expression/conditional/nine-slice fixture renders on mapped screens at GUI scales 1 and 2. Nine real-pack fallback screens exercised; chest native transactions/conservation and unchanged reference archive asserted.',
    reference_pack_result='Recolourful authored layouts still unsupported. Vanilla engine templates, variable template names, implicit namespaces/variant roots, cursor/item bindings/renderers, assets paths, scroll and modifications remain blockers. Trading is not exercised.',
    validation_boundary='Java diagnostic captures inspected; no Bedrock differential, full property parity, Iris, remote multiplayer or performance benchmark. Existing counts remain 9/9 partial, 0/9 complete; no denominator inflation.')
outputs = {'BEDROCK_RP_COVERAGE.json':json.dumps(data, indent=2, ensure_ascii=False)+'\n'}
for name in ['BEDROCK_RP_COVERAGE.md', 'BEDROCK_JSON_UI_COMPATIBILITY.md']:
    text = (root/name).read_text()
    for id, limitation in limitations.items():
        text = re.sub(r'^(\| `'+re.escape(id)+r'`[^\n]*? \| Partial(?: / targeted_unit_and_client_subset|, targeted unit/client subset) \| )[^|]+', lambda m:m.group(1)+limitation+' ', text, flags=re.M)
    text = text.replace('Fresh isolated unit suite: **399 tests, 0 failures, 0 errors, 0 skipped**', 'Fresh isolated unit suite: **414 tests, 0 failures, 0 errors, 0 skipped**')
    if name == 'BEDROCK_JSON_UI_COMPATIBILITY.md':
        text = text.replace('Updated 2026-10-12 after the interaction bridge.', 'Updated 2026-10-12; see the current [real-pack pass](BEDROCK_JSON_UI_REAL_PACK_COMPATIBILITY.md). Earlier interaction results below remain historical.')
        text = text.replace('general UI expressions, nine-slice/font parity', 'complete UI expression semantics, nine-slice/font parity')
        text += '\n## Real-pack follow-up\n\nBounded scalar expressions, static conditional variables, variable child arrays, four common primitives and scalar/per-edge nine-slice now execute. Local view bindings are supported; named-control bindings and scrolling/modifications remain unsupported. **414 unit tests pass**, including 15 new tests. Standard/Sodium client regressions and GUI scales 1/2 pass. Recolourful still falls back on nine exercised screens; trading and authored layout fidelity remain unverified. See the real-pack report for exact dependencies, limits and changed files.\n'
    else:
        text = text.replace('JSON UI general expressions/factories/engine catalog remain unsupported', 'JSON UI complete expression semantics/factories/engine catalog remain unsupported')
        start = text.index('## JSON UI follow-up')
        text = text[:start] + '## JSON UI follow-up\n\nSee [real-pack compatibility](BEDROCK_JSON_UI_REAL_PACK_COMPATIBILITY.md). **9/9 broad partial functional and targeted-tested contracts, 0/9 complete**, unchanged denominators. Bounded scalar expressions, static conditionals, variable child arrays, four known primitives, local view bindings and nine-slice now execute. Full standard/Sodium/A&S regressions pass with 414 units and GUI scales 1/2. Recolourful authored layouts remain blocked: nine native fallback screens were exercised, not certified as translated. No Bedrock differential. Exact evidence is in JSON `json_ui_real_pack_pass`; earlier pass records are historical.\n'
    outputs[name] = text
name = 'BEDROCK_RP_ROADMAP.md'
text = (root/name).read_text().replace('Prioritize built-in vanilla templates/pack-stack semantics and bounded conditional expressions, then factories/view bindings/renderer catalog, scrolling/content-relative sizing, nine-slice/fonts, safe modifications and interaction triggers.', 'The real-pack pass adds four known primitives, bounded expressions/static conditionals, local view bindings and scalar/per-edge nine-slice. Prioritize engine templates/variant roots/pack-stack semantics, variable template names, factories/named view bindings/renderer catalog, scrolling/content-relative sizing, asset paths, safe modifications and interaction triggers.')
text = text.replace('built-in vanilla template catalog, bounded UI expressions/conditional variables, view/factory data and engine renderer registry.', 'built-in screen template catalog/variant selection, variable template names, named view/factory data and engine renderer registry. Bounded scalar expressions/static conditionals/local view bindings now execute, but full semantics remain incomplete.')
text = text.replace('Current 396 units', 'Current 414 units').replace('GUI-scale/resize matrix, hardware TTS/controller', 'GUI-scale/resize matrix beyond tested scales 1/2, hardware TTS/controller')
outputs[name] = text
print('*** Begin Patch')
for name, content in outputs.items():
    old = (root/name).read_text()
    if old == content:
        continue
    print('*** Update File: '+str(root/name))
    for line in list(difflib.unified_diff(old.splitlines(), content.splitlines(), n=3))[2:]:
        print('@@' if line.startswith('@@') else line)
print('*** End Patch')
