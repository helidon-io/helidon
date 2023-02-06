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

package io.helidon.pico.configdriven.configuredby.testsubjects;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.pico.DefaultQualifierAndValue;
import io.helidon.pico.DefaultServiceInfoCriteria;
import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.Services;
import io.helidon.pico.configdriven.ConfiguredBy;
import io.helidon.pico.testing.PicoTestingSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.pico.testing.PicoTestingSupport.testableServices;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

class ConfiguredByTest {
    static final String TAG_FAKE_SOCKET_CONFIG = "fake-socket-config";
    static final String TAG_FAKE_SERVER_CONFIG_CONFIG = "fake-server-config";

    private PicoServices picoServices;
    private Services services;

    @BeforeEach
    void reset() {
        PicoTestingSupport.resetAll();
        Config config = io.helidon.config.Config.create(
                ConfigSources.create(
                        Map.of(PicoServicesConfig.NAME + "." + PicoServicesConfig.KEY_PERMITS_DYNAMIC, "true",
                                TAG_FAKE_SOCKET_CONFIG + ".port", "8080",
                               TAG_FAKE_SERVER_CONFIG_CONFIG + ".worker-count", "1"
                        ), "config-1"));
        this.picoServices = testableServices(config);
        this.services = picoServices.services();
    }

    @Test
    void serviceRegistry() {
        DefaultServiceInfoCriteria criteria = DefaultServiceInfoCriteria.builder()
                .addQualifier(DefaultQualifierAndValue.create(ConfiguredBy.class))
                .build();
        List<ServiceProvider<Object>> list = services.lookupAll(criteria);
        List<String> desc = list.stream().map(ServiceProvider::description).collect(Collectors.toList());
        assertThat(desc, contains("FakeWebServer:PENDING"));
    }

}
