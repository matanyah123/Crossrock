#!/usr/bin/env python3
"""Emit an apply_patch for the query pass, preserving other API-family audit findings.

Usage: python3 tools/update_molang_coverage.py /path/to/minecraft-creator/creator | apply_patch
Requires the pinned official documentation checkout; does not write files or install packs.
Evidence mappings below are reviewed execution paths, not arbitrary identifier string hits.
"""
import collections
import json
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
DOC = pathlib.Path(sys.argv[1])
COMMIT = '4186dc4ec84c8980fc25d89c26e1414aa6c2fde2'
QUERY_DIR = DOC / 'Reference/Content/MolangReference/Examples/MolangConcepts/QueryFunctions'
data = json.loads((ROOT / 'BEDROCK_RP_COVERAGE.json').read_text())
unit = 'src/test/java/dev/matanyah/bedrockcompat/bedrock/entity/MolangQueryCompatibilityTest.java'
client = 'src/gametest/java/dev/matanyah/bedrockcompat/client/entity/NativeQueryChecks.java'

def source(name):
    matches = list((ROOT / 'src/main').rglob(name + '.java')) + list((ROOT / 'src/client').rglob(name + '.java'))
    assert len(matches) == 1, (name, matches)
    return matches[0]

def evidence(name, query):
    p = source(name)
    lines = p.read_text().splitlines()
    line = next((i for i, s in enumerate(lines, 1) if '"' + query + '"' in s), 1)
    return dict(path=str(p.relative_to(ROOT)), line=line, symbol_or_anchor=query, role='reviewed query execution/provider')

def test(path, method, claim):
    return dict(path=path, method=method, run='passed_query_pass', claim=claim)

groups = {
    'MolangQueryFunctions': ('count unhappy_counter last_hit_by_player any all in_range', 'contextIndependentContracts'),
    'MolangItemQueries': ('max_durability remaining_durability is_enchanted is_stackable any_tag all_tags', 'renderedItemIsIndependentOfOwnerEquipmentAndSupportsDurabilityAndTags'),
    'EntityFrameQueries': ('armor_damage_slot item_remaining_use_duration equipment_count camera_rotation', 'equipmentDamageIncludesBodyAndDurationTracksSelectedHandAndNormalization'),
    'EntityQueryContext': ('get_pack_setting', 'sliderSettingsPreserveNumericValueAndDoNotCoerceToggleOrMissingSetting'),
    'EntityEnvironmentQueries': ('block_neighbor_has_any_tag block_neighbor_has_all_tags', 'neighboringBlockCoordinatesAreBlockRelativeNotActorRelative'),
    'ParticleQueryContext': ('effect_emitter_count effect_particle_count distance_from_camera', 'particleCountsAndDistanceRemainLiveAndDoNotReadActorRoot'),
}
native_names = '''day moon_phase moon_brightness cardinal_facing cardinal_facing_2d cardinal_player_facing client_max_render_distance
has_gravity is_silent is_fire_immune is_in_water_or_rain head_is_in_water is_onfire is_moving movement_direction
is_dancing is_carrying_block is_chested is_ignited is_resting is_playing_dead is_elder is_pregnant is_laying_egg
is_interested is_stalking is_searching is_croaking is_emerging is_digging is_roaring is_sniffing is_eating is_rolling
is_scared is_sneezing sneeze_counter sit_amount lie_amount current_squish_value previous_squish_value player_level
model_scale swim_amount is_levitating has_head_gear'''.split()
known_existing = {
    'NativeEntityRelations': 'has_rider has_player_rider ride_body_x_rotation ride_body_y_rotation ride_head_x_rotation ride_head_y_rotation',
    'BedrockAnimationController': 'all_animations_finished any_animation_finished',
    'BedrockEntityInstance': 'bone_origin bone_rotation get_root_locator_offset',
}
reviewed = {}
for cls, (names, method) in groups.items():
    for name in names.split():
        reviewed[name] = (cls, test(unit, method, 'Targeted execution/boundary assertions; not full semantic parity.'))
