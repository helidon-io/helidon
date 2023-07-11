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

package io.helidon.inject.runtime;

import java.io.Closeable;
import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.inject.api.InjectionPointProvider;
import io.helidon.inject.api.Bootstrap;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.ModuleComponent;
import io.helidon.inject.api.ServiceBinder;
import io.helidon.inject.api.ServiceInfo;
import io.helidon.inject.api.ServiceInfoCriteria;
import io.helidon.inject.api.ServiceProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.inject.api.Qualifier.createNamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

class DefaultInjectionPlansTest {
    static final FakeInjectionPointProviderActivator sp1 = new FakeInjectionPointProviderActivator();
    static final FakeRegularActivator sp2 = new FakeRegularActivator();

    Config config = Config.builder(
                    ConfigSources.create(
                            Map.of("inject.permits-dynamic", "true"), "config-1"))
            .disableEnvironmentVariablesSource()
            .disableSystemPropertiesSource()
            .build();

    @BeforeEach
    void init() {
        InjectionServices.globalBootstrap(Bootstrap.builder().config(config).build());
    }

    @AfterEach
    void tearDown() {
        SimpleInjectionTestingSupport.resetAll();
    }

    /**
     * Also exercised in examples/inject.
     */
    @Test
    void testInjectionPointResolversFor() {
        InjectionServices injectionServices = InjectionServices.injectionServices().orElseThrow();
        DefaultServices services = (DefaultServices) InjectionServices.realizedServices();
        services.bind(injectionServices, new FakeModuleComponent(), true);

        ServiceInfoCriteria criteria = ServiceInfoCriteria.builder()
                .addQualifier(createNamed("whatever"))
                .addContractImplemented(Closeable.class)
                .build();
        List<String> result = DefaultInjectionPlans.injectionPointProvidersFor(services, criteria).stream()
                .map(ServiceProvider::description).toList();
        assertThat(result,
                   contains(sp1.description()));
    }

    static class FakeModuleComponent implements ModuleComponent {
        @Override
        public void configure(ServiceBinder binder) {
            binder.bind(sp1);
            binder.bind(sp2);
        }
    }

    static class FakeInjectionPointProviderActivator extends AbstractServiceProvider<Closeable> {
        private static final ServiceInfo serviceInfo =
                ServiceInfo.builder()
                        .serviceTypeName(FakeInjectionPointProviderActivator.class)
                        .addContractImplemented(Closeable.class)
                        .addExternalContractImplemented(InjectionPointProvider.class)
                        .addExternalContractImplemented(jakarta.inject.Provider.class)
                        .build();

        public FakeInjectionPointProviderActivator() {
            serviceInfo(serviceInfo);
        }

        @Override
        public Class<Closeable> serviceType() {
            return Closeable.class;
        }
    }

    static class FakeRegularActivator extends AbstractServiceProvider<Closeable> {
        private static final ServiceInfo serviceInfo =
                ServiceInfo.builder()
                        .serviceTypeName(FakeRegularActivator.class)
                        .addContractImplemented(Closeable.class)
                        .addExternalContractImplemented(jakarta.inject.Provider.class)
                        .build();

        public FakeRegularActivator() {
            serviceInfo(serviceInfo);
        }

        @Override
        public Class<Closeable> serviceType() {
            return Closeable.class;
        }
    }

}
