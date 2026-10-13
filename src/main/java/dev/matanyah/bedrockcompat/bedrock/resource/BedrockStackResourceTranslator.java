package dev.matanyah.bedrockcompat.bedrock.resource;

import dev.matanyah.bedrockcompat.resource.GeneratedResourceSink;
import java.util.List;

/** Resource consumers whose references are resolved across the selected pack stack. */
public interface BedrockStackResourceTranslator extends BedrockResourceTranslator {
    void translateStack(List<BedrockTranslationPipeline.ConfiguredPack> highToLow,
                        GeneratedResourceSink sink, List<String> diagnostics);
}
