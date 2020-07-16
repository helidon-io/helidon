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
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

class TimeoutImpl implements Timeout {
    private final long timeoutMillis;
    private final LazyValue<? extends ScheduledExecutorService> executor;
    private final boolean async;

    TimeoutImpl(Timeout.Builder builder, boolean async) {
        this.timeoutMillis = builder.timeout().toMillis();
        this.executor = builder.executor();
        this.async = async;
    }

    @Override
    public <T> Multi<T> invokeMulti(Supplier<? extends Flow.Publisher<T>> supplier) {
        if (!async) {
            throw new UnsupportedOperationException("Timeout with Publisher<T> must be async");
        }
        return Multi.create(supplier.get())
                .timeout(timeoutMillis, TimeUnit.MILLISECONDS, executor.get());
    }

    @Override
    public <T> Single<T> invoke(Supplier<? extends CompletionStage<T>> supplier) {
        if (async) {
            return Single.create(supplier.get())
                    .timeout(timeoutMillis, TimeUnit.MILLISECONDS, executor.get());
        } else {
            Thread currentThread = Thread.currentThread();
            AtomicBoolean called = new AtomicBoolean();
            Timeout.create(Duration.ofMillis(timeoutMillis))
                    .invoke(Single::never)
                    .exceptionally(it -> {
                        if (called.compareAndSet(false, true)) {
                            currentThread.interrupt();
                        }
                        return null;
                    });
            Single<T> single = Single.create(supplier.get());       // runs in current thread
            called.set(true);
            return single;
        }
    }
}
