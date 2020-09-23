/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.tests.functional.mpcompression;

import java.io.IOException;
import java.util.logging.LogManager;

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

class MpCompressionTest {
    private static Client client;
    private static Server server;
    private static WebTarget baseTarget;

    @BeforeAll
    static void initClass() throws IOException {
        LogManager.getLogManager().readConfiguration(MpCompressionTest.class.getResourceAsStream("/logging.properties"));
        Main.main(new String[0]);
        server = Main.server();
        client = ClientBuilder.newClient();
        baseTarget = client.target("http://localhost:" + server.port());
    }

    @AfterAll
    static void destroyClass() {
        server.stop();
    }

    @Test
    void testGzip() throws Exception {
        WebTarget target = baseTarget.path("/compressed");
        Response response = target.request().header("accept-encoding", "gzip").get();
        assertOk(response, "Hello World");
    }

    @Test
    void testDeflate() throws Exception {
        WebTarget target = baseTarget.path("/compressed");
        Response response = target.request().header("accept-encoding", "deflate").get();
        assertOk(response, "Hello World");
    }

    private void assertOk(Response response, String expectedMessage) {
        assertThat(response.getStatus(), is(200));
        assertThat(response.readEntity(String.class), is(expectedMessage));
    }
}
