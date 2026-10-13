package dev.matanyah.bedrockcompat.bedrock.discovery;

import com.google.gson.JsonParseException;
import dev.matanyah.bedrockcompat.bedrock.format.BedrockManifestParser;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;
import dev.matanyah.bedrockcompat.bedrock.model.PackLoadResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class BedrockPackRepository {
	private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
	private static final int MAX_ICON_BYTES = 8 * 1024 * 1024;
	private static final int MAX_LANGUAGE_BYTES = 1024 * 1024;
	private static final int MAX_NESTED_ARCHIVE_BYTES = 64 * 1024 * 1024;
	private static final int MAX_ARCHIVE_ENTRIES = 100_000;
	private static final int MAX_PACKS_PER_ARCHIVE = 256;
	private static final int DIRECTORY_SCAN_DEPTH = 5;

	private final Path addonsDirectory;
	private final BedrockManifestParser parser;
	private record Stamp(long size, FileTime modified, Object fileKey) {}
	private record Cached(List<Stamp> stamps, List<PackLoadResult> results) {}
	private static final Map<Path, Cached> METADATA_CACHE = new LinkedHashMap<>(64, .75f, true);

	public BedrockPackRepository(Path gameDirectory) {
		this(gameDirectory.resolve("addons"), new BedrockManifestParser());
	}

	BedrockPackRepository(Path addonsDirectory, BedrockManifestParser parser) {
		this.addonsDirectory = addonsDirectory;
		this.parser = parser;
	}

	public static BedrockPackRepository atDirectory(Path directory) {
		return new BedrockPackRepository(directory, new BedrockManifestParser());
	}

	public Path addonsDirectory() {
		return addonsDirectory;
	}

	public List<PackLoadResult> scan() {
		List<PackLoadResult> results = new ArrayList<>();
		try {
			Files.createDirectories(addonsDirectory);
			Files.walkFileTree(addonsDirectory, Set.of(), DIRECTORY_SCAN_DEPTH, new SimpleFileVisitor<>() {
				@Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
					Path manifest = directory.resolve("manifest.json");
					if (Files.isRegularFile(manifest)) {
						results.addAll(cached(manifest, false, () -> List.of(scanManifestFile(manifest))));
						// An unpacked pack's assets are not discovery inputs.
						return FileVisitResult.SKIP_SUBTREE;
					}
					return FileVisitResult.CONTINUE;
				}
				@Override public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) {
					String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
					if (attributes.isRegularFile() && name.equals("manifest.json"))
						results.addAll(cached(path, false, () -> List.of(scanManifestFile(path))));
					else if (attributes.isRegularFile() && (name.endsWith(".mcpack") || name.endsWith(".mcaddon")))
						results.addAll(cached(path, true, () -> scanArchive(path)));
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException | UncheckedIOException | SecurityException exception) {
			results.add(PackLoadResult.failed(addonsDirectory, "", concise(exception)));
		}
		results.sort(Comparator.comparing(PackLoadResult::displayName, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(result -> result.source().toString())
			.thenComparing(PackLoadResult::locationInSource));
		return List.copyOf(results);
	}

	/** Shared across screen instances; only metadata file stamps invalidate unpacked packs. */
	private List<PackLoadResult> cached(Path path, boolean archive, java.util.function.Supplier<List<PackLoadResult>> load) {
		Path key = path.toAbsolutePath().normalize();
		try {
			List<Stamp> stamps = new ArrayList<>();
			for (Path input : archive ? List.of(path) : List.of(path, path.resolveSibling("pack_icon.png"), path.resolveSibling("texts/en_US.lang"))) {
				try {
					var attributes = Files.readAttributes(input, BasicFileAttributes.class);
					stamps.add(new Stamp(attributes.size(), attributes.lastModifiedTime(), attributes.fileKey()));
				} catch (java.nio.file.NoSuchFileException missing) { stamps.add(new Stamp(-1, FileTime.fromMillis(0), null)); }
			}
			synchronized (METADATA_CACHE) {
				var existing = METADATA_CACHE.get(key);
				if (existing != null && existing.stamps().equals(stamps)) return existing.results();
			}
			var loaded = List.copyOf(load.get());
			synchronized (METADATA_CACHE) {
				METADATA_CACHE.put(key, new Cached(List.copyOf(stamps), loaded));
				while (METADATA_CACHE.size() > 64) METADATA_CACHE.remove(METADATA_CACHE.keySet().iterator().next());
			}
			return loaded;
		} catch (IOException | SecurityException ignored) { return load.get(); }
	}

	private PackLoadResult scanManifestFile(Path manifest) {
		Path packDirectory = manifest.getParent();
		Path icon = packDirectory.resolve("pack_icon.png");
		try {
			byte[] iconBytes = Files.isRegularFile(icon) ? readLimited(() -> Files.newInputStream(icon), MAX_ICON_BYTES) : null;
			Path language = packDirectory.resolve("texts/en_US.lang");
			Map<String, String> translations = Files.isRegularFile(language)
				? parseLanguage(readLimited(() -> Files.newInputStream(language), MAX_LANGUAGE_BYTES)) : Map.of();
			byte[] manifestBytes = readLimited(() -> Files.newInputStream(manifest), MAX_MANIFEST_BYTES);
			return parse(manifestBytes, packDirectory, "manifest.json", iconBytes, translations);
		} catch (Exception exception) {
			return failure(packDirectory, "manifest.json", exception);
		}
	}

	private List<PackLoadResult> scanArchive(Path archive) {
		List<PackLoadResult> results = new ArrayList<>();
		try (ZipFile zip = new ZipFile(archive.toFile())) {
			Map<String, ZipEntry> entries = safeEntries(zip);
			List<String> manifests = entries.keySet().stream().filter(BedrockPackRepository::isManifest).sorted().toList();
			if (manifests.size() > MAX_PACKS_PER_ARCHIVE) throw new IOException("Archive contains too many manifests");
			for (String manifestName : manifests) {
				String prefix = parentPrefix(manifestName);
				ZipEntry icon = findCaseInsensitive(entries, prefix + "pack_icon.png");
				ZipEntry language = findCaseInsensitive(entries, prefix + "texts/en_US.lang");
				byte[] iconBytes = icon == null ? null : readLimited(() -> zip.getInputStream(icon), MAX_ICON_BYTES);
				Map<String, String> translations = language == null ? Map.of()
					: parseLanguage(readLimited(() -> zip.getInputStream(language), MAX_LANGUAGE_BYTES));
				byte[] manifestBytes = readLimited(() -> zip.getInputStream(entries.get(manifestName)), MAX_MANIFEST_BYTES);
				results.add(parse(manifestBytes, archive, manifestName, iconBytes, translations));
			}

			if (archive.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mcaddon")) {
				for (Map.Entry<String, ZipEntry> entry : entries.entrySet()) {
					if (entry.getKey().toLowerCase(Locale.ROOT).endsWith(".mcpack")) {
						byte[] nested = readLimited(() -> zip.getInputStream(entry.getValue()), MAX_NESTED_ARCHIVE_BYTES);
						results.addAll(scanNestedArchive(archive, entry.getKey(), nested));
					}
				}
			}
			if (results.isEmpty()) results.add(PackLoadResult.failed(archive, "", "Archive contains no manifest.json"));
		} catch (Exception exception) {
			results.clear();
			results.add(failure(archive, "", exception));
		}
		return results;
	}

	private List<PackLoadResult> scanNestedArchive(Path outerArchive, String nestedName, byte[] bytes) throws IOException {
		Map<String, byte[]> selected = new HashMap<>();
		// ZipInputStream has to inflate every skipped entry to reach the next one.
		// A central-directory reader can seek straight to the few metadata entries.
		Path temporary = Files.createTempFile("crossrock-metadata-", ".mcpack");
		try {
			Files.write(temporary, bytes);
			try (ZipFile zip = new ZipFile(temporary.toFile())) {
				for (var item : safeEntries(zip).entrySet()) {
					ZipEntry entry = item.getValue();
					String name = item.getKey();
					String lowerName = name.toLowerCase(Locale.ROOT);
					if (!entry.isDirectory() && (isManifest(name) || lowerName.endsWith("pack_icon.png")
						|| lowerName.endsWith("texts/en_us.lang"))) {
						int limit = isManifest(name) ? MAX_MANIFEST_BYTES
							: lowerName.endsWith(".lang") ? MAX_LANGUAGE_BYTES : MAX_ICON_BYTES;
						selected.put(name, readLimited(() -> zip.getInputStream(entry), limit));
					}
				}
			}
		} finally { Files.deleteIfExists(temporary); }
		List<String> manifests = selected.keySet().stream().filter(BedrockPackRepository::isManifest).sorted().toList();
		if (manifests.size() > MAX_PACKS_PER_ARCHIVE) throw new IOException("Nested archive contains too many manifests");
		List<PackLoadResult> results = new ArrayList<>();
		for (String manifest : manifests) {
			String location = nestedName + "!/" + manifest;
			byte[] icon = findCaseInsensitiveBytes(selected, parentPrefix(manifest) + "pack_icon.png");
			byte[] language = findCaseInsensitiveBytes(selected, parentPrefix(manifest) + "texts/en_US.lang");
			Map<String, String> translations = language == null ? Map.of() : parseLanguage(language);
			results.add(parse(selected.get(manifest), outerArchive, location, icon, translations));
		}
		if (results.isEmpty()) results.add(PackLoadResult.failed(outerArchive, nestedName, "Nested .mcpack contains no manifest.json"));
		return results;
	}

	private Map<String, ZipEntry> safeEntries(ZipFile zip) throws IOException {
		Map<String, ZipEntry> entries = new HashMap<>();
		Enumeration<? extends ZipEntry> enumeration = zip.entries();
		int count = 0;
		while (enumeration.hasMoreElements()) {
			if (++count > MAX_ARCHIVE_ENTRIES) throw new IOException("Archive contains too many entries");
			ZipEntry entry = enumeration.nextElement();
			if (!entry.isDirectory()) {
				String name = safeEntryName(entry.getName());
				String lowerName = name.toLowerCase(Locale.ROOT);
				if (isManifest(name) || lowerName.endsWith("pack_icon.png") || lowerName.endsWith("texts/en_us.lang")
					|| lowerName.endsWith(".mcpack")) {
					entries.put(name, entry);
				}
			}
		}
		return entries;
	}

	private PackLoadResult parse(byte[] manifest, Path source, String location, byte[] icon,
		Map<String, String> translations) {
		try {
			BedrockPack pack = parser.parse(manifest, source, location, icon, translations);
			return PackLoadResult.loaded(pack);
		} catch (JsonParseException | IllegalArgumentException exception) {
			return failure(source, location, exception);
		}
	}

	private PackLoadResult failure(Path source, String location, Exception exception) {
		return PackLoadResult.failed(source, location, concise(exception));
	}

	private static byte[] readLimited(IOSupplier<InputStream> supplier, int limit) throws IOException {
		try (InputStream input = supplier.get()) {
			return readLimited(input, limit);
		}
	}

	private static byte[] readLimited(InputStream input, int limit) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192));
		byte[] buffer = new byte[8192];
		int total = 0;
		int read;
		while ((read = input.read(buffer)) != -1) {
			total += read;
			if (total > limit) throw new IOException("Entry exceeds the " + limit + " byte safety limit");
			output.write(buffer, 0, read);
		}
		return output.toByteArray();
	}

	private static String safeEntryName(String rawName) throws IOException {
		String name = rawName.replace('\\', '/');
		if (name.startsWith("/") || name.matches("^[A-Za-z]:.*")) throw new IOException("Archive contains an absolute path");
		for (String part : name.split("/")) {
			if (part.equals("..")) throw new IOException("Archive contains a parent path segment");
		}
		return name;
	}

	private static boolean isManifest(String name) {
		return name.equalsIgnoreCase("manifest.json") || name.toLowerCase(Locale.ROOT).endsWith("/manifest.json");
	}

	private static String parentPrefix(String name) {
		int slash = name.lastIndexOf('/');
		return slash < 0 ? "" : name.substring(0, slash + 1);
	}

	private static ZipEntry findCaseInsensitive(Map<String, ZipEntry> entries, String name) {
		return entries.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
			.map(Map.Entry::getValue).findFirst().orElse(null);
	}

	private static byte[] findCaseInsensitiveBytes(Map<String, byte[]> entries, String name) {
		return entries.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
			.map(Map.Entry::getValue).findFirst().orElse(null);
	}

	private static String concise(Exception exception) {
		String message = exception.getMessage();
		return exception.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
	}

	private static Map<String, String> parseLanguage(byte[] bytes) {
		Map<String, String> translations = new HashMap<>();
		String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
		for (String line : content.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
			int separator = line.indexOf('=');
			if (separator <= 0) continue;
			String key = line.substring(0, separator).trim();
			String value = line.substring(separator + 1);
			int comment = value.indexOf("\t#");
			if (comment >= 0) value = value.substring(0, comment);
			translations.putIfAbsent(key, value.trim());
		}
		return Map.copyOf(translations);
	}

	@FunctionalInterface
	private interface IOSupplier<T> {
		T get() throws IOException;
	}
}
