package dev.matanyah.bedrockcompat.bedrock.resource;

import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackConfiguration;
import dev.matanyah.bedrockcompat.resource.GeneratedResourceSink;

import java.io.IOException;

public interface BedrockResourceTranslator {
	void translate(BedrockPack pack, BedrockPackConfiguration configuration, GeneratedResourceSink sink) throws IOException;
}
