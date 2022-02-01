/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.webserver;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Multi;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReqEntityAnalyzedTest {
    private static final Duration TIME_OUT = Duration.ofSeconds(5);
    static final ExecutorService exec = Executors.newFixedThreadPool(3);
    private static WebServer server;
    private static WebClient webClient;

    @BeforeAll
    static void beforeAll() {
        server = WebServer.builder()
                .routing(Routing.builder()
                        .register("/test", rules -> rules.put((req, res) -> {
                            req.content()
                                    .observeOn(exec)
                                    .forEach(DataChunk::release);
                            res.send(
                                    Multi.range(0, 3)
                                            .observeOn(exec)
                                            .map(i -> "Server:" + i)
                                            .map(String::getBytes)
                                            .map(DataChunk::create)
                            );
                        }))
                        .build())
                .build();
        server.start().await(TIME_OUT);

        webClient = WebClient.builder()
                .keepAlive(true)
                .baseUri("http://localhost:" + server.port())
                .build();
    }

    @RepeatedTest(100)
    void webClient() {
        testCall(webClient, Multi.range(0, 3)
                .map(integer -> "Client:" + integer)
                .map(String::getBytes)
                .map(bytes -> DataChunk.create(true, ByteBuffer.wrap(bytes))));
    }

    private void testCall(WebClient webClient, Multi<DataChunk> payload) {
        WebClientResponse webClientResponse = null;
        try {
            webClientResponse = webClient
                    .put()
                    .path("/test")
                    .submit(payload)
                    .onError(Throwable::printStackTrace)
                    .await(TIME_OUT);

            assertThat(webClientResponse.status(), is(Http.Status.OK_200));

            String response = webClientResponse.content()
                    .as(String.class)
                    .await(TIME_OUT);

            assertThat(response, is("Server:0Server:1Server:2"));
        } catch (Exception e) {
            // this is expected - we do not support parallel read of entity
            if (e.getMessage().contains("reset by the host")) {
                return;
            }
            throw e;
        } finally {
            if (webClientResponse != null) {
                webClientResponse.close();
            }
        }
    }

    @AfterAll
    static void afterAll() throws InterruptedException {
        server.shutdown();
        exec.shutdownNow();
        assertTrue(exec.awaitTermination(TIME_OUT.toMillis(), TimeUnit.MILLISECONDS));
    }

}