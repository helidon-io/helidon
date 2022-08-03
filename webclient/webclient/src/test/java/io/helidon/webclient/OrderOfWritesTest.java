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
package io.helidon.webclient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.helidon.common.LogConfig;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;

public class OrderOfWritesTest {
    private static final Duration TIME_OUT = Duration.ofSeconds(5);

    @Test
    void threadMixUp() throws IOException, InterruptedException {
        LogConfig.configureRuntime();
        ExecutorService exec = null;
        HttpServer server = null;
        CompletableFuture<String> resultFuture = new CompletableFuture<>();
        try {
            exec = Executors.newFixedThreadPool(3);
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", res -> {
                resultFuture.complete(new String(res.getRequestBody().readAllBytes()));
                res.sendResponseHeaders(200, 0);
            });
            server.setExecutor(exec);
            server.start();

            WebClient.builder()
                    .baseUri("http://localhost:" + server.getAddress().getPort())
                    .connectTimeout(TIME_OUT.toMillis(), TimeUnit.MILLISECONDS)
                    .build()
                    .post()
                    .submit(Multi.just("1", "2", "3")
                            .map(String::valueOf)
                            .map(String::getBytes)
                            // Condition: initiate write from different thread than event loop
                            .observeOn(exec)
                            .map(n -> DataChunk.create(ByteBuffer.wrap(n)))
                            // Condition: mix-up upstream threads, flatMap has a prefetch with cache
                            // eg. some onNexts are going to be served by requesting thread
                            .flatMap(Single::just, 2, true, 2)
                    )
                    .await(TIME_OUT);

            String result = Single.create(resultFuture).await(TIME_OUT);

            assertThat(result, Matchers.equalTo("123"));
        } finally {
            if (server != null) {
                server.stop(0);
            }
            if (exec != null) {
                exec.shutdownNow();
                assertTrue(exec.awaitTermination(TIME_OUT.toMillis(), TimeUnit.MILLISECONDS));
            }
        }
    }
}
