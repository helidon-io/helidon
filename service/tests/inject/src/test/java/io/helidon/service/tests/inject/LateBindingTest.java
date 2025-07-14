/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.service.tests.inject;

import java.util.List;

import io.helidon.common.Weighted;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryException;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LateBindingTest {
    @Test
    void testLateBindingFailsPostUseContract() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        ServiceRegistry registry = manager.registry();
        GlobalServiceRegistry.registry(registry);
        try {
            List<LateBindingTypes.Contract> contracts = registry.all(LateBindingTypes.Contract.class);
            assertThat(contracts, hasSize(1));
            assertThat(contracts.getFirst().message(), is("injected"));

            assertThrows(ServiceRegistryException.class,
                         () -> Services.add(LateBindingTypes.Contract.class,
                                            Weighted.DEFAULT_WEIGHT - 10,
                                            new LateBindingTypes.ServiceProvider("custom")));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void testLateBindingFailsPostUseService() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        ServiceRegistry registry = manager.registry();
        GlobalServiceRegistry.registry(registry);
        try {
            List<LateBindingTypes.ServiceProvider> contracts = registry.all(LateBindingTypes.ServiceProvider.class);
            assertThat(contracts, hasSize(1));
            assertThat(contracts.getFirst().message(), is("injected"));

            assertThrows(ServiceRegistryException.class,
                         () -> Services.set(LateBindingTypes.Contract.class,
                                            new LateBindingTypes.ServiceProvider("custom")));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void testLateBindingContractSingleSet() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        ServiceRegistry registry = manager.registry();
        GlobalServiceRegistry.registry(registry);
        try {
            Services.set(LateBindingTypes.Contract.class, new LateBindingTypes.ServiceProvider("custom"));

            List<LateBindingTypes.Contract> contracts = registry.all(LateBindingTypes.Contract.class);
            assertThat(contracts, hasSize(1));
            assertThat(contracts.getFirst().message(), is("custom"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void testLateBindingContractMultiSet() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        ServiceRegistry registry = manager.registry();
        GlobalServiceRegistry.registry(registry);
        try {
            Services.set(LateBindingTypes.Contract.class,
                         new LateBindingTypes.ServiceProvider("custom1"),
                         new LateBindingTypes.ServiceProvider("custom2"));

            List<LateBindingTypes.Contract> contracts = registry.all(LateBindingTypes.Contract.class);
            assertThat(contracts, hasSize(2));
            assertThat(contracts.get(0).message(), is("custom1"));
            assertThat(contracts.get(1).message(), is("custom2"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void testLateBindingServiceSingleSet() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        ServiceRegistry registry = manager.registry();
        GlobalServiceRegistry.registry(registry);
        try {
            Services.set(LateBindingTypes.ServiceProvider.class, new LateBindingTypes.ServiceProvider("custom"));

            List<LateBindingTypes.Contract> contracts = registry.all(LateBindingTypes.Contract.class);
            assertThat(contracts, hasSize(1));
            assertThat(contracts.getFirst().message(), is("custom"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void testLateBindingServiceMultiSet() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        ServiceRegistry registry = manager.registry();
        GlobalServiceRegistry.registry(registry);
        try {
            ServiceRegistryException e = assertThrows(ServiceRegistryException.class,
                                                      () -> Services.set(LateBindingTypes.ServiceProvider.class,
                                                                         new LateBindingTypes.ServiceProvider("custom1"),
                                                                         new LateBindingTypes.ServiceProvider("custom2")));
            assertThat(e.getMessage(), containsString("exactly one instance"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void testLateBindingContractSingleAdd() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        ServiceRegistry registry = manager.registry();
        GlobalServiceRegistry.registry(registry);
        try {
            Services.add(LateBindingTypes.Contract.class,
                         Weighted.DEFAULT_WEIGHT - 10,
                         new LateBindingTypes.ServiceProvider("custom"));

            List<LateBindingTypes.Contract> contracts = registry.all(LateBindingTypes.Contract.class);
            assertThat(contracts, hasSize(2));
            assertThat(contracts.get(0).message(), is("injected"));
            assertThat(contracts.get(1).message(), is("custom"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void testLateBindingContractMultiAdd() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        ServiceRegistry registry = manager.registry();
        GlobalServiceRegistry.registry(registry);
        try {
            Services.add(LateBindingTypes.Contract.class,
                         Weighted.DEFAULT_WEIGHT + 10,
                         new LateBindingTypes.ServiceProvider("custom1"));
            Services.add(LateBindingTypes.Contract.class,
                         Weighted.DEFAULT_WEIGHT - 10,
                         new LateBindingTypes.ServiceProvider("custom2"));

            List<LateBindingTypes.Contract> contracts = registry.all(LateBindingTypes.Contract.class);
            assertThat(contracts, hasSize(3));
            assertThat(contracts.get(0).message(), is("custom1"));
            assertThat(contracts.get(1).message(), is("injected"));
            assertThat(contracts.get(2).message(), is("custom2"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void testLateBindingServiceSingleAdd() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        GlobalServiceRegistry.registry(manager.registry());
        try {
            ServiceRegistryException e = assertThrows(ServiceRegistryException.class,
                                                      () -> Services.add(LateBindingTypes.ServiceProvider.class,
                                                                         Weighted.DEFAULT_WEIGHT,
                                                                         new LateBindingTypes.ServiceProvider("custom")));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void testLateBindingContractNamed() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        ServiceRegistry registry = manager.registry();
        GlobalServiceRegistry.registry(registry);
        try {
            Services.setNamed(LateBindingTypes.Contract.class, new LateBindingTypes.ServiceProvider("named"), "named");

            LateBindingTypes.Contract contract = registry.getNamed(LateBindingTypes.Contract.class, "named");
            assertThat(contract.message(), is("named"));
            contract = Services.getNamed(LateBindingTypes.Contract.class, "named");
            assertThat(contract.message(), is("named"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void testLateBindingContractQualified() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        ServiceRegistry registry = manager.registry();
        GlobalServiceRegistry.registry(registry);
        try {
            Services.setQualified(LateBindingTypes.Contract.class, new LateBindingTypes.ServiceProvider("named"),
                                  Qualifier.createNamed("named"));

            LateBindingTypes.Contract contract = registry.getNamed(LateBindingTypes.Contract.class, "named");
            assertThat(contract.message(), is("named"));
        } finally {
            manager.shutdown();
        }
    }
}
