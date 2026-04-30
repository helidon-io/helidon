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
import java.util.List;

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
        List<SomeProvider.SomeService> services = value.optionalListDiscover().orElseThrow();
        assertThat(services, hasSize(2));
        assertThat(services.get(0).prop(), is("some-1"));
        assertThat(services.get(1).prop(), is("some-2"));
        assertThat(value.listNotDiscover(), hasSize(0));
        assertThat(value.optionalListNotDiscover(), optionalEmpty());

        /*
        Test all the methods for a provider with no implementations
         */
        assertThat(value.optionalNoImplDiscover(), optionalEmpty());
        assertThat(value.optionalNoImplNotDiscover(), optionalEmpty());
        assertThat(value.listNoImplDiscover(), hasSize(0));
        assertThat(value.optionalListNoImplDiscoverNoConfig(), optionalEmpty());
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

        services = value.optionalListNotDiscover().orElseThrow();
        assertThat(services, hasSize(2));
        assertThat(services.get(0).prop(), is("config"));
        assertThat(services.get(1).prop(), is("config2"));
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

        services = value.optionalListNotDiscover().orElseThrow();
        assertThat(services, hasSize(1));
        assertThat(services.get(0).prop(), is("config2"));
    }

    @Test
    void testEmptyOptionalList() {
        WithProviderRegistry value = WithProviderRegistry.builder()
                .config(config.get("empty-optional-list-object"))
                .mappersExplicit(Mappers.create())
                .build();

        assertThat(value.optionalListDiscover(), optionalValue(is(List.of())));
        assertThat(value.optionalListNotDiscover(), optionalValue(is(List.of())));

        value = WithProviderRegistry.builder()
                .config(config.get("empty-optional-list-array"))
                .mappersExplicit(Mappers.create())
                .build();

        assertThat(value.optionalListDiscover(), optionalValue(is(List.of())));
        assertThat(value.optionalListNotDiscover(), optionalValue(is(List.of())));
    }

    @Test
    void testOptionalListBuilderMethods() {
        SomeProvider.SomeService someService = new DummyService();

        WithProviderRegistry value = WithProviderRegistry.builder()
                .oneNotDiscover(someService)
                .mappersExplicit(Mappers.create())
                .optionalListNotDiscover(List.of())
                .build();
        assertThat(value.optionalListNotDiscover(), optionalValue(is(List.of())));

        value = WithProviderRegistry.builder()
                .oneNotDiscover(someService)
                .mappersExplicit(Mappers.create())
                .addOptionalListNotDiscover(List.of(someService))
                .build();
        assertThat(value.optionalListNotDiscover(), optionalValue(is(List.of(someService))));

        value = WithProviderRegistry.builder()
                .oneNotDiscover(someService)
                .mappersExplicit(Mappers.create())
                .optionalListNotDiscover(List.of(someService))
                .clearOptionalListNotDiscover()
                .build();
        assertThat(value.optionalListNotDiscover(), optionalEmpty());
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
    void testOptionalListDiscoveryEnabled() {
        SomeProvider.SomeService someService = new DummyService();

        WithProviderRegistry value = WithProviderRegistry.builder()
                .config(config.get("min-defined"))
                .oneNotDiscover(someService)
                .mappersExplicit(Mappers.create())
                .optionalListNotDiscoverDiscoverServices(true)
                .build();

        List<SomeProvider.SomeService> services = value.optionalListNotDiscover().orElseThrow();
        assertThat(services, hasSize(2));
        assertThat(services.get(0).prop(), is("some-1"));
        assertThat(services.get(1).prop(), is("some-2"));
    }

    @Test
    void testOptionalListDiscoveryEnabledFromConfig() {
        WithProviderRegistry value = WithProviderRegistry.builder()
                .config(config.get("discover-optional-list-from-config"))
                .mappersExplicit(Mappers.create())
                .build();

        List<SomeProvider.SomeService> services = value.optionalListNotDiscover().orElseThrow();
        assertThat(services, hasSize(2));
        assertThat(services.get(0).prop(), is("some-1"));
        assertThat(services.get(1).prop(), is("some-2"));
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

}
