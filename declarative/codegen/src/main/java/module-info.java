module io.helidon.declarative.codegen {
    requires io.helidon.codegen;
    requires io.helidon.codegen.classmodel;
    requires io.helidon.service.codegen;

    exports io.helidon.declarative.codegen;
    exports io.helidon.declarative.codegen.spi;

    uses io.helidon.declarative.codegen.spi.HttpParameterCodegenProvider;

    provides io.helidon.codegen.spi.CodegenExtensionProvider
            with io.helidon.declarative.codegen.WebServerCodegenProvider;
}