/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.health.mp.disabled;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import io.helidon.microprofile.server.Server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link HealthDisabledMain}.
 */
class HealthDisabledMainTest {
    private static Server server;

    @BeforeAll
    static void initClass() {
        server = HealthDisabledMain.startServer();
    }

    @AfterAll
    static void destroyClass() {
        server.stop();
    }

    @Test
    void testHealthEndpoint() {
        Client client = ClientBuilder.newClient();
        WebTarget baseTarget = client.target("http://localhost:" + server.port());
        // metrics should work
        baseTarget.path("/metrics")
                .request()
                .get(String.class);

        // health should not work
        assertThrows(NotFoundException.class, () -> baseTarget.path("/health")
                .request()
                .get(String.class));
    }
}