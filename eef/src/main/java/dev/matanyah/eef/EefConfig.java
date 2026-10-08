package dev.matanyah.eef;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/** Small persistent client preference store with no GUI dependency. */
public final class EefConfig implements EefRulePreferences {
	private static final String SUBTITLES = "subtitles";
	private static final String SPEECH_BUBBLES = "speech_bubbles";
	private static final String BUBBLE_GRACE_DISTANCE = "bubble_grace_distance";
	private static final String COOLDOWN_MULTIPLIER = "cooldown_multiplier";
	private static final String CHATTINESS = "chattiness";
	private static final String RARE_LINES = "rare_lines";
	public static final double MAX_BUBBLE_GRACE_DISTANCE = 48.0;
	public static final double MIN_COOLDOWN_MULTIPLIER = 0.25;
	public static final double MAX_COOLDOWN_MULTIPLIER = 4.0;
	private final Path path;
	private boolean subtitlesEnabled = true;
	private boolean speechBubblesEnabled = true;
	private double bubbleGraceDistance = MAX_BUBBLE_GRACE_DISTANCE;
	private double cooldownMultiplier = 1.0;
	private Chattiness chattiness = Chattiness.CHATTY;
	private RareLines rareLines = RareLines.DEFAULT;
	private final Map<String, Integer> triggerFrequencies = new HashMap<>();
	private final Map<String, Boolean> triggerEnabled = new HashMap<>();
	private final Map<String, Boolean> triggerImportant = new HashMap<>();
	private final List<String> triggerOrder = new ArrayList<>();

	private EefConfig(Path path) { this.path = path; }
	public enum Chattiness { MUTED, SHY, CHATTY, SUPER_CHATTY;
		public Chattiness next() { return values()[(ordinal() + 1) % values().length]; }
		public double cooldownMultiplier() { return switch (this) { case MUTED, CHATTY -> 1.0; case SHY -> 2.0; case SUPER_CHATTY -> 0.2; }; }
	}
	public enum RareLines { NEVER, DEFAULT, OFTEN;
		public RareLines next() { return values()[(ordinal() + 1) % values().length]; }
	}

	public static EefConfig load(Path path) {
		EefConfig config = new EefConfig(path);
		if (Files.isRegularFile(path)) {
			Properties properties = new Properties();
			try (Reader reader = Files.newBufferedReader(path)) {
				properties.load(reader);
					config.subtitlesEnabled = Boolean.parseBoolean(properties.getProperty(SUBTITLES, "true"));
					config.speechBubblesEnabled = Boolean.parseBoolean(properties.getProperty(SPEECH_BUBBLES, "true"));
					try { config.bubbleGraceDistance = clampGrace(Double.parseDouble(properties.getProperty(BUBBLE_GRACE_DISTANCE, "48"))); }
					catch (NumberFormatException ignored) { config.bubbleGraceDistance = MAX_BUBBLE_GRACE_DISTANCE; }
					try { config.cooldownMultiplier = clampCooldownMultiplier(Double.parseDouble(properties.getProperty(COOLDOWN_MULTIPLIER, "1"))); }
					catch (NumberFormatException ignored) { config.cooldownMultiplier = 1.0; }
					try { config.chattiness = Chattiness.valueOf(properties.getProperty(CHATTINESS, "CHATTY")); }
					catch (IllegalArgumentException ignored) { config.chattiness = Chattiness.CHATTY; }
					try { config.rareLines = RareLines.valueOf(properties.getProperty(RARE_LINES, "DEFAULT")); }
					catch (IllegalArgumentException ignored) { config.rareLines = RareLines.DEFAULT; }
					for (String key : properties.stringPropertyNames()) {
						if (!key.startsWith("trigger.")) continue;
						String rest = key.substring("trigger.".length());
						int split = rest.lastIndexOf('.'); if (split <= 0) continue;
						String id = rest.substring(0, split); String field = rest.substring(split + 1);
						try {
							switch (field) {
								case "frequency" -> config.triggerFrequencies.put(id, clampFrequency(Integer.parseInt(properties.getProperty(key))));
								case "enabled" -> config.triggerEnabled.put(id, Boolean.parseBoolean(properties.getProperty(key)));
								case "important" -> config.triggerImportant.put(id, Boolean.parseBoolean(properties.getProperty(key)));
								default -> { }
							}
						} catch (NumberFormatException ignored) { }
					}
					String order = properties.getProperty("trigger_order", "");
					if (!order.isBlank()) for (String id : order.split(",")) if (!id.isBlank() && !config.triggerOrder.contains(id)) config.triggerOrder.add(id);
			} catch (IOException exception) {
				EntityEventFeatures.LOGGER.warn("Could not read EEF config {}: {}", path, exception.getMessage());
			}
		}
		return config;
	}

