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

import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;

@Service.Singleton
class SingletonServiceSupplier implements Supplier<SuppliedContract> {
    private final AtomicInteger counter = new AtomicInteger();
    private final ServiceRegistry registry;

    private boolean reenter;
    private boolean fail;
    private boolean resolvingDuringGet;
    private boolean otherRegistryResolvingDuringGet;
    private Optional<SuppliedContract> activeDuringGet = Optional.empty();
    private ServiceRegistry otherRegistry;

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

    void failOnGet() {
        fail = true;
    }

    void checkOtherRegistry(ServiceRegistry otherRegistry) {
        this.otherRegistry = otherRegistry;
    }

    Optional<SuppliedContract> activeDuringGet() {
        return activeDuringGet;
    }

    boolean resolvingDuringGet() {
        return resolvingDuringGet;
    }

    boolean otherRegistryResolvingDuringGet() {
        return otherRegistryResolvingDuringGet;
    }

    @Override
    public SuppliedContract get() {
        int i = counter.incrementAndGet();
        ServiceRegistry currentRegistry = registry == null ? GlobalServiceRegistry.registry() : registry;
        resolvingDuringGet = currentRegistry.isResolvingOnCurrentThread(SuppliedContract.class);
        if (otherRegistry != null) {
            otherRegistryResolvingDuringGet = otherRegistry.isResolvingOnCurrentThread(SuppliedContract.class);
        }
        if (reenter) {
            activeDuringGet = currentRegistry.firstActive(SuppliedContract.class);
        }
        if (fail) {
            throw new IllegalStateException("Supplier failure");
        }
        return () -> "Supplied:" + i;
    }
}
