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

package io.helidon.webserver;


import io.helidon.common.http.Http;
import io.helidon.webserver.utils.SocketHttpClient;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

class UriParamsServerTest extends BaseServerTest {

    @BeforeAll
    static void startServer() throws Exception {
        Routing routing = Routing.builder()
                .get("/shop",
                        (req, res) -> res.send("shop"))
                .get("/admin",
                        (req, res) -> res.send("admin"))
                .build();
        startServer(0, routing);
    }

    @Test
    void testEndpoints() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/shop",
                Http.Method.GET, null, null, webServer());
        assertThat(s, containsString("shop"));
        s = SocketHttpClient.sendAndReceive("/admin",
                Http.Method.GET, null, null, webServer());
        assertThat(s, containsString("admin"));
    }

    @Test
    void testEndpointsWithParams() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/shop;a=b",
                Http.Method.GET, null, null, webServer());
        assertThat(s, containsString("shop"));
        s = SocketHttpClient.sendAndReceive("/admin;a=b",
                Http.Method.GET, null, null, webServer());
        assertThat(s, containsString("admin"));
    }
}
