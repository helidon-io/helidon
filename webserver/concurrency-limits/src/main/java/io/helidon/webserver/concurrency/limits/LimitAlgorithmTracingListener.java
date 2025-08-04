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

    static LimitAlgorithmTracingListenerConfig.Builder builder() {
        return LimitAlgorithmTracingListenerConfig.builder();
    }

    static LimitAlgorithmTracingListener create() {
        return builder().build();
    }

    static LimitAlgorithmTracingListener create(LimitAlgorithmTracingListenerConfig config) {
        return new LimitAlgorithmTracingListenerImpl(config);
    }

    static LimitAlgorithmTracingListener create(Consumer<LimitAlgorithmTracingListenerConfig.Builder> consumer) {
        return builder()
                .update(consumer)
                .build();
    }


    interface Context extends LimitAlgorithmListener.Context {

        default boolean isRecordable() {
            return false;
        }

        default void process(Tracer tracer, Optional<SpanContext> spanContext) {
        }
    }

    /**
     * Whether the listener is enabled.
     *
     * @return true if enabled; false otherwise
     */
    boolean enabled();
}