	public synchronized boolean subtitlesEnabled() { return subtitlesEnabled; }
	public synchronized boolean toggleSubtitles() { subtitlesEnabled = !subtitlesEnabled; save(); return subtitlesEnabled; }
	public synchronized void setSubtitlesEnabled(boolean enabled) { subtitlesEnabled = enabled; save(); }
	public synchronized boolean speechBubblesEnabled() { return speechBubblesEnabled; }
	public synchronized boolean toggleSpeechBubbles() { speechBubblesEnabled = !speechBubblesEnabled; save(); return speechBubblesEnabled; }
	public synchronized void setSpeechBubblesEnabled(boolean enabled) { speechBubblesEnabled = enabled; save(); }
	public synchronized double bubbleGraceDistance() { return bubbleGraceDistance; }
	public synchronized void setBubbleGraceDistance(double distance) { bubbleGraceDistance = clampGrace(distance); save(); }
	public synchronized double cooldownMultiplier() { return cooldownMultiplier; }
	public synchronized void setCooldownMultiplier(double multiplier) { cooldownMultiplier = clampCooldownMultiplier(multiplier); save(); }
	public synchronized Chattiness chattiness() { return chattiness; }
	public synchronized Chattiness cycleChattiness() { chattiness = chattiness.next(); cooldownMultiplier = chattiness.cooldownMultiplier(); save(); return chattiness; }
	public synchronized RareLines rareLines() { return rareLines; }
	public synchronized RareLines cycleRareLines() { rareLines = rareLines.next(); save(); return rareLines; }
	@Override public synchronized boolean muted() { return chattiness == Chattiness.MUTED; }
	@Override public synchronized double outcomeWeight(EefRule rule, EefOutcome outcome, double maximumWeight) {
		return switch (rareLines) {
			case NEVER -> outcome.weight() >= maximumWeight * 0.8 ? outcome.weight() : 0.0;
			case DEFAULT -> outcome.weight();
			case OFTEN -> maximumWeight + 1.0 - outcome.weight();
		};
	}
	@Override public synchronized boolean enabled(EefRule rule) { return triggerEnabled.getOrDefault(rule.id(), true); }
	@Override public synchronized int frequency(EefRule rule) { return triggerFrequencies.getOrDefault(rule.id(), rule.defaultFrequency()); }
	@Override public synchronized boolean important(EefRule rule) { return triggerImportant.getOrDefault(rule.id(), rule.defaultImportant()); }
	public synchronized void setTriggerFrequency(String id, int frequency) { triggerFrequencies.put(id, clampFrequency(frequency)); save(); }
	public synchronized int triggerFrequency(EefRule rule) { return frequency(rule); }
	public synchronized boolean toggleTrigger(String id) { boolean enabled = !triggerEnabled.getOrDefault(id, true); triggerEnabled.put(id, enabled); save(); return enabled; }
	public synchronized void setTriggerEnabled(String id, boolean enabled) { triggerEnabled.put(id, enabled); save(); }
	public synchronized boolean triggerEnabled(EefRule rule) { return enabled(rule); }
	public synchronized boolean toggleTriggerImportance(String id) { boolean important = !triggerImportant.getOrDefault(id, true); triggerImportant.put(id, important); save(); return important; }
	public synchronized boolean triggerImportant(EefRule rule) { return important(rule); }
	public synchronized List<EefRule> orderedRules(List<EefRule> rules) {
		List<EefRule> result = new ArrayList<>(rules);
		result.sort(java.util.Comparator.comparingInt(rule -> { int i = triggerOrder.indexOf(rule.id()); return i < 0 ? Integer.MAX_VALUE : i; }));
		return result;
	}
	public synchronized void moveTriggerBefore(String movingId, String beforeId, List<EefRule> rules) {
		triggerOrder.remove(movingId); int index = triggerOrder.indexOf(beforeId);
		if (index < 0) { triggerOrder.add(movingId); } else triggerOrder.add(index, movingId);
		for (EefRule rule : rules) if (!triggerOrder.contains(rule.id())) triggerOrder.add(rule.id());
		save();
	}
	private static double clampGrace(double distance) {
		return Double.isFinite(distance) ? Math.max(0.0, Math.min(MAX_BUBBLE_GRACE_DISTANCE, distance)) : MAX_BUBBLE_GRACE_DISTANCE;
	}
	private static double clampCooldownMultiplier(double multiplier) {
		return Double.isFinite(multiplier) ? Math.max(MIN_COOLDOWN_MULTIPLIER, Math.min(MAX_COOLDOWN_MULTIPLIER, multiplier)) : 1.0;
	}
	private static int clampFrequency(int frequency) { return Math.max(1, Math.min(10, frequency)); }

	private void save() {
		Properties properties = new Properties();
		properties.setProperty(SUBTITLES, Boolean.toString(subtitlesEnabled));
		properties.setProperty(SPEECH_BUBBLES, Boolean.toString(speechBubblesEnabled));
		properties.setProperty(BUBBLE_GRACE_DISTANCE, Double.toString(bubbleGraceDistance));
		properties.setProperty(COOLDOWN_MULTIPLIER, Double.toString(cooldownMultiplier));
		properties.setProperty(CHATTINESS, chattiness.name());
		properties.setProperty(RARE_LINES, rareLines.name());
		for (Map.Entry<String, Integer> entry : triggerFrequencies.entrySet()) properties.setProperty("trigger." + entry.getKey() + ".frequency", Integer.toString(entry.getValue()));
		for (Map.Entry<String, Boolean> entry : triggerEnabled.entrySet()) properties.setProperty("trigger." + entry.getKey() + ".enabled", Boolean.toString(entry.getValue()));
		for (Map.Entry<String, Boolean> entry : triggerImportant.entrySet()) properties.setProperty("trigger." + entry.getKey() + ".important", Boolean.toString(entry.getValue()));
		if (!triggerOrder.isEmpty()) properties.setProperty("trigger_order", String.join(",", triggerOrder));
		try {
			Path parent = path.getParent(); if (parent != null) Files.createDirectories(parent);
			try (Writer writer = Files.newBufferedWriter(path)) { properties.store(writer, "Entity Event Features client settings"); }
		} catch (IOException exception) {
			EntityEventFeatures.LOGGER.warn("Could not save EEF config {}: {}", path, exception.getMessage());
		}
	}
}
