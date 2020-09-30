/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
    class Builder implements io.helidon.common.Builder<Timeout> {
        private Duration timeout = Duration.ofSeconds(10);
        private LazyValue<? extends ScheduledExecutorService> executor = FaultTolerance.scheduledExecutor();
        private boolean currentThread = false;

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

        Duration timeout() {
            return timeout;
        }

        LazyValue<? extends ScheduledExecutorService> executor() {
            return executor;
        }

        boolean currentThread() {
            return currentThread;
        }
    }
}
