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

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

class TimeoutImpl implements Timeout {
    private final long timeoutMillis;
    private final LazyValue<? extends ScheduledExecutorService> executor;

    TimeoutImpl(Timeout.Builder builder) {
        this.timeoutMillis = builder.timeout().toMillis();
        this.executor = builder.executor();
    }

    @Override
    public <T> Multi<T> invokeMulti(Supplier<? extends Flow.Publisher<T>> supplier) {
        return Multi.create(supplier.get())
                .timeout(timeoutMillis, TimeUnit.MILLISECONDS, executor.get());
    }

    @Override
    public <T> Single<T> invoke(Supplier<? extends CompletionStage<T>> supplier) {
        return Single.create(supplier.get(), true)
                .timeout(timeoutMillis, TimeUnit.MILLISECONDS, executor.get());
    }
}
