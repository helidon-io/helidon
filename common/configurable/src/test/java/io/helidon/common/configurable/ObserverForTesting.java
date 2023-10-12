/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Supplier;

import io.helidon.common.configurable.spi.ExecutorServiceSupplierObserver;

public class ObserverForTesting implements ExecutorServiceSupplierObserver {

    static ObserverForTesting instance;

    private final Map<Supplier<? extends ExecutorService>, SupplierInfo> suppliers = new HashMap<>();

    public ObserverForTesting() {
        instance = this;
    }

    static void clear() {
        if (instance != null) {
            instance.suppliers.clear();
        }
    }

    @Override
    public SupplierObserverContext registerSupplier(Supplier<? extends ExecutorService> supplier, int supplierIndex, String supplierCategory) {
        SupplierInfo supplierInfo = new SupplierInfo(supplierCategory);
        suppliers.put(supplier, supplierInfo);
        return supplierInfo.context;
    }

    Map<Supplier<? extends ExecutorService>, SupplierInfo> suppliers() {
        return suppliers;
    }

    static class Context implements SupplierObserverContext {

        private final List<ExecutorService> executorServices = new ArrayList<>();

        private int scheduledCount;
        private int threadPoolExecutorCount;

        @Override
        public void registerExecutorService(ExecutorService executorService, int index) {
            executorServices.add(index, executorService);
            if (executorService instanceof ScheduledThreadPoolExecutor) {
                scheduledCount++;
            } else if (executorService instanceof ThreadPoolExecutor) {
                threadPoolExecutorCount++;
            }
        }

        @Override
        public void unregisterExecutorService(ExecutorService executorService) {
            executorServices.remove(executorService);
            if (executorService instanceof ScheduledThreadPoolExecutor) {
                scheduledCount--;
            } else if (executorService instanceof ThreadPoolExecutor) {
                threadPoolExecutorCount--;
            }
        }

        List<ExecutorService> executorServices() {
            return executorServices;
        }

        int scheduledCount() {
            return scheduledCount;
        }

        int threadPoolCount() {
            return threadPoolExecutorCount;
        }
    }

    static class SupplierInfo {

        private final String supplierCategory;
        private final Context context = new Context();

        private SupplierInfo(String supplierCategory) {
            this.supplierCategory = supplierCategory;
        }

        String supplierCategory() {
            return supplierCategory;
        }

        Context context() {
            return context;
        }
    }
}
