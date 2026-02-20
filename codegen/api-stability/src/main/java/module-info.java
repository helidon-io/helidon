module io.helidon.codegen.api.stability {
    requires jdk.compiler;
    requires java.compiler;

    requires io.helidon.codegen;
    requires io.helidon.common.types;
    requires io.helidon.common;
    requires java.xml;
    requires java.desktop;

    exports io.helidon.codegen.api.stability;

    provides javax.annotation.processing.Processor with
            io.helidon.codegen.api.stability.ApiStabilityProcessor;
}