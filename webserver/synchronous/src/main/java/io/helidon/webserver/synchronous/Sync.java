/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.webserver.synchronous;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.webserver.ServerRequest;

/**
 * Synchronous method support for Web Server.
 *
 * @see #submit(ServerRequest, Supplier)
 * @see #accept(ServerRequest, Runnable)
 */
public class Sync {
    private final ExecutorService executorService;

    Sync(ExecutorService executorService) {
        this.executorService = executorService;
    }

    /**
     * Execute a blocking task and return a {@link CompletableFuture} representing the future
     * result.
     *
     * @param req      request to obtain {@link SyncSupport} registered with {@link io.helidon.webserver.Routing}
     * @param supplier representing the blocking operation
     * @param <T>      type of the result returned by provided supplier
     * @return a future that completes when the synchronous blocking operation finishes execution on
     * {@link ExecutorService} provided by {@link SyncSupport}
     */
    public static <T> CompletableFuture<T> submit(ServerRequest req, Supplier<T> supplier) {
        // get the sync registered by sync support
        // this is done so we can have a single place of configuration that is
        // bound to webserver - now for executor service
        Sync sync = req.context().get(Sync.class)
                .orElseThrow(() -> new SyncExecutionException("SyncSupport is not properly configured. Please add "
                                                                      + "routingBuilder.register(SyncSupport.create()) to your "
                                                                      + "setup"));

        // need to access the future before it is created
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();

        // cancel support built in
        CompletableFuture<T> result = new CompletableFuture<T>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                Future<?> future = futureRef.get();
                if (null != future) {
                    super.cancel(mayInterruptIfRunning);
                    return future.cancel(mayInterruptIfRunning);
                } else {
                    return false;
                }
            }
        };

        // submit the supplier to our executor service
        Future<?> future = sync.executorService.submit(() -> {
            try {
                T theResult = supplier.get();
                result.complete(theResult);
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });

        futureRef.set(future);

        return result;
    }

    /**
     * The same behavior as {@link #submit(ServerRequest, Supplier)} for {@link Runnable} - for cases
     * that do not supply any value.
     *
     * @param req      request to obtain {@link SyncSupport} registered with {@link io.helidon.webserver.Routing}
     * @param runnable representing the blocking operation
     * @return a future that completes when the synchronous blocking operation finishes execution on
     * {@link ExecutorService} provided by {@link SyncSupport}
     */
    public static CompletableFuture<Void> accept(ServerRequest req, Runnable runnable) {
        return submit(req, () -> {
            runnable.run();
            return null;
        });
    }
}
