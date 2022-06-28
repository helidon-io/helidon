package io.helidon.tracing;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public interface Span {
    static Optional<Span> current() {
        return TracerProviderHelper.currentSpan();
    }

    default void tag(Tag<?> tag) {
        tag.apply(this);
    }

    void tag(String key, String value);
    void tag(String key, Boolean value);
    void tag(String key, Number value);

    void status(Status status);
    SpanContext context();

    void addEvent(String name, Map<String, ?> attributes);

    void end();

    Scope activate();

    /**
     * And with error status and an exception.
     * @param t
     */
    void end(Throwable t);

    default void addEvent(String logMessage) {
        addEvent(logMessage, Map.of());
    }

    /**
     * Access the underlying span by specific type.
     * This is a dangerous operation that will succeed only if the span is of expected type. This practically
     * removes abstraction capabilities of this API.
     *
     * @param spanClass type to access
     * @return instance of the span
     * @param <T> type of the span
     * @throws java.lang.IllegalArgumentException in case the span cannot provide the expected type
     */
    default <T> T unwrap(Class<T> spanClass) {
        try {
            return spanClass.cast(this);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("This span is not compatible with " + spanClass.getName());
        }
    }

    interface Builder<B extends Builder<B>> extends io.helidon.common.Builder<B, Span> {
        B parent(SpanContext spanContext);

        B kind(Kind kind);

        default B tag(Tag<?> tag) {
            tag.apply(this);
            return identity();
        }
        B tag(String key, String value);
        B tag(String key, Boolean value);
        B tag(String key, Number value);

        default Span start() {
            return start(Instant.now());
        }

        Span start(Instant instant);

        default <T> T unwrap(Class<T> type) {
            return type.cast(this);
        }
    }

    enum Kind {
        INTERNAL,
        SERVER,
        CLIENT,
        PRODUCER,
        CONSUMER
    }

    enum Status {
        UNSET,
        OK,
        ERROR
    }
}
