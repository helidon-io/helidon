/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.sdk.runtime;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.common.config.GlobalConfig;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.FactoryType;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryException;
import io.helidon.service.registry.ServiceRegistryManager;

import com.oracle.bmc.Region;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("removal")
class OciRegionProviderTest {
    static ServiceRegistryManager registryManager;
    static ServiceRegistry registry;

    @AfterAll
    static void tearDown() {
        if (registryManager != null) {
            registryManager.shutdown();
        }
        registryManager = null;
        registry = null;
    }

    void resetWith(Config config, ServiceRegistryConfig injectionConfig) {
        if (registryManager != null) {
            registryManager.shutdown();
        }
        registryManager = ServiceRegistryManager.create(injectionConfig);
        OciExtension.serviceRegistry(registryManager);
        registry = registryManager.registry();
        GlobalConfig.config(() -> config, true);
    }

    @Test
    void regionProviderService() {
        Config config = OciExtensionTest.createTestConfig(
                OciExtensionTest.ociAuthConfigStrategies(OciAuthenticationDetailsProvider.VAL_AUTO),
                OciExtensionTest.ociAuthSimpleConfig("tenant", "user", "phrase", "fp", null, null, "region"));
        resetWith(config, ServiceRegistryConfig.create());

        Supplier<Region> regionSupplier = registryManager
                .registry()
                .supply(Lookup.builder().addContract(Region.class).build());
        assertThrows(ServiceRegistryException.class,
                     regionSupplier::get);

        TypeName regionType = TypeName.create(Region.class);

        Lookup query = Lookup.create(
                Dependency.builder()
                        .contract(regionType)
                        .descriptorConstant("TEST_ONLY")
                        .descriptor(TypeName.create("io.helidon.Whatever"))
                        .typeName(regionType)
                        .service(TypeName.create("io.helidon.Whatever"))
                        .name("region")
                        .elementKind(ElementKind.METHOD)
                        .access(AccessModifier.PUBLIC)
                        .addQualifier(Qualifier.createNamed("us-phoenix-1"))
                        .build());

        Service.InjectionPointFactory<Region> regionProvider = registryManager
                .registry()
                .get(Lookup.builder()
                             .addFactoryType(FactoryType.INJECTION_POINT)
                             .addContract(Region.class)
                             .build());

        Optional<Service.QualifiedInstance<Region>> regionInstance = regionProvider.first(query);
        assertThat(regionInstance, optionalPresent());
        Service.QualifiedInstance<Region> regionQualifiedInstance = regionInstance.get();
        Region region = regionQualifiedInstance.get();
        Set<Qualifier> qualifiers = regionQualifiedInstance.qualifiers();

        assertThat(region, is(Region.US_PHOENIX_1));
        assertThat(qualifiers, contains(Qualifier.createNamed(Region.US_PHOENIX_1.getRegionId())));
    }
}
