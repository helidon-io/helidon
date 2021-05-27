/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.jaegertest;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import io.helidon.microprofile.server.Server;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JaegerTest {
    private static Server server;
    private static Client client;
    private static WebTarget target;

    @BeforeAll
    static void initClass() {
        server = Server.create(TestApplication.class).start();
        client = ClientBuilder.newClient();
        target = client.target("http://localhost:" + server.port() + "/test");
    }

    @AfterAll
    static void destroyClass() {
        if (null != server) {
            server.stop();
        }
        if (null != client) {
            client.close();
        }
    }

    @Test
    void testTracing() {
        String response = target
                .request()
                .get(String.class);

        assertThat(response, is("Test Message"));
    }
}
