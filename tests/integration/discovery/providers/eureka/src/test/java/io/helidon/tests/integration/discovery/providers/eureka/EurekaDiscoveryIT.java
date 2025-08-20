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
package io.helidon.tests.integration.discovery.providers.eureka;

import java.net.URI;
import java.util.SequencedSet;

import io.helidon.discovery.DiscoveredUri;
import io.helidon.discovery.providers.eureka.EurekaDiscovery;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.helidon.common.media.type.MediaTypes.APPLICATION_JSON;
import static io.helidon.common.testing.junit5.MatcherWithRetry.assertThatWithRetry;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

@TestMethodOrder(OrderAnnotation.class)
class EurekaDiscoveryIT {

    private static final URI defaultValue = URI.create("http://example.com/nonexistent");

    private static EurekaDiscovery d;

    EurekaDiscoveryIT() {
        super();
    }

    @BeforeAll
    static void beforeAll() {
        d = Services.get(EurekaDiscovery.class);
        assertThat(d.getClass().getName(), is("io.helidon.discovery.providers.eureka.EurekaDiscoveryImpl"));
    }

    @AfterAll
    static void afterAll() {
        if (d != null) {
            d.close();
        }
    }

    @Test
    @Order(1)
    void testInitialDiscoverNoApplications() {
        // No applications registered. Make sure the default value is returned.
        assertThat(d.uris("EXAMPLE", defaultValue).stream().map(DiscoveredUri::uri).toList(), contains(defaultValue));
    }

    @Test
    @Order(2)
    void testRegisterAndDiscover() {
        String json = """
            {
                "instance": {
                    "app": "EXAMPLE",
                    "instanceId": "EXAMPLE_INSTANCE_ID",
                    "hostName": "localhost",
                    "ipAddr": "127.0.0.1",
                    "dataCenterInfo": {
                        "@class": "com.netflix.appinfo.MyDataCenterInfo",
                        "name":"MyOwn"
                    },
                    "port": {
                        "@enabled": true,
                        "$":80
                    },
                    "securePort": {
                        "@enabled": false,
                        "$": 443
                    }
                }
            }
        """;
        try (var response = d.prototype().client().orElseThrow()
             .post("/v2/apps/EXAMPLE")
             .contentType(APPLICATION_JSON)
             .submit(json)) {
            assertThat(response.status().code(), is(204));
        }
        assertThatWithRetry(() -> d.uris("EXAMPLE", defaultValue).stream().map(DiscoveredUri::uri).toList(),
                            contains(URI.create("http://localhost:80"), defaultValue));
    }

    @Test
    @Order(3)
    void testDeleteAndDiscover() {
        try (var response = d.prototype().client().orElseThrow()
             .delete("/v2/apps/EXAMPLE/EXAMPLE_INSTANCE_ID")
             .request()) {
            assertThat(response.status().code(), is(200));
        }
        assertThatWithRetry(() -> d.uris("EXAMPLE", defaultValue).stream().map(DiscoveredUri::uri).toList(),
                            contains(defaultValue));
    }

}
