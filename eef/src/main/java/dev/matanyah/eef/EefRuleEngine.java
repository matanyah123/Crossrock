package dev.matanyah.eef;

import java.util.*;
import java.util.random.RandomGenerator;

/** Reloadable rule runtime: match, select, cool down, schedule, and execute. */
public final class EefRuleEngine implements EntityEventListener {
	private volatile List<EefRule> rules = List.of();
	private volatile Map<EntityEventKind, List<EefRule>> rulesByEvent = Map.of();
	private volatile Set<Double> cachedProximityThresholds = Set.of();
	private volatile Set<String> stateObservedEntityTypes = Set.of();
	private volatile boolean observesAllEntityStateTypes;
	private volatile Set<String> requiredConditionPaths = Set.of();
	private volatile EefActionHandler handler = (action, event) -> {};
	private volatile EefSubjectResolver subjectResolver = EefSubjectResolver.NONE;
	private final RandomGenerator random;
	private final Map<String, Long> cooldownUntil = new HashMap<>();
	private final Map<String, String> lastOutcome = new HashMap<>();
	private final Map<String, Long> activeSequences = new HashMap<>();
	private final Map<String, Long> activeSubjectMatches = new HashMap<>();
	private record ActivationState(long matchingSince, boolean qualified, boolean fired, long lastSeen, long resetAfterTicks) {}
	private final Map<String, ActivationState> activations = new HashMap<>();
	private final Map<String, Long> audioChannelUntil = new HashMap<>();
	private final Map<String, Long> speakerAudioUntil = new HashMap<>();
	private final Map<String, Long> frequencyCounts = new HashMap<>();
	private final EefScheduler scheduler = new EefScheduler();
	private final EefStateStore state = new EefStateStore();
	private double cooldownMultiplier = 1.0;
	private EefRulePreferences preferences = EefRulePreferences.DEFAULT;

	public EefRuleEngine() { this(RandomGenerator.getDefault()); }
	public EefRuleEngine(EefActionHandler handler) { this(); this.handler = Objects.requireNonNull(handler); }
	public EefRuleEngine(RandomGenerator random) { this.random = Objects.requireNonNull(random); }
	public EefRuleEngine(RandomGenerator random, EefActionHandler handler) { this(random); this.handler = Objects.requireNonNull(handler); }
	public void setActionHandler(EefActionHandler handler) { this.handler = Objects.requireNonNull(handler); }
	public void setSubjectResolver(EefSubjectResolver resolver) { this.subjectResolver = Objects.requireNonNull(resolver); }
	public synchronized void setRulePreferences(EefRulePreferences preferences) { this.preferences = Objects.requireNonNull(preferences); frequencyCounts.clear(); }
	/** Scales every pack-defined cooldown while preserving the relative timing chosen by its author. */
	public synchronized void setCooldownMultiplier(double multiplier) {
		double bounded = Double.isFinite(multiplier) ? Math.max(EefConfig.MIN_COOLDOWN_MULTIPLIER,
			Math.min(EefConfig.MAX_COOLDOWN_MULTIPLIER, multiplier)) : 1.0;
		if (Double.compare(cooldownMultiplier, bounded) != 0) cooldownUntil.clear();
		cooldownMultiplier = bounded;
	}
	public synchronized double cooldownMultiplier() { return cooldownMultiplier; }
	public synchronized void reload(EefRuleSet set) {
		rules = List.copyOf(set.rules());
		EnumMap<EntityEventKind, List<EefRule>> indexed = new EnumMap<>(EntityEventKind.class);
		for (EefRule rule : rules) indexed.computeIfAbsent(rule.event(), ignored -> new ArrayList<>()).add(rule);
		indexed.replaceAll((kind, matching) -> List.copyOf(matching));
		rulesByEvent = Collections.unmodifiableMap(indexed);
		cachedProximityThresholds = collectProximityThresholds(rules);
		StateTypeFilter stateFilter = collectStateTypeFilter(indexed.getOrDefault(EntityEventKind.ENTITY_STATE_CHANGED, List.of()));
		stateObservedEntityTypes = stateFilter.types(); observesAllEntityStateTypes = stateFilter.allTypes();
		requiredConditionPaths = rules.stream().flatMap(rule -> rule.conditions().stream()).map(EefCondition::path)
			.collect(java.util.stream.Collectors.toUnmodifiableSet());
		resetRuntimeState();
	}
	/** Clears world-session state without discarding resource-pack rules. */
	public synchronized void resetRuntimeState() {
		cooldownUntil.clear(); lastOutcome.clear(); activeSequences.clear(); activeSubjectMatches.clear();
		activations.clear(); audioChannelUntil.clear(); speakerAudioUntil.clear(); frequencyCounts.clear(); scheduler.clear(); state.clear();
	}
	public List<EefRule> rules() { return rules; }
	/** Stable, pack-authored catalog exposed to the developer command UI. */
	public List<RuleSummary> ruleCatalog() {
		return rules.stream().map(rule -> new RuleSummary(rule.id(), rule.event(),
			rule.outcomes().stream().map(EefOutcome::id).toList())).sorted(Comparator.comparing(RuleSummary::id)).toList();
	}
	public record RuleSummary(String id, EntityEventKind event, List<String> outcomes) {}
	public enum ManualPlayStatus { PLAYED, NOT_FOUND, AMBIGUOUS, INVALID_VARIANT, NO_OUTCOMES }
	public record ManualPlayResult(ManualPlayStatus status, String ruleId, String outcomeId,
		int variant, int variantCount, List<String> matches) {}

