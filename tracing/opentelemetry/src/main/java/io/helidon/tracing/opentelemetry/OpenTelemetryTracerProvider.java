package io.helidon.tracing.opentelemetry;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.LazyValue;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;
import io.helidon.tracing.spi.TracerProvider;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;

public class OpenTelemetryTracerProvider implements TracerProvider {
    private static final System.Logger LOGGER = System.getLogger(OpenTelemetryTracerProvider.class.getName());

    private static final AtomicReference<OpenTelemetryTracer> CONFIGURED_TRACER = new AtomicReference<>();
    private static final LazyValue<Tracer> GLOBAL_TRACER;

    static {
        GLOBAL_TRACER = LazyValue.create(() -> {
            // try to get from configured global tracer
            Tracer tracer = CONFIGURED_TRACER.get();
            if (tracer != null) {
                return tracer;
            }
            Context global = Contexts.globalContext();
            return global.get(OpenTelemetryTracer.class)
                    .map(Tracer.class::cast)
                    .orElseGet(() -> {
                        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                            LOGGER.log(System.Logger.Level.TRACE, "Global tracer is not registered. Register it through "
                                    + "Tracer.global(HelidonOpenTelemetry.create(ot, tracer). Using global open telemetry");
                        }
                        OpenTelemetry ot = GlobalOpenTelemetry.get();
                        return new OpenTelemetryTracer(ot, ot.getTracer("helidon-service"));
                    });
        });
    }

    @Override
    public TracerBuilder<?> createBuilder() {
        return OpenTelemetryTracer.builder();
    }

    @Override
    public Tracer global() {
        return GLOBAL_TRACER.get();
    }

    @Override
    public void global(Tracer tracer) {
        if (tracer instanceof OpenTelemetryTracer ott) {
            CONFIGURED_TRACER.set(ott);
        }
        throw new IllegalArgumentException("Tracer must be an instance of Helidon OpenTelemetry tracer. "
                                                   + "Please use HelidonOpenTelemetry to create such instance");
    }

    @Override
    public Optional<Span> currentSpan() {
        return Optional.ofNullable(io.opentelemetry.api.trace.Span.current()).map(HelidonOpenTelemetry::create);
    }
}