for name in ('position', 'position_delta', 'movement_direction'):
    reviewed[name] = ('EntityQueryContext', test(unit, 'positionAxesRejectFractionalNonFiniteMissingAndOutOfRangeArguments', 'Axis selection and invalid/missing arguments; state production not certified by this test.'))
for name in ('property', 'has_property'):
    reviewed[name] = ('EntityQueryContext', test(unit, 'integrationDistinguishesMissingFromFalseAndRoutesArgumentsAndTypedProperties', 'Typed external property dispatch, missing versus present-zero, argument routing; no BP gameplay.'))
for name in native_names:
    # movement_direction is unit-tested for axis dispatch, not a Java/Bedrock motion oracle.
    if name != 'movement_direction':
        reviewed[name] = ('NativeEntityFacts', test(client, 'verify', 'Client-state adapter assertions inside an integrated world; some entity-specific queries have only non-applicable/zero cases. Not visual or Bedrock parity.'))
for cls, names in known_existing.items():
    for name in names.split():
        reviewed.setdefault(name, (cls, None))
for name in ('all_animations_finished', 'any_animation_finished'):
    reviewed[name] = ('BedrockAnimationController', test('src/test/java/dev/matanyah/bedrockcompat/bedrock/entity/BedrockAnimationControllerTest.java',
        'completionQueriesAndStateClockAreScoped', 'Existing controller transition/completion assertions rerun; not all timing/version cases.'))

