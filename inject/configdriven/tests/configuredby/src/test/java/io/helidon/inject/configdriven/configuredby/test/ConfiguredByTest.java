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

package io.helidon.inject.configdriven.configuredby.test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.MapConfigSource;
import io.helidon.inject.api.ServiceProvider;
import io.helidon.inject.configdriven.api.NamedInstance;
import io.helidon.inject.configdriven.configuredby.yaml.test.Async;
import io.helidon.inject.configdriven.runtime.ConfigBeanRegistry;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

/**
 * Executes the tests from the base.
 */
class ConfiguredByTest extends AbstractConfiguredByTest {
    @Test
    void testRepeatableConfigBean() {
        resetWith(Config.create());

        List<Async> serviceProviders = services.lookupAll(Async.class)
                .stream()
                .map(ServiceProvider::get)
                .toList();

        assertThat(serviceProviders, hasSize(2));

        Async async = services.lookup(Async.class, "first").get();
        assertThat(async.config(), notNullValue());
        assertThat(async.config().executor(), is("exec"));

        async = services.lookup(Async.class, "second").get();
        assertThat(async.config(), notNullValue());
        assertThat(async.config().executor(), is("service"));
    }

    @Test
    void onlyRootConfigBeansAreCreated() {
        resetWith(io.helidon.config.Config.builder(createBasicTestingConfigSource(),
                                                   createRootDefault8080TestingConfigSource(),
                                                   createNested8080TestingConfigSource())
                          .disableEnvironmentVariablesSource()
                          .disableSystemPropertiesSource()
                          .build());

        ConfigBeanRegistry cbr = ConfigBeanRegistry.instance();
        assertThat(cbr.ready(),
                   is(true));

        Map<Class<?>, List<NamedInstance<?>>> beans = cbr.allConfigBeans();
        assertThat(beans, hasKey(SomeServiceConfig.class));
        assertThat(beans, hasKey(ASingletonConfigBean.class));

        assertHasNamed(beans.get(SomeServiceConfig.class), "@default");
        assertHasNamed(beans.get(ASingletonConfigBean.class), "@default");
    }

    private void assertHasNamed(List<NamedInstance<?>> namedInstances, String... expectedNames) {
        List<String> nameList = namedInstances.stream()
                .map(NamedInstance::name)
                .toList();
        Set<String> nameSet = Set.copyOf(nameList);

        assertThat("Names should be unique.", nameList, is(List.copyOf(nameSet)));
        assertThat(nameSet, contains(expectedNames));
    }

    protected MapConfigSource.Builder createNested8080TestingConfigSource() {
        return ConfigSources.create(
                Map.of(
                        "nested." + FAKE_SERVER_CONFIG + ".0.name", "nested",
                        "nested." + FAKE_SERVER_CONFIG + ".0.port", "8080",
                        "nested." + FAKE_SERVER_CONFIG + ".0.worker-count", "1"
                ), "config-nested-default-8080");
    }

}
