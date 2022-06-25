package io.helidon.tracing;

import java.util.Optional;
import java.util.function.BiConsumer;

class NoOpTracerProvider implements io.helidon.tracing.spi.TracerProvider {
    private static final NoOpBuilder BUILDER = NoOpBuilder.create();
    private static final Tracer TRACER = BUILDER.build();

    @Override
    public TracerBuilder<?> createBuilder() {
        return BUILDER;
    }

    @Override
    public Optional<SpanContext> extract(HeaderProvider headersProvider) {
        return Optional.of(NoOpTracer.SPAN_CONTEXT);
    }

    @Override
    public void inject(SpanContext spanContext,
                       HeaderProvider inboundHeadersProvider,
                       BiConsumer<String, String> outboundHeadersConsumer) {

    }

    @Override
    public Tracer global() {
        return TRACER;
    }

    @Override
    public void global(Tracer tracer) {
        // ignored
    }
}
