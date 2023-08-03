/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.webserver.resourcelimit;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import io.helidon.common.http.Http;
import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webclient.api.ClientResponseTyped;
import io.helidon.nima.webclient.api.WebClient;
import io.helidon.nima.webserver.WebServerConfig;
import io.helidon.nima.webserver.http.HttpRules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@ServerTest
class MaxConcurrentRequestsTest {
    private static volatile CountDownLatch cdl;
    private final SocketHttpClient client;
    private final WebClient webClient;

    MaxConcurrentRequestsTest(SocketHttpClient client, WebClient webClient) {
        this.client = client;
        this.webClient = webClient;
    }

    @SetUpServer
    static void serverSetup(WebServerConfig.Builder builder) {
        builder.maxConcurrentRequests(1);
    }

    @SetUpRoute
    static void routeSetup(HttpRules rules) {
        rules.get("/greet", (req, res) -> {
            cdl.await();
            res.send("hello");
        });
    }

    @BeforeEach
    void beforeEach() {
        cdl = new CountDownLatch(1);
    }

    @Test
    void testConcurrentRequests() {
        client.request(Http.Method.GET, "/greet", null, List.of("Connection: keep-alive"));
        // now that we have request in progress, any other should fail
        ClientResponseTyped<String> response = webClient.get("/greet")
                .request(String.class);
        assertThat(response.status(), is(Http.Status.SERVICE_UNAVAILABLE_503));
        cdl.countDown();
        assertThat(client.receive(), containsString("200 OK"));
    }
}