unindexed = set('block_property cardinal_block_face_placed_on dash_cooldown_progress debug_output get_equipped_item_name get_name has_block_property is_feeling_happy overlay_alpha owner_identifier unhappy_counter'.split())
bp_only = set('anger_level had_component_group scoreboard server_memory_tier'.split())
docs = {p.stem[6:].replace('.', '_'): p for p in QUERY_DIR.glob('query_*.md')}
assert len(docs) == 317
queries = []
for f in data['features']:
    if f['inventory_kind'] != 'query':
        continue
    original = f['id']
    name = original.removeprefix('query.').replace('.', '_')
    f['id'] = f['name'] = 'query.' + name
    if f['id'] != original:
        f['legacy_inventory_id'] = original
    p = docs[name]
    body = p.read_text().split('---', 2)[-1]
    prose = ' '.join(line for line in body.splitlines() if line and not line.startswith(('#', '>', '```', '"')))
    # At most 25 words quoted from each individual documentation leaf.
    f['documented_behavior_excerpt'] = ' '.join(prose.split()[:25])
    f['documentation_filename'] = p.name
    f['documented_versions'] = sorted(set(re.findall(r'\b1\.(?:[2-9][0-9])(?:\.[0-9]+)?\b', body)))
    f['deprecated_or_removed'] = bool(re.search(r'deprecated|no longer available|no longer in use', body, re.I))
    f['current_learn_index'] = name not in unindexed
    f['documentation'] = ['https://github.com/MicrosoftDocs/minecraft-creator/blob/' + COMMIT + '/creator/' + str(p.relative_to(DOC))]
    f['semantic_parity'] = 'Unknown; no cross-engine oracle'
    f['query_audit_level'] = 'Documentation inventory/restrictions and execution path reviewed; comprehensive edge-case/version parity remains incomplete.'
    if name in reviewed:
        cls, targeted = reviewed[name]
        f.update(status='Partial', implemented=True, substantially_complete=False, completeness='Not certified; documented limitations remain')
        f['evidence'] = [evidence(cls, name)]
        if name in ('property', 'has_property'):
            f['evidence'].extend(evidence(c, name) for c in ('MolangQueryProvider', 'NativeMolangQueries'))
        if targeted:
            f.update(validated=True, validation_level='targeted_client_state_subset' if targeted['path'] == client else 'targeted_unit_subset')
            f['tests'] = [targeted]
            f['validation_dimensions'].update(automated_subset=True, fresh_in_game=targeted['path'] == client,
                cross_engine_differential=False, full_semantic_parity='Not established')
        if name in native_names:
            f['limitations'] = ['Java client-visible state adapter. Non-applicable entities return zero (direction queries use undefined=6). Java state machines, pose timing and Bedrock numerical semantics have not been differentially compared. Offscreen actor observations may be stale in particle contexts.']
        elif cls == 'MolangItemQueries':
            f['limitations'] = ['Rendered attachable item snapshot; not a generic block context or a standalone imported item-definition runtime. Tags use the existing Java/Bedrock tag adapter; custom BP tags require external synchronized metadata.']
        elif cls == 'ParticleQueryContext':
            f['limitations'] = ['Emitter-root distance and live per-effect imported particle counts. Emitter construction is not yet registered when startup expressions execute, so its own emitter count may be absent during startup. Vanilla-only particle engine statistics are not synthesized.']
        elif name in ('property', 'has_property'):
            f['limitations'] = ['Known Java-derived properties plus per-actor snapshot providers through NativeMolangQueries.register. No custom property synchronization, BP component execution or scoreboard implementation is supplied by this interface.']
        elif name == 'get_pack_setting':
            f['limitations'] = ['Numeric slider values only; missing/non-numeric settings report unavailable data. Pack schema/engine-version gating remains incomplete.']
        elif name == 'item_remaining_use_duration':
            f['limitations'] = ['Client-observed active hand use ticks converted to seconds; positive normalization divides the seconds value. Exact Bedrock normalization conventions, remote use timing and version differences need a differential oracle.']
        elif name == 'armor_damage_slot':
            f['limitations'] = ['Uses visible Java ItemStack damage, including body armor. Unsynchronized damage on remote actors cannot be reconstructed.']
        elif name.startswith('block_neighbor_'):
            f['limitations'] = ['Offsets start at floored actor block position, not fractional actor position; block tags only, within the existing 128-block observed range. No separate placement/block-definition context is supplied.']
        elif name in ('count', 'last_hit_by_player', 'unhappy_counter'):
            f['limitations'] = ['Documented client/legacy behavior tested. Version availability gates and full Bedrock parity are not certified.']
        elif cls in known_existing:
            f['limitations'] = ['Existing scoped query provider confirmed by code review. Full argument, timing, coordinate and version semantics are not certified. Controller completion has targeted transition tests; relation/bone queries still need contract-specific tests.']
    if name in bp_only:
        f['responsibility'] = 'bp_only'
        f['limitations'] = ['Official documentation restricts this query to behavior-pack/server contexts. Not a missing RP gameplay API. Future authoritative data may be exposed through MolangQueryProvider/NativeMolangQueries, but this pass does not supply it.']
    elif not f['implemented']:
        note = 'Not implemented by this pass. This is an outstanding adapter/API gap, not automatically a BP dependency. An optional provider interface is not counted as a functional implementation.'
        if note not in f['limitations']:
            f['limitations'].append(note)
    if f['deprecated_or_removed']:
        note = 'Legacy/removal/version restriction recorded from docs; min_engine_version-based runtime gating is not implemented.'
        if note not in f['limitations']:
            f['limitations'].append(note)
    queries.append(f)
assert len(queries) == 317 and len({f['id'] for f in queries}) == 317

def coverage(rows):
    result = {'denominator': len(rows)}
    for key in ('implemented', 'validated', 'substantially_complete'):
        n = sum(bool(f[key]) for f in rows)
        result[key] = {'numerator': n, 'percent': round(n * 100 / len(rows), 1) if rows else None}
    result['status_counts'] = dict(collections.Counter(f['status'] for f in rows))
    return result

eligible = [f for f in data['features'] if f['included_in_rp_denominator']]
data['coverage']['all_inventory_units'] = coverage(eligible)
data['coverage']['by_category']['Molang queries'] = coverage(queries)
rp_queries = [f for f in queries if f['responsibility'] != 'bp_only']
data['molang_query_pass'] = dict(baseline=dict(denominator=317, implemented=124, validated=18), current=coverage(queries),
    rp_eligible_queries=coverage(rp_queries), current_learn_index_count=306, legacy_unindexed=sorted(unindexed),
    bp_only=sorted(bp_only), docs_commit=COMMIT, completion='Partial implementation pass; 317/317 implementation and per-supported-query validation goals NOT achieved.',
    inventory_corrections='Five documentation filenames contain a stray period; API spellings normalized against page titles/current index. No new leaves found in the current Learn index.',
    notes=['Legacy 317 denominator retained for comparison; explicitly BP-only queries are not counted as missing RP gameplay. Separate RP-eligible denominator also reported.',
        'Functional breadth includes partial Java adapters and previously missed existing providers, not 317-contract semantic parity.'])
