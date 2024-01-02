/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.inject;

import java.util.function.Supplier;

import io.helidon.inject.service.ServiceProvider;

final class ServiceProviderFactory {
    private ServiceProviderFactory() {
    }

    static <T> ServiceProvider<T> create(Supplier<T> supplier) {
        return new SupplierProvider<>(supplier);
    }

    private static class SupplierProvider<T> implements ServiceProvider<T> {
        private final Supplier<T> supplier;

        private SupplierProvider(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public T get() {
            return supplier.get();
        }
    }
}
