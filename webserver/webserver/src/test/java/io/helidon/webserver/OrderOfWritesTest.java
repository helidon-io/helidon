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
 */
package io.helidon.webserver;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Multi;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class OrderOfWritesTest {

    private static final Duration TIME_OUT = Duration.ofSeconds(5);

    @Test
    void threadMixUp() throws Exception {
        final String expected = "_1_2_3";
        final byte[] underscore = "_".getBytes(StandardCharsets.UTF_8);
        final ExecutorService exec = Executors.newSingleThreadExecutor();

        WebServer server = null;

        try {
            server = WebServer.builder()
                    .routing(r -> r
                            .get((req, res) -> res.send(Multi.just("1", "2", "3")
                                    .map(String::valueOf)
                                    .map(String::getBytes)
                                    // Condition: initiate write from different thread than event loop
                                    .observeOn(exec)
                                    // Condition: mix-up writes with flushes
                                    .flatMap(number -> Multi.just(
                                            DataChunk.create(false, ByteBuffer.wrap(underscore)),
                                            DataChunk.create(true, ByteBuffer.wrap(number))
                                    ))
                            )))
                    .host("localhost")
                    .port(0)
                    .build()
                    .start()
                    .await(TIME_OUT);

            WebClient client = WebClient.builder()
                    .baseUri("http://localhost:" + server.port())
                    .connectTimeout(TIME_OUT.toMillis(), TimeUnit.MILLISECONDS)
                    .build();

            for (AtomicInteger i = new AtomicInteger(); i.get() < 5000; i.getAndIncrement()) {
                WebClientResponse response = null;
                try {
                    response = client.get()
                            .request()
                            .await(TIME_OUT);
                    Assertions.assertEquals(Http.ResponseStatus.Family.SUCCESSFUL, response.status().family());
                    String content = response.content().as(String.class).await(TIME_OUT);
                    Assertions.assertEquals(expected, content, "Failed at " + i.get() + " " + content);
                } finally {
                    if (response != null) {
                        response.close();
                    }
                }
            }
        } finally {
            exec.shutdownNow();
            if (server != null) {
                server.shutdown().await(TIME_OUT);
            }
            assertThat(exec.awaitTermination(TIME_OUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
        }
    }
}
