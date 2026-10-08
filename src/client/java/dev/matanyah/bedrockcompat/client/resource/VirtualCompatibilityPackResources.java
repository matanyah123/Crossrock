package dev.matanyah.bedrockcompat.client.resource;

import dev.matanyah.bedrockcompat.resource.GeneratedResourceSnapshot;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Set;

final class VirtualCompatibilityPackResources implements PackResources {
	private static final byte[] PACK_ICON = loadIcon();
	private final PackLocationInfo location;
	private final GeneratedResourceSnapshot snapshot;

	VirtualCompatibilityPackResources(PackLocationInfo location, GeneratedResourceSnapshot snapshot) {
		this.location = location;
		this.snapshot = snapshot;
	}

	@Override
	public IoSupplier<InputStream> getRootResource(String... elements) {
	if (elements.length == 1 && elements[0].equals("pack.png")) {
			return () -> new ByteArrayInputStream(PACK_ICON.clone());
		}
		return null;
	}

	private static byte[] loadIcon() {
		try (InputStream input = VirtualCompatibilityPackResources.class
			.getResourceAsStream("/assets/bedrock_compat/icon.png")) {
			return input == null ? new byte[0] : input.readAllBytes();
		} catch (java.io.IOException ignored) {
			return new byte[0];
		}
	}

	@Override
	public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
		if (type != PackType.CLIENT_RESOURCES) return null;
		byte[] data = snapshot.resources().get(id);
		return data == null ? null : () -> new ByteArrayInputStream(data);
	}

	@Override
	public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
		if (type != PackType.CLIENT_RESOURCES) return;
		for (var entry : snapshot.resources().entrySet()) {
			Identifier id = entry.getKey();
			if (!id.getNamespace().equals(namespace) || !(path.isEmpty()
				|| id.getPath().startsWith(path + "/"))) continue;
			byte[] data = entry.getValue();
			output.accept(id, () -> new ByteArrayInputStream(data));
		}
	}

	@Override
	public Set<String> getNamespaces(PackType type) {
		return type == PackType.CLIENT_RESOURCES ? snapshot.namespaces() : Set.of();
	}

	@Override
	public <T> T getMetadataSection(MetadataSectionType<T> type) {
		return null;
	}

	@Override
	public PackLocationInfo location() {
		return location;
	}

	@Override
	public void close() {
	}
}
