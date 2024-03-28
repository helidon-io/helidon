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

package io.helidon.service.tests.inject.configdriven;

import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.config.ConfigException;
import io.helidon.common.config.GlobalConfig;
import io.helidon.config.Config;
import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.Qualifier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConfigDrivenNoConfigTest {
    private static InjectRegistryManager registryManager;
    private static InjectRegistry registry;

    @BeforeAll
    static void init() {
        GlobalConfig.config(Config::empty, true);
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
        // zero or one configured instance
        var services = registry.all(AService.class);
        // there is no configured bean
        assertThat(services, empty());
    }

    @Test
    public void testBService() {
        // one instance (either configured, or default)
        var services = registry.all(BService.class);
        // one or two instance (default and configured, if present and not default name)
        assertThat(services, hasSize(1));

        var instance = services.getFirst();
        assertThat(instance.name(), is("@default"));
        assertThat(instance.value(), is("defaultValue"));
    }

    @Test
    public void testCService() {
        // two instances (default and configured, if not default name)
        ConfigException configException = assertThrows(ConfigException.class, () -> registry.all(CService.class));
        assertThat(configException.getMessage(), containsString("\"config-c\""));
    }

    @Test
    public void testDService() {
        // default instance + zero or more configured instances
        var services = registry.all(DService.class);
        // there is no configured bean, with default, we should get a default instance
        assertThat(services, hasSize(1));

        var instance = services.getFirst();
        assertThat(instance.name(), is("@default"));
        assertThat(instance.value(), is("defaultValue"));
    }

    @Test
    public void testEService() {
        // default instance + at least one configured instance
        // no configuration, fail
        ConfigException configException = assertThrows(ConfigException.class, () -> registry.all(EService.class));
        assertThat(configException.getMessage(), containsString("\"config-e\""));
    }

    @Test
    public void testFService() {
        // fails without config, single instance if configured
        // no configuration, fail
        ConfigException configException = assertThrows(ConfigException.class, () -> registry.all(FService.class));
        assertThat(configException.getMessage(), containsString("\"config-f\""));
    }

    @Test
    public void testGService() {
        // at least one configured instance
        // no configuration, fail
        ConfigException configException = assertThrows(ConfigException.class, () -> registry.all(GService.class));
        assertThat(configException.getMessage(), containsString("\"config-g\""));
    }

    @Test
    public void testHService() {
        // zero or more configured instances
        var services = registry.all(HService.class);
        // there is no configured bean
        assertThat(services, empty());
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
