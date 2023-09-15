/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

import io.helidon.microprofile.testing.junit5.AddBean;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static jakarta.ws.rs.client.Entity.text;
import static org.hamcrest.CoreMatchers.is;

/**
 * A test that mixes Websocket endpoints and REST resources in the same
 * application.
 */
@AddBean(WebSocketRestEndpointTest.EchoResource.class)
public class WebSocketRestEndpointTest extends WebSocketBaseTest {

    @Test
    public void testEchoRest() {
        String echo = target()
                .path("echoRest")
                .request("text/plain")
                .post(text("echo"), String.class);
        MatcherAssert.assertThat(echo, is("echo"));
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
