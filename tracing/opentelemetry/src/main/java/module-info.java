module io.helidon.tracing.opentelemetry {
    requires io.helidon.tracing;
    requires io.helidon.common.context;

    requires io.opentelemetry.api;
    requires io.opentelemetry.context;
    requires io.opentelemetry.semconv;
    requires io.helidon.common;
    requires io.helidon.config;

    exports io.helidon.tracing.opentelemetry;

    provides io.helidon.tracing.spi.TracerProvider with io.helidon.tracing.opentelemetry.OpenTelemetryTracerProvider;
}