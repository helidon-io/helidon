/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

package io.helidon.builder.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.helidon.builder.test.testsubjects.SomeProvider;
import io.helidon.builder.test.testsubjects.WithProviderRegistry;
import io.helidon.common.Errors;
import io.helidon.common.mapper.Mappers;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderRegistryTest {
    private static Config config;

    @BeforeAll
    static void beforeAll() {
        config = Config.just(ConfigSources.classpath("provider-test.yaml"));
    }

    @Test
    void testMinimalDefined() {
        /*
        Only the property that does not discover services and does not return optional is defined in config
         */
        WithProviderRegistry value = WithProviderRegistry.builder()
                .config(config.get("min-defined"))
                .mappersExplicit(Mappers.create())
                .build();

        assertThat(value.oneDiscover().prop(), is("some-1"));
        assertThat(value.oneNotDiscover().prop(), is("config"));

        assertThat(value.optionalDiscover().map(SomeProvider.SomeService::prop), optionalValue(is("some-1")));
        assertThat(value.optionalNotDiscover().map(SomeProvider.SomeService::prop), optionalEmpty());

        assertThat(value.listDiscover(), hasSize(2));
        List<SomeProvider.SomeService> optionalListDiscover = value.optionalListDiscover().orElseThrow();
        Collection<SomeProvider.SomeService> optionalSetDiscover = value.optionalSetDiscover().orElseThrow();
        assertServicePropsInOrder(optionalListDiscover, "some-1", "some-2");
        assertServiceProps(optionalSetDiscover, "some-1", "some-2");
        assertUnmodifiable(optionalListDiscover);
        assertUnmodifiable(optionalSetDiscover);
        assertThat(value.listNotDiscover(), hasSize(0));
        assertThat(value.optionalListNotDiscover(), optionalEmpty());
        assertThat(value.optionalSetNotDiscover(), optionalEmpty());

        /*
        Test all the methods for a provider with no implementations
         */
        assertThat(value.optionalNoImplDiscover(), optionalEmpty());
        assertThat(value.optionalNoImplNotDiscover(), optionalEmpty());
        assertThat(value.listNoImplDiscover(), hasSize(0));
        assertThat(value.optionalListNoImplDiscoverNoConfig(), optionalEmpty());
        assertThat(value.optionalSetNoImplDiscoverNoConfig(), optionalEmpty());
        assertThat(value.listNoImplNotDiscover(), hasSize(0));
    }

    @Test
    void testAllDefined() {
        /*
        Everything is customized
         */
        WithProviderRegistry value = WithProviderRegistry.builder()
                .config(config.get("all-defined"))
                .mappersExplicit(Mappers.create())
                .build();

        assertThat(value.oneDiscover().prop(), is("config"));
        assertThat(value.oneNotDiscover().prop(), is("config"));

        assertThat(value.optionalDiscover().map(SomeProvider.SomeService::prop), optionalValue(is("config")));
        assertThat(value.optionalNotDiscover().map(SomeProvider.SomeService::prop), optionalValue(is("config")));

        List<SomeProvider.SomeService> services = value.listDiscover();
        assertThat(services, hasSize(2));
        assertThat(services.get(0).prop(), is("config"));
        assertThat(services.get(1).prop(), is("config2"));

        services = value.optionalListDiscover().orElseThrow();
        assertThat(services, hasSize(2));
        assertThat(services.get(0).prop(), is("config"));
        assertThat(services.get(1).prop(), is("config2"));

        services = value.listNotDiscover();
        assertThat(value.listNotDiscover(), hasSize(2));
        assertThat(services.get(0).prop(), is("config"));
        assertThat(services.get(1).prop(), is("config2"));

        assertServicePropsInOrder(value.optionalListDiscover().orElseThrow(), "config", "config2");
        assertServiceProps(value.optionalSetDiscover().orElseThrow(), "config", "config2");
        assertServicePropsInOrder(value.optionalListNotDiscover().orElseThrow(), "config", "config2");
        assertServiceProps(value.optionalSetNotDiscover().orElseThrow(), "config", "config2");
    }

    @Test
    void testFail() {
        /*
        Missing the one mandatory option in config
         */
        Errors.ErrorMessagesException fail = assertThrows(Errors.ErrorMessagesException.class,
                                                          () -> WithProviderRegistry.builder()
                                                                  .config(config.get("fail"))
                                                                  .mappersExplicit(Mappers.create())
                                                                  .build());

        assertThat(fail.getMessages(), hasSize(1));
        assertThat(fail.getMessage(), containsString("\"oneNotDiscover\" must not be null"));
    }

    @Test
    void testSingleList() {
        /*
        Single value list (when not using discovery). When discovery is used, all in service loader are discovered, even
        if not configured
         */
        WithProviderRegistry value = WithProviderRegistry.builder()
                .config(config.get("single-list"))
                .mappersExplicit(Mappers.create())
                .build();

        assertThat(value.oneNotDiscover().prop(), is("config2"));

        List<SomeProvider.SomeService> services = value.listDiscover();
        assertThat(services, hasSize(2));
        assertThat(services.get(0).prop(), is("config"));
        assertThat(services.get(1).prop(), is("some-2"));

        services = value.optionalListDiscover().orElseThrow();
        assertThat(services, hasSize(2));
        assertThat(services.get(0).prop(), is("config"));
        assertThat(services.get(1).prop(), is("some-2"));

        services = value.listNotDiscover();
        assertThat(value.listNotDiscover(), hasSize(1));
        assertThat(services.get(0).prop(), is("config2"));

        assertServicePropsInOrder(value.optionalListDiscover().orElseThrow(), "config", "some-2");
        assertServiceProps(value.optionalSetDiscover().orElseThrow(), "config", "some-2");
        assertServicePropsInOrder(value.optionalListNotDiscover().orElseThrow(), "config2");
        assertServiceProps(value.optionalSetNotDiscover().orElseThrow(), "config2");
    }

    @Test
    void testEmptyOptionalProviderContainers() {
        WithProviderRegistry value = WithProviderRegistry.builder()
                .config(config.get("empty-optional-containers-object"))
                .mappersExplicit(Mappers.create())
                .build();

        assertThat(value.optionalListDiscover(), optionalValue(is(List.of())));
        assertThat(value.optionalSetDiscover(), optionalValue(is(Set.of())));
        assertThat(value.optionalListNotDiscover(), optionalValue(is(List.of())));
        assertThat(value.optionalSetNotDiscover(), optionalValue(is(Set.of())));

        value = WithProviderRegistry.builder()
                .config(config.get("empty-optional-containers-array"))
                .mappersExplicit(Mappers.create())
                .build();

        assertThat(value.optionalListDiscover(), optionalValue(is(List.of())));
        assertThat(value.optionalSetDiscover(), optionalValue(is(Set.of())));
        assertThat(value.optionalListNotDiscover(), optionalValue(is(List.of())));
        assertThat(value.optionalSetNotDiscover(), optionalValue(is(Set.of())));
    }

    @Test
    void testOptionalListDefensiveCopy() {
        SomeProvider.SomeService someService = new DummyService();
        List<SomeProvider.SomeService> configuredServices = new ArrayList<>();
        configuredServices.add(someService);

        WithProviderRegistry value = WithProviderRegistry.builder()
                .oneNotDiscover(someService)
                .mappersExplicit(Mappers.create())
                .optionalListNotDiscover(configuredServices)
                .build();
        configuredServices.clear();

        List<SomeProvider.SomeService> builtServices = value.optionalListNotDiscover().orElseThrow();
        assertThat(builtServices, is(List.of(someService)));
        assertThrows(UnsupportedOperationException.class, () -> builtServices.add(new DummyService()));
    }

    @Test
    void testOptionalProviderContainerBuilderMethods() {
        SomeProvider.SomeService someService = new DummyService();

        WithProviderRegistry value = WithProviderRegistry.builder()
                .oneNotDiscover(someService)
                .mappersExplicit(Mappers.create())
                .optionalListNotDiscover(List.of())
                .optionalSetNotDiscover(Set.of())
                .build();
        assertThat(value.optionalListNotDiscover(), optionalValue(is(List.of())));
        assertThat(value.optionalSetNotDiscover(), optionalValue(is(Set.of())));

        value = WithProviderRegistry.builder()
                .oneNotDiscover(someService)
                .mappersExplicit(Mappers.create())
                .addOptionalListNotDiscover(List.of(someService))
                .addOptionalSetNotDiscover(Set.of(someService))
                .build();
        assertThat(value.optionalListNotDiscover(), optionalValue(is(List.of(someService))));
        assertThat(value.optionalSetNotDiscover(), optionalValue(is(Set.of(someService))));

        value = WithProviderRegistry.builder()
                .oneNotDiscover(someService)
                .mappersExplicit(Mappers.create())
                .optionalListNotDiscover(List.of(someService))
                .optionalSetNotDiscover(Set.of(someService))
                .clearOptionalListNotDiscover()
                .clearOptionalSetNotDiscover()
                .build();
        assertThat(value.optionalListNotDiscover(), optionalEmpty());
        assertThat(value.optionalSetNotDiscover(), optionalEmpty());
    }

    @Test
    void testOptionalProviderContainerDiscoveryEnabled() {
        SomeProvider.SomeService someService = new DummyService();

        WithProviderRegistry value = WithProviderRegistry.builder()
                .config(config.get("min-defined"))
                .oneNotDiscover(someService)
                .mappersExplicit(Mappers.create())
                .optionalListNotDiscoverDiscoverServices(true)
                .optionalSetNotDiscoverDiscoverServices(true)
                .build();

        assertServicePropsInOrder(value.optionalListNotDiscover().orElseThrow(), "some-1", "some-2");
        assertServiceProps(value.optionalSetNotDiscover().orElseThrow(), "some-1", "some-2");
    }

    @Test
    void testOptionalProviderContainerDiscoveryEnabledFromConfig() {
        WithProviderRegistry value = WithProviderRegistry.builder()
                .config(config.get("discover-optional-containers-from-config"))
                .mappersExplicit(Mappers.create())
                .build();

        assertServicePropsInOrder(value.optionalListNotDiscover().orElseThrow(), "some-1", "some-2");
        assertServiceProps(value.optionalSetNotDiscover().orElseThrow(), "some-1", "some-2");
    }

    @Test
    void testDisabledDiscoveryOnTheCopy() {
        SomeProvider.SomeService someService = new DummyService();
        WithProviderRegistry value = WithProviderRegistry.builder()
                .listDiscoverDiscoverServices(false)
                .oneNotDiscover(someService) //This needs to be set, otherwise validation fails
                .mappersExplicit(Mappers.create())
                .build();
        assertThat(value.listDiscover(), is(List.of()));

        WithProviderRegistry copy = WithProviderRegistry.builder()
                .from(value)
                .build();
        assertThat(copy.listDiscover(), is(List.of()));
    }

    @Test
    void testDisabledDiscoveryOnTheCopiedBuilder() {
        SomeProvider.SomeService someService = new DummyService();
        WithProviderRegistry.Builder value = WithProviderRegistry.builder()
                .listDiscoverDiscoverServices(false)
                .mappersExplicit(Mappers.create())
                .oneNotDiscover(someService);

        WithProviderRegistry copy = WithProviderRegistry.builder()
                .from(value)
                .build();

        assertThat(copy.listDiscover(), is(List.of()));
    }

    @Test
    void testDisabledOptionalContainerDiscoveryOnTheCopy() {
        SomeProvider.SomeService someService = new DummyService();
        WithProviderRegistry value = WithProviderRegistry.builder()
                .optionalListDiscoverDiscoverServices(false)
                .optionalSetDiscoverDiscoverServices(false)
                .optionalListDiscover(List.of())
                .optionalSetDiscover(Set.of())
                .oneNotDiscover(someService)
                .mappersExplicit(Mappers.create())
                .build();
        assertThat(value.optionalListDiscover(), optionalValue(is(List.of())));
        assertThat(value.optionalSetDiscover(), optionalValue(is(Set.of())));

        WithProviderRegistry copy = WithProviderRegistry.builder()
                .from(value)
                .build();
        assertThat(copy.optionalListDiscover(), optionalValue(is(List.of())));
        assertThat(copy.optionalSetDiscover(), optionalValue(is(Set.of())));

        value = WithProviderRegistry.builder()
                .optionalListDiscoverDiscoverServices(false)
                .optionalSetDiscoverDiscoverServices(false)
                .optionalListDiscover(List.of(someService))
                .optionalSetDiscover(Set.of(someService))
                .oneNotDiscover(someService)
                .mappersExplicit(Mappers.create())
                .build();
        copy = WithProviderRegistry.builder()
                .from(value)
                .build();
        assertThat(copy.optionalListDiscover(), optionalValue(is(List.of(someService))));
        assertThat(copy.optionalSetDiscover(), optionalValue(is(Set.of(someService))));
    }

    @Test
    void testDisabledOptionalContainerDiscoveryOnTheCopiedBuilder() {
        SomeProvider.SomeService someService = new DummyService();
        WithProviderRegistry.Builder value = WithProviderRegistry.builder()
                .optionalListDiscoverDiscoverServices(false)
                .optionalSetDiscoverDiscoverServices(false)
                .optionalListDiscover(List.of())
                .optionalSetDiscover(Set.of())
                .oneNotDiscover(someService)
                .mappersExplicit(Mappers.create());

        WithProviderRegistry copy = WithProviderRegistry.builder()
                .from(value)
                .build();
        assertThat(copy.optionalListDiscover(), optionalValue(is(List.of())));
        assertThat(copy.optionalSetDiscover(), optionalValue(is(Set.of())));

        value = WithProviderRegistry.builder()
                .optionalListDiscoverDiscoverServices(false)
                .optionalSetDiscoverDiscoverServices(false)
                .optionalListDiscover(List.of(someService))
                .optionalSetDiscover(Set.of(someService))
                .oneNotDiscover(someService)
                .mappersExplicit(Mappers.create());
        copy = WithProviderRegistry.builder()
                .from(value)
                .build();
        assertThat(copy.optionalListDiscover(), optionalValue(is(List.of(someService))));
        assertThat(copy.optionalSetDiscover(), optionalValue(is(Set.of(someService))));
    }

    private static class DummyService implements SomeProvider.SomeService {
        @Override
        public String prop() {
            return null;
        }

        @Override
        public String name() {
            return null;
        }

        @Override
        public String type() {
            return null;
        }
    }

    private static void assertServiceProps(Collection<SomeProvider.SomeService> services, String... props) {
        assertThat(services.stream().map(SomeProvider.SomeService::prop).toList(), containsInAnyOrder(props));
    }

    private static void assertServicePropsInOrder(List<SomeProvider.SomeService> services, String... props) {
        assertThat(services.stream().map(SomeProvider.SomeService::prop).toList(), is(List.of(props)));
    }

    private static void assertUnmodifiable(Collection<SomeProvider.SomeService> services) {
        assertThrows(UnsupportedOperationException.class, () -> services.add(new DummyService()));
    }

}