results = []
for p in pathlib.Path('/tmp/crossrock-rp-audit-G6didE/build/root/test-results/test').glob('TEST-*.xml'):
    r = ET.parse(p).getroot()
    results.append(dict(suite=r.attrib['name'], **{k: int(r.attrib.get(k, 0)) for k in ('tests', 'failures', 'errors', 'skipped')}))
data['validation']['fresh_unit_run'] = {k: sum(r[k] for r in results) for k in ('tests', 'failures', 'errors', 'skipped')}
data['validation']['suite_results'] = results
data['validation']['query_pass_client_log'] = '/tmp/crossrock-rp-audit-G6didE/molang-final.log'
assert 'BUILD SUCCESSFUL' in pathlib.Path(data['validation']['query_pass_client_log']).read_text(), 'Final client/unit run has not passed'
data['validation']['query_pass_client_result'] = 'Passed full client/integrated-server suite with read-only Actions & Stuff 1.12; no full visual or cross-engine certification.'
materials_log = pathlib.Path('/tmp/crossrock-rp-audit-G6didE/molang-materials.log')
assert 'BUILD SUCCESSFUL' in materials_log.read_text(), 'Focused material/trail regressions have not passed'
data['validation']['query_pass_materials_trails'] = dict(log=str(materials_log), result='Passed focused Actions & Stuff material/trail client/integrated-server checks; no exhaustive visual parity certification.')
for f in queries:
    for t in f['tests']:
        if t.get('run') == 'passed_query_pass':
            assert re.search(r'\b' + re.escape(t['method']) + r'\s*\(', (ROOT / t['path']).read_text()), t

def metric(stats, key):
    return f"{stats[key]['numerator']}/{stats['denominator']} ({stats[key]['percent']:.1f}%)"

qstats = coverage(queries)
astats = data['coverage']['all_inventory_units']
md = (ROOT / 'BEDROCK_RP_COVERAGE.md').read_text()
md = md.replace('Updated after the particle implementation/validation pass; other API families retain the earlier audit findings.', 'Updated after the particle and partial Molang implementation/validation passes; other API families retain the earlier audit findings.')
md = re.sub(r'^\| Molang queries \|.*$', '| Molang queries | ' + ' | '.join(metric(qstats, k) for k in ('implemented', 'validated', 'substantially_complete')) + ' |', md, flags=re.M)
md = re.sub(r'^\| All enumerated inventory units \|.*$', '| All enumerated inventory units | ' + ' | '.join(metric(astats, k) for k in ('implemented', 'validated', 'substantially_complete')) + ' |', md, flags=re.M)
md = re.sub(r'Fresh isolated unit suite: \*\*\d+ tests, \d+ failures, \d+ errors, \d+ skipped\*\*',
    'Fresh isolated unit suite: **' + ', '.join(f"{data['validation']['fresh_unit_run'][k]} {k}" for k in ('tests', 'failures', 'errors', 'skipped')) + '**', md)
