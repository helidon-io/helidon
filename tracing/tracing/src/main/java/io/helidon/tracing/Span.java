/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
     * @return current span
     */
    default Span tag(Tag<?> tag) {
        tag.apply(this);
        return this;
    }

    /**
     * Add a string tag.
     *
     * @param key   tag key
     * @param value tag value
     * @return current span
     */
    Span tag(String key, String value);

    /**
     * Add a boolean tag.
     *
     * @param key   tag key
     * @param value tag value
     * @return current span
     */
    Span tag(String key, Boolean value);

    /**
     * Add a numeric tag.
     *
     * @param key   tag key
     * @param value tag value
     * @return current span
     */
    Span tag(String key, Number value);

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
     * Add an event to this span.
     *
     * @param name name of the event
     * @param attributes event attributes to be recorded
     */
    void addEvent(String name, Map<String, ?> attributes);

    /**
     * End this tag (finish processing) using current timestamp.
     */
    void end();

    /**
     * End with error status and an exception. Configures status to {@link io.helidon.tracing.Span.Status#ERROR}, and
     * adds appropriate tags and events to report this exception.
     *
     * @param t throwable that caused the error status
     */
    void end(Throwable t);

    /**
     * Make this span the current active span. This is expected to use thread locals and as such is not suitable for
     * reactive environment.
     *
     * @return current scope
     */
    Scope activate();

    /**
     * Sets a baggage item in the Span (and its SpanContext) as a key/value pair.
     *
     * @param key String Key
     * @param value String Value
     * @return current Span instance
     */
    Span baggage(String key, String value);

    /**
     * Get Baggage Item by key.
     *
     * @param key String key
     * @return {@link Optional} of the value of the baggage item
     */
    Optional<String> baggage(String key);

    /**
     * Add a new event to this span.
     *
     * @param logMessage message to log
     */
    default void addEvent(String logMessage) {
        addEvent(logMessage, Map.of());
    }

    /**
     * Access the underlying span by specific type.
     * This is a dangerous operation that will succeed only if the span is of expected type. This practically
     * removes abstraction capabilities of this API.
     *
     * @param spanClass type to access
     * @param <T>       type of the span
     * @return instance of the span
     * @throws java.lang.IllegalArgumentException in case the span cannot provide the expected type
     */
    default <T> T unwrap(Class<T> spanClass) {
        try {
            return spanClass.cast(this);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("This span is not compatible with " + spanClass.getName());
        }
    }

    /**
     * Span kind.
     */
    enum Kind {
        /**
         * Internal span, not leaving the scope of the service.
         */
        INTERNAL,
        /**
         * Server span kind, parent of a server interaction.
         */
        SERVER,
        /**
         * Client span, should cover outbound request, where the span creator acts as a client.
         */
        CLIENT,
        /**
         * Producer span, in messaging.
         */
        PRODUCER,
        /**
         * Consumer span, in messaging.
         */
        CONSUMER
    }

    /**
     * Span status.
     */
    enum Status {
        /**
         * The default status, not explicitly set.
         */
        UNSET,
        /**
         * The span was successful.
         */
        OK,
        /**
         * The span ended with an error.
         */
        ERROR
    }

    /**
     * Fluent API builder to create a new {@link io.helidon.tracing.Span}.
     *
     * @param <B> type of the builder that implements this interface, to have correct return types of builder methods
     */
    interface Builder<B extends Builder<B>> extends io.helidon.common.Builder<B, Span> {
        /**
         * Parent span of the new span.
         *
         * @return updated builder instance
         */
        B parent(SpanContext spanContext);

        /**
         * Kind of this span.
         *
         * @param kind kind to use
         * @return updated builder instance
         */
        B kind(Kind kind);

        /**
         * Add a tag.
         *
         * @param tag tag to add (or set)
         * @return updated builder instance
         */
        default B tag(Tag<?> tag) {
            tag.apply(this);
            return identity();
        }

        /**
         * Add a string tag.
         *
         * @param key   tag key
         * @param value tag value
         * @return updated builder instance
         */
        B tag(String key, String value);

        /**
         * Add a boolean tag.
         *
         * @param key   tag key
         * @param value tag value
         * @return updated builder instance
         */
        B tag(String key, Boolean value);

        /**
         * Add a number tag.
         *
         * @param key   tag key
         * @param value tag value
         * @return updated builder instance
         */
        B tag(String key, Number value);

        /**
         * Build and start the span with current timestamp.
         *
         * @return newly created and started span
         */
        default Span start() {
            return start(Instant.now());
        }

        /**
         * Start the span with an explicit timestamp.
         *
         * @param instant when the span started
         * @return newly created and started span
         */

        Span start(Instant instant);

        /**
         * Unwrap this builder instance into a known type. This method limits abstraction and will not allow replacement
         * of tracer implementation.
         *
         * @param type type to unwrap to
         * @param <T>  type of the builder
         * @return unwrapped instance
         * @throws java.lang.IllegalArgumentException when the expected type is not the actual type, or the builder cannot be
         *                                            coerced into that type
         */
        default <T> T unwrap(Class<T> type) {
            if (type.isAssignableFrom(getClass())) {
                return type.cast(this);
            }
            throw new IllegalArgumentException("This instance cannot be unwrapped to " + type.getName()
                                                       + ", this builder: " + getClass().getName());
        }
    }
}
