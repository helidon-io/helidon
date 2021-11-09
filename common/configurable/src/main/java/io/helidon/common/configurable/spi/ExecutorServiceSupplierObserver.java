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
package io.helidon.common.configurable.spi;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Behavior for observers of the various executor service suppliers.
 */
public interface ExecutorServiceSupplierObserver {

    /**
     * Makes a supplier known to the observer and returns a supplier context for the supplier to use for future interactions
     * with the observer.
     *
     * @param supplier the executor service supplier registering with the observer
     * @param category the category of supplier registering (e.g., scheduled, server, thread-pool)
     * @param supplierName name for this supplier (possibly the prefix to use for names of thread pools from this supplier)
     *
     * @return the {@code SupplierObserverContext} for the supplier
     */
    <E extends ExecutorService> SupplierObserverContext registerSupplier(Supplier<E> supplier,
                                                                         String category,
                                                                         String supplierName);

    /**
     * Makes a supplier known to the observer and returns a supplier context for the supplier to use for future interactions
     * with the observer.
     *
     * @param supplier the executor service supplier registering with the observer
     * @param category the category of supplier registering (e.g., scheduled, server, thread-pool)
     * @param supplierName name for this supplier (possibly the prefix to use for names of thread pools from this supplier)
     * @param methodInvocations method invocation information for retrieving interesting information from the supplier's
     *                          executor services
     *
     * @return the {@code SupplierObserverContext} for the supplier
     */
    default <E extends ExecutorService> SupplierObserverContext registerSupplier(Supplier<E> supplier,
                                                                                 String category,
                                                                                 String supplierName,
                                                                                 List<MethodInvocation> methodInvocations) {
        return null;
    }

    /**
     * Context with which suppliers (or their surrogates) interact with observers.
     */
    interface SupplierObserverContext {

        /**
         * Informs the observer which created the context of a new executor service created by the supplier.
         *
         * @param executorService the new executor service
         */
        void registerExecutorService(ExecutorService executorService);
    }

    /**
     * Information about method invocations to retrieve interesting (e.g., metrics) values from an executor service.
     */
    interface MethodInvocation {

        /**
         * Returns a displayable name for the value.
         *
         * @return display name for the value
         */
        String displayName();

        /**
         * Returns a brief description of the interesting value.
         *
         * @return description
         */
        String description();

        /**
         * Returns the method to invoke to retrieve the value.
         *
         * @return {@code Method} which returns the value.
         */
        Method method();

        /**
         * Returns the data type of the interesting value.
         *
         * @return the type
         */
        Class<?> type();
    }
}
