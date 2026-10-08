package dev.matanyah.eef;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityEventFeaturesEntrypointTest {
	@Test
	void fabricCanConstructMainEntrypoint() throws ReflectiveOperationException {
		var constructor = EntityEventFeatures.class.getDeclaredConstructor();
		assertTrue(Modifier.isPublic(constructor.getModifiers()));
		assertTrue(constructor.newInstance() instanceof EntityEventFeatures);
	}
}
