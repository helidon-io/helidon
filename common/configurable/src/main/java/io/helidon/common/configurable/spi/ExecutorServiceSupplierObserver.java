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
package io.helidon.common.configurable.spi;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Behavior for observers of the various executor service suppliers.
 * <p>
 *     This component identifies suppliers to observers using:
 *     <ul>
 *         <li>the supplier itself,</li>
 *         <li>the supplier category (scheduled, server, ad-hoc), and</li>
 *         <li>the index of this supplier among suppliers in the same category.</li>
 *     </ul>
 *     Further, executor services furnished by the suppliers are identified to observers using:
 *     <ul>
 *         <li>the executor service itself, and</li>
 *         <li>the index of the executor service among those from the same supplier.</li>
 *     </ul>
 *     The consuming observers can use this identifying information however makes sense for them.
 *
 */
public interface ExecutorServiceSupplierObserver {

    /**
     * Makes a supplier known to the observer and returns a supplier context for the supplier to use for future interactions
     * with the observer.
     *
     * @param supplier the executor service supplier registering with the observer
     * @param supplierIndex unique index across suppliers with the same name
     * @param supplierCategory supplier category for this supplier
     *
     * @return the {@code SupplierObserverContext} for the supplier
     */
    SupplierObserverContext registerSupplier(Supplier<? extends ExecutorService> supplier,
                                             int supplierIndex,
                                             String supplierCategory);

    /**
     * Makes a supplier known to the observer and returns a supplier context for the supplier to use for future interactions
     * with the observer, using the {@link io.helidon.common.configurable.spi.ExecutorServiceSupplierObserver.MethodInvocation}
     * abstraction for invoking methods to obtain metric values.
     *
     * @param supplier the executor service supplier registering with the observer
     * @param supplierIndex unique index across suppliers with the same name
     * @param supplierCategory the category of supplier registering (e.g., scheduled, server, thread-pool)
     * @param methodInvocations method invocation information for retrieving interesting information from the supplier's
     *                          executor services
     *
     * @return the {@code SupplierObserverContext} for the supplier
     */
    SupplierObserverContext registerSupplier(Supplier<? extends ExecutorService> supplier,
                                                     int supplierIndex,
                                                     String supplierCategory,
                                                     List<MethodInvocation> methodInvocations);

    /**
     * Context with which suppliers (or their surrogates) interact with observers.
     */
    interface SupplierObserverContext {

        /**
         * Informs the observer which created the context of a new executor service created by the supplier.
         *
         * @param executorService the new executor service
         * @param index unique index value for the executor service within its supplier
         */
        void registerExecutorService(ExecutorService executorService, int index);

        /**
         * Informs the observer that an executor is shutting down.
         *
         * @param executorService the executor service shutting down
         */
        void unregisterExecutorService(ExecutorService executorService);
    }

    /**
     * Information about method invocations to retrieve interesting (e.g., metrics) values from an executor service.
     * <p>
     *     Used for dealing with {@code ThreadPerTaskExecutor} executor services which might not exist in every JDK we support.
     * </p>
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
