package io.helidon.service.tests.inject.configdriven;

import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.Qualifier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

public class ConfigDrivenDefaultsTest {
    private static InjectRegistryManager registryManager;
    private static InjectRegistry registry;

    @BeforeAll
    static void init() {
        registryManager = InjectRegistryManager.create();
        registry = registryManager.registry();
    }

    @AfterAll
    static void shutdown() {
        if (registryManager != null) {
            IService iService = registry.get(IService.class);
            assertThat(iService.isRunning(), is(true));
            registryManager.shutdown();
            assertThat(iService.isRunning(), is(false));
        }
    }

    @Test
    public void testAService() {
        Optional<AService> maybeService = registry.first(AService.class);
        // there is no configured bean, no default, we should not get an instance
        assertThat(maybeService, optionalEmpty());
    }

    @Test
    public void testBService() {
        Optional<BService> maybeService = registry.first(BService.class);
        // there is no configured bean, with default, we should get a default instance
        assertThat(maybeService, optionalPresent());

        BService instance= maybeService.get();

    }

    @Test
    public void testIService() {
        Supplier<IService> iService = registry.supply(IService.class);
        assertThat(iService.get().isRunning(), is(true));

        IService jane = registry.get(lookup(IService.class, "jane"));
        assertThat(jane, sameInstance(iService.get()));

        IService defaultName = registry.get(lookup(IService.class, "@default"));
        assertThat(defaultName, sameInstance(iService.get()));

        Optional<IService> invalidName = registry.first(lookup(IService.class, "notthere"));
        assertThat(invalidName, optionalEmpty());
    }

    private static Lookup lookup(Class<?> contract, String name) {
        return Lookup.builder()
                .addContract(contract)
                .addQualifier(Qualifier.createNamed(name))
                .build();
    }
}