	/**
	 * Explicit developer playback. It intentionally bypasses event conditions, chance,
	 * frequency, and cooldowns, while retaining normal weighted selection, scheduling,
	 * channels, subtitles, and positional actions.
	 */
	public synchronized ManualPlayResult playManual(String query, int oneBasedVariant, EntityEvent event) {
		List<EefRule> matches = findManualRules(query);
		if (matches.isEmpty()) return manualResult(ManualPlayStatus.NOT_FOUND, null, null, 0, 0, List.of());
		if (matches.size() > 1) return manualResult(ManualPlayStatus.AMBIGUOUS, null, null, 0, 0,
			matches.stream().limit(20).map(EefRule::id).toList());
		EefRule rule = matches.getFirst();
		if (rule.outcomes().isEmpty()) return manualResult(ManualPlayStatus.NO_OUTCOMES, rule.id(), null, 0, 0, List.of());
		if (oneBasedVariant < 0 || oneBasedVariant > rule.outcomes().size())
			return manualResult(ManualPlayStatus.INVALID_VARIANT, rule.id(), null, oneBasedVariant, rule.outcomes().size(), List.of());
		EefOutcome outcome = oneBasedVariant == 0 ? select(rule, event) : rule.outcomes().get(oneBasedVariant - 1);
		if (outcome == null) return manualResult(ManualPlayStatus.NO_OUTCOMES, rule.id(), null, 0, rule.outcomes().size(), List.of());
		int selectedVariant = rule.outcomes().indexOf(outcome) + 1;
		lastOutcome.put(rule.id() + ':' + event.entityKey(), outcome.id());
		queueOutcome(rule, outcome, event);
		return manualResult(ManualPlayStatus.PLAYED, rule.id(), outcome.id(), selectedVariant, rule.outcomes().size(), List.of());
	}