query_block = '''\n## Molang query follow-up (2026-10-12)\n\nThis is a **partial implementation pass**, not completion of the requested 317-query goal. See [individual query inventory](MOLANG_QUERY_COMPATIBILITY.md) and `molang_query_pass` in the JSON. Five malformed documentation filename spellings are normalized to their documented API names. Learn's current index lists 306 queries; the official leaf corpus has 317 including 11 legacy/unindexed entries. Four leaves are explicitly BP/server-only and are not missing RP gameplay. The legacy 317 denominator is retained for comparison, with a separate 313 RP-eligible denominator in JSON.\n\nBehavioral tests now exercise common Java state adapters, numerical/argument boundaries, rendered-item durability/tags, block-neighbor coordinates, live particle query contexts and optional property integration. Existing incomplete/constant inputs are still limitations; tests do not certify all supported leaves or every Actions & Stuff animation/morph/facial state. Runtime engine-version gating is still absent.\n'''
md = re.sub(r'\n## Molang query follow-up \(2026-10-12\).*?(?=\n## |\Z)', '', md, flags=re.S)
md += query_block
# Replace query matrix lines, retaining every other family unchanged.
lines = []
by_id = {f['id']: f for f in queries}
for line in md.splitlines():
    found = re.search(r'`(query\.[a-z_.]+)`', line)
    if found and line.startswith('|'):
        key = 'query.' + found.group(1).removeprefix('query.').replace('.', '_')
        f = by_id.get(key)
        if f:
            ev = f['evidence'][0] if f['evidence'] else None
            refs = f"[{pathlib.Path(ev['path']).stem}]({ROOT / ev['path']}:{ev['line']})" if ev else 'No provider'
            line = f"| `{key}` | {f['status']} / {f['validation_level']} | {' '.join(f['limitations']).replace('|', '/')} | {refs} | [Official contract]({f['documentation'][0]}) |"
    lines.append(line)
md = '\n'.join(lines) + '\n'

roadmap = (ROOT / 'BEDROCK_RP_ROADMAP.md').read_text()
roadmap = roadmap.replace('Audit-only plan, 2026-10-12.', 'Audit baseline with particle and partial Molang implementation follow-ups, 2026-10-12.').replace('No implementation was performed during the audit.', 'The initial audit made no production changes; subsequent scoped passes are tracked separately.')
roadmap = re.sub(r'\n## Molang query pass priorities.*?(?=\n## |\Z)', '', roadmap, flags=re.S)
roadmap += '''\n## Molang query pass priorities\n\nThe 317-query goal remains open; see `MOLANG_QUERY_COMPATIBILITY.md`. Reusable snapshot integration now exists through `MolangQueryProvider` and `NativeMolangQueries.register`; this is not a claim of BP execution or unavailable-data support.\n\n1. Finish remaining RP-feasible query providers: frame statistics, bone AABB/matrix and locator/default-pivot queries, target/head indexing, full equipment/inventory/cooldown contexts, surface particle sampling, client input, missing vanilla actor states and event contexts. Do not label all remaining queries BP-dependent.\n2. Add targeted tests to every previously supported leaf; current evidence is still incomplete. Expand positive entity-specific cases beyond non-applicable zero assertions. Test offscreen/reentry, remote synchronization, detached particles, UI/hand/attachable clocks and reload isolation.\n3. Add engine/schema context and enforce documented version/removal behavior. Define numerical parity (float precision, epsilon and angle conventions) using an independent Bedrock oracle. Resolve player position origin, position_delta sampling, hurt_time units and armor enum semantics before changing them globally.\n4. Correct particle startup statistics and add total-world counters without confusing imported particles with vanilla particles. Keep child-query context and locator transforms under regression coverage.\n5. Integrate authoritative external properties only through synchronized snapshot providers. The four explicitly BP/server-only query leaves remain outside RP gameplay completeness.\n'''

