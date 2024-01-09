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

import java.util.function.Supplier;

import io.helidon.common.config.GlobalConfig;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.inject.InjectionConfig;
import io.helidon.inject.InjectionServiceProviderException;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.Services;
import io.helidon.inject.service.InjectionPointProvider;
import io.helidon.inject.service.Ip;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.testing.InjectionTestingSupport;

import com.oracle.bmc.Region;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OciRegionProviderTest {
    static InjectionServices injectionServices;
    static Services services;

    @AfterAll
    static void tearDown() {
        InjectionTestingSupport.shutdown(injectionServices);
        injectionServices = null;
        services = null;
    }

    void resetWith(Config config, InjectionConfig injectionConfig) {
        InjectionTestingSupport.shutdown(injectionServices);
        injectionServices = InjectionServices.create(injectionConfig);
        services = injectionServices.services();
        GlobalConfig.config(() -> config, true);
    }

    @Test
    void regionProviderService() {
        Config config = OciExtensionTest.createTestConfig(
                OciExtensionTest.ociAuthConfigStrategies(OciAuthenticationDetailsProvider.VAL_AUTO),
                OciExtensionTest.ociAuthSimpleConfig("tenant", "user", "phrase", "fp", null, null, "region"));
        resetWith(config, InjectionConfig.create());

        Supplier<Region> regionSupplier = InjectionServices.instance()
                .services()
                .supply(Lookup.builder().addContract(Region.class).build());
        assertThrows(InjectionServiceProviderException.class,
                     regionSupplier::get);

        TypeName regionType = TypeName.create(Region.class);

        Lookup query = Lookup.create(
                Ip.builder()
                        .contract(regionType)
                        .field("TEST_ONLY")
                        .descriptor(TypeName.create("io.helidon.Whatever"))
                        .typeName(regionType)
                        .service(TypeName.create("io.helidon.Whatever"))
                        .name("region")
                        .elementKind(ElementKind.METHOD)
                        .access(AccessModifier.PUBLIC)
                        .addQualifier(Qualifier.createNamed("us-phoenix-1"))
                        .build());

        InjectionPointProvider<Region> regionProvider = InjectionServices.instance()
                .services()
                .get(Lookup.builder()
                             .addContract(InjectionPointProvider.class)
                             .addContract(Region.class)
                             .build());

        assertThat(regionProvider.first(query),
                   optionalValue(equalTo(Region.US_PHOENIX_1)));
    }
}
