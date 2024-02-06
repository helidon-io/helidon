package io.helidon.service.tests.inject;

import java.util.List;

import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.service.registry.ServiceRegistry;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

public class InjectRegistryTest {
    private static InjectRegistryManager registryManager;
    private static ServiceRegistry registry;

    @BeforeAll
    public static void init() {
        registryManager = InjectRegistryManager.create();
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
    public void testRegistryAll() {
        List<MyContract> myContracts = registry.all(MyContract.class);
        assertThat(myContracts, hasSize(3));
        // higher weight
        assertThat(myContracts.get(0), instanceOf(MyService2.class));
        assertThat(myContracts.get(1), instanceOf(MyService.class));
        assertThat(myContracts.get(2), instanceOf(MyService3.class));

        assertThat(MyService2.instances, is(1));
        assertThat(MyService.instances, is(1));
    }
}
