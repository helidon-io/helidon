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

package io.helidon.webserver.tests;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.Method;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test support for HTTP 1.1 pipelining.
 */
@ServerTest
class HttpPipelineTest {
    private static final System.Logger LOGGER = System.getLogger(HttpPipelineTest.class.getName());
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private final SocketHttpClient socketHttpClient;

    HttpPipelineTest(SocketHttpClient socketHttpClient) {
        this.socketHttpClient = socketHttpClient;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.put("/", (req, res) -> {
                    COUNTER.set(0);

                    String content = req.content().as(String.class);
                    log("put server: " + content);
                    res.send(content);
                })
                .get("/", (req, res) -> {
                    log("get server");
                    int n = COUNTER.getAndIncrement();
                    int delay = (n % 2 == 0) ? 100 : 0;    // alternate delay 100 millis and no delay
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        return;
                    }

                    res.send("Response " + n + "\n");
                });
    }

    /**
     * Pipelines request_0 and request_1 and makes sure responses are returned in the
     * correct order. Note that the server will delay the response for request_0 to
     * make sure they are properly synchronized.
     */
    //@RepeatedTest(10)
    void testPipelining() {
        socketHttpClient.request(Method.PUT, "/");        // reset server
        socketHttpClient.request(Method.GET, null);        // request_0
        socketHttpClient.request(Method.GET, null);        // request_1
        log("put client");
        String reset = socketHttpClient.receive();
        assertThat(reset, notNullValue());
        log("request0 client");
        String request_0 = socketHttpClient.receive();
        assertThat(request_0, containsString("Response 0"));
        log("request1 client");
        String request_1 = socketHttpClient.receive();
        assertThat(request_1, containsString("Response 1"));
    }

    private static void log(String prefix) {
        LOGGER.log(System.Logger.Level.INFO, () -> prefix + " " + Thread.currentThread());
    }
}
