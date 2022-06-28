package io.helidon.tracing;

import java.util.Optional;

class NoOpTracerProvider implements io.helidon.tracing.spi.TracerProvider {
    private static final NoOpBuilder BUILDER = NoOpBuilder.create();
    private static final Tracer TRACER = BUILDER.build();

    @Override
    public TracerBuilder<?> createBuilder() {
        return BUILDER;
    }

    @Override
    public Tracer global() {
        return TRACER;
    }

    @Override
    public void global(Tracer tracer) {
        // ignored
    }

    @Override
    public Optional<Span> currentSpan() {
        return Optional.empty();
    }
}
