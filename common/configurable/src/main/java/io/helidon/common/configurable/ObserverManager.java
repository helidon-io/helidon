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
package io.helidon.common.configurable;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.helidon.common.LazyValue;
import io.helidon.common.configurable.spi.ExecutorServiceSupplierObserver;

/**
 * Central coordination point between executor service suppliers and observers of them.
 * <p>
 *     Each executor service supplier should:
 *     <ul>
 *         <li>from its constructor, register with this class by invoking one of the
 *         {@code #registerSupplier} methods, and</li>
 *         <li>notify this class whenever it creates a new executor service by invoking
 *         {@link #registerExecutorService(java.util.function.Supplier, java.util.concurrent.ExecutorService)}.</li>
 *     </ul>
 * </p>
 */
class ObserverManager {

    private static final Logger LOGGER = Logger.getLogger(ObserverManager.class.getName());

    private static final LazyValue<List<ExecutorServiceSupplierObserver>> OBSERVER_LIST = LazyValue
            .create(ObserverManager::loadObservers);

    private static final Map<Supplier<? extends ExecutorService>, List<ExecutorServiceSupplierObserver.SupplierObserverContext>>
            SUPPLIER_CONTEXTS = new ConcurrentHashMap<>();

    private ObserverManager() {
    }

    /**
     * Registers a supplier which might provide thread-per-task thread pools.
     *
     * @param supplier the supplier of {@code ExecutorService} instances
     * @param supplierName name of the supplier
     * @param useVirtualThreads whether virtual threads should be used
     */
    static void registerSupplier(Supplier<? extends ExecutorService> supplier,
                                String supplierName,
                                boolean useVirtualThreads) {
        SUPPLIER_CONTEXTS.computeIfAbsent(supplier, key ->
                OBSERVER_LIST.get()
                        .stream()
                        .map(observer -> registerSupplier(observer, supplier, supplierName, useVirtualThreads))
                        .collect(Collectors.toList()));
    }

    /**
     * Registers a supplier which will never use thread-per-task thread pools.
     *
     * @param supplier the supplier of {@code ExecutorService} instances
     * @param category category of thread pools which the supplier provides (e.g., server, scheduled)
     * @param supplierName name of the supplier
     */
    static void registerSupplier(Supplier<? extends ExecutorService> supplier,
                                 String category,
                                 String supplierName) {
        SUPPLIER_CONTEXTS.computeIfAbsent(supplier, key ->
                OBSERVER_LIST.get()
                        .stream()
                        .map(observer -> observer.registerSupplier(key, category, supplierName))
                        .collect(Collectors.toList()));
    }

    private static ExecutorServiceSupplierObserver.SupplierObserverContext registerSupplier(
            ExecutorServiceSupplierObserver observer,
            Supplier<? extends ExecutorService> supplier,
            String supplierName,
            boolean useVirtualThreads) {
        return useVirtualThreads
                ? observer.registerSupplier(supplier,
                                            "ad-hoc",
                                            supplierName,
                                            VirtualExecutorUtil.METRICS_RELATED_METHOD_INVOCATIONS)
                : observer.registerSupplier(supplier,
                                            "ad-hoc",
                                            supplierName);
    }

    static <E extends ExecutorService> E registerExecutorService(Supplier<E> supplier, E executorService) {
        SUPPLIER_CONTEXTS.get(supplier)
                .forEach(observer -> observer.registerExecutorService(executorService));
        return executorService;
    }

    private static List<ExecutorServiceSupplierObserver> loadObservers() {
        ServiceLoader<ExecutorServiceSupplierObserver> loader = ServiceLoader.load(ExecutorServiceSupplierObserver.class);
        return loader.stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toList());
    }
}
