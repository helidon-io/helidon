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

import java.util.concurrent.TimeUnit;

import io.helidon.common.LogConfig;
import io.helidon.common.http.Http;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.http.Http.Status.BAD_REQUEST_400;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ReasonPhraseTest {
    public static final String CUSTOM_ERROR = "Custom error";
    private static final Http.ResponseStatus CUSTOM_BAD_REQUEST = Http.ResponseStatus.create(BAD_REQUEST_400.code(),
                                                                                             CUSTOM_ERROR);
    private static WebServer server;
    private static WebClient client;

    @BeforeAll
    static void createWebServer() {
        LogConfig.configureRuntime();
        server = WebServer.builder()
                .host("localhost")
                .routing(Routing.builder()
                                 .get("/default", ReasonPhraseTest::defaultCode)
                                 .get("/custom", ReasonPhraseTest::customCode))
                .build()
                .start()
                .await(10, TimeUnit.SECONDS);

        client = WebClient.builder()
                .baseUri("http://localhost:" + server.port())
                .build();
    }

    @AfterAll
    static void stopWebServer() {
        if (server != null) {
            server.shutdown()
                    .await(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void testDefaultReasonPhrase() {
        WebClientResponse response = client.get()
                .path("/default")
                .request()
                .await(10, TimeUnit.SECONDS);

        Http.ResponseStatus status = response.status();
        assertThat(status.code(), is(400));
        assertThat(status.reasonPhrase(), is(BAD_REQUEST_400.reasonPhrase()));

        response.close();
    }

    @Test
    void testCustomReasonPhrase() {
        WebClientResponse response = client.get()
                .path("/custom")
                .request()
                .await(10, TimeUnit.SECONDS);

        Http.ResponseStatus status = response.status();
        assertThat(status.code(), is(400));
        assertThat(status.reasonPhrase(), is(CUSTOM_ERROR));

        response.close();
    }

    private static void defaultCode(ServerRequest req, ServerResponse res) {
        res.status(BAD_REQUEST_400)
                .send();
    }

    private static void customCode(ServerRequest req, ServerResponse res) {
        res.status(CUSTOM_BAD_REQUEST)
                .send();
    }
}