	/** Exact IDs win; otherwise normalized trigger names, outcome IDs, and action identifiers are searchable. */
	public List<EefRule> findManualRules(String query) {
		String needle = normalizeSearch(query);
		if (needle.isBlank()) return List.of();
		List<EefRule> exact = rules.stream().filter(rule -> normalizeSearch(rule.id()).equals(needle)).toList();
		if (!exact.isEmpty()) return exact;
		return rules.stream().filter(rule -> searchableText(rule).contains(needle))
			.sorted(Comparator.comparing(EefRule::id)).toList();
	}
	private static ManualPlayResult manualResult(ManualPlayStatus status, String ruleId, String outcomeId,
		int variant, int count, List<String> matches) {
		return new ManualPlayResult(status, ruleId, outcomeId, variant, count, List.copyOf(matches));
	}
	private static String searchableText(EefRule rule) {
		StringBuilder value = new StringBuilder(normalizeSearch(rule.id()));
		for (EefOutcome outcome : rule.outcomes()) {
			value.append(' ').append(normalizeSearch(outcome.id()));
			appendActionSearch(value, outcome.actions());
		}
		return value.toString();
	}
	private static void appendActionSearch(StringBuilder value, List<EefAction> actions) {
		for (EefAction action : actions) {
			for (String key : List.of("sound", "translation_key", "text")) {
				Object candidate = action.parameters().get(key);
				if (candidate != null) value.append(' ').append(normalizeSearch(String.valueOf(candidate)));
			}
			if (action.type().equals("sequence") && action.parameters().get("steps") instanceof List<?> steps)
				for (Object rawStep : steps) if (rawStep instanceof Map<?,?> step && step.get("actions") instanceof List<?> nested) {
					List<EefAction> parsed = new ArrayList<>();
					for (Object rawAction : nested) if (rawAction instanceof Map<?,?> map) {
						Map<String,Object> copy = new LinkedHashMap<>(); map.forEach((key, item) -> copy.put(String.valueOf(key), item));
						parsed.add(actionFromMap(copy));
					}
					appendActionSearch(value, parsed);
				}
		}
	}
	private static String normalizeSearch(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).trim()
			.replaceAll("[^a-z0-9:.]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
	}
	public boolean captures(EntityEventKind kind) { return !rulesByEvent.getOrDefault(kind, List.of()).isEmpty(); }
	public boolean observesEntityState(String entityType) { return observesAllEntityStateTypes || stateObservedEntityTypes.contains(entityType); }
	public boolean requiresContextPrefix(String prefix) { return requiredConditionPaths.stream().anyMatch(path -> path.startsWith(prefix)); }
	public int scheduledActionCount() { return scheduler.size(); }
	public boolean isSequenceActive(String key, long tick) { return activeSequences.getOrDefault(key, Long.MIN_VALUE) >= tick; }
	/** Distances referenced by range-transition rules; capture remains entirely pack-driven. */
	public Set<Double> proximityThresholds() {
		return cachedProximityThresholds;
	}
	private static Set<Double> collectProximityThresholds(List<EefRule> rules) {
		Set<Double> thresholds = new TreeSet<>();
		for (EefRule rule : rules) if (rule.event() == EntityEventKind.ENTITY_ENTERED_RANGE || rule.event() == EntityEventKind.ENTITY_EXITED_RANGE)
			for (EefCondition condition : rule.conditions())
				if (condition.path().equals("distance.observer_to_self") && condition.value() instanceof Number number && number.doubleValue() > 0)
					thresholds.add(number.doubleValue());
		return Collections.unmodifiableSet(thresholds);
	}
	/** True when the event matches a rule capable of replacing its vanilla sound. */
	public boolean hasMatchingSoundReplacement(EntityEvent event) {
		for (EefRule rule : rulesByEvent.getOrDefault(event.kind(), List.of())) if (preferences.enabled(rule)
				&& rule.chance() == 1 && !rule.outcomes().isEmpty() && matches(rule, event, state)
				&& rule.outcomes().stream().allMatch(outcome -> containsPlaySound(outcome.actions()))) return true;
		return false;
	}
	/** True when an enabled rule pack provides sound dialogue for this generic event/entity pair. */
	public boolean hasSoundProvider(EntityEventKind event, String entityType) {
		for (EefRule rule : rulesByEvent.getOrDefault(event, List.of())) {
			if (!preferences.enabled(rule) || rule.outcomes().isEmpty()
					|| !rule.outcomes().stream().allMatch(outcome -> containsPlaySound(outcome.actions()))) continue;
			boolean typeAllowed = rule.conditions().stream().filter(condition -> condition.path().equals("self.type"))
				.allMatch(condition -> condition.test(entityType));
			if (typeAllowed) return true;
		}
		return false;
	}

