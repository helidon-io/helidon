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

package io.helidon.service.tests.qualified.providers;

import io.helidon.service.registry.FactoryType;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

public class QualifiedProvidersTest {
    @Test
    public void testQualifiedProvidersNoApp() {
        ServiceRegistryManager registryManager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                     .useBinding(false)
                                                                                     .build());

        try {
            testServices(registryManager.registry());
        } finally {
            registryManager.shutdown();
        }
    }

    @Test
    public void testQualifiedProvidersWithApp() {
        ServiceRegistryManager registryManager = ServiceRegistryManager.create();

        try {
            testServices(registryManager.registry());
        } finally {
            registryManager.shutdown();
        }
    }

    private void testServices(ServiceRegistry registry) {
        TheService theService = registry.get(TheService.class);

        assertThat(theService.first(), is("first"));
        assertThat(theService.second(), is(49));
        assertThat(theService.firstContract().name(), is("first"));
        assertThat(theService.secondContract().name(), is("second"));

        Lookup singletonProviderLookup = Lookup.builder()
                .serviceType(FirstQualifiedProvider.class)
                .addFactoryType(FactoryType.QUALIFIED)
                .build();
        Service.QualifiedFactory<?, ?> singletonProvider = registry.get(singletonProviderLookup);
        assertThat(singletonProvider, instanceOf(FirstQualifiedProvider.class));
        assertThat(registry.get(singletonProviderLookup), sameInstance(singletonProvider));

        Lookup perLookupProviderLookup = Lookup.builder()
                .serviceType(SecondQualifiedProvider.class)
                .addFactoryType(FactoryType.QUALIFIED)
                .build();
        Service.QualifiedFactory<?, ?> perLookupProvider = registry.get(perLookupProviderLookup);
        assertThat(perLookupProvider, instanceOf(SecondQualifiedProvider.class));
        assertThat(registry.get(perLookupProviderLookup), not(sameInstance(perLookupProvider)));
    }
}
