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

import io.helidon.common.config.GlobalConfig;
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.service.inject.api.InjectRegistry;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

public class ConfigDrivenInterceptorTest {
    private static InjectRegistryManager registryManager;
    private static InjectRegistry registry;

    @BeforeAll
    static void init() {
        TrackInterceptor.INVOKED.clear();
        Config config = Config.just(ConfigSources.create(configNodes()));
        GlobalConfig.config(() -> config, true);
        registryManager = InjectRegistryManager.create();
        registry = registryManager.registry();
    }

    @Test
    public void testAService() {
        // zero or one configured instance
        var services = registry.all(AService.class);

        assertThat(services, hasSize(1));

        var instance = services.getFirst();
        assertThat(instance.name(), is("custom"));
        assertThat(instance.value(), is("value-a"));

        assertThat(TrackInterceptor.INVOKED, hasItems(TypeName.create(AConfig__ConfigBean.class)));
    }


    private static Map<String, String> configNodes() {
        Map<String, String> result = new HashMap<>();
        // A - zero or one
        addSingle(result, "a");
        return result;
    }

    private static void addSingle(Map<String, String> result, String letter) {
        result.put("config-" + letter + ".name", "custom");
        result.put("config-" + letter + ".value", "value-" + letter);
    }
}
