package dev.matanyah.eef;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EefConfigTest {
	@TempDir Path temporaryDirectory;

	@Test void subtitleToggleDefaultsOnAndPersists() {
		Path path = temporaryDirectory.resolve("eef.properties");
		EefConfig config = EefConfig.load(path);
		assertTrue(config.subtitlesEnabled());
		assertFalse(config.toggleSubtitles());
		assertFalse(EefConfig.load(path).subtitlesEnabled());
		assertTrue(config.toggleSubtitles());
		assertTrue(EefConfig.load(path).subtitlesEnabled());
	}

	@Test void speechBubbleToggleDefaultsOnAndPersists() {
		Path path = temporaryDirectory.resolve("eef-bubbles.properties");
		EefConfig config = EefConfig.load(path);
		assertTrue(config.speechBubblesEnabled());
		assertFalse(config.toggleSpeechBubbles());
		assertFalse(EefConfig.load(path).speechBubblesEnabled());
	}

	@Test void bubbleGraceDistanceIsBoundedAndPersists() {
		Path path = temporaryDirectory.resolve("eef-grace.properties");
		EefConfig config = EefConfig.load(path);
		assertEquals(48.0, config.bubbleGraceDistance());
		config.setBubbleGraceDistance(-5);
		assertEquals(0.0, EefConfig.load(path).bubbleGraceDistance());
		config.setBubbleGraceDistance(99);
		assertEquals(48.0, EefConfig.load(path).bubbleGraceDistance());
	}

	@Test void cooldownMultiplierIsBoundedAndPersists() {
		Path path = temporaryDirectory.resolve("eef-cooldown.properties");
		EefConfig config = EefConfig.load(path);
		assertEquals(1.0, config.cooldownMultiplier());
		config.setCooldownMultiplier(0);
		assertEquals(0.25, EefConfig.load(path).cooldownMultiplier());
		config.setCooldownMultiplier(9);
		assertEquals(4.0, EefConfig.load(path).cooldownMultiplier());
	}

	@Test void addonStyleChattinessAndRareLineSettingsPersist() {
		Path path = temporaryDirectory.resolve("eef-addon-settings.properties");
		EefConfig config = EefConfig.load(path);
		assertEquals(EefConfig.Chattiness.CHATTY, config.chattiness());
		assertEquals(EefConfig.RareLines.DEFAULT, config.rareLines());
		assertEquals(EefConfig.Chattiness.SUPER_CHATTY, config.cycleChattiness());
		assertEquals(0.2, config.cooldownMultiplier());
		assertEquals(EefConfig.RareLines.OFTEN, config.cycleRareLines());
		EefConfig loaded = EefConfig.load(path);
		assertEquals(EefConfig.Chattiness.SUPER_CHATTY, loaded.chattiness());
		assertEquals(EefConfig.RareLines.OFTEN, loaded.rareLines());
	}
}