	@Override public synchronized void onEntityEvent(EntityEvent event) {
		tick(event.gameTick());
		IdentityHashMap<EntityEvent, Map<EefSubjectQuery, List<EntityEvent>>> subjectCache = new IdentityHashMap<>();
		for (EefRule rule : rulesByEvent.getOrDefault(event.kind(), List.of())) {
			if (preferences.muted()) continue;
			if (!matchesEnvelope(rule, event)) continue;
			if (!preservedParticipantConditionsAllow(rule, event)) continue;
			List<EntityEvent> boundEvents = bindSubjects(rule, event, subjectCache);
			List<EntityEvent> matched = boundEvents.stream().filter(bound -> activationAllows(rule, bound, matches(rule, bound, state))).toList();
			if (requiresExitToRetrigger(rule)) {
				String key = subjectMatchKey(rule, event);
				if (matched.isEmpty()) { activeSubjectMatches.remove(key); continue; }
				long grace = rule.subjects().stream().filter(query -> query.retrigger() == EefSubjectQuery.Retrigger.AFTER_EXIT)
					.mapToLong(EefSubjectQuery::exitGraceTicks).max().orElse(40);
				boolean alreadyActive = activeSubjectMatches.put(key, event.gameTick() + grace) != null;
				if (alreadyActive) continue;
			}
			if (synchronizedFanout(rule)) {
				executeSynchronizedFanout(rule, event, matched);
				continue;
			}
			for (EntityEvent bound : matched) {
				if (!preferences.enabled(rule)) continue;
				if (random.nextDouble() >= rule.chance()) continue;
				if (!preferences.important(rule) && speakerAudioUntil.getOrDefault(bound.entityKey(), Long.MIN_VALUE) > bound.gameTick()) continue;
				int frequency = Math.max(1, Math.min(10, preferences.frequency(rule)));
				String frequencyKey = rule.id() + ':' + bound.entityKey();
				long occurrence = frequencyCounts.merge(frequencyKey, 1L, Long::sum);
				if (occurrence % frequency != 0) continue;
				if (rule.cooldowns().stream().anyMatch(cooldown -> isCoolingDown(rule, bound, cooldown))) continue;
				EefOutcome outcome = select(rule, bound);
				if (outcome == null) continue;
				for (EefCooldown cooldown : rule.cooldowns()) cooldownUntil.put(cooldownKey(rule, bound, cooldown.scope()),
					bound.gameTick() + scaledCooldownTicks(cooldown.ticks()));
				lastOutcome.put(rule.id() + ':' + bound.entityKey(), outcome.id());
				queueOutcome(rule, outcome, bound);
			}
		}
	}

	private boolean synchronizedFanout(EefRule rule) {
		return rule.subjects().stream().anyMatch(query -> query.selection() == EefSubjectQuery.Selection.ALL && query.synchronize());
	}

	/** A synchronized all-subject query is one occurrence whose eligible speakers begin together. */
	private void executeSynchronizedFanout(EefRule rule, EntityEvent original, List<EntityEvent> matched) {
		if (matched.isEmpty() || !preferences.enabled(rule) || random.nextDouble() >= rule.chance()) return;
		int frequency = Math.max(1, Math.min(10, preferences.frequency(rule)));
		String frequencyKey = rule.id() + ":fanout:" + original.entityKey();
		long occurrence = frequencyCounts.merge(frequencyKey, 1L, Long::sum);
		if (occurrence % frequency != 0) return;
		List<EntityEvent> eligible = matched.stream().filter(bound ->
			(preferences.important(rule) || speakerAudioUntil.getOrDefault(bound.entityKey(), Long.MIN_VALUE) <= bound.gameTick())
			&& rule.cooldowns().stream().noneMatch(cooldown -> isCoolingDown(rule, bound, cooldown))).toList();
		if (eligible.isEmpty()) return;
		List<Map.Entry<EntityEvent,EefOutcome>> selected = new ArrayList<>();
		long commonDelay = 0;
		for (EntityEvent bound : eligible) {
			EefOutcome outcome = select(rule, bound);
			if (outcome == null) continue;
			selected.add(Map.entry(bound, outcome));
			commonDelay = Math.max(commonDelay, audioBundleAvailableDelay(rule, outcome.actions(), bound));
		}
		for (Map.Entry<EntityEvent,EefOutcome> execution : selected) {
			EntityEvent bound = execution.getKey(); EefOutcome outcome = execution.getValue();
			for (EefCooldown cooldown : rule.cooldowns()) cooldownUntil.put(cooldownKey(rule, bound, cooldown.scope()),
				bound.gameTick() + scaledCooldownTicks(cooldown.ticks()));
			lastOutcome.put(rule.id() + ':' + bound.entityKey(), outcome.id());
			reserveAudioBundle(rule, outcome.actions(), bound, commonDelay);
			for (EefAction action : outcome.actions()) queueOrExecute(action, bound, rule.id(), null, commonDelay);
		}
	}

