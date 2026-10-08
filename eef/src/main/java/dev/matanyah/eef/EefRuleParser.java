package dev.matanyah.eef;

import com.google.gson.*;
import java.util.*;

/** Parser for declarative assets/&lt;namespace&gt;/eef/*.json rule files. */
public final class EefRuleParser {
	private EefRuleParser() {}

	public static EefRuleSet parse(String json) {
		JsonElement parsed;
		try { parsed = JsonParser.parseString(json); }
		catch (RuntimeException exception) { throw new JsonParseException("Malformed EEF JSON", exception); }
		if (!parsed.isJsonObject()) throw new JsonParseException("EEF root must be an object");
		JsonObject root = parsed.getAsJsonObject();
		String id = string(root, "id", "pack");
		int schema = root.has("schema") ? root.get("schema").getAsInt() : 1;
		if (schema < 1 || schema > 2) throw new JsonParseException("Unsupported EEF schema: " + schema);
		JsonArray array = root.getAsJsonArray("rules");
		if (array == null) throw new JsonParseException("EEF rule set is missing rules[]");
		List<EefRule> rules = new ArrayList<>();
		for (JsonElement element : array) {
			if (!element.isJsonObject()) throw new JsonParseException("EEF rule must be an object");
			JsonObject object = element.getAsJsonObject();
			String eventName = requiredString(object, "event").toUpperCase(Locale.ROOT).replace('-', '_');
			EntityEventKind event;
			try { event = EntityEventKind.valueOf(eventName); }
			catch (IllegalArgumentException exception) { throw new JsonParseException("Unknown EEF event: " + eventName); }
			List<EefCondition> conditions = parseConditions(object.get("conditions"));
			String entity = string(object, "entity", null);
			if (entity != null) conditions.add(new EefCondition("self.type", EefCondition.Operator.EQ, entity));
			try {
				rules.add(new EefRule(string(object, "id", "rule_" + rules.size()), event,
					string(object, "detail", null), parseReliability(object.get("reliability")), parseSubjects(object.get("subjects")), conditions,
					parseActivation(object.get("activation")),
					object.has("chance") ? object.get("chance").getAsDouble() : 1.0,
					object.has("frequency") ? object.get("frequency").getAsInt() : 1,
					!object.has("important") || object.get("important").getAsBoolean(),
					parseCooldowns(object.get("cooldown")),
					object.has("avoid_immediate_repeat") && object.get("avoid_immediate_repeat").getAsBoolean(),
					parseOutcomes(object, rules.size())));
			} catch (IllegalArgumentException exception) { throw new JsonParseException(exception.getMessage(), exception); }
		}
		return new EefRuleSet(id, schema, rules);
	}

	private static EefActivation parseActivation(JsonElement element) {
		if (element == null || element.isJsonNull()) return EefActivation.DEFAULT;
		if (!element.isJsonObject()) throw new JsonParseException("activation must be an object");
		JsonObject object = element.getAsJsonObject();
		String modeName = string(object, "mode", "match").toUpperCase(Locale.ROOT).replace('-', '_');
		try {
			return new EefActivation(EefActivation.Mode.valueOf(modeName),
				object.has("hold_ticks") ? object.get("hold_ticks").getAsLong() : 0,
				object.has("reset_after_ticks") ? object.get("reset_after_ticks").getAsLong() : 100,
				string(object, "key", null));
		} catch (IllegalArgumentException exception) {
			throw new JsonParseException("Invalid activation: " + exception.getMessage(), exception);
		}
	}

