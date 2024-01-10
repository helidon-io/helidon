/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.tests.configbeans.driven.configuredby.test;

import java.util.List;
import java.util.Map;

import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.MapConfigSource;
import io.helidon.inject.service.ConfigBeans;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.service.ServiceInstance;
import io.helidon.inject.tests.configbeans.driven.configuredby.yaml.test.Async;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

/**
 * Executes the tests from the base.
 */
class ConfiguredByTest extends AbstractConfiguredByTest {
    @Test
    void testRepeatableConfigBean() {
        shutDown();
        resetWith(Config.create());

        List<Async> serviceProviders = services.all(Async.class);

        assertThat(serviceProviders, hasSize(2));

        Async async = services.get(Lookup.builder()
                                           .addContract(Async.class)
                                           .addQualifier(Qualifier.createNamed("first"))
                                           .build());
        assertThat(async.config(), notNullValue());
        assertThat(async.config().executor(), is("exec"));

        async = services.get(Lookup.builder()
                                     .addContract(Async.class)
                                     .addQualifier(Qualifier.createNamed("second"))
                                     .build());
        assertThat(async.config(), notNullValue());
        assertThat(async.config().executor(), is("service"));
    }

    @Test
    void onlyRootConfigBeansAreCreated() {
        shutDown();
        resetWith(io.helidon.config.Config.builder(createRootDefault8080TestingConfigSource(),
                                                   createNested8080TestingConfigSource())
                          .disableEnvironmentVariablesSource()
                          .disableSystemPropertiesSource()
                          .build());

        List<ServiceInstance<Object>> configBeans = services.lookupInstances(Lookup.builder()
                                                                                      .addQualifier(Qualifier.create(
                                                                                              ConfigBeans.ConfigBean.class))
                                                                                      .addQualifier(Qualifier.WILDCARD_NAMED)
                                                                                      .build());

        assertHasNamed(configBeans, SomeServiceConfig.class, "@default");
        assertHasNamed(configBeans, ASingletonConfigBean.class, "@default");
    }

    protected MapConfigSource.Builder createNested8080TestingConfigSource() {
        return ConfigSources.create(
                Map.of(
                        "nested." + FAKE_SERVER_CONFIG + ".0.name", "nested",
                        "nested." + FAKE_SERVER_CONFIG + ".0.port", "8080",
                        "nested." + FAKE_SERVER_CONFIG + ".0.worker-count", "1"
                ), "config-nested-default-8080");
    }

    private void assertHasNamed(List<ServiceInstance<Object>> instances, Class<?> type, String name) {
        boolean contains = false;

        for (ServiceInstance<Object> instance : instances) {
            if (instance.contracts().contains(TypeName.create(type))) {
                if (instance.qualifiers().contains(Qualifier.createNamed(name))) {
                    contains = true;
                }
            }

        }
        assertThat("Instances should have contained a named (\"" + name + "\" instance of "
                           + type.getName() + ", instances: " + instances,
                   contains,
                   is(true));
    }

}
