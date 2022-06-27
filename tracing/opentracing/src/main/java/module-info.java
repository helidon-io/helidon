module io.helidon.tracing.opentracing {
    exports io.helidon.tracing.opentracing.spi;
    exports io.helidon.tracing.opentracing;
    requires transitive io.helidon.common;
    requires transitive io.helidon.config;
    requires transitive io.helidon.tracing;

    requires io.opentracing.util;
    requires io.opentracing.api;
    requires io.opentracing.noop;
    requires io.helidon.common.serviceloader;

    uses io.helidon.tracing.opentracing.spi.OpenTracingProvider;
    provides io.helidon.tracing.spi.TracerProvider with io.helidon.tracing.opentracing.OpenTracingTracerProvider;
}