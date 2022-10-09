/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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

package io.helidon.service.employee;

import java.util.concurrent.TimeUnit;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class MainTest {

    private static WebServer webServer;
    private static WebClient webClient;

    @BeforeAll
    public static void startTheServer() {
        webServer = Main.startServer().await();

        webClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .addHeader(Http.Header.ACCEPT, MediaType.APPLICATION_JSON.toString())
                .build();
    }

    @AfterAll
    public static void stopServer() {
        if (webServer != null) {
            webServer.shutdown()
                    .await(10, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testHelloWorld() {
        webClient.get()
                .path("/employees")
                .request()
                .thenAccept(response -> {
                    response.close();
                    assertThat("HTTP response2", response.status(), is(Http.Status.OK_200));
                })
                .await();

        webClient.get()
                .path("/health")
                .request()
                .thenAccept(response -> {
                    response.close();
                    assertThat("HTTP response2", response.status(), is(Http.Status.OK_200));
                })
                .await();

        webClient.get()
                .path("/metrics")
                .request()
                .thenAccept(response -> {
                    response.close();
                    assertThat("HTTP response2", response.status(), is(Http.Status.OK_200));
                })
                .await();
    }

}
