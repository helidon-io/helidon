/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.blocking;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.configurable.ThreadPoolSupplier;

/**
 * Handling of configured (or default) executor service.
 */
final class ExecutorServiceSupport {
    private ExecutorServiceSupport() {
    }

    static void defaultExecutor(ExecutorService executor) {
        ExecutorServiceHolder.EXECUTOR_SUPPLIER.set(() -> executor);
    }

    static void defaultExecutor(Supplier<ExecutorService> executor) {
        ExecutorServiceHolder.EXECUTOR_SUPPLIER.set(executor);
    }

    static ExecutorService executorService() {
        return ExecutorServiceHolder.EXECUTOR.get();
    }

    private static ExecutorService defaultExecutor() {
        return ThreadPoolSupplier.builder()
                .virtualIfAvailable(true)
                .name("server-blocking")
                .threadNamePrefix("blocking-")
                .build()
                .get();
    }

    private static final class ExecutorServiceHolder {
        private static final AtomicReference<Supplier<ExecutorService>> EXECUTOR_SUPPLIER =
                new AtomicReference<>(ExecutorServiceSupport::defaultExecutor);

        private static final LazyValue<ExecutorService> EXECUTOR = LazyValue.create(() -> EXECUTOR_SUPPLIER.get().get());
    }
}