	private List<EntityEvent> bindSubjects(EefRule rule, EntityEvent original,
		IdentityHashMap<EntityEvent, Map<EefSubjectQuery, List<EntityEvent>>> cache) {
		List<EntityEvent> current = List.of(original);
		for (EefSubjectQuery query : rule.subjects()) {
			List<EntityEvent> next = new ArrayList<>();
			for (EntityEvent event : current) {
				List<EntityEvent> resolved = cache.computeIfAbsent(event, ignored -> new HashMap<>())
					.computeIfAbsent(query, ignored -> List.copyOf(subjectResolver.resolve(query, event)));
				List<EntityEvent> candidates = resolved.stream()
					.filter(candidate -> query.conditions().stream().allMatch(condition -> condition.test(candidate.value(condition.path()).orElse(null))))
					.toList();
				if (query.selection() == EefSubjectQuery.Selection.RANDOM) {
					List<EntityEvent> shuffled = new ArrayList<>(candidates);
					for (int i = shuffled.size() - 1; i > 0; i--) Collections.swap(shuffled, i, random.nextInt(i + 1));
					candidates = shuffled;
				}
				int count = query.selection() == EefSubjectQuery.Selection.NEAREST ? Math.min(1, candidates.size()) : Math.min(query.limit(), candidates.size());
				next.addAll(candidates.subList(0, count));
			}
			current = List.copyOf(next);
			if (current.isEmpty()) break;
		}
		return current;
	}

	/**
	 * Conditions on a role preserved by the first subject binding can be checked against
	 * the original role before performing a world query. They are still evaluated normally
	 * after binding; this is only a safe early rejection.
	 */
	private static boolean preservedParticipantConditionsAllow(EefRule rule, EntityEvent event) {
		if (rule.subjects().isEmpty()) return true;
		Set<String> reboundRoles = rule.subjects().stream().map(EefSubjectQuery::binding).collect(java.util.stream.Collectors.toSet());
		Map<String,String> preservedRoles = new HashMap<>();
		for (EefSubjectQuery query : rule.subjects()) if (query.preserveAs() != null)
			preservedRoles.put(query.preserveAs(), query.binding());
		for (EefCondition condition : rule.conditions()) {
			String root = condition.path().contains(".") ? condition.path().substring(0, condition.path().indexOf('.')) : condition.path();
			if (root.equals("relationships") || root.equals("query") || reboundRoles.contains(root)) continue;
			String path = condition.path();
			if (preservedRoles.containsKey(root)) path = preservedRoles.get(root) + condition.path().substring(root.length());
			Optional<Object> actual = event.value(path);
			if (actual.isPresent() && !condition.test(actual.get())) return false;
		}
		return true;
	}

	private record StateTypeFilter(Set<String> types, boolean allTypes) {}
	private static StateTypeFilter collectStateTypeFilter(List<EefRule> stateRules) {
		Set<String> types = new HashSet<>();
		for (EefRule rule : stateRules) {
			EefCondition type = rule.conditions().stream().filter(condition -> condition.path().equals("self.type")
				&& (condition.operator() == EefCondition.Operator.EQ || condition.operator() == EefCondition.Operator.IN)).findFirst().orElse(null);
			if (type == null) return new StateTypeFilter(Set.of(), true);
			if (type.operator() == EefCondition.Operator.EQ) types.add(String.valueOf(type.value()));
			else if (type.value() instanceof Collection<?> values) for (Object value : values) types.add(String.valueOf(value));
		}
		return new StateTypeFilter(Set.copyOf(types), false);
	}

