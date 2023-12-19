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

import io.helidon.common.config.GlobalConfig;
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.inject.InjectionConfig;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.Services;
import io.helidon.inject.configdriven.CbrServiceDescriptor;
import io.helidon.inject.configdriven.ConfigBeanRegistry;
import io.helidon.inject.configdriven.service.NamedInstance;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.inject.testing.InjectionTestingSupport.resetAll;
import static io.helidon.inject.testing.InjectionTestingSupport.testableServices;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;

class NamedConfiguredByTest {
    InjectionServices injectionServices;
    Services services;

    @AfterAll
    static void tearDown() {
        resetAll();
    }

    void resetWith(InjectionConfig config) {
        resetAll();
        this.injectionServices = testableServices(config);
        this.services = injectionServices.services();
    }

    @BeforeEach
    void setup() {
        Config config = Config.builder()
                .addSource(ConfigSources.classpath("application.yaml"))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();
        GlobalConfig.config(() -> config, true);
        resetWith(InjectionConfig.builder()
                          .permitsDynamic(true)
                          .build());
    }

    @Test
    void namedConfiguredServices() {
        ConfigBeanRegistry cbr = services.serviceProviders()
                .<ConfigBeanRegistry>get(CbrServiceDescriptor.INSTANCE).get();
        Map<TypeName, List<NamedInstance<?>>> allConfigBeans = cbr.allConfigBeans();

        List<NamedInstance<?>> namedInstances = allConfigBeans.get(TypeName.create(AsyncConfig.class));

        assertThat(namedInstances.stream().map(NamedInstance::name).toList(),
                   containsInAnyOrder("first", "second"));
    }

}
