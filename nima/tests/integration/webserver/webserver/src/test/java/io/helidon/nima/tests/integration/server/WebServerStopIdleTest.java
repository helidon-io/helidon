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

package io.helidon.nima.tests.integration.server;

import io.helidon.common.http.Http;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRouting;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class WebServerStopIdleTest {

    private final Http1Client client;
    private final WebServer webServer;

    WebServerStopIdleTest(Http1Client client, WebServer webServer) {
        this.client = client;
        this.webServer = webServer;
    }

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        router.get("ok", (req, res) -> res.send("ok"));
    }

    //@Timeout(value = 400, unit = TimeUnit.MILLISECONDS)
    @Test
    void stop_whenIdle_expect_timelyStop() {
        var response = client.get("/ok").request();
        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(response.entity().as(String.class), is("ok"));
        long startMillis = System.currentTimeMillis();
        System.out.println("webServer.stop() requested");
        webServer.stop();
        System.out.println("webServer.stop() completed");
        long stopExecutionTimeInMillis = System.currentTimeMillis() - startMillis;
        System.out.println(stopExecutionTimeInMillis);
        assertThat(stopExecutionTimeInMillis < 400, is(true));
    }

}
