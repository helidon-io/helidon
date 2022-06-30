/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.tracing;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Tracing span. A span is started by {@link io.helidon.tracing.Span.Builder#start()} and ended by either
 * {@link #end()} or {@link #end(Throwable)}.
 * You can obtain {@link io.helidon.tracing.SpanContext} from {@link #context()}.
 * Span is the base reporting unit of tracing. Spans can have a parent ({@link Builder#parent(SpanContext)},
 * you can add {@link #tag(Tag)} to it, and you can log {@link #addEvent(String, java.util.Map)}.
 */
public interface Span {
    /**
     * Provide current span if one is available.
     * This is using a thread local, so it may provide unexpected results in a reactive environment. Please use
     * request methods to obtain request span in Reactive Web Server.
     *
     * @return current span or empty optional if there is no current span
     * @see #activate()
     */
    static Optional<Span> current() {
        return TracerProviderHelper.currentSpan();
    }

    /**
     * Add a tag to this span.
     *
     * @param tag tag to add
     */
    default void tag(Tag<?> tag) {
        tag.apply(this);
    }

    /**
     * Add a string tag.
     *
     * @param key tag key
     * @param value tag value
     */
    void tag(String key, String value);
    /**
     * Add a boolean tag.
     *
     * @param key tag key
     * @param value tag value
     */
    void tag(String key, Boolean value);
    /**
     * Add a numeric tag.
     *
     * @param key tag key
     * @param value tag value
     */
    void tag(String key, Number value);

    /**
     * Span status, mostly used to configure {@link Status#ERROR}.
     *
     * @param status status to set
     */
    void status(Status status);

    /**
     * Span context can be used to configure a span parent, as is used when a span reference is needed, without the
     * possibility to end such a span.
     *
     * @return context of this span
     */
    SpanContext context();

    /**
     * Add an event to this span
     * @param name
     * @param attributes
     */
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
