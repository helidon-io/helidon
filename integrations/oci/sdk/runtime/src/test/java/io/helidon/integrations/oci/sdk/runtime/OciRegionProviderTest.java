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

package io.helidon.integrations.oci.sdk.runtime;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.common.config.GlobalConfig;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.service.inject.InjectConfig;
import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.service.inject.api.Injection.InjectionPointProvider;
import io.helidon.service.inject.api.Injection.QualifiedInstance;
import io.helidon.service.inject.api.Ip;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.Qualifier;
import io.helidon.service.registry.ServiceRegistryException;

import com.oracle.bmc.Region;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OciRegionProviderTest {
    static InjectRegistryManager registryManager;
    static InjectRegistry registry;

    @AfterAll
    static void tearDown() {
        if (registryManager != null) {
            registryManager.shutdown();
        }
        registryManager = null;
        registry = null;
    }

    void resetWith(Config config, InjectConfig injectionConfig) {
        if (registryManager != null) {
            registryManager.shutdown();
        }
        registryManager = InjectRegistryManager.create(injectionConfig);
        OciExtension.serviceRegistry(registryManager);
        registry = registryManager.registry();
        GlobalConfig.config(() -> config, true);
    }

    @Test
    void regionProviderService() {
        Config config = OciExtensionTest.createTestConfig(
                OciExtensionTest.ociAuthConfigStrategies(OciAuthenticationDetailsProvider.VAL_AUTO),
                OciExtensionTest.ociAuthSimpleConfig("tenant", "user", "phrase", "fp", null, null, "region"));
        resetWith(config, InjectConfig.create());

        Supplier<Region> regionSupplier = registryManager
                .registry()
                .supply(Lookup.builder().addContract(Region.class).build());
        assertThrows(ServiceRegistryException.class,
                     regionSupplier::get);

        TypeName regionType = TypeName.create(Region.class);

        Lookup query = Lookup.create(
                Ip.builder()
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

        InjectionPointProvider<Region> regionProvider = registryManager
                .registry()
                .get(Lookup.builder()
                             .addContract(InjectionPointProvider.class)
                             .addContract(Region.class)
                             .build());

        Optional<QualifiedInstance<Region>> regionInstance = regionProvider.first(query);
        assertThat(regionInstance, optionalPresent());
        QualifiedInstance<Region> regionQualifiedInstance = regionInstance.get();
        Region region = regionQualifiedInstance.get();
        Set<Qualifier> qualifiers = regionQualifiedInstance.qualifiers();

        assertThat(region, is(Region.US_PHOENIX_1));
        assertThat(qualifiers, contains(Qualifier.createNamed(Region.US_PHOENIX_1.getRegionId())));
    }
}
