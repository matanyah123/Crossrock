package dev.matanyah.bedrockcompat.resource;

import net.minecraft.resources.Identifier;

public interface GeneratedResourceSink {
	void put(Identifier id, byte[] data);

	/** Text overlays may append to lower-priority generated content without exposing storage. */
	default void putText(Identifier id, byte[] data, boolean append) { put(id, data); }
	default void putText(Identifier id, byte[] data, boolean append, byte[] base) { putText(id, data, append); }
}
