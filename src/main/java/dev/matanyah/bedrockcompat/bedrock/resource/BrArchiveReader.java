package dev.matanyah.bedrockcompat.bedrock.resource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Defensive reader for Mojang's uncompressed BRArchive container format. */
final class BrArchiveReader {
	private static final long MAGIC = 0x267052A0B125277DL;
	private static final int HEADER_SIZE = 16;
	private static final int DESCRIPTOR_SIZE = 256;
	private static final int NAME_BYTES = 247;
	private static final int MAX_ENTRIES = 100_000;

	private BrArchiveReader() {
	}

	static void visit(byte[] archive, EntryConsumer consumer) throws IOException {
		if (archive.length < HEADER_SIZE) throw new IOException("BRArchive header is truncated");
		ByteBuffer buffer = ByteBuffer.wrap(archive).order(ByteOrder.LITTLE_ENDIAN);
		if (buffer.getLong(0) != MAGIC) throw new IOException("Invalid BRArchive magic");
		long countLong = Integer.toUnsignedLong(buffer.getInt(8));
		int version = buffer.getInt(12);
		if (version != 1) throw new IOException("Unsupported BRArchive version " + version);
		if (countLong > MAX_ENTRIES) throw new IOException("BRArchive contains too many entries");
		int count = (int) countLong;
		long contentBaseLong = HEADER_SIZE + countLong * DESCRIPTOR_SIZE;
		if (contentBaseLong > archive.length) throw new IOException("BRArchive descriptors are truncated");
		int contentBase = (int) contentBaseLong;
		for (int index = 0; index < count; index++) {
			int descriptor = HEADER_SIZE + index * DESCRIPTOR_SIZE;
			int nameLength = Byte.toUnsignedInt(archive[descriptor]);
			if (nameLength > NAME_BYTES) throw new IOException("Invalid BRArchive entry name length");
			String name = new String(archive, descriptor + 1, nameLength, StandardCharsets.UTF_8);
			long offset = Integer.toUnsignedLong(buffer.getInt(descriptor + 248));
			long length = Integer.toUnsignedLong(buffer.getInt(descriptor + 252));
			long start = contentBaseLong + offset;
			long end = start + length;
			if (start < contentBase || end < start || end > archive.length) {
				throw new IOException("BRArchive entry points outside the archive");
			}
			byte[] data = new byte[(int) length];
			System.arraycopy(archive, (int) start, data, 0, data.length);
			consumer.accept(name, data);
		}
	}

	@FunctionalInterface
	interface EntryConsumer {
		void accept(String name, byte[] data) throws IOException;
	}
}
