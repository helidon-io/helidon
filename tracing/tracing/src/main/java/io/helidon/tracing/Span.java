package io.helidon.tracing;

import java.time.Instant;
import java.util.Map;

public interface Span {
    default void tag(Tag<?> tag) {
        tag.apply(this);
    }

    void tag(String key, String value);
    void tag(String key, Boolean value);
    void tag(String key, Number value);

    void status(Status status);
    SpanContext context();

    void addEvent(String name, Map<String, String> attributes);

    void end();

    /**
     * And with error status and an exception.
     * @param t
     */
    void end(Throwable t);

    default void addEvent(String logMessage) {
        addEvent(logMessage, Map.of());
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