report = f'''# Crossrock Molang query compatibility\n\nPartial implementation/validation pass, 2026-10-12. **The 317/317 goal has not been achieved.**\n\n## Coverage and counting\n\n- Functional breadth (including partial adapters): **{metric(qstats, 'implemented')}**; baseline 124/317.\n- Targeted behavioral subsets: **{metric(qstats, 'validated')}**; baseline 18/317.\n- Certified substantially complete: **0/317**. No Bedrock differential oracle was run.\n- RP-eligible inventory excludes four explicitly BP/server-only leaves: **{metric(coverage(rp_queries), 'implemented')}** functional, **{metric(coverage(rp_queries), 'validated')}** targeted.\n- 317 official documentation leaves checked for inventory/restrictions; 306 in the [current Learn index](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/molangreference/examples/molangconcepts/queryfunctions?view=minecraft-bedrock-stable). The remaining 11 are retained as legacy/unindexed contracts, not silently removed to improve percentages.\n- Official repository HEAD was rechecked against commit `{COMMIT}`. The five filenames with stray periods are normalized to documented underscores: cardinal_player_facing, has_player_rider, is_jump_goal_jumping, movement_direction and player_level. JSON retains old IDs in `legacy_inventory_id`.\n- “Partial” means an executable path, not correct behavior for every entity/context/argument/version. Existing baseline providers retain baseline limitations unless explicitly reassessed. Parsing alone and the optional provider interface receive no support credit.\n\n## Runtime changes\n\nCommon client-visible actor state, cube squish history, panda counters/blends, world day/moon observations and player facing/level now have providers. Query utilities are available through Molang.Context regardless of actor/particle/attachable consumer. Numeric slider settings execute. Axis indices reject missing, fractional, non-finite and out-of-range arguments.\n\nRendered attachable items have their own durability/enchantment/stackability/tag snapshots instead of implicitly reading the owner's main hand. Equipment snapshots include body armor damage and hand-specific remaining use time. Missing known slots are empty; unknown slots remain errors. Block-neighbor tags use the containing block origin, unlike actor-relative tags.\n\nParticle definitions can observe live per-effect emitter/particle counts and actual emitter-root camera distance, including unbound effects. Actor queries continue through the owning actor snapshot; child variable scopes remain independent. Particle query contexts no longer fabricate universal zero in an actor-less provider. Unsupported actor queries still lack a value; entity rendering retains its existing content-error fallback.\n\n`MolangQueryProvider` represents unavailable data as Optional.empty, distinct from present false/zero/string results. `NativeMolangQueries.register` supplies per-actor extraction snapshots, forwarded through ordinary entity contexts to render controllers, animations, owner-backed attachables and particle effects. Factories must supply observations, not execute gameplay or load chunks. Nothing here synchronizes BP properties or implements server logic.\n\n## Validation\n\nFresh complete unit suite: **{data['validation']['fresh_unit_run']['tests']} tests**, {data['validation']['fresh_unit_run']['failures']} failures, {data['validation']['fresh_unit_run']['errors']} errors, {data['validation']['fresh_unit_run']['skipped']} skipped. New tests are in `MolangQueryCompatibilityTest`; client-state assertions in `NativeQueryChecks`; live standalone/bound/unbound particle assertions in `NativeParticleQueryChecks`. Existing controller completion tests are now correctly credited.\n\nFull client/integrated-server suite runs with the read-only Actions & Stuff 1.12 pack. This exercises existing player/balance/equipment/entity/material/rendering regressions, not every facial expression, morph or pack animation. Client-state fixtures are not Bedrock visual comparisons. Several new entity-specific adapters are currently validated only for their non-applicable/zero case; their positive-state transitions need more tests. No installed test pack is modified.\n\nCommands:\n\n```sh\nBEDROCK_REFERENCE_PACK='<installed Actions and Stuff 1.12.mcpack>' ./gradlew -I /tmp/crossrock-rp-audit-G6didE/audit-build.gradle :test :runClientGameTest --rerun-tasks --console=plain\n```\n\nInitial client fixture failures were unassigned test entity IDs and a server-only allay setter; fixed with test-only ID assignment and synchronized client state. These were not reported as successful regressions. Final run evidence is recorded in the JSON validation section.\n\n## Remaining gaps (not all BP dependencies)\n\n- Frame statistics, bone AABB/orientation matrix, complete locator/default pivot contexts, surface particle texture/color sampling, full inventory/cooldown/armor enums, platform/input and several vanilla-specific actor/action states still lack providers. The inventory below is authoritative for this pass's limited support claims.\n- Version availability/removal semantics are recorded, **not enforced**. Deprecated queries may still execute regardless of min_engine_version.\n- Four documented BP/server-only queries: anger_level, had_component_group, scoreboard, server_memory_tier. They are outside RP gameplay completeness and remain unavailable; the extension interface is not an implementation of their data.\n- Exact normalized-use-time convention, player world-origin offset, movement sampling and remote state availability need Bedrock comparisons. Existing hurt_time unit inconsistency, UUID-derived skin_id, visibility constants and splash-water contact approximation remain unresolved.\n- Per-effect emitter count can omit the constructing emitter during startup expressions. Total-world counts including Java particles are unsupported. Offscreen actor/locator snapshots may be stale.\n- Generic block/item-definition and placement-event contexts are not added by the rendered-attachable item adapter. Custom tags/properties require real authoritative sources.\n- Unit coverage is not yet per-supported-contract exhaustive. Full cross-context, version, event-chain and positive actor-state parity remains open.\n\n## Individual inventory\n\nDocumentation, provider and test links are per contract. `targeted_client_state_subset` means client-state assertions, not visual verification. `code_inspected_only` is not runtime validation. The JSON contains version flags, current-index membership, legacy IDs and a short official behavior excerpt.\n\n| Query | Execution | Validation | Limitations | Evidence | Documentation |\n|---|---|---|---|---|---|\n'''
report = report.replace('Full client/integrated-server suite runs with', 'Full client/integrated-server suite passed with')
report = report.replace('Commands:\n', 'The focused Actions & Stuff material/trail suite also passed (1m 52s). Final combined unit/client run passed in 2m 20s. Logs are recorded in JSON.\n\nCommands:\n')
for f in queries:
    ev = ', '.join(f"[{pathlib.Path(e['path']).stem}]({ROOT / e['path']}:{e['line']})" for e in f['evidence'])
    ts = ', '.join(f"[{pathlib.Path(t['path']).stem}]({ROOT / t['path']})" for t in f['tests'])
    limit = ' '.join(f['limitations']).replace('|', '/')
    report += f"| `{f['name']}` | {f['status']}" + (' (BP-only)' if f['responsibility'] == 'bp_only' else '') + f" | {f['validation_level']} | {limit} | {ev}{', ' if ev and ts else ''}{ts} | [Official]({f['documentation'][0]}) |\n"

