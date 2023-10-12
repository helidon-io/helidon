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

import java.time.Duration;
import java.util.List;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.Method;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@ServerTest
@Disabled("Under heavy load, this test does not correctly finish.")
class IdleTimeoutTest {
    private final SocketHttpClient client;

    IdleTimeoutTest(SocketHttpClient client) {
        this.client = client;
    }

    @SetUpServer
    static void serverSetup(WebServerConfig.Builder builder) {
        builder.idleConnectionTimeout(Duration.ofSeconds(2))
                .idleConnectionPeriod(Duration.ofMillis(100));
    }

    @SetUpRoute
    static void routeSetup(HttpRules rules) {
        rules.get("/greet", (req, res) -> res.send("hello"));
    }

    @Test
    void testNoTimeout() throws InterruptedException {
        // the timer is using second precision, we must run for longer than 3 seconds to make sure
        for (int i = 0; i < 20; i++) {
            String response = client.sendAndReceive(Method.GET, "/greet", null, List.of("Connection: keep-alive"));
            assertThat(response, containsString("200 OK"));
            // we sleep for 50, timeout is 250, it should be ok
            Thread.sleep(150);
        }
    }

    @Test
    void testTimeout() throws InterruptedException {
        // the timer is using second precision, we must run for longer than 3 seconds to make sure
        String response = client.sendAndReceive(Method.GET, "/greet", null, List.of("Connection: keep-alive"));
        assertThat(response, containsString("200 OK"));
        // this should be triggered correctly through the timer
        Thread.sleep(3000);
        client.assertConnectionIsClosed();
    }
}
