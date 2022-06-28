package io.helidon.tracing.opentelemetry;


import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

public class HelidonOpenTelemetry {
    public static OpenTelemetryTracer create(OpenTelemetry telemetry, Tracer tracer) {
        return new OpenTelemetryTracer(telemetry, tracer);
    }

    public static io.helidon.tracing.Span create(Span span) {
        return new OpenTelemetrySpan(span);
    }
}
