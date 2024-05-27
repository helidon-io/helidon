/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import java.time.Duration;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;

/**
 * Timeout attempts to terminate execution after defined duration of time.
 */
@RuntimeType.PrototypedBy(TimeoutConfig.class)
public interface Timeout extends FtHandler, RuntimeType.Api<TimeoutConfig> {
    /**
     * Create a timeout based on configuration.
     *
     * @param config timeout configuration
     * @return timeout handler
     */
    static Timeout create(TimeoutConfig config) {
        return new TimeoutImpl(config);
    }

    /**
     * Create a timeout with a possibility to customize its configuration.
     *
     * @param builderConsumer consumer to customize configuration
     * @return a new timeout
     */
    static Timeout create(Consumer<TimeoutConfig.Builder> builderConsumer) {
        var builder = TimeoutConfig.builder();
        builderConsumer.accept(builder);
        return create(builder.buildPrototype());
    }

    /**
     * Create a new fluent API builder to customize a timeout.
     *
     * @return a new timeout builder
     */
    static TimeoutConfig.Builder builder() {
        return TimeoutConfig.builder();
    }

    /**
     * Create a {@link Timeout} with specified timeout.
     *
     * @param timeout duration of the timeout of operations handled by the new Timeout instance
     * @return a new timeout
     */
    static Timeout create(Duration timeout) {
        return create(TimeoutConfig.builder()
                              .timeout(timeout)
                              .buildPrototype());
    }
}
