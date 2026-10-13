#!/usr/bin/env python3
"""Emit reviewed apply_patch text from fresh evidence. Does not edit reports itself."""
import difflib, json, pathlib, re, xml.etree.ElementTree as ET
root = pathlib.Path(__file__).resolve().parents[1]
run = pathlib.Path('/tmp/crossrock-rp-audit-G6didE')
logs = [run/'ui-dependency-standard.log', run/'ui-dependency-sodium.log']
for log in logs:
    assert 'BUILD SUCCESSFUL' in log.read_text(), log
suites = []
for path in sorted((run/'build/root/test-results/test').glob('TEST-*.xml')):
    xml = ET.parse(path).getroot()
    suites.append(dict(suite=xml.get('name'), **{k:int(xml.get(k, 0)) for k in ['tests','failures','errors','skipped']}))
totals = {k:sum(s[k] for s in suites) for k in ['tests','failures','errors','skipped']}
assert totals == dict(tests=424, failures=0, errors=0, skipped=0), totals
trace = json.loads((root/'BEDROCK_JSON_UI_DEPENDENCY_TRACE.json').read_text())
assert 'chest.small_chest_screen' in trace['compileFailures']
limit = ('Safe pack-relative registered JSON/JSONC resources, four common primitive dependencies and verified implicit chest-file namespace load. '
         'No full vanilla file/template catalog, variant root adapter, complete execution graph or certified cross-file pack precedence. '
         'Recolourful authored chest still fails expression compilation; native fallback retained.')
data = json.loads((root/'BEDROCK_RP_COVERAGE.json').read_text())
data['validation']['fresh_unit_run'] = totals
data['validation']['suite_results'] = suites
feature = next(f for f in data['features'] if f['id'] == 'ui.defs')
feature['limitations'] = [limit]
for name in ['BedrockUiTemplates', 'BedrockUiDependencies']:
    path = f'src/main/java/dev/matanyah/bedrockcompat/bedrock/ui/{name}.java'
    evidence = dict(path=path, line=next(i+1 for i,s in enumerate((root/path).read_text().splitlines()) if 'public final class' in s), symbol_or_anchor=name)
    feature['evidence'] = [e for e in feature['evidence'] if e['path'] != path] + [evidence]
test = dict(path='src/test/java/dev/matanyah/bedrockcompat/bedrock/ui/BedrockUiDependenciesTest.java', method='realPackRegistrationResolvesPackOwnedPaginationWithoutChangingArchive', run='passed_json_ui_dependency_pass', note='Source resolution and deliberate fallback only, not authored chest rendering. Eight dependency/compiler tests plus two loader tests added.')
feature['tests'] = [t for t in feature['tests'] if t.get('run') != test['run']] + [test]
data['json_ui_dependency_pass'] = dict(report='BEDROCK_JSON_UI_DEPENDENCY_TRACE.md', machine_report='BEDROCK_JSON_UI_DEPENDENCY_TRACE.json', test_results=totals, targeted_new_tests=10, logs=[str(p) for p in logs],
    pack_sha256=trace['packSha256'], graph_counts={k:len(v) for k,v in trace['graph'].items()},
    chest_result='NOT ACHIEVED: pack-owned pagination now loads; colon variable token/string subtraction, lifecycle/input/item contexts, dynamic templates and assets paths remain blockers. Native adapter still targets generic chest root.',
    validation_boundary='Standard/Sodium integrated client regressions, Actions & Stuff and nine native-fallback menus. Not real-pack visual fidelity, full GUI-scale/window-size matrix, shulker success or Bedrock parity. Broad UI coverage remains 9/9 partial and 0/9 complete.')
outputs = {'BEDROCK_RP_COVERAGE.json':json.dumps(data, indent=2, ensure_ascii=False)+'\n'}
note = ('## Dependency-tracing follow-up (2026-10-12)\n\n'
        'See [BEDROCK_JSON_UI_DEPENDENCY_TRACE.md](BEDROCK_JSON_UI_DEPENDENCY_TRACE.md) and its machine-readable JSON. '
        'The previously missing `pagination._ui_screen_generator` is **pack-provided in `update-logs.txt`**, not an absent engine template. '
        'Root-relative registration and verified implicit chest-file loading/namespace are fixed. '
        'Cached source/pointer/ownership diagnostics and bounded root-specific failures are exposed in Add-ons errors. '
        '**The authored Recolourful chest remains unsupported**; expression semantics, native item/cursor context, dynamic templates and asset resolution still block it. '
        'No empty-template or forced-visibility workaround was added.\n\n'
        '**424 unit tests passed, zero failures/errors/skips**, including 10 new targeted tests. '
        'Final standard and Sodium client/integrated-server regressions passed, including Actions & Stuff, generated UI fixtures and nine Recolourful native-fallback menus. '
        'The live reload asserts that pagination and the chest override actually loaded. Native chest transactions and server conservation remain covered. '
        'This is not authored chest visual validation, a complete window-size/scale matrix or Bedrock differential evidence. '
        'Coverage remains **9/9 partial UI families, 0/9 complete**. Logs: `/tmp/crossrock-rp-audit-G6didE/ui-dependency-standard.log` and `ui-dependency-sodium.log`.\n')
for name in ['BEDROCK_JSON_UI_REAL_PACK_COMPATIBILITY.md','BEDROCK_JSON_UI_COMPATIBILITY.md','BEDROCK_JSON_UI_INTERACTION.md','BEDROCK_RP_COVERAGE.md','BEDROCK_RP_ROADMAP.md']:
    text = (root/name).read_text()
    # Replace previous follow-up if rerun; retain earlier pass evidence as history.
    text = re.sub(r'\n## Dependency-tracing follow-up \(2026-10-12\).*\Z','',text,flags=re.S)
    if name in ['BEDROCK_RP_COVERAGE.md','BEDROCK_JSON_UI_COMPATIBILITY.md']:
        text = re.sub(r'^(\| `ui\.defs`[^\n]*? \| Partial(?: / targeted_unit_and_client_subset|, targeted unit/client subset) \| )[^|]+', lambda m:m.group(1)+limit+' ',text,flags=re.M)
    if name == 'BEDROCK_RP_COVERAGE.md':
        text = re.sub(r'Fresh isolated unit suite: \*\*\d+ tests, 0 failures, 0 errors, 0 skipped\*\*','Fresh isolated unit suite: **424 tests, 0 failures, 0 errors, 0 skipped**',text)
    if name == 'BEDROCK_JSON_UI_REAL_PACK_COMPATIBILITY.md':
        text = text.replace('Crossrock requires an explicit namespace and its chest adapter requests', 'Before the dependency pass Crossrock required an explicit namespace; the verified chest-file identity now loads. Its chest adapter still requests')
    outputs[name] = text.rstrip()+'\n\n'+note
print('*** Begin Patch')
for name,new in outputs.items():
    old=(root/name).read_text()
    if new==old: continue
    print('*** Update File: '+str(root/name))
    for line in list(difflib.unified_diff(old.splitlines(),new.splitlines(),lineterm=''))[2:]:
        print('@@' if line.startswith('@@') else line)
print('*** End Patch')
