/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
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
 *         {@code #registerSupplier} methods,</li>
 *         <li>notify this class whenever it creates a new executor service by invoking
 *         {@link #registerExecutorService(java.util.function.Supplier, java.util.concurrent.ExecutorService)}, and</li>
 *         <li>notify this class whenever it shuts down an executor service by invoking
 *         {@link #unregisterExecutorService(java.util.concurrent.ExecutorService)}.</li>
 *     </ul>
 * </p>
 */
class ObserverManager {

    private static final Logger LOGGER = Logger.getLogger(ObserverManager.class.getName());

    private static final LazyValue<List<ExecutorServiceSupplierObserver>> OBSERVERS = LazyValue
            .create(ObserverManager::loadObservers);

    private static final Map<Supplier<? extends ExecutorService>, SupplierInfo> SUPPLIERS = new ConcurrentHashMap<>();

    // A given supplier category can have multiple suppliers, so keep track of the next available index by category.
    private static final Map<String, AtomicInteger> SUPPLIER_CATEGORY_NEXT_INDEX_VALUES = new ConcurrentHashMap<>();

    private static final Map<ExecutorService, SupplierInfo> EXECUTOR_SERVICES = new ConcurrentHashMap<>();

    // Defer building list until use to avoid loading problems if this JDK does not support ThreadPerTaskExecutor.
    private static final LazyValue<List<ExecutorServiceSupplierObserver.MethodInvocation>> METRICS_RELATED_METHOD_INVOCATIONS =
            LazyValue.create(() -> List.of(
                    MethodInvocationImpl.create("Thread count", "thread-count", "threadCount")));

    private ObserverManager() {
    }

    /**
     * Registers a supplier which might provide thread-per-task thread pools.
     *
     * @param supplier the supplier of {@code ExecutorService} instances
     * @param supplierCategory category of the supplier (e.g., scheduled, server)
     * @param executorServiceCategory category of executor services the supplier creates (e.g., ad-hoc)
     * @param useVirtualThreads whether virtual threads should be used
     */
    static void registerSupplier(Supplier<? extends ExecutorService> supplier,
                                 String supplierCategory,
                                 String executorServiceCategory,
                                 boolean useVirtualThreads) {
        int supplierIndex = SUPPLIER_CATEGORY_NEXT_INDEX_VALUES.computeIfAbsent(supplierCategory, key -> new AtomicInteger())
                .getAndIncrement();
        SUPPLIERS.computeIfAbsent(supplier,
                                  s -> SupplierInfo.create(s,
                                                           executorServiceCategory,
                                                           supplierCategory,
                                                           supplierIndex,
                                                           useVirtualThreads));
    }

    /**
     * Registers a supplier which will never use thread-per-task thread pools.
     *
     * @param supplier the supplier of {@code ExecutorService} instances
     * @param supplierCategory category of the supplier (e.g., server, scheduled)
     * @param executorServiceCategory category of thread pools which the supplier provides
     */
    static void registerSupplier(Supplier<? extends ExecutorService> supplier,
                                 String supplierCategory,
                                 String executorServiceCategory) {
        registerSupplier(supplier, supplierCategory, executorServiceCategory, false);
    }

    /**
     * Registers an executor service from a supplier.
     *
     * @param supplier the supplier registering the executor service
     * @param executorService the executor service being registered
     * @param <E> type of the executor service being registered
     * @return the same executor service being registered
     * @throws IllegalStateException if the supplier has not previously registered itself
     */
    static <E extends ExecutorService> E registerExecutorService(Supplier<E> supplier, E executorService) {
        SupplierInfo supplierInfo = SUPPLIERS.get(supplier);
        if (supplierInfo == null) {
            throw new IllegalStateException("Attempt to register an executor service to an unregistered supplier");
        }
        supplierInfo.registerExecutorService(executorService);
        return executorService;
    }

    /**
     * Unregisters a previously-registered executor service that is being shut down.
     * <p>
     *     During production, the executor service would have been previously registered by a supplier. But during testing that
     *     is not always the case.
     * </p>
     * @param executorService the executor service being shut down
     */
    static void unregisterExecutorService(ExecutorService executorService) {
        SupplierInfo supplierInfo = EXECUTOR_SERVICES.get(executorService);
        if (supplierInfo == null) {
            // This can happen in some unit tests but should not happen in production.
            LOGGER.log(Level.WARNING, String.format(
                    "Executor service %s is being unregistered but could not locate supplier to notify observers",
                    executorService));
            return;
        }
        supplierInfo.unregisterExecutorService(executorService);
    }

    private static List<ExecutorServiceSupplierObserver> loadObservers() {
        ServiceLoader<ExecutorServiceSupplierObserver> loader = ServiceLoader.load(ExecutorServiceSupplierObserver.class);
        return loader.stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toList());
    }

    private static class SupplierInfo {
        private final Supplier<? extends ExecutorService> supplier;
        private final String executorServiceCategory;
        private final String supplierCategory;
        private final int supplierIndex;
        private final boolean useVirtualThreads;
        private final AtomicInteger nextThreadPoolIndex = new AtomicInteger(0);
        private final List<ExecutorServiceSupplierObserver.SupplierObserverContext> observerContexts;

        private static SupplierInfo create(Supplier<? extends ExecutorService> supplier,
                                           String executorServiceCategory,
                                           String supplierCategory,
                                           int supplierIndex,
                                           boolean useVirtualThreads) {
            return new SupplierInfo(supplier, supplierCategory, executorServiceCategory, supplierIndex, useVirtualThreads);
        }

        private SupplierInfo(Supplier<? extends ExecutorService> supplier,
                             String supplierCategory,
                             String executorServiceCategory,
                             int supplierIndex,
                             boolean useVirtualThreads) {
            this.supplier = supplier;
            this.supplierCategory = supplierCategory;
            this.executorServiceCategory = executorServiceCategory;
            this.supplierIndex = supplierIndex;
            this.useVirtualThreads = useVirtualThreads;
            observerContexts = collectObserverContexts();
        }

        private List<ExecutorServiceSupplierObserver.SupplierObserverContext> collectObserverContexts() {
            return OBSERVERS.get()
                    .stream()
                    .map(observer ->
                                 useVirtualThreads
                                         ? observer.registerSupplier(supplier,
                                                                     supplierIndex,
                                                                     supplierCategory,
                                                                     METRICS_RELATED_METHOD_INVOCATIONS.get())
                                         : observer.registerSupplier(supplier,
                                                                     supplierIndex,
                                                                     supplierCategory))

                    .collect(Collectors.toList());
        }

        void registerExecutorService(ExecutorService executorService) {
            int threadPoolIndex = nextThreadPoolIndex.getAndIncrement();
            EXECUTOR_SERVICES.put(executorService, this);
            observerContexts
                    .forEach(observer -> observer.registerExecutorService(executorService, threadPoolIndex));
        }

        void unregisterExecutorService(ExecutorService executorService) {
            observerContexts
                    .forEach(observer -> observer.unregisterExecutorService(executorService));
            EXECUTOR_SERVICES.remove(executorService);
        }
    }

    /**
     * Encapsulation of information needed to invoke methods on {@code ThreadPerTaskExecutor} and to create metrics from the
     * returned values.
     */
    private static class MethodInvocationImpl implements ExecutorServiceSupplierObserver.MethodInvocation {
        private final String displayName;
        private final String description;
        private final Method method;
        private final Class<?> type;

        private static final LazyValue<ExecutorService> VIRTUAL_EXECUTOR_SERVICE = LazyValue
                .create(VirtualExecutorUtil::executorService);

        static MethodInvocationImpl create(String displayName, String description, String methodName)  {
            ExecutorService executorService = VIRTUAL_EXECUTOR_SERVICE.get();
            Method method = null;
            try {
                method = executorService.getClass().getDeclaredMethod(methodName);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
            return new MethodInvocationImpl(displayName, description, method);
        }

        MethodInvocationImpl(String displayName, String description, Method method) {
            this.displayName = displayName;
            this.description = description;
            this.method = method;
            this.type = method.getReturnType();
        }

        @Override
        public String displayName() {
            return displayName;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public Method method() {
            return method;
        }

        @Override
        public Class<?> type() {
            return type;
        }
    }
}
