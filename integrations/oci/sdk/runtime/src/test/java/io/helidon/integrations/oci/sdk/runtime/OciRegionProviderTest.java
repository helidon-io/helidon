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

package io.helidon.integrations.oci.sdk.runtime;

import io.helidon.common.config.GlobalConfig;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.inject.ContextualServiceQuery;
import io.helidon.inject.InjectionConfig;
import io.helidon.inject.InjectionServiceProviderException;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.ServiceProvider;
import io.helidon.inject.Services;
import io.helidon.inject.service.Ip;
import io.helidon.inject.service.Qualifier;

import com.oracle.bmc.Region;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static io.helidon.inject.testing.InjectionTestingSupport.resetAll;
import static io.helidon.inject.testing.InjectionTestingSupport.testableServices;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OciRegionProviderTest {
    InjectionServices injectionServices;
    Services services;

    @AfterAll
    static void tearDown() {
        resetAll();
    }

    void resetWith(Config config, InjectionConfig injectionConfig) {
        resetAll();
        this.injectionServices = testableServices(injectionConfig);
        this.services = injectionServices.services();
        GlobalConfig.config(() -> config, true);
    }

    @Test
    void regionProviderService() {
        Config config = OciExtensionTest.createTestConfig(
                OciExtensionTest.ociAuthConfigStrategies(OciAuthenticationDetailsProvider.VAL_AUTO),
                OciExtensionTest.ociAuthSimpleConfig("tenant", "user", "phrase", "fp", null, null, "region"));
        resetWith(config, InjectionConfig.builder()
                .permitsDynamic(true)
                .build());

        ServiceProvider<Region> regionProvider = InjectionServices.instance()
                .services()
                .serviceProviders()
                .get(Region.class);
        assertThrows(InjectionServiceProviderException.class,
                     regionProvider::get);

        TypeName regionType = TypeName.create(Region.class);

        ContextualServiceQuery query = ContextualServiceQuery.create(
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
                        .build(),
                false);
        assertThat(regionProvider.first(query),
                   optionalValue(equalTo(Region.US_PHOENIX_1)));
    }

}
