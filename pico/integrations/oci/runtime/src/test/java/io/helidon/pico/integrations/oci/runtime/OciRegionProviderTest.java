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
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.ServiceProvider;
import io.helidon.pico.api.Services;

import com.oracle.bmc.Region;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static io.helidon.pico.testing.PicoTestingSupport.resetAll;
import static io.helidon.pico.testing.PicoTestingSupport.testableServices;

class OciRegionProviderTest {
    PicoServices picoServices;
    Services services;

    @AfterAll
    static void tearDown() {
//        resetAll();
    }

    void resetWith(Config config) {
        resetAll();
        this.picoServices = testableServices(config);
        this.services = picoServices.services();
    }

        @Test
    void regionProviderService() {
        ServiceProvider<Region> regionProvider = PicoServices.realizedServices()
                .lookupFirst(Region.class, true).orElseThrow();
//        assertThat(regionProvider.get(),
//                   nullValue());
    }

}
