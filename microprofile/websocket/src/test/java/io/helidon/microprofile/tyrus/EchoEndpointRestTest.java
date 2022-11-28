/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tyrus;

import java.net.URI;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static jakarta.ws.rs.client.Entity.text;
import static org.hamcrest.CoreMatchers.is;

@HelidonTest
@AddBean(EchoEndpoint.class)
@AddBean(EchoEndpointRestTest.EchoResource.class)
class EchoEndpointRestTest extends EchoEndpointBaseTest {

    @Test
    public void testEchoRest() throws Exception {
        // Test REST endpoint
        String restUri = "http://localhost:" + serverPort() + "/echoRest";
        Client restClient = ClientBuilder.newClient();
        String echo = restClient.target(restUri)
                .request("text/plain")
                .post(text("echo"), String.class);
        MatcherAssert.assertThat(echo, is("echo"));

        // Test WS endpoint
        String echoUri = "ws://localhost:" + serverPort() + "/echoAnnot";
        EchoClient echoClient = new EchoClient(URI.create(echoUri));
        echoClient.echo("hi", "how are you?");
    }

    @Path("/echoRest")
    public static class EchoResource {

        @POST
        @Produces("text/plain")
        @Consumes("text/plain")
        public String echo(String s) {
            return s;
        }
    }
}
