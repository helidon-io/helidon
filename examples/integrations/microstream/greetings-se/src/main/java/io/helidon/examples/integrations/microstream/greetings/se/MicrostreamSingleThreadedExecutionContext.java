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

package io.helidon.examples.integrations.microstream.greetings.se;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import io.helidon.common.reactive.Single;

import one.microstream.reference.LazyReferenceManager;
import one.microstream.storage.embedded.types.EmbeddedStorageManager;

/**
 *
 * The MicrostreamSingleThreadedExecutionContext provides a very simply way to ensure
 * thread safe access to a Microstream storage and it's associated data.
 *
 * This example just uses a single-treaded ExecutorService to avoid the need to manually synchronize
 * any multi-threaded access to the storage and the user provided object-graph.
 *
 */
public class MicrostreamSingleThreadedExecutionContext {

    private final EmbeddedStorageManager storage;
    private final ExecutorService executor;

    /**
     * Creates a MicrostreamSingleThreadedExecutionContext.
     *
     * @param storageManager the used EmbeddedStorageManager.
     */
    public MicrostreamSingleThreadedExecutionContext(EmbeddedStorageManager storageManager) {
        this.storage = storageManager;
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * returns the used storageManager.
     *
     * @return the used EmbeddedStorageManager.
     */
    public EmbeddedStorageManager storageManager() {
        return storage;
    }

    /**
     * returns the used ExecutorService.
     *
     * @return the used ExecutorService.
     */
    public ExecutorService executor() {
        return executor;
    }

    /**
     * Start the storage.
     *
     * @return a Single providing the started EmbeddedStorageManager.
     */
    public Single<EmbeddedStorageManager> start() {
        CompletableFuture<EmbeddedStorageManager> completableFuture = CompletableFuture.supplyAsync(
                storage::start, executor);

        return Single.create(completableFuture);
    }

    /**
     * Shutdown the storage.
     *
     * @return a Single providing stopped EmbeddedStorageManager.
     */
    public Single<EmbeddedStorageManager> shutdown() {
        CompletableFuture<EmbeddedStorageManager> completableFuture = CompletableFuture.supplyAsync(
                () -> {
                    storage.shutdown();
                    LazyReferenceManager.get().stop();
                    executor.shutdown();
                    return storage;
                }, executor);

        return Single.create(completableFuture);
    }

    /**
     * Return the persistent object graph's root object.
     *
     * @param <T> type of the root object
     * @return a Single containing the graph's root object casted to <T>
     */
    public <T> Single<T> root() {
        @SuppressWarnings("unchecked")
        CompletableFuture<T> completableFuture = CompletableFuture.supplyAsync(() -> {
            return (T) storage.root();
        }, executor);
        return Single.create(completableFuture);
    }

    /**
     * Sets the passed instance as the new root for the persistent object graph.
     *
     * @param object the new root object
     * @return Single containing the new root object
     */
    public Single<Object> setRoot(Object object) {
        CompletableFuture<Object> completableFuture = CompletableFuture.supplyAsync(() -> {
            return storage.setRoot(object);
        }, executor);
        return Single.create(completableFuture);
    }

    /**
     * Stores the registered root instance.
     *
     * @return Single containing the root instance's objectId.
     */
    public Single<Long> storeRoot() {
        CompletableFuture<Long> completableFuture = CompletableFuture.supplyAsync(storage::storeRoot, executor);
        return Single.create(completableFuture);
    }

    /**
     * Stores the passed object.
     *
     * @param object
     * @return Single containing the object id representing the passed instance.
     */
    public Single<Long> store(Object object) {
        CompletableFuture<Long> completableFuture = CompletableFuture.supplyAsync(() -> {
            return storage.store(object);
        }, executor);
        return Single.create(completableFuture);
    }

    /**
     * Creates a new CompletableFuture that executes in this context.
     *
     * @param <R> the return type
     * @param supplier a function returning the value to be used to complete the returned CompletableFuture
     * @return the new CompletableFuture
     */
    public <R> CompletableFuture<R> execute(Supplier<R> supplier) {
        CompletableFuture<R> completableFuture = CompletableFuture.supplyAsync(() -> {
            return supplier.get();
        }, executor);
        return completableFuture;
    }
}
