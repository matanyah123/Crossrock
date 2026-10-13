package dev.matanyah.bedrockcompat.bedrock.discovery;

import dev.matanyah.bedrockcompat.bedrock.format.BedrockManifestParser;
import dev.matanyah.bedrockcompat.bedrock.model.PackLoadResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockPackRepositoryTest {
	@TempDir
	Path temporaryDirectory;

	@Test void unpackedDiscoveryStopsAtPackRootAndCachesOnlyMetadataInputs() throws IOException {
		Path addons = temporaryDirectory.resolve("addons");
		Path pack = addons.resolve("pack");
		write(pack.resolve("manifest.json"), manifest("Cached", "resources"));
		write(pack.resolve("textures/unrelated.mcpack"), "Not an archive; not a discovery input");
		var first = repository(addons).scan();
		assertEquals(1, first.size(), "Asset folders must not be traversed for discovery");
		write(pack.resolve("textures/changed.json"), "{broken");
		var second = repository(addons).scan();
		org.junit.jupiter.api.Assertions.assertSame(first.getFirst(), second.getFirst(), "Asset changes must not reread metadata");
		write(pack.resolve("pack_icon.png"), "new icon bytes");
		var third = repository(addons).scan();
		org.junit.jupiter.api.Assertions.assertNotSame(second.getFirst(), third.getFirst(), "Icon changes must invalidate metadata");
		write(pack.resolve("manifest.json"), manifest("Changed metadata", "resources"));
		assertEquals("Changed metadata", repository(addons).scan().getFirst().displayName());
	}

	@Test void nestedDiscoveryDoesNotInflateUnrelatedAssets() throws IOException {
		Path addons = temporaryDirectory.resolve("addons");
		Files.createDirectories(addons);
		var entries = new java.util.LinkedHashMap<String, Object>();
		entries.put("textures/unrelated.png", new byte[8192]);
		entries.put("manifest.json", manifest("Metadata only", "resources"));
		byte[] nested = zipBytes(entries);
		var header = java.nio.ByteBuffer.wrap(nested).order(java.nio.ByteOrder.LITTLE_ENDIAN);
		int dataOffset = 30 + Short.toUnsignedInt(header.getShort(26)) + Short.toUnsignedInt(header.getShort(28));
		nested[dataOffset] = (byte)0xff; // Corrupt irrelevant DEFLATE data, retaining the central directory.
		writeZipBytes(addons.resolve("nested.mcaddon"), Map.of("nested.mcpack", nested));
		var first = repository(addons).scan();
		assertEquals("Metadata only", first.getFirst().pack().orElseThrow().displayName());
		org.junit.jupiter.api.Assertions.assertSame(first.getFirst(), repository(addons).scan().getFirst());
	}

	@Test
	void discoversUnpackedPackAndMalformedPack() throws IOException {
		Path addons = temporaryDirectory.resolve("addons");
		write(addons.resolve("good/manifest.json"), manifest("Good", "resources"));
		write(addons.resolve("bad/manifest.json"), "{not json");

		List<PackLoadResult> results = repository(addons).scan();

		assertEquals(2, results.size());
		assertEquals(1, results.stream().filter(result -> result.pack().isPresent()).count());
		assertEquals(1, results.stream().filter(result -> result.error().isPresent()).count());
	}

	@Test
	void discoversMultiplePacksInMcaddon() throws IOException {
		Path addons = temporaryDirectory.resolve("addons");
		Files.createDirectories(addons);
		writeZip(addons.resolve("bundle.mcaddon"), Map.of(
			"RP/manifest.json", manifest("Resources", "resources"),
			"BP/manifest.json", manifest("Behavior", "data")
		));

		List<PackLoadResult> results = repository(addons).scan();

		assertEquals(2, results.size());
		assertTrue(results.stream().allMatch(result -> result.pack().isPresent()));
	}

	@Test
	void keepsGoodArchiveEntriesWhenAnotherManifestIsMalformed() throws IOException {
		Path addons = temporaryDirectory.resolve("addons");
		Files.createDirectories(addons);
		writeZip(addons.resolve("partial.mcaddon"), Map.of(
			"good/manifest.json", manifest("Good", "resources"),
			"bad/manifest.json", "{broken"
		));

		List<PackLoadResult> results = repository(addons).scan();

		assertEquals(2, results.size());
		assertEquals(1, results.stream().filter(result -> result.pack().isPresent()).count());
		assertEquals(1, results.stream().filter(result -> result.error().isPresent()).count());
	}

	@Test
	void discoversMcpackNestedInsideMcaddon() throws IOException {
		Path addons = temporaryDirectory.resolve("addons");
		Files.createDirectories(addons);
		byte[] nested = zipBytes(Map.of("manifest.json", manifest("Nested", "resources")));
		writeZipBytes(addons.resolve("nested.mcaddon"), Map.of("nested.mcpack", nested));

		List<PackLoadResult> results = repository(addons).scan();

		assertEquals(1, results.size());
		assertEquals("Nested", results.getFirst().pack().orElseThrow().displayName());
		assertEquals("nested.mcpack!/manifest.json", results.getFirst().locationInSource());
	}

	@Test
	void acceptsAssetHeavyPackWithMoreThanTenThousandEntries() throws IOException {
		Path addons = temporaryDirectory.resolve("addons");
		Files.createDirectories(addons);
		Path archive = addons.resolve("large.mcpack");
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
			for (int index = 0; index < 10_700; index++) {
				zip.putNextEntry(new ZipEntry("textures/generated/asset_" + index + ".png"));
				zip.closeEntry();
			}
			zip.putNextEntry(new ZipEntry("manifest.json"));
			zip.write(manifest("Large", "resources").getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}

		List<PackLoadResult> results = repository(addons).scan();

		assertEquals(1, results.size());
		assertEquals("Large", results.getFirst().pack().orElseThrow().displayName());
	}

	private BedrockPackRepository repository(Path addons) {
		return new BedrockPackRepository(addons, new BedrockManifestParser());
	}

	private static String manifest(String name, String type) {
		return """
			{"format_version":2,"header":{"name":"%s","description":"Test","uuid":"12345678-1234-1234-1234-1234567890ab","version":[1,0,0]},"modules":[{"type":"%s","uuid":"abcdefab-1234-1234-1234-abcdefabcdef","version":[1,0,0]}]}
			""".formatted(name, type);
	}

	private static void write(Path path, String content) throws IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, content);
	}

	private static void writeZip(Path path, Map<String, String> entries) throws IOException {
		Map<String, byte[]> bytes = entries.entrySet().stream().collect(java.util.stream.Collectors.toMap(
			Map.Entry::getKey, entry -> entry.getValue().getBytes(StandardCharsets.UTF_8)));
		writeZipBytes(path, bytes);
	}

	private static void writeZipBytes(Path path, Map<String, byte[]> entries) throws IOException {
		Files.write(path, zipBytes(entries));
	}

	private static byte[] zipBytes(Map<String, ?> entries) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (Map.Entry<String, ?> entry : entries.entrySet()) {
				zip.putNextEntry(new ZipEntry(entry.getKey()));
				byte[] value = entry.getValue() instanceof byte[] raw ? raw : entry.getValue().toString().getBytes(StandardCharsets.UTF_8);
				zip.write(value);
				zip.closeEntry();
			}
		}
		return bytes.toByteArray();
	}
}
