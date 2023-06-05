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

package io.helidon.pico.integrations.oci.runtime;

import io.helidon.config.Config;
import io.helidon.pico.api.ContextualServiceQuery;
import io.helidon.pico.api.ElementInfo;
import io.helidon.pico.api.InjectionPointInfoDefault;
import io.helidon.pico.api.PicoServiceProviderException;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.QualifierAndValueDefault;
import io.helidon.pico.api.ServiceInfoCriteriaDefault;
import io.helidon.pico.api.ServiceProvider;
import io.helidon.pico.api.Services;

import com.oracle.bmc.Region;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static io.helidon.pico.integrations.oci.runtime.OciAuthenticationDetailsProvider.TAG_AUTO;
import static io.helidon.pico.integrations.oci.runtime.OciConfigBeanTest.basicTestingConfigSource;
import static io.helidon.pico.integrations.oci.runtime.OciConfigBeanTest.createTestConfig;
import static io.helidon.pico.integrations.oci.runtime.OciConfigBeanTest.ociAuthConfigStrategies;
import static io.helidon.pico.integrations.oci.runtime.OciConfigBeanTest.ociAuthSimpleConfig;
import static io.helidon.pico.testing.PicoTestingSupport.resetAll;
import static io.helidon.pico.testing.PicoTestingSupport.testableServices;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OciRegionProviderTest {
    PicoServices picoServices;
    Services services;

    @AfterAll
    static void tearDown() {
        resetAll();
    }

    void resetWith(Config config) {
        resetAll();
        this.picoServices = testableServices(config);
        this.services = picoServices.services();
    }

    @Test
    void regionProviderService() {
        Config config = createTestConfig(
                basicTestingConfigSource(),
                ociAuthConfigStrategies(TAG_AUTO),
                ociAuthSimpleConfig("tenant", "user", "phrase", "fp", null, null, "region"));
        resetWith(config);

        ServiceProvider<Region> regionProvider = PicoServices.realizedServices()
                .lookupFirst(Region.class, false).orElseThrow();
        assertThrows(PicoServiceProviderException.class,
                     regionProvider::get);

        ContextualServiceQuery query = ContextualServiceQuery.create(
                InjectionPointInfoDefault.builder()
                        .serviceTypeName("whatever")
                        .elementKind(ElementInfo.ElementKind.METHOD)
                        .elementName("m")
                        .elementTypeName(Region.class.getName())
                        .baseIdentity("m")
                        .id("m1")
                        .access(ElementInfo.Access.PUBLIC)
                        .addQualifier(QualifierAndValueDefault.createNamed("us-phoenix-1"))
                        .dependencyToServiceInfo(ServiceInfoCriteriaDefault.builder()
                                                         .addContractImplemented(Region.class.getName())
                                                         .addQualifier(QualifierAndValueDefault.createNamed("us-phoenix-1"))
                                                         .build())
                        .build(),
                false);
        assertThat(regionProvider.first(query),
                   optionalValue(equalTo(Region.US_PHOENIX_1)));
    }

}
