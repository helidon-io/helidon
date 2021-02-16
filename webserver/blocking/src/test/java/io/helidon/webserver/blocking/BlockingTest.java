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

package io.helidon.webserver.blocking;

import java.util.concurrent.TimeUnit;

import io.helidon.common.http.Http;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.HttpException;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class BlockingTest {
    private static WebServer webServer;
    private static WebClient client;

    @BeforeAll
    static void initClass() {
        webServer = WebServer.create(routing())
            .start()
            .await(10, TimeUnit.SECONDS);

        client = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .build();
    }

    @AfterAll
    static void destroyClass() {
        if (webServer != null) {
            webServer.shutdown().await(10, TimeUnit.SECONDS);
        }
        client = null;
    }

    @Test
    void testFullEcho() {
        String entity = "Hello world!";

        String response = client.post()
                .path("/echo/full")
                .submit(entity, String.class)
                .await(10, TimeUnit.SECONDS);

        assertThat(response, is(entity));
    }

    @Test
    void testSimpleEcho() {
        String entity = "Hello world!";

        String response = client.post()
                .path("/echo/simple")
                .submit(entity, String.class)
                .await(10, TimeUnit.SECONDS);

        assertThat(response, is(entity));
    }

    @Test
    void testUpper() {
        String text = "VeryText";

        String response = client.get()
                .path("/upper/" + text)
                .request(String.class)
                .await(10, TimeUnit.SECONDS);

        assertThat(response, is(text.toUpperCase()));
    }

    @Test
    void testFailureStatusCode() {
        WebClientResponse response = client.post()
                .path("/fail")
                .submit("message")
                .await(10, TimeUnit.SECONDS);

        assertThat(response.status(), is(Http.Status.BAD_GATEWAY_502));
        assertThat(response.content().as(String.class).await(10, TimeUnit.SECONDS), is("Failure"));
    }

    private static Routing routing() {
        return Routing.builder()
                .post("/echo/full", (BlockingHandler) BlockingTest::fullEcho)
                .post("/echo/simple", BlockingHandler.create(String.class, BlockingTest::simpleEcho))
                .get("/upper/{text}", BlockingHandler.create(BlockingTest::upperCase))
                .post("/fail", BlockingHandler.create(BlockingTest::fail))
                .build();
    }

    private static String fail() {
        throw new HttpException("Failure", Http.Status.BAD_GATEWAY_502);
    }

    private static void fullEcho(BlockingRequest request, BlockingResponse response) {
        response.send(request.content().as(String.class));
    }

    private static String upperCase(BlockingRequest request) {
        return request.path().param("text").toUpperCase();
    }

    private static String simpleEcho(String entity) {
        return entity;
    }
}