changed_names = '''MolangQueryProvider MolangQueryFunctions MolangItemQueries ParticleQueryContext
Molang EntityQueryContext EntityFrameQueries EntityEnvironmentQueries BedrockMotionInputs
NativeMolangQueries NativeEntityFacts NativeEntityRenderer NativeAttachableRenderer
NativeParticles NativeParticle MolangQueryCompatibilityTest NativeQueryChecks NativeParticleQueryChecks
NativeEntityGameTest ParticleChecks'''.split()
changed_paths = []
for name in changed_names:
    paths = list((ROOT / 'src').rglob(name + '.java'))
    assert len(paths) == 1, (name, paths)
    changed_paths.append(str(paths[0].relative_to(ROOT)))
changed_paths += ['tools/update_molang_coverage.py', 'BEDROCK_RP_COVERAGE.md', 'BEDROCK_RP_COVERAGE.json', 'BEDROCK_RP_ROADMAP.md', 'MOLANG_QUERY_COMPATIBILITY.md']
data['molang_query_pass']['changed_files'] = changed_paths
report += '\n## Files changed by this pass\n\nThe workspace already contained unrelated edits. This list identifies this pass\'s files; it does not attribute all existing changes in those files to this pass. Installed packs were read-only.\n\n'
report += '\n'.join(f'- [{path}]({ROOT / path})' for path in changed_paths) + '\n'
outputs = {'BEDROCK_RP_COVERAGE.json': json.dumps(data, indent=2, ensure_ascii=False) + '\n',
    'BEDROCK_RP_COVERAGE.md': md, 'BEDROCK_RP_ROADMAP.md': roadmap, 'MOLANG_QUERY_COMPATIBILITY.md': report}
print('*** Begin Patch')
for name, content in outputs.items():
    p = ROOT / name
    if p.exists():
        old = p.read_text()
        if old == content:
            continue
        print('*** Update File: ' + str(p))
        print('@@')
        for line in old.splitlines():
            print('-' + line)
    else:
        print('*** Add File: ' + str(p))
    for line in content.splitlines():
        print('+' + line)
print('*** End Patch')
