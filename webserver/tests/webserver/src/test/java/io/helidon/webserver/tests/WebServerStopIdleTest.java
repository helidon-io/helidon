/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;

class WebServerStopIdleTest {

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void stopWhenIdleExpectTimelyStop() {
        stopWhenIdleExpectTimelyStop(500);
    }

    @Test
    @EnabledOnOs(
            value = OS.WINDOWS,
            disabledReason = "Slow pipeline runner make it stop a few millisecond later")
    void stopWhenIdleExpectTimelyStopWindows() {
        stopWhenIdleExpectTimelyStop(505);
    }

    void stopWhenIdleExpectTimelyStop(int timeout) {
        WebServer webServer = WebServer.builder()
                .routing(router -> router.get("ok", (req, res) -> res.send("ok")))
                .build();
        webServer.start();

        Http1Client client = Http1Client.builder()
                .baseUri("http://localhost:" + webServer.port())
                .build();
        try (var response = client.get("/ok").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(String.class), is("ok"));
        }

        long startMillis = System.currentTimeMillis();
        webServer.stop();
        int stopExecutionTimeInMillis = (int) (System.currentTimeMillis() - startMillis);
        assertThat(stopExecutionTimeInMillis, is(lessThan(timeout)));
    }
}