	public synchronized void tick(long gameTick) {
		for (EefScheduler.Scheduled scheduled : scheduler.drain(gameTick))
			executeInternal(scheduled.action(), scheduled.event(), "scheduled", scheduled.cancellationKey(), gameTick);
		activeSequences.entrySet().removeIf(entry -> entry.getValue() < gameTick);
		activeSubjectMatches.entrySet().removeIf(entry -> entry.getValue() < gameTick);
		activations.entrySet().removeIf(entry -> entry.getValue().lastSeen() + entry.getValue().resetAfterTicks() < gameTick);
		audioChannelUntil.entrySet().removeIf(entry -> entry.getValue() < gameTick);
		speakerAudioUntil.entrySet().removeIf(entry -> entry.getValue() < gameTick);
	}

	private boolean activationAllows(EefRule rule, EntityEvent event, boolean matches) {
		EefActivation activation = rule.activation();
		if (activation.mode() == EefActivation.Mode.MATCH) return matches;
		String identity = activation.keyPath() == null ? event.entityKey()
			: event.value(activation.keyPath()).map(String::valueOf).orElse("none");
		String key = rule.id() + ':' + identity;
		ActivationState previous = activations.get(key);
		if (matches) {
			long since = previous == null ? event.gameTick() : previous.matchingSince();
			boolean qualified = previous != null && previous.qualified() || event.gameTick() - since >= activation.holdTicks();
			boolean fired = previous != null && previous.fired();
			boolean fire = activation.mode() == EefActivation.Mode.ENTERED && qualified && !fired;
			activations.put(key, new ActivationState(since, qualified, fired || fire, event.gameTick(), activation.resetAfterTicks()));
			return fire;
		}
		activations.remove(key);
		return activation.mode() == EefActivation.Mode.EXITED && previous != null && previous.qualified();
	}

	private static boolean requiresExitToRetrigger(EefRule rule) {
		return rule.subjects().stream().anyMatch(query -> query.retrigger() == EefSubjectQuery.Retrigger.AFTER_EXIT);
	}
	private static String subjectMatchKey(EefRule rule, EntityEvent original) {
		return rule.id() + ':' + original.entityKey();
	}

	public static boolean matches(EefRule rule, EntityEvent event) { return matches(rule, event, new EefStateStore()); }
	private static boolean matches(EefRule rule, EntityEvent event, EefStateStore state) {
		if (!matchesEnvelope(rule, event)) return false;
		for (EefCondition condition : rule.conditions()) {
			Object actual;
			if (condition.path().startsWith("state.global."))
				actual = state.get("global", condition.path().substring(13), event.gameTick()).orElse(null);
			else if (condition.path().startsWith("state.entity."))
				actual = state.get(event.entityKey(), condition.path().substring(13), event.gameTick()).orElse(null);
			else if (condition.path().startsWith("state."))
				actual = state.get(event.entityKey(), condition.path().substring(6), event.gameTick()).orElse(null);
			else actual = event.value(condition.path()).orElse(null);
			if (!condition.test(actual)) return false;
		}
		return true;
	}
	private static boolean matchesEnvelope(EefRule rule, EntityEvent event) {
		if (rule.event() != event.kind()) return false;
		if (rule.detail() != null && (event.detail() == null || !rule.detail().equals(event.detail().toString()))) return false;
		return rule.reliability().isEmpty() || rule.reliability().contains(event.reliability());
	}

