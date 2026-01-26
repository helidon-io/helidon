/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tests.server;

import io.helidon.common.Weight;
import io.helidon.microprofile.server.RoutingPath;
import io.helidon.microprofile.testing.AddBean;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddBean(HelidonRoutingAfterJerseyTest.SeService.class)
public class HelidonRoutingAfterJerseyTest {
    private static final String HELLO = "Hello!";

    @Test
    public void testRouting(WebTarget target) throws Exception {
        Response response = target.path("/se")
                .request(MediaType.TEXT_PLAIN)
                .get();

        assertThat(response.getStatus(), is(200));
        assertThat(response.readEntity(String.class), is(HELLO));
        // must be content-length, not chunked, as we are expecting to buffer smaller entities
        assertThat(response.getHeaderString("Content-Length"), is(String.valueOf(HELLO.length())));
    }

    @RoutingPath("/se")
    @Weight(100)
    public static class SeService implements HttpService {

        @Override
        public void routing(HttpRules rules) {
            rules.get("/", this::hello);
        }

        private void hello(ServerRequest req, ServerResponse res) {
            res.send(HELLO);
        }
    }
}
