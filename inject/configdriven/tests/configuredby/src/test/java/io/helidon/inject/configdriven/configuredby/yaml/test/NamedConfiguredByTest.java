/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.inject.configdriven.configuredby.yaml.test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.yaml.YamlConfigParser;
import io.helidon.inject.api.Bootstrap;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.Services;
import io.helidon.inject.configdriven.api.NamedInstance;
import io.helidon.inject.configdriven.runtime.ConfigBeanRegistry;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.inject.testing.InjectionTestingSupport.resetAll;
import static io.helidon.inject.testing.InjectionTestingSupport.testableServices;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;

class NamedConfiguredByTest {
    InjectionServices injectionServices;
    Services services;

    @BeforeAll
    static void initialStateChecks() {
        ConfigBeanRegistry cbr = ConfigBeanRegistry.instance();
        assertThat(cbr.ready(), is(false));
    }

    @AfterAll
    static void tearDown() {
        resetAll();
    }

    void resetWith(Config config) {
        resetAll();
        this.injectionServices = testableServices(config);
        this.services = injectionServices.services();
    }

    @BeforeEach
    void setup() {
        Optional<Bootstrap> existingBootstrap = InjectionServices.globalBootstrap();
        assertThat(existingBootstrap, OptionalMatcher.optionalEmpty());

        Config config = Config.builder()
                .addSource(ConfigSources.classpath("application.yaml"))
                .addParser(YamlConfigParser.create())
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();
        resetWith(config);
    }

    @Test
    void namedConfiguredServices() {
        ConfigBeanRegistry cbr = ConfigBeanRegistry.instance();
        Map<Class<?>, List<NamedInstance<?>>> allConfigBeans = cbr.allConfigBeans();

        List<NamedInstance<?>> namedInstances = allConfigBeans.get(AsyncConfig.class);

        assertThat(namedInstances.stream().map(NamedInstance::name).toList(),
                   containsInAnyOrder("first", "second"));
    }

}
