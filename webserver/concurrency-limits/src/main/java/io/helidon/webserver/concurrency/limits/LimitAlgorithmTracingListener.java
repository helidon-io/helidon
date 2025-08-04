/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.concurrency.limits;

import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.concurrency.limits.LimitAlgorithmListener;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;

/**
 * Behavior of a {@link io.helidon.common.concurrency.limits.LimitAlgorithmListener} for adding a "time waiting" span.
 */
@RuntimeType.PrototypedBy(LimitAlgorithmTracingListenerConfig.class)
public interface LimitAlgorithmTracingListener extends LimitAlgorithmListener<LimitAlgorithmTracingListener.Context>,
                                                       RuntimeType.Api<LimitAlgorithmTracingListenerConfig> {

    /**
     * Creates a new builder for a tracing listener.
     *
     * @return new builder
     */
    static LimitAlgorithmTracingListenerConfig.Builder builder() {
        return LimitAlgorithmTracingListenerConfig.builder();
    }

    /**
     * Creates a default tracing listener.
     *
     * @return new tracing listener with default settings
     */
    static LimitAlgorithmTracingListener create() {
        return builder().build();
    }

    /**
     * Creates a new tracing listener using the specified settings.
     *
     * @param config settings for building the new listener
     * @return new listener configured using the specified settings
     */
    static LimitAlgorithmTracingListener create(LimitAlgorithmTracingListenerConfig config) {
        return new LimitAlgorithmTracingListenerImpl(config);
    }

    /**
     * Creates a new tracing listener from a builder, customizing the builder's settings.
     *
     * @param consumer consumer of the builder, modifying the builder as needed
     * @return new tracing listener
     */
    static LimitAlgorithmTracingListener create(Consumer<LimitAlgorithmTracingListenerConfig.Builder> consumer) {
        return builder()
                .update(consumer)
                .build();
    }

    /**
     * Whether the listener is enabled.
     *
     * @return true if enabled; false otherwise
     */
    boolean enabled();

    /**
     * Limit tracing listener context.
     */
    interface Context extends LimitAlgorithmListener.Context {

        /**
         * Indicates if the context represents a limit decision that should be recorded as a tracing span.
         *
         * @return true if a span should be recorded for this context; false otherwise
         */
        default boolean isRecordable() {
            return false;
        }

        /**
         * Creates a tracing span for the limits decision represented by this context.
         *
         * @param tracer the {@link io.helidon.tracing.Tracer} to use in creating the span
         * @param spanContext the parent {@link io.helidon.tracing.SpanContext} to use in creating the span, if any
         */
        default void createWaitingSpan(Tracer tracer, Optional<SpanContext> spanContext) {
        }
    }
}
