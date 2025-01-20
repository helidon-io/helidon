module io.helidon.integrations.langchain4j.codegen {
    requires transitive io.helidon.codegen;
    requires io.helidon.service.codegen;

    exports io.helidon.integrations.langchain4j.codegen;

    provides io.helidon.codegen.spi.CodegenExtensionProvider
            with io.helidon.integrations.langchain4j.codegen.AiServiceCodegenProvider;
    provides io.helidon.codegen.spi.TypeMapperProvider
            with io.helidon.integrations.langchain4j.codegen.LcToolsMapperProvider;
}