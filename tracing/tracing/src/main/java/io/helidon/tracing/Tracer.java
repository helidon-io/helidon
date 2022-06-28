package io.helidon.tracing;

import java.util.Optional;

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

    void inject(SpanContext spanContext, HeaderProvider inboundHeadersProvider, HeaderConsumer outboundHeadersConsumer);

    /**
     * Access the underlying tracer by specific type.
     * This is a dangerous operation that will succeed only if the tracer is of expected type. This practically
     * removes abstraction capabilities of this API.
     *
     * @param tracerClass type to access
     * @return instance of the tracer
     * @param <T> type of the tracer
     * @throws java.lang.IllegalArgumentException in case the tracer cannot provide the expected type
     */
    default <T> T unwrap(Class<T> tracerClass) {
        try {
            return tracerClass.cast(this);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("This tracer is not compatible with " + tracerClass.getName());
        }
    }
}