	private EefOutcome select(EefRule rule, EntityEvent event) {
		List<EefOutcome> candidates = rule.outcomes();
		if (rule.avoidImmediateRepeat() && candidates.size() > 1) {
			String previous = lastOutcome.get(rule.id() + ':' + event.entityKey());
			List<EefOutcome> filtered = candidates.stream().filter(outcome -> !outcome.id().equals(previous)).toList();
			if (!filtered.isEmpty()) candidates = filtered;
		}
		double maximum = candidates.stream().mapToDouble(EefOutcome::weight).max().orElse(1.0);
		double total = candidates.stream().mapToDouble(outcome -> preferences.outcomeWeight(rule, outcome, maximum)).sum();
		if (total <= 0) return null;
		double choice = random.nextDouble() * total;
		for (EefOutcome outcome : candidates) { choice -= preferences.outcomeWeight(rule, outcome, maximum); if (choice < 0) return outcome; }
		return candidates.isEmpty() ? null : candidates.getLast();
	}

	private boolean isCoolingDown(EefRule rule, EntityEvent event, EefCooldown cooldown) { return cooldownUntil.getOrDefault(cooldownKey(rule, event, cooldown.scope()), Long.MIN_VALUE) > event.gameTick(); }
	private long scaledCooldownTicks(long ticks) { return ticks <= 0 ? 0 : Math.max(1, Math.round(ticks * cooldownMultiplier)); }
	private static String cooldownKey(EefRule rule, EntityEvent event, EefCooldown.Scope scope) {
		return switch (scope) {
			case GLOBAL -> "global";
			case RULE -> "rule:" + rule.id();
			case ENTITY -> "entity:" + event.entityKey();
			case RULE_ENTITY -> "rule_entity:" + rule.id() + ':' + event.entityKey();
		};
	}

	private void queueOutcome(EefRule rule, EefOutcome outcome, EntityEvent event) {
		long bundleDelay = audioBundleDelay(rule, outcome.actions(), event);
		for (EefAction action : outcome.actions()) queueOrExecute(action, event, rule.id(), null, bundleDelay);
	}

	private long audioBundleDelay(EefRule rule, List<EefAction> actions, EntityEvent event) {
		long offset = audioBundleAvailableDelay(rule, actions, event);
		reserveAudioBundle(rule, actions, event, offset);
		return Math.max(0, offset);
	}

	private long audioBundleAvailableDelay(EefRule rule, List<EefAction> actions, EntityEvent event) {
		long offset = 0;
		for (EefAction action : actions) if (action.type().equals("play_sound")
				&& action.parameters().get("channel") != null && longNumber(action.parameters().get("duration_ticks"), 0) > 0) {
			if ("overlap".equals(String.valueOf(action.parameters().getOrDefault("channel_mode", "queue")))) continue;
			String key = audioChannelKey(rule, event, action);
			long requested = event.gameTick() + longNumber(action.parameters().get("delay_ticks"), 0);
			offset = Math.max(offset, audioChannelUntil.getOrDefault(key, requested) - requested);
		}
		return Math.max(0, offset);
	}

	private void reserveAudioBundle(EefRule rule, List<EefAction> actions, EntityEvent event, long offset) {
		for (EefAction action : actions) if (action.type().equals("play_sound")
				&& action.parameters().get("channel") != null && longNumber(action.parameters().get("duration_ticks"), 0) > 0) {
			if ("overlap".equals(String.valueOf(action.parameters().getOrDefault("channel_mode", "queue")))) continue;
			String key = audioChannelKey(rule, event, action);
			long start = event.gameTick() + longNumber(action.parameters().get("delay_ticks"), 0) + offset;
			long end = start + longNumber(action.parameters().get("duration_ticks"), 0)
				+ Math.max(0, longNumber(action.parameters().get("gap_ticks"), 0));
			audioChannelUntil.put(key, Math.max(audioChannelUntil.getOrDefault(key, Long.MIN_VALUE), end));
			String speaker = speakerKey(action, event);
			speakerAudioUntil.put(speaker, Math.max(speakerAudioUntil.getOrDefault(speaker, Long.MIN_VALUE), end));
		}
	}

