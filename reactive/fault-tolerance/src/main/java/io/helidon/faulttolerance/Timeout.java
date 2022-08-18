/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
import java.util.concurrent.ScheduledExecutorService;

import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Timeout attempts to terminate execution after a duration time passes.
 * In such a case, the consumer of this handler receives a {@link io.helidon.common.reactive.Single}
 * or {@link io.helidon.common.reactive.Multi} with a {@link java.util.concurrent.TimeoutException}.
 */
public interface Timeout extends FtHandler {
    /**
     * A builder to create a customized {@link io.helidon.faulttolerance.Timeout}.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create a {@link io.helidon.faulttolerance.Timeout} with specified timeout.
     *
     * @param timeout duration of the timeout of operations handled by the new Timeout instance
     * @return a new timeout
     */
    static Timeout create(Duration timeout) {
        return builder().timeout(timeout).build();
    }

    /**
     * Fluent API builder for {@link io.helidon.faulttolerance.Timeout}.
     */
    @Configured
    class Builder implements io.helidon.common.Builder<Builder, Timeout> {
        private Duration timeout = Duration.ofSeconds(10);
        private LazyValue<? extends ScheduledExecutorService> executor = FaultTolerance.scheduledExecutor();
        private boolean currentThread = false;
        private String name = "Timeout-" + System.identityHashCode(this);
        private boolean cancelSource = true;

        private Builder() {
        }

        @Override
        public Timeout build() {
            return new TimeoutImpl(this);
        }

        /**
         * Timeout duration.
         *
         * @param timeout duration of the timeout of operations handled by the new Timeout instance
         * @return updated builder instance
         */
        @ConfiguredOption("PT10S")
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Flag to indicate that code must be executed in current thread instead
         * of in an executor's thread. This flag is {@code false} by default.
         *
         * @param currentThread setting for this timeout
         * @return updated builder instance
         */
        @ConfiguredOption("false")
        public Builder currentThread(boolean currentThread) {
            this.currentThread = currentThread;
            return this;
        }

        /**
         * Executor service to schedule the timeout.
         *
         * @param executor scheduled executor service to use
         * @return updated builder instance
         */
        public Builder executor(ScheduledExecutorService executor) {
            this.executor = LazyValue.create(executor);
            return this;
        }

        /**
         * A name assigned for debugging, error reporting or configuration purposes.
         *
         * @param name the name
         * @return updated builder instance
         */
        @ConfiguredOption("Timeout-")
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Cancel source if destination stage is cancelled.
         *
         * @param cancelSource setting for cancel source, defaults (@code true}
         * @return updated builder instance
         */
        @ConfiguredOption("true")
        public Builder cancelSource(boolean cancelSource) {
            this.cancelSource = cancelSource;
            return this;
        }

        /**
         * <p>
         * Load all properties for this circuit breaker from configuration.
         * </p>
         * <table class="config">
         * <caption>Configuration</caption>
         * <tr>
         *     <th>key</th>
         *     <th>default value</th>
         *     <th>description</th>
         * </tr>
         * <tr>
         *     <td>timeout</td>
         *     <td>10 seconds</td>
         *     <td>Length of timeout</td>
         * </tr>
         * <tr>
         *     <td>current-thread</td>
         *     <td>false</td>
         *     <td>Control that task is executed in calling thread</td>
         * </tr>
         * <tr>
         *     <td>name</td>
         *     <td>Timeout-N</td>
         *     <td>Name used for debugging</td>
         * </tr>
         * <tr>
         *     <td>cancel-source</td>
         *     <td>true</td>
         *     <td>Cancel task source if task is cancelled</td>
         * </tr>
         * </table>
         *
         * @param config the config node to use
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("timeout").as(Duration.class).ifPresent(this::timeout);
            config.get("current-thread").asBoolean().ifPresent(this::currentThread);
            config.get("name").asString().ifPresent(this::name);
            config.get("cancel-source").asBoolean().ifPresent(this::cancelSource);
            return this;
        }

        Duration timeout() {
            return timeout;
        }

        LazyValue<? extends ScheduledExecutorService> executor() {
            return executor;
        }

        boolean currentThread() {
            return currentThread;
        }

        String name() {
            return name;
        }

        boolean cancelSource() {
            return cancelSource;
        }
    }
}
