/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.webclient.discovery;

import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.service.registry.Services;
import io.helidon.webclient.api.WebClientConfig;
import io.helidon.webclient.spi.WebClientService;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;

class WebClientConfigTest {

    private WebClientConfigTest() {
        super();
    }

    @Test
    void testWebClientDiscoveryIsPickedUpAndConfigured() {
        WebClientConfig c = WebClientConfig.builder().buildPrototype();
        List<WebClientService> services = c.services();
        assertThat(services, hasSize(1));
        WebClientDiscovery s = (WebClientDiscovery) services.get(0);
        // (We have the Eureka provider on the test classpath.)
        assertThat(s.prototype().discovery().getClass().getName(),
                   is("io.helidon.discovery.providers.eureka.EurekaDiscoveryImpl"));
    }

    @Test
    void testPropertiesFromConfig() {
        WebClientConfig c = WebClientConfig.builder()
            .config(Services.get(Config.class).get(this.getClass().getSimpleName() + "-testPropertiesFromConfig"))
            .buildPrototype();
        Map<String, String> properties = c.properties();
        assertThat(properties.keySet(), hasSize(2));
        assertThat(properties, hasEntry("helidon-discovery-TEST-prefix-uri", "http://test.example.com/"));
        assertThat(properties, hasEntry("helidon-discovery-PROD-prefix-uri", "http://prod.example.com/"));
    }

}