	private static String audioChannelKey(EefRule rule, EntityEvent event, EefAction action) {
		String channel = String.valueOf(action.parameters().get("channel"));
		return switch (String.valueOf(action.parameters().getOrDefault("channel_scope", "entity"))) {
			case "global" -> "global:" + channel;
			case "rule" -> "rule:" + rule.id() + ':' + channel;
			case "rule_entity" -> "rule_entity:" + rule.id() + ':' + event.entityKey() + ':' + channel;
			case "conversation" -> {
				String path = String.valueOf(action.parameters().getOrDefault("channel_key_path", "conversation.id"));
				String conversation = event.value(path).map(String::valueOf).orElse(event.entityKey());
				yield "conversation:" + conversation + ':' + channel;
			}
			default -> "entity:" + speakerKey(action, event) + ':' + channel;
		};
	}
	private static String speakerKey(EefAction action, EntityEvent event) {
		String role = String.valueOf(action.parameters().getOrDefault("at", "self"));
		return event.value(role + ".id").map(String::valueOf).orElse(event.entityKey());
	}

	private void queueOrExecute(EefAction action, EntityEvent event, String ruleId, String cancellationKey, long baseDelay) {
		long delay = baseDelay + longNumber(action.parameters().get("delay_ticks"), 0);
		if (delay > 0) scheduler.schedule(event.gameTick() + delay, action, event, cancellationKey);
		else executeInternal(action, event, ruleId, cancellationKey, event.gameTick());
	}

	@SuppressWarnings("unchecked")
	private void executeInternal(EefAction action, EntityEvent event, String ruleId, String cancellationKey, long now) {
		switch (action.type()) {
			case "set_state" -> state.put(stateScope(action, event), String.valueOf(action.parameters().get("key")), action.parameters().getOrDefault("value", true), now, longNumber(action.parameters().get("ttl_ticks"), 0));
			case "clear_state" -> state.remove(stateScope(action, event), String.valueOf(action.parameters().get("key")));
			case "cancel_sequence" -> { String key = sequenceKey(action, event, ruleId); scheduler.cancel(key); activeSequences.remove(key); }
			case "sequence" -> {
				String key = sequenceKey(action, event, ruleId);
				String mode = String.valueOf(action.parameters().getOrDefault("mode", "replace"));
				if (isSequenceActive(key, now) && mode.equals("ignore")) return;
				if (isSequenceActive(key, now)) scheduler.cancel(key);
				long end = now;
				Object rawSteps = action.parameters().get("steps");
				if (rawSteps instanceof List<?> steps) for (Object raw : steps) if (raw instanceof Map<?, ?> step) {
					long delay = longNumber(step.get("delay_ticks"), 0);
					Object rawActions = step.get("actions");
					if (rawActions instanceof List<?> actions) for (Object rawAction : actions)
						if (rawAction instanceof Map<?, ?> map) {
							EefAction nested = actionFromMap((Map<String,Object>) map);
							if (delay <= 0) executeInternal(nested, event, ruleId, key, now);
							else scheduler.schedule(now + delay, nested, event, key);
						}
					end = Math.max(end, now + delay);
				}
				activeSequences.put(key, end);
			}
			default -> handler.execute(action, event);
		}
	}

	private static EefAction actionFromMap(Map<String,Object> map) { Map<String,Object> parameters = new LinkedHashMap<>(map); String type = String.valueOf(parameters.remove("type")); return new EefAction(type, parameters); }
	private static boolean containsPlaySound(List<EefAction> actions) {
		for (EefAction action : actions) {
			if (action.type().equals("play_sound")) return true;
			if (action.type().equals("sequence") && action.parameters().get("steps") instanceof List<?> steps)
				for (Object rawStep : steps) if (rawStep instanceof Map<?,?> step && step.get("actions") instanceof List<?> nested)
					for (Object rawAction : nested) if (rawAction instanceof Map<?,?> map && "play_sound".equals(map.get("type"))) return true;
		}
		return false;
	}
	private static String stateScope(EefAction action, EntityEvent event) { return String.valueOf(action.parameters().getOrDefault("scope", "entity")).equals("global") ? "global" : event.entityKey(); }
	private static String sequenceKey(EefAction action, EntityEvent event, String ruleId) { return String.valueOf(action.parameters().getOrDefault("key", ruleId)) + ':' + event.entityKey(); }
	private static long longNumber(Object value, long fallback) { return value instanceof Number number ? number.longValue() : fallback; }
}
