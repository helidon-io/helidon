module io.helidon.config.metadata {
    requires java.compiler;
    requires java.json;

    exports io.helidon.config.metadata;

    provides javax.annotation.processing.Processor with io.helidon.config.metadata.ConfigMetadataProcessor;
}