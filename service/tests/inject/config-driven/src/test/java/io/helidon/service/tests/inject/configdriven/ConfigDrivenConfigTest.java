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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.config.GlobalConfig;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.Qualifier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

public class ConfigDrivenConfigTest {
    private static InjectRegistryManager registryManager;
    private static InjectRegistry registry;

    @BeforeAll
    static void init() {
        Config config = Config.just(ConfigSources.create(configNodes()));
        GlobalConfig.config(() -> config, true);
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

        assertThat(services, hasSize(1));

        var instance = services.getFirst();
        assertThat(instance.name(), is("custom"));
        assertThat(instance.value(), is("value-a"));
    }

    @Test
    public void testBService() {
        // one instance (either configured, or default)
        var services = registry.all(BService.class);
        // one or two instance (default and configured, if present and not default name)
        assertThat(services, hasSize(2));

        var instance = services.get(0);
        assertThat(instance.name(), is("@default"));
        assertThat(instance.value(), is("defaultValue"));
        instance = services.get(1);
        assertThat(instance.name(), is("custom"));
        assertThat(instance.value(), is("value-b"));
    }

    @Test
    public void testCService() {
        // two instances (default and configured, if not default name)
        var services = registry.all(CService.class);
        // one or two instance (default and configured, if present and not default name)
        assertThat(services, hasSize(2));

        var instance = services.get(0);
        assertThat(instance.name(), is("@default"));
        assertThat(instance.value(), is("defaultValue"));
        instance = services.get(1);
        assertThat(instance.name(), is("custom"));
        assertThat(instance.value(), is("value-c"));
    }

    @Test
    public void testDService() {
        // default instance + zero or more configured instances
        var services = registry.all(DService.class);
        // default + 3 configured instances
        assertThat(services, hasSize(4));

        var instance = services.get(0);
        assertThat(instance.name(), is("@default"));
        assertThat(instance.value(), is("defaultValue"));

        instance = services.get(1);
        assertThat(instance.name(), is("custom_0"));
        assertThat(instance.value(), is("value-d_0"));

        instance = services.get(2);
        assertThat(instance.name(), is("custom_1"));
        assertThat(instance.value(), is("value-d_1"));

        instance = services.get(3);
        assertThat(instance.name(), is("custom_2"));
        assertThat(instance.value(), is("value-d_2"));
    }

    @Test
    public void testEService() {
        // default instance + at least one configured instance
        var services = registry.all(DService.class);
        // default + 3 configured instances
        assertThat(services, hasSize(4));

        var instance = services.get(0);
        assertThat(instance.name(), is("@default"));
        assertThat(instance.value(), is("defaultValue"));

        instance = services.get(1);
        assertThat(instance.name(), is("custom_0"));
        assertThat(instance.value(), is("value-d_0"));

        instance = services.get(2);
        assertThat(instance.name(), is("custom_1"));
        assertThat(instance.value(), is("value-d_1"));

        instance = services.get(3);
        assertThat(instance.name(), is("custom_2"));
        assertThat(instance.value(), is("value-d_2"));
    }

    @Test
    public void testFService() {
        // fails without config, single instance if configured
        var services = registry.all(FService.class);

        assertThat(services, hasSize(1));

        var instance = services.get(0);
        assertThat(instance.name(), is("custom"));
        assertThat(instance.value(), is("value-f"));
    }

    @Test
    public void testGService() {
        // at least one configured instance
        var services = registry.all(GService.class);
        // 3 configured instances
        assertThat(services, hasSize(3));

        var instance = services.get(0);
        assertThat(instance.name(), is("custom_0"));
        assertThat(instance.value(), is("value-g_0"));

        instance = services.get(1);
        assertThat(instance.name(), is("custom_1"));
        assertThat(instance.value(), is("value-g_1"));

        instance = services.get(2);
        assertThat(instance.name(), is("custom_2"));
        assertThat(instance.value(), is("value-g_2"));
    }

    @Test
    public void testHService() {
        // zero or more configured instances
        var services = registry.all(HService.class);
        // 3 configured instances
        assertThat(services, hasSize(3));

        var instance = services.get(0);
        assertThat(instance.name(), is("custom_0"));
        assertThat(instance.value(), is("value-h_0"));

        instance = services.get(1);
        assertThat(instance.name(), is("custom_1"));
        assertThat(instance.value(), is("value-h_1"));

        instance = services.get(2);
        assertThat(instance.name(), is("custom_2"));
        assertThat(instance.value(), is("value-h_2"));
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

    private static Map<String, String> configNodes() {
        Map<String, String> result = new HashMap<>();
        // A - zero or one
        addSingle(result, "a");
        // B - zero or one (use default if not defined)
        addSingle(result, "b");
        // C - one + default
        addSingle(result, "c");
        // D - repeatable + default
        addRepeatable(result, "d");
        // E - repeatable at least one + default
        addRepeatable(result, "e");
        // F - at least one
        addSingle(result, "f");
        // G - repeatable at least one
        addRepeatable(result, "g");
        // H - repeatable
        addRepeatable(result, "h");

        return result;
    }

    private static void addRepeatable(Map<String, String> result, String letter) {
        String prefix = "config-" + letter + ".";
        for (int i = 0; i < 3; i++) {
            String indexPrefix = prefix + i + ".";
            result.put(indexPrefix + "name", "custom_" + i);
            result.put(indexPrefix + "value", "value-" + letter + "_" + i);
        }
    }

    private static void addSingle(Map<String, String> result, String letter) {
        result.put("config-" + letter + ".name", "custom");
        result.put("config-" + letter + ".value", "value-" + letter);
    }
}