	private static List<EefSubjectQuery> parseSubjects(JsonElement element) {
		if (element == null || element.isJsonNull()) return List.of();
		if (!element.isJsonObject()) throw new JsonParseException("subjects must be an object");
		List<EefSubjectQuery> result = new ArrayList<>();
		for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
			if (!entry.getValue().isJsonObject()) throw new JsonParseException("subject query must be an object");
			JsonObject object = entry.getValue().getAsJsonObject();
			String selectionName = string(object, "selection", "nearest").toUpperCase(Locale.ROOT).replace('-', '_');
			EefSubjectQuery.Selection selection;
			try { selection = EefSubjectQuery.Selection.valueOf(selectionName); }
			catch (IllegalArgumentException exception) { throw new JsonParseException("Unknown subject selection: " + selectionName); }
			String retriggerName = string(object, "retrigger", "cooldown").toUpperCase(Locale.ROOT).replace('-', '_');
			EefSubjectQuery.Retrigger retrigger;
			try { retrigger = EefSubjectQuery.Retrigger.valueOf(retriggerName); }
			catch (IllegalArgumentException exception) { throw new JsonParseException("Unknown subject retrigger mode: " + retriggerName); }
			List<EefCondition> conditions = parseConditions(object.get("conditions")).stream()
				.map(condition -> condition.path().startsWith(entry.getKey() + ".") ? condition
					: new EefCondition(entry.getKey() + "." + condition.path(), condition.operator(), condition.value()))
				.toList();
			try {
				result.add(new EefSubjectQuery(entry.getKey(), string(object, "query", "nearby_entities"),
					string(object, "origin", "event.position"), object.has("radius") ? object.get("radius").getAsDouble() : 16,
					conditions, selection, object.has("limit") ? object.get("limit").getAsInt()
						: selection == EefSubjectQuery.Selection.ALL ? 256 : 1,
					string(object, "preserve_as", null), stringList(object.get("exclude")),
					object.has("line_of_sight") && object.get("line_of_sight").getAsBoolean(), retrigger,
					object.has("exit_grace_ticks") ? object.get("exit_grace_ticks").getAsLong() : 40,
					object.has("synchronize") && object.get("synchronize").getAsBoolean()));
			} catch (IllegalArgumentException exception) { throw new JsonParseException(exception.getMessage(), exception); }
		}
		return List.copyOf(result);
	}

	private static List<String> stringList(JsonElement element) {
		if (element == null || element.isJsonNull()) return List.of();
		if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) return List.of(element.getAsString());
		if (!element.isJsonArray()) throw new JsonParseException("Expected a string or string array");
		List<String> result = new ArrayList<>();
		for (JsonElement item : element.getAsJsonArray()) {
			if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) throw new JsonParseException("Expected a string array");
			result.add(item.getAsString());
		}
		return List.copyOf(result);
	}

	private static List<EefCondition> parseConditions(JsonElement element) {
		List<EefCondition> result = new ArrayList<>();
		if (element == null || element.isJsonNull()) return result;
		if (element.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet())
				result.add(new EefCondition(entry.getKey(), EefCondition.Operator.EQ, value(entry.getValue())));
			return result;
		}
		if (!element.isJsonArray()) throw new JsonParseException("conditions must be an object or array");
		for (JsonElement item : element.getAsJsonArray()) {
			if (!item.isJsonObject()) throw new JsonParseException("condition must be an object");
			JsonObject object = item.getAsJsonObject();
			String name = string(object, "operator", "eq").toUpperCase(Locale.ROOT).replace('-', '_');
			EefCondition.Operator operator;
			try { operator = EefCondition.Operator.valueOf(name); }
			catch (IllegalArgumentException exception) { throw new JsonParseException("Unknown EEF condition operator: " + name); }
			result.add(new EefCondition(requiredString(object, "path"), operator,
				object.has("value") ? value(object.get("value")) : null));
		}
		return result;
	}

	private static List<EefOutcome> parseOutcomes(JsonObject rule, int ruleIndex) {
		JsonArray array = rule.getAsJsonArray("outcomes");
		if (array == null) return List.of(new EefOutcome("default", 1, parseActions(rule.get("actions"))));
		List<EefOutcome> outcomes = new ArrayList<>(); int index = 0;
		for (JsonElement item : array) {
			if (!item.isJsonObject()) throw new JsonParseException("outcome must be an object");
			JsonObject object = item.getAsJsonObject();
			outcomes.add(new EefOutcome(string(object, "id", "outcome_" + ruleIndex + "_" + index++),
				object.has("weight") ? object.get("weight").getAsDouble() : 1, parseActions(object.get("actions"))));
		}
		if (outcomes.isEmpty()) throw new JsonParseException("Rule outcomes cannot be empty");
		return outcomes;
	}

	private static List<EefAction> parseActions(JsonElement element) {
		if (element == null || element.isJsonNull()) return List.of();
		if (!element.isJsonArray()) throw new JsonParseException("actions must be an array");
		List<EefAction> actions = new ArrayList<>();
		for (JsonElement item : element.getAsJsonArray()) {
			if (!item.isJsonObject()) throw new JsonParseException("action must be an object");
			JsonObject object = item.getAsJsonObject();
			String type = requiredString(object, "type");
			Map<String, Object> parameters = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> parameter : object.entrySet())
				if (!parameter.getKey().equals("type")) parameters.put(parameter.getKey(), value(parameter.getValue()));
			actions.add(new EefAction(type, parameters));
		}
		return actions;
	}

	private static List<EefCooldown> parseCooldowns(JsonElement element) {
		if (element == null || element.isJsonNull()) return List.of();
		List<JsonElement> elements = element.isJsonArray() ? element.getAsJsonArray().asList() : List.of(element);
		List<EefCooldown> result = new ArrayList<>();
		for (JsonElement item : elements) {
			if (!item.isJsonObject()) throw new JsonParseException("cooldown must be an object or array");
			JsonObject object = item.getAsJsonObject();
			String scopeName = string(object, "scope", "rule_entity").toUpperCase(Locale.ROOT);
			try { result.add(new EefCooldown(object.has("ticks") ? object.get("ticks").getAsLong() : 0,
				EefCooldown.Scope.valueOf(scopeName))); }
			catch (IllegalArgumentException exception) { throw new JsonParseException("Unknown cooldown scope: " + scopeName); }
		}
		return result;
	}

	private static Set<EventReliability> parseReliability(JsonElement element) {
		if (element == null || element.isJsonNull()) return Set.of();
		List<String> names = new ArrayList<>();
		if (element.isJsonArray()) element.getAsJsonArray().forEach(item -> names.add(item.getAsString())); else names.add(element.getAsString());
		Set<EventReliability> result = EnumSet.noneOf(EventReliability.class);
		for (String name : names) try { result.add(EventReliability.valueOf(name.toUpperCase(Locale.ROOT))); }
		catch (IllegalArgumentException exception) { throw new JsonParseException("Unknown event reliability: " + name); }
		return result;
	}

	private static String requiredString(JsonObject object, String key) {
		String result = string(object, key, null);
		if (result == null || result.isBlank()) throw new JsonParseException("Missing string: " + key);
		return result;
	}
	private static String string(JsonObject object, String key, String fallback) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() ? element.getAsString() : fallback;
	}
	private static Object value(JsonElement element) {
		if (element == null || element.isJsonNull()) return null;
		if (element.isJsonPrimitive()) { JsonPrimitive p = element.getAsJsonPrimitive(); if (p.isBoolean()) return p.getAsBoolean(); if (p.isNumber()) return p.getAsDouble(); return p.getAsString(); }
		if (element.isJsonArray()) { List<Object> values = new ArrayList<>(); element.getAsJsonArray().forEach(item -> values.add(value(item))); return List.copyOf(values); }
		Map<String,Object> values = new LinkedHashMap<>(); for (Map.Entry<String,JsonElement> entry : element.getAsJsonObject().entrySet()) values.put(entry.getKey(), value(entry.getValue())); return Collections.unmodifiableMap(values);
	}
}
