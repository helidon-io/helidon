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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.reactive.Single;

public class Timeout implements Handler {
    private final Duration timeout;
    private final LazyValue<? extends ScheduledExecutorService> executor;

    private Timeout(Builder builder) {
        this.timeout = builder.timeout;
        this.executor = builder.executor;;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public <T> Single<T> invoke(Supplier<? extends CompletionStage<T>> supplier) {
        return Single.create(supplier.get())
                .timeout(timeout.toMillis(), TimeUnit.MILLISECONDS, executor.get());
    }

    public static class Builder implements io.helidon.common.Builder<Timeout> {
        private Duration timeout = Duration.ofSeconds(10);
        private LazyValue<? extends ScheduledExecutorService> executor = FaultTolerance.scheduledExecutor();;

        private Builder() {
        }

        @Override
        public Timeout build() {
            return new Timeout(this);
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder executor(ScheduledExecutorService executor) {
            this.executor = LazyValue.create(executor);
            return this;
        }
    }
}
