/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.util.Map;

/**
 * {@link io.helidon.tracing.Span} operations that do not affect the span's life cycle state.
 */
public interface SpanInfo extends TagSetter<SpanInfo> {

    /**
     * Add an event to this span.
     *
     * @param name name of the event
     * @param attributes event attributes to be recorded
     */
    void addEvent(String name, Map<String, ?> attributes);

    /**
     * Returns writable baggage associated with this span.
     *
     * @return the mutable baggage instance for the span
     */
    WritableBaggage baggage();

    /**
     * Add a new event to this span.
     *
     * @param logMessage message to log
     */
    default void addEvent(String logMessage) {
        addEvent(logMessage, Map.of());
    }

    /**
     * {@link io.helidon.tracing.Span.Builder} operations that do not affect the builder's lifecycle state.
     *
     * @param <T> type returned from tag-setting methods
     */
    interface BuilderInfo<T> extends TagSetter<T> {
    }

    /**
     * {@link io.helidon.tracing.Scope} operations that do not affect the scope's lifecycle state.
     */
    interface ScopeInfo {
    }
}
