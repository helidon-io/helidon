/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.function.Supplier;

import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

public class RegistryTest {
    private static ServiceRegistryManager registryManager;
    private static ServiceRegistry registry;

    @BeforeAll
    public static void init() {
        registryManager = ServiceRegistryManager.create();
        registry = registryManager.registry();
    }

    @AfterAll
    public static void shutdown() {
        if (registryManager != null) {
            registryManager.shutdown();
        }
        registryManager = null;
        registry = null;
    }

    @Test
    public void testRegistryGet() {
        MyContract myContract = registry.get(MyContract.class);
        // higher weight
        assertThat(myContract, instanceOf(MyService2.class));

        assertThat(MyService2.instances, is(1));
        assertThat(MyService.instances, is(1));
    }

    @Test
    public void testRegistryFirst() {
        MyContract myContract = registry.first(MyContract.class).get();
        // higher weight
        assertThat(myContract, instanceOf(MyService2.class));
        assertThat(MyService2.instances, is(1));
        assertThat(MyService.instances, is(1));
    }

    @Test
    public void testRegistryFirstActive() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        ServiceRegistry serviceRegistry = manager.registry();
        FirstActiveService.instances = 0;

        try {
            assertThat(serviceRegistry.firstActive(FirstActiveContract.class).isEmpty(), is(true));
            assertThat(FirstActiveService.instances, is(0));

            FirstActiveContract activated = serviceRegistry.get(FirstActiveContract.class);
            assertThat(activated, notNullValue());
            assertThat(FirstActiveService.instances, is(1));

            assertThat(serviceRegistry.firstActive(FirstActiveContract.class).orElseThrow(), sameInstance(activated));
            assertThat(FirstActiveService.instances, is(1));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testRegistryFirstActiveDoesNotBlockSet() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        ServiceRegistry serviceRegistry = manager.registry();
        FirstActiveService.instances = 0;
        FirstActiveContract explicit = new FirstActiveContract() {
        };

        Services.registry(serviceRegistry);
        try {
            assertThat(serviceRegistry.firstActive(FirstActiveContract.class).isEmpty(), is(true));
            assertThat(FirstActiveService.instances, is(0));

            Services.set(FirstActiveContract.class, explicit);

            assertThat(serviceRegistry.get(FirstActiveContract.class), sameInstance(explicit));
            assertThat(FirstActiveService.instances, is(0));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testRegistryFirstActiveDoesNotBlockSetByServiceType() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        ServiceRegistry serviceRegistry = manager.registry();
        FirstActiveService.instances = 0;

        Services.registry(serviceRegistry);
        try {
            assertThat(serviceRegistry.firstActive(FirstActiveService.class).isEmpty(), is(true));
            assertThat(FirstActiveService.instances, is(0));

            FirstActiveService explicit = new FirstActiveService();
            Services.set(FirstActiveService.class, explicit);

            assertThat(serviceRegistry.firstActive(FirstActiveService.class).orElseThrow(), sameInstance(explicit));
            assertThat(serviceRegistry.get(FirstActiveService.class), sameInstance(explicit));
            assertThat(FirstActiveService.instances, is(1));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testRegistryFirstActiveReturnsSetInstance() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        ServiceRegistry serviceRegistry = manager.registry();
        FirstActiveService.instances = 0;
        FirstActiveContract explicit = new FirstActiveContract() {
        };

        Services.registry(serviceRegistry);
        try {
            Services.set(FirstActiveContract.class, explicit);

            assertThat(serviceRegistry.firstActive(FirstActiveContract.class).orElseThrow(), sameInstance(explicit));
            assertThat(FirstActiveService.instances, is(0));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testRegistryFirstActiveReturnsQualifiedSetInstance() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        ServiceRegistry serviceRegistry = manager.registry();
        FirstActiveContract explicit = new FirstActiveContract() {
        };
        Qualifier qualifier = Qualifier.createNamed("explicit");
        Qualifier otherQualifier = Qualifier.createNamed("other");

        Services.registry(serviceRegistry);
        try {
            Services.setQualified(FirstActiveContract.class, explicit, qualifier);

            assertThat(serviceRegistry.firstActive(FirstActiveContract.class, otherQualifier).isEmpty(), is(true));
            assertThat(serviceRegistry.firstActive(FirstActiveContract.class, qualifier).orElseThrow(),
                       sameInstance(explicit));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testRegistryFirstActiveDoesNotReturnDestroyedSetInstance() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        ServiceRegistry serviceRegistry = manager.registry();
        FirstActiveContract explicit = new FirstActiveContract() {
        };
        boolean shutDown = false;

        Services.registry(serviceRegistry);
        try {
            Services.set(FirstActiveContract.class, explicit);

            assertThat(serviceRegistry.firstActive(FirstActiveContract.class).orElseThrow(), sameInstance(explicit));

            manager.shutdown();
            shutDown = true;

            assertThat(serviceRegistry.firstActive(FirstActiveContract.class).isEmpty(), is(true));
        } finally {
            if (!shutDown) {
                manager.shutdown();
            }
        }
    }

    @Test
    public void testRegistryFirstActiveDoesNotCreatePerLookupInstance() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        ServiceRegistry serviceRegistry = manager.registry();
        ServiceSupplier.reset();

        try {
            assertThat(serviceRegistry.firstActive(SuppliedContract.class).isEmpty(), is(true));
            assertThat(ServiceSupplier.instances(), is(0));

            Supplier<SuppliedContract> supplier = serviceRegistry.supply(SuppliedContract.class);
            SuppliedContract first = supplier.get();
            assertThat(first.message(), is("Supplied:1"));

            assertThat(serviceRegistry.firstActive(SuppliedContract.class).isEmpty(), is(true));
            assertThat(ServiceSupplier.instances(), is(1));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testRegistryAll() {
        List<MyContract> myContracts = registry.all(MyContract.class);
        assertThat(myContracts, hasSize(2));
        // higher weight
        assertThat(myContracts.get(0), instanceOf(MyService2.class));
        assertThat(myContracts.get(1), instanceOf(MyService.class));

        assertThat(MyService2.instances, is(1));
        assertThat(MyService.instances, is(1));
    }

    @Test
    public void testSupplier() {
        ServiceSupplier.reset();
        Supplier<SuppliedContract> supplier = registry.supply(SuppliedContract.class);

        SuppliedContract first = supplier.get();
        // sanity check we use the counter per instance, not per call
        assertThat(first.message(), is("Supplied:1"));
        assertThat(first.message(), is("Supplied:1"));

        // second instance should be a new one
        SuppliedContract second = supplier.get();
        assertThat(second, not(sameInstance(first)));
        assertThat(second.message(), is("Supplied:2"));
    }

    @Test
    public void testSupplierWithNonSupplierService() {
        Supplier<MyContract> myContractSupplier = registry.supply(MyContract.class);

        // higher weight
        MyContract firstInstance = myContractSupplier.get();
        assertThat(firstInstance, instanceOf(MyService2.class));

        MyContract secondInstance = myContractSupplier.get();
        assertThat(secondInstance, sameInstance(firstInstance));
    }
}
