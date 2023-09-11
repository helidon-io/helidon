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

package io.helidon.webserver.tests.resourcelimit;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@ServerTest
class MaxConcurrentRequestsTest {
    private static volatile CountDownLatch clientCountDown;
    private static volatile CountDownLatch serverCountDown;
    private final SocketHttpClient client;
    private final WebClient webClient;
    private final Http2Client http2Client;

    MaxConcurrentRequestsTest(SocketHttpClient client, WebClient webClient, Http2Client http2Client) {
        this.client = client;
        this.webClient = webClient;
        this.http2Client = http2Client;
    }

    @SetUpServer
    static void serverSetup(WebServerConfig.Builder builder) {
        builder.maxConcurrentRequests(1);
    }

    @SetUpRoute
    static void routeSetup(HttpRules rules) {
        rules.get("/greet", (req, res) -> {
            serverCountDown.countDown();
            clientCountDown.await();
            res.send("hello");
        });
    }

    @BeforeEach
    void beforeEach() {
        serverCountDown = new CountDownLatch(1);
        clientCountDown = new CountDownLatch(1);
    }

    @Test
    void testConcurrentRequests() throws InterruptedException {
        client.request(Method.GET, "/greet", null, List.of("Connection: keep-alive"));
        serverCountDown.await(); // need to make sure we are in the server request
        // now that we have request in progress, any other should fail
        ClientResponseTyped<String> response = webClient.get("/greet")
                .request(String.class);
        assertThat(response.status(), is(Status.SERVICE_UNAVAILABLE_503));
        response = http2Client.get("/greet")
                .request(String.class);
        assertThat(response.status(), is(Status.SERVICE_UNAVAILABLE_503));
        clientCountDown.countDown();
        assertThat(client.receive(), containsString("200 OK"));
    }
}
