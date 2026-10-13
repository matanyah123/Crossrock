package dev.matanyah.bedrockcompat.bedrock.resource;

import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public final class BedrockPackContentReader {
	private static final int MAX_RESOURCE_BYTES = 32 * 1024 * 1024;
	private static final int MAX_NESTED_ARCHIVE_BYTES = 128 * 1024 * 1024;
	private static final int MAX_BRARCHIVE_BYTES = 32 * 1024 * 1024;
	private static final int MAX_ARCHIVE_ENTRIES = 100_000;

	public void visit(BedrockPack pack, EntryConsumer consumer) throws IOException {
		visit(pack, Optional.empty(), consumer);
	}

	public void visit(BedrockPack pack, Optional<String> selectedSubpack, EntryConsumer consumer) throws IOException {
		visitSelected(pack, selectedSubpack, BedrockPackContentReader::isTextureSupported, true, consumer);
	}

	public void visitLanguages(BedrockPack pack, Optional<String> selectedSubpack, EntryConsumer consumer)
		throws IOException {
		visitSelected(pack, selectedSubpack, BedrockPackContentReader::isLanguageSupported, false, consumer);
	}

	public void visitPresentation(BedrockPack pack, Optional<String> selectedSubpack, EntryConsumer consumer) throws IOException {
		visitSelected(pack, selectedSubpack, name -> {
			String path = name.replace('\\', '/').toLowerCase(Locale.ROOT);
			return path.equals("splashes.json") || path.equals("texts/splashes.json")
				|| path.startsWith("textures/ui/") && (path.endsWith(".png") || path.endsWith(".json"));
		}, true, consumer);
	}

    public void visitBlockAssets(BedrockPack pack, Optional<String> selectedSubpack, EntryConsumer consumer) throws IOException {
        visitSelected(pack, selectedSubpack, path -> {
            String p = path.replace('\\', '/').toLowerCase(Locale.ROOT);
            return p.startsWith("blocks/") && p.endsWith(".json")
                || p.startsWith("models/blocks/") && p.endsWith(".json")
                || p.startsWith("block_culling/") && p.endsWith(".json")
                || p.startsWith("items/") && p.endsWith(".json")
                || p.equals("textures/item_texture.json")
                || p.startsWith("textures/") && (p.endsWith(".png") || p.endsWith(".tga"))
                || p.equals("textures/terrain_texture.json") || p.equals("textures/flipbook_textures.json");
        }, true, consumer);
    }

	/** Visits all selected-pack files, expanding BRArchives while excluding the containers themselves. */
	public void visitAll(BedrockPack pack, Optional<String> selectedSubpack, EntryConsumer consumer)
		throws IOException {
		visitSelected(pack, selectedSubpack,
			name -> !isResourceArchive(name), true, consumer);
	}

	/** Visits the raw contents of every subpack. Used only for resolving entity metadata
	 * whose geometry is shared by multiple A&S feature subpacks. */
	public void visitAllSubpacks(BedrockPack pack, EntryConsumer consumer) throws IOException {
		visitRaw(pack, name -> !isResourceArchive(name), true, consumer);
	}

	public void visitSounds(BedrockPack pack, Optional<String> selectedSubpack, EntryConsumer consumer)
		throws IOException {
		visitSelected(pack, selectedSubpack, BedrockPackContentReader::isSoundSupported, true, consumer);
	}

    public void visitUi(BedrockPack pack, Optional<String> selectedSubpack, EntryConsumer consumer) throws IOException {
        visitSelected(pack,selectedSubpack,p->p.startsWith("ui/")&&p.endsWith(".json"),false,consumer);
    }
    public void visitUiRegistered(BedrockPack pack,Optional<String> selectedSubpack,java.util.Set<String> paths,EntryConsumer consumer)throws IOException {
        visitSelected(pack,selectedSubpack,paths::contains,false,consumer);
    }

	private void visitSelected(BedrockPack pack, Optional<String> selectedSubpack, Predicate<String> selector,
		boolean expandSoundArchives, EntryConsumer consumer) throws IOException {
		Map<String, byte[]> overrides = new LinkedHashMap<>();
		String selectedPrefix = selectedSubpack.map(value -> "subpacks/" + value + "/").orElse(null);
		visitRaw(pack, name -> {
			String path = name.replace('\\', '/');
			if (startsWithIgnoreCase(path, "subpacks/")) {
				int slash = path.indexOf('/', "subpacks/".length());
				if (slash < 0) return false;
				path = path.substring(slash + 1);
			}
			return selector.test(path);
		}, expandSoundArchives, (path, data) -> {
			if (!startsWithIgnoreCase(path, "subpacks/")) consumer.accept(path, data);
			else if (selectedPrefix != null && startsWithIgnoreCase(path, selectedPrefix)) {
				overrides.put(path.substring(selectedPrefix.length()), data);
			}
		});
		for (Map.Entry<String, byte[]> override : overrides.entrySet()) consumer.accept(override.getKey(), override.getValue());
	}

	private void visitRaw(BedrockPack pack, Predicate<String> selector, boolean expandSoundArchives,
		EntryConsumer consumer) throws IOException {
		if (Files.isDirectory(pack.source())) visitDirectory(pack.source(), selector, expandSoundArchives, consumer);
		else if (pack.locationInSource().contains("!/")) visitNestedArchive(pack, selector, expandSoundArchives, consumer);
		else visitArchive(pack.source(), parentPrefix(pack.locationInSource()), selector, expandSoundArchives, consumer);
	}

	private void visitDirectory(Path root, Predicate<String> selector, boolean expandSoundArchives,
		EntryConsumer consumer) throws IOException {
		Path realRoot = root.toRealPath();
		try (var paths = Files.walk(realRoot)) {
			for (Path path : paths.filter(Files::isRegularFile).toList()) {
				Path realPath = path.toRealPath();
				if (!realPath.startsWith(realRoot)) continue;
				String name = realRoot.relativize(realPath).toString().replace('\\', '/');
				if (selector.test(name)) consumer.accept(name, readLimited(Files.newInputStream(realPath), MAX_RESOURCE_BYTES));
				else if (expandSoundArchives && isResourceArchive(name)) {
					expandBrArchive(name, readLimited(Files.newInputStream(realPath), MAX_BRARCHIVE_BYTES), selector, consumer);
				}
			}
		}
	}

	private void visitArchive(Path archive, String prefix, Predicate<String> selector, boolean expandSoundArchives,
		EntryConsumer consumer) throws IOException {
		try (ZipFile zip = new ZipFile(archive.toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			int count = 0;
			while (entries.hasMoreElements()) {
				if (++count > MAX_ARCHIVE_ENTRIES) throw new IOException("Archive contains too many entries");
				ZipEntry entry = entries.nextElement();
				String name = safeEntryName(entry.getName());
				if (entry.isDirectory() || !startsWithIgnoreCase(name, prefix)) continue;
				String relative = name.substring(prefix.length());
				if (selector.test(relative)) consumer.accept(relative, readLimited(zip.getInputStream(entry), MAX_RESOURCE_BYTES));
				else if (expandSoundArchives && isResourceArchive(relative)) {
					expandBrArchive(relative, readLimited(zip.getInputStream(entry), MAX_BRARCHIVE_BYTES), selector, consumer);
				}
			}
		}
	}

	private void visitNestedArchive(BedrockPack pack, Predicate<String> selector, boolean expandSoundArchives,
		EntryConsumer consumer) throws IOException {
		String[] location = pack.locationInSource().split("!/", 2);
		byte[] nested;
		try (ZipFile outer = new ZipFile(pack.source().toFile())) {
			ZipEntry nestedEntry = findEntry(outer, location[0]);
			if (nestedEntry == null) throw new IOException("Nested pack is no longer present: " + location[0]);
			nested = readLimited(outer.getInputStream(nestedEntry), MAX_NESTED_ARCHIVE_BYTES);
		}
		String prefix = parentPrefix(location[1]);
		try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(nested))) {
			ZipEntry entry;
			int count = 0;
			while ((entry = input.getNextEntry()) != null) {
				if (++count > MAX_ARCHIVE_ENTRIES) throw new IOException("Nested archive contains too many entries");
				String name = safeEntryName(entry.getName());
				if (entry.isDirectory() || !startsWithIgnoreCase(name, prefix)) continue;
				String relative = name.substring(prefix.length());
				if (selector.test(relative)) consumer.accept(relative, readLimitedWithoutClosing(input, MAX_RESOURCE_BYTES));
				else if (expandSoundArchives && isResourceArchive(relative)) {
					expandBrArchive(relative, readLimitedWithoutClosing(input, MAX_BRARCHIVE_BYTES), selector, consumer);
				}
			}
		}
	}

	private static ZipEntry findEntry(ZipFile zip, String expected) {
		return zip.stream().filter(entry -> entry.getName().replace('\\', '/').equalsIgnoreCase(expected)).findFirst().orElse(null);
	}

	private static boolean isTextureSupported(String name) {
		String normalized = name.toLowerCase(Locale.ROOT);
		return normalized.endsWith(".png") || normalized.endsWith(".tga") || normalized.equals("textures/terrain_texture.json")
			|| normalized.equals("textures/item_texture.json") || normalized.equals("textures/flipbook_textures.json") || normalized.equals("blocks.json")
			|| normalized.equals("models/mobs.json")
			|| normalized.startsWith("models/entity/") && normalized.endsWith(".geo.json")
			|| normalized.startsWith("entity/") && normalized.endsWith(".json");
	}

	private static boolean isLanguageSupported(String name) {
		String normalized = name.replace('\\', '/').toLowerCase(Locale.ROOT);
		return normalized.startsWith("texts/") && normalized.endsWith(".lang");
	}

	private static boolean isSoundSupported(String name) {
		String normalized = name.toLowerCase(Locale.ROOT);
		return normalized.endsWith(".ogg") || normalized.endsWith("sounds.json")
			|| normalized.endsWith("sound_definitions.json");
	}

	private static boolean isResourceArchive(String name) {
		String normalized = name.replace('\\', '/').toLowerCase(Locale.ROOT);
		return normalized.contains("__brarchive/") && normalized.endsWith(".brarchive");
	}

	private static void expandBrArchive(String archivePath, byte[] archive, Predicate<String> selector,
		EntryConsumer consumer) throws IOException {
		String normalized = archivePath.replace('\\', '/');
		int marker = normalized.toLowerCase(Locale.ROOT).indexOf("__brarchive/");
		if (marker < 0) return;
		String before = normalized.substring(0, marker);
		String mirrored = normalized.substring(marker + "__brarchive/".length(), normalized.length() - ".brarchive".length());
		String outputPrefix = before + mirrored + "/";
		BrArchiveReader.visit(archive, (entryName, data) -> {
			if (data.length == 0) return;
			String expanded = outputPrefix + safeEntryName(entryName);
			if (selector.test(expanded)) consumer.accept(expanded, data);
		});
	}

	private static boolean startsWithIgnoreCase(String value, String prefix) {
		return value.regionMatches(true, 0, prefix, 0, prefix.length());
	}

	private static String parentPrefix(String name) {
		int slash = name.lastIndexOf('/');
		return slash < 0 ? "" : name.substring(0, slash + 1);
	}

	private static String safeEntryName(String rawName) throws IOException {
		String name = rawName.replace('\\', '/');
		if (name.startsWith("/") || name.matches("^[A-Za-z]:.*")) throw new IOException("Archive contains an absolute path");
		for (String part : name.split("/")) {
			if (part.equals("..")) throw new IOException("Archive contains a parent path segment");
		}
		return name;
	}

	private static byte[] readLimited(InputStream rawInput, int limit) throws IOException {
		try (InputStream input = rawInput; ByteArrayOutputStream output = new ByteArrayOutputStream(8192)) {
			return copyLimited(input, output, limit);
		}
	}

	private static byte[] readLimitedWithoutClosing(InputStream input, int limit) throws IOException {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream(8192)) {
			return copyLimited(input, output, limit);
		}
	}

	private static byte[] copyLimited(InputStream input, ByteArrayOutputStream output, int limit) throws IOException {
			byte[] buffer = new byte[8192];
			int total = 0;
			int read;
			while ((read = input.read(buffer)) != -1) {
				total += read;
				if (total > limit) throw new IOException("Resource exceeds the " + limit + " byte safety limit");
				output.write(buffer, 0, read);
			}
		return output.toByteArray();
	}

	@FunctionalInterface
	public interface EntryConsumer {
		void accept(String path, byte[] data) throws IOException;
	}
}
