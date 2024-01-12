module io.helidon.webserver.codegen {
    requires io.helidon.codegen;
    requires io.helidon.codegen.classmodel;
    requires io.helidon.inject.codegen;

    exports io.helidon.webserver.codegen;

    provides io.helidon.codegen.spi.CodegenExtensionProvider
            with io.helidon.webserver.codegen.WebServerCodegenProvider;
}