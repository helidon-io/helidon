/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.util.List;
import java.util.Set;

import io.helidon.config.spi.ConfigSource;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.service.registry.ServiceRegistry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Isolated
class MetaConfigFactoryTest {
    @Test
    void profileSynthesisUsesOwningRegistry() {
        String profileProperty = MetaConfigFinder.HELIDON_CONFIG_PROFILE_SYSTEM_PROPERTY;
        String previousProfile = System.getProperty(profileProperty);
        ServiceRegistry previousRegistry = GlobalServiceRegistry.registry();
        ServiceRegistry ownerRegistry = registryWithNamedSource("owner");
        ServiceRegistry globalRegistry = registryWithNamedSource("global");

        try {
            System.setProperty(profileProperty, "missing-owner-profile");
            GlobalServiceRegistry.registry(globalRegistry);

            Config metaConfig = new MetaConfigFactory(ownerRegistry)
                    .services()
                    .getFirst()
                    .get()
                    .metaConfiguration();
            List<String> sourceTypes = metaConfig.get("sources")
                    .asNodeList()
                    .orElseThrow()
                    .stream()
                    .map(source -> source.get("type").asString().orElseThrow())
                    .toList();

            assertThat(sourceTypes, hasItem("owner"));
            assertThat(sourceTypes, not(hasItem("global")));
        } finally {
            if (previousProfile == null) {
                System.clearProperty(profileProperty);
            } else {
                System.setProperty(profileProperty, previousProfile);
            }
            GlobalServiceRegistry.registry(previousRegistry);
        }
    }

    private static ServiceRegistry registryWithNamedSource(String name) {
        ServiceInfo serviceInfo = mock(ServiceInfo.class);
        when(serviceInfo.qualifiers()).thenReturn(Set.of(Qualifier.createNamed(name)));
        ServiceRegistry serviceRegistry = mock(ServiceRegistry.class);
        when(serviceRegistry.allServices(ConfigSource.class)).thenReturn(List.of(serviceInfo));
        return serviceRegistry;
    }
}
