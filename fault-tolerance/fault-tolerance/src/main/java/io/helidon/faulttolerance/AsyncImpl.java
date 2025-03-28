/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;

/**
 * Implementation of {@code Async}. Default executor accessed from {@link FaultTolerance#executor()}.
 */
@Service.PerInstance(AsyncConfigBlueprint.class)
class AsyncImpl implements Async {
    private static final System.Logger LOGGER = System.getLogger(AsyncImpl.class.getName());

    private final ExecutorService executor;
    private final CompletableFuture<Async> onStart;
    private final AsyncConfig config;

    // this must only be invoked when within Inject, so we can use inject services
    @Service.Inject
    AsyncImpl(ServiceRegistry services, AsyncConfig config) {
        this.executor = config.executor()
                .or(() -> config.executorName().flatMap(it -> executorService(services, it)))
                .orElseGet(() -> FaultTolerance.executor().get());
        this.onStart = config.onStart().orElseGet(CompletableFuture::new);
        this.config = config;
    }

    AsyncImpl(AsyncConfig config, boolean internal) {
        this.executor = config.executor().orElseGet(() -> FaultTolerance.executor().get());
        this.onStart = config.onStart().orElseGet(CompletableFuture::new);
        this.config = config;
    }

    @Override
    public AsyncConfig prototype() {
        return config;
    }

    @Override
    public <T> CompletableFuture<T> invoke(Supplier<T> supplier) {
        AtomicReference<Future<?>> ourFuture = new AtomicReference<>();
        CompletableFuture<T> result = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                Future<?> toCancel = ourFuture.get();
                if (toCancel == null) {
                    // cancelled before the future was assigned - this should not happen, as we do
                    // not escape this method before that
                    LOGGER.log(System.Logger.Level.WARNING, "Failed to cancel future, it is not yet available.");
                    return false;
                } else {
                    return toCancel.cancel(mayInterruptIfRunning);
                }
            }
        };
        Future<?> future = executor.submit(() -> {
            Thread thread = Thread.currentThread();
            thread.setName(thread.getName() + ": async");
            if (onStart != null) {
                onStart.complete(this);
            }
            try {
                T t = supplier.get();
                result.complete(t);
            } catch (Throwable t) {
                Throwable throwable = SupplierHelper.unwrapThrowable(t);
                result.completeExceptionally(throwable);
            }
        });
        ourFuture.set(future);

        return result;
    }

    private static Optional<ExecutorService> executorService(ServiceRegistry services, String name) {
        return services.first(Lookup.builder()
                                      .addContract(ExecutorService.class)
                                      .addQualifier(Qualifier.createNamed(name))
                                      .build());
    }
}
