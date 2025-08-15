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
package io.helidon.discovery.providers.eureka;

import java.time.Duration;

import io.helidon.common.config.Config;
import io.helidon.discovery.Discovery;
import io.helidon.service.registry.Services;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientConfig;

import org.junit.jupiter.api.Test;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class TestSpike {

    private TestSpike() {
        super();
    }

    @Test
    void testServiceRegistryLookup() {
        try (var d = (EurekaDiscoveryImpl) Services.get(Discovery.class)) {
            assertThat(d.prototype().registryFetchInterval(), is(Duration.of(20, SECONDS)));
        }
    }

    @Test
    void testBuilder() {
        try (var d = EurekaDiscovery.builder().config(Services.get(Config.class).get("discovery.eureka")).build()) {
            assertThat(d.prototype().registryFetchInterval(), is(Duration.of(20, SECONDS)));
        }
    }

    @Test
    void testClientCreate() {
        // create() in both cases says it uses "default values"; make sure the default value for baseUri is an empty
        // Optional.
        assertThat(Http1Client.create().prototype().baseUri().isEmpty(), is(true));
        assertThat(Http1ClientConfig.create().baseUri().isEmpty(), is(true));

        // Contrast this with "default values" used by the ClientUri#create() method. Http1Client will use
        // ClientUri#create() internally, if it detects its baseUri is empty, which means that unless we take care in
        // the Eureka Discovery code (which we do), we could end up trying to contact a Eureka server at
        // http://localhost:80/, which would be silly.
        assertThat(ClientUri.create().host(), is("localhost"));
        assertThat(ClientUri.create().port(), is(80));
    }

}
