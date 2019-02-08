/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.tests.integration.server3;

import java.util.concurrent.atomic.AtomicLong;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.server.Server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link io.helidon.microprofile.tests.integration.server3.TheApplication3}.
 */
class TheApplication3Test {
    private static final AtomicLong INVOCATIONS = new AtomicLong();

    private static Server server;
    private static Client client;
    private static WebTarget rootTarget;

    @BeforeAll
    static void initClass() {
        INVOCATIONS.set(0);

        server = Server
                .builder()
                .port(0)
                .build()
                .start();

        client = ClientBuilder.newClient();

        rootTarget = client.target("http://localhost:" + server.port());
    }

    @Test
    void testApplicationEndpoints() {
        WebTarget appTarget = rootTarget.path("/application");

        testEndpoint(appTarget, "first", 200);
        testEndpoint(appTarget, "second", 200);
    }

    private void testEndpoint(WebTarget target, String path, int expectedStatus) {
        Response response = target.path(path)
                .request()
                .get();

        assertThat("endpoint " + target.getUri() + "/" + path, response.getStatus(), is(expectedStatus));

        if (expectedStatus == 200) {
            INVOCATIONS.incrementAndGet();
            assertThat(response.readEntity(String.class), is(path));
        }
    }

    @Test
    void testResourceEndpoints() {
        testEndpoint(rootTarget, "first", 404);
        testEndpoint(rootTarget, "second", 404);
    }

    @AfterAll
    static void destroyClass() {
        server.stop();
        client.close();

        assertThat(TheProvider3.count(), is(INVOCATIONS.get()));
    }
}