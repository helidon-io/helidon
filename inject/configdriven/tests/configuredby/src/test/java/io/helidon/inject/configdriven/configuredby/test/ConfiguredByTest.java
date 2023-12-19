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
import java.util.function.Supplier;

import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.MapConfigSource;
import io.helidon.inject.Lookup;
import io.helidon.inject.ServiceProvider;
import io.helidon.inject.configdriven.CbrServiceDescriptor;
import io.helidon.inject.configdriven.ConfigBeanRegistry;
import io.helidon.inject.configdriven.configuredby.yaml.test.Async;
import io.helidon.inject.configdriven.service.NamedInstance;
import io.helidon.inject.service.Qualifier;

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
    void testInjectCbr() {
        resetWith(Config.create());

        ServiceProvider<ServiceUsingRegistry> lookup = services.get(ServiceUsingRegistry__ServiceDescriptor.INSTANCE);
        ServiceUsingRegistry service = lookup.get();

        assertThat(service.registry(), notNullValue());
        assertThat(service.registry().ready(), is(true));
    }

    @Test
    void testRepeatableConfigBean() {
        resetWith(Config.create());

        List<Async> serviceProviders = serviceRegistry.all(Async.class)
                .stream()
                .map(Supplier::get)
                .toList();

        assertThat(serviceProviders, hasSize(2));

        Async async = services.<Async>get(Lookup.builder()
                                                  .addContract(Async.class)
                                                  .addQualifier(Qualifier.createNamed("first"))
                                                  .build())
                .get();
        assertThat(async.config(), notNullValue());
        assertThat(async.config().executor(), is("exec"));

        async = services.<Async>get(Lookup.builder()
                                            .addContract(Async.class)
                                            .addQualifier(Qualifier.createNamed("second"))
                                            .build())
                .get();
        assertThat(async.config(), notNullValue());
        assertThat(async.config().executor(), is("service"));
    }

    @Test
    void onlyRootConfigBeansAreCreated() {
        resetWith(io.helidon.config.Config.builder(createRootDefault8080TestingConfigSource(),
                                                   createNested8080TestingConfigSource())
                          .disableEnvironmentVariablesSource()
                          .disableSystemPropertiesSource()
                          .build());

        ConfigBeanRegistry cbr = services.<ConfigBeanRegistry>get(CbrServiceDescriptor.INSTANCE).get();
        assertThat(cbr.ready(),
                   is(true));

        Map<TypeName, List<NamedInstance<?>>> beans = cbr.allConfigBeans();
        assertThat(beans, hasKey(TypeName.create(SomeServiceConfig.class)));
        assertThat(beans, hasKey(TypeName.create(ASingletonConfigBean.class)));

        assertHasNamed(beans.get(TypeName.create(SomeServiceConfig.class)), "@default");
        assertHasNamed(beans.get(TypeName.create(ASingletonConfigBean.class)), "@default");
    }

    protected MapConfigSource.Builder createNested8080TestingConfigSource() {
        return ConfigSources.create(
                Map.of(
                        "nested." + FAKE_SERVER_CONFIG + ".0.name", "nested",
                        "nested." + FAKE_SERVER_CONFIG + ".0.port", "8080",
                        "nested." + FAKE_SERVER_CONFIG + ".0.worker-count", "1"
                ), "config-nested-default-8080");
    }

    private void assertHasNamed(List<NamedInstance<?>> namedInstances, String... expectedNames) {
        List<String> nameList = namedInstances.stream()
                .map(NamedInstance::name)
                .toList();
        Set<String> nameSet = Set.copyOf(nameList);

        assertThat("Names should be unique.", nameList, is(List.copyOf(nameSet)));
        assertThat(nameSet, contains(expectedNames));
    }

}
