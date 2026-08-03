/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.service.test.registry;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;

@Service.Singleton
class SingletonServiceSupplier implements Supplier<SuppliedContract> {
    private final AtomicInteger counter = new AtomicInteger();
    private final ServiceRegistry registry;

    private boolean reenter;
    private Optional<SuppliedContract> activeDuringGet = Optional.empty();

    @Service.Inject
    SingletonServiceSupplier() {
        registry = null;
    }

    SingletonServiceSupplier(ServiceRegistry registry) {
        this.registry = registry;
    }

    int instances() {
        return counter.get();
    }

    void reenterOnGet() {
        reenter = true;
    }

    Optional<SuppliedContract> activeDuringGet() {
        return activeDuringGet;
    }

    @Override
    public SuppliedContract get() {
        int i = counter.incrementAndGet();
        if (reenter) {
            activeDuringGet = registry.firstActive(SuppliedContract.class);
        }
        return () -> "Supplied:" + i;
    }
}
