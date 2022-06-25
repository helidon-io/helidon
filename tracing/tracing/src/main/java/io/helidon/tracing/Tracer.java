package io.helidon.tracing;

import java.util.Optional;
import java.util.function.BiConsumer;

public interface Tracer {

    static Tracer noOp() {
        return new NoOpTracer();
    }
    static Tracer global() {
        return TracerProviderHelper.global();
    }

    static void global(Tracer tracer) {
        TracerProviderHelper.global(tracer);
    }

    boolean enabled();

    Span.Builder<?> spanBuilder(String name);

    Optional<SpanContext> extract(HeaderProvider headersProvider);

    void inject(SpanContext spanContext, HeaderProvider inboundHeadersProvider, BiConsumer<String, String> outboundHeadersConsumer);
}
