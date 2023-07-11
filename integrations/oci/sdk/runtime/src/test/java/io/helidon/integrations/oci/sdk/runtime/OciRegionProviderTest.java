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

import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.inject.api.AccessModifier;
import io.helidon.inject.api.ContextualServiceQuery;
import io.helidon.inject.api.ElementKind;
import io.helidon.inject.api.InjectionPointInfo;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.InjectionServiceProviderException;
import io.helidon.inject.api.Qualifier;
import io.helidon.inject.api.ServiceInfoCriteria;
import io.helidon.inject.api.ServiceProvider;
import io.helidon.inject.api.Services;

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

    void resetWith(Config config) {
        resetAll();
        this.injectionServices = testableServices(config);
        this.services = injectionServices.services();
    }

    @Test
    void regionProviderService() {
        Config config = OciConfigTest.createTestConfig(
                OciConfigTest.basicTestingConfigSource(),
                OciConfigTest.ociAuthConfigStrategies(OciAuthenticationDetailsProvider.TAG_AUTO),
                OciConfigTest.ociAuthSimpleConfig("tenant", "user", "phrase", "fp", null, null, "region"));
        resetWith(config);

        ServiceProvider<Region> regionProvider = InjectionServices.realizedServices()
                .lookupFirst(Region.class, false).orElseThrow();
        assertThrows(InjectionServiceProviderException.class,
                     regionProvider::get);

        TypeName regionType = TypeName.create(Region.class);

        ContextualServiceQuery query = ContextualServiceQuery.create(
                InjectionPointInfo.builder()
                        .ipType(regionType)
                        .ipName("region")
                        .serviceTypeName(TypeName.create("io.helidon.Whatever"))
                        .elementKind(ElementKind.METHOD)
                        .elementName("m")
                        .elementTypeName(regionType)
                        .baseIdentity("m")
                        .id("m1")
                        .access(AccessModifier.PUBLIC)
                        .addQualifier(Qualifier.createNamed("us-phoenix-1"))
                        .dependencyToServiceInfo(ServiceInfoCriteria.builder()
                                                         .addContractImplemented(regionType)
                                                         .addQualifier(Qualifier.createNamed("us-phoenix-1"))
                                                         .build())
                        .build(),
                                         false);
        assertThat(regionProvider.first(query),
                   optionalValue(equalTo(Region.US_PHOENIX_1)));
    }

}
