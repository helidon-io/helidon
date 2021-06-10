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

import java.util.concurrent.ExecutionException;

import io.helidon.webclient.WebClientException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

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
        String s = webClient().get()
                .path("shop")
                .request(String.class)
                .get();
        assertThat(s, is("shop"));
        s = webClient().get()
                .path("admin")
                .request(String.class)
                .get();
        assertThat(s, is("admin"));
    }

    @Test
    void testEndpointsWithParams() throws Exception {
        String s = webClient().get()
                .path("shop;a=b")
                .request(String.class)
                .get();
        assertThat(s, is("shop"));
        s = webClient().get()
                .path("admin;a=b")
                .request(String.class)
                .get();
        assertThat(s, is("admin"));
    }
}
