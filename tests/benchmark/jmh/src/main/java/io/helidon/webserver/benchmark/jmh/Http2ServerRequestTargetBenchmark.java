/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.benchmark.jmh;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;
import io.helidon.webserver.http2.Http2Upgrader;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Benchmark)
public class Http2ServerRequestTargetBenchmark {
    private static final Duration EXCHANGE_TIMEOUT = Duration.ofSeconds(10);

    @Param({"32", "4000"})
    private int targetLength;

    private WebServer server;
    private HttpClient client;
    private HttpRequest request;

    @Setup
    public void setup() throws IOException, InterruptedException {
        Http2Config http2Config = Http2Config.builder()
                .validatePath(true)
                .build();
        server = WebServer.builder()
                .host("127.0.0.1")
                .port(-1)
                .protocolsDiscoverServices(false)
                .addConnectionSelector(Http2ConnectionSelector.builder()
                                               .http2Config(http2Config)
                                               .build())
                .addConnectionSelector(Http1ConnectionSelector.builder()
                                               .config(Http1Config.create())
                                               .addUpgrader(Http2Upgrader.create(http2Config))
                                               .build())
                .routing(routing -> routing.any((req, res) -> res.send("OK")))
                .build()
                .start();

        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(EXCHANGE_TIMEOUT)
                .build();
        String path = "/" + "a".repeat(targetLength - 1);
        request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.port() + path))
                .timeout(EXCHANGE_TIMEOUT)
                .build();

        boolean setupComplete = false;
        try {
            HttpResponse<Void> response = exchange();
            if (response.version() != HttpClient.Version.HTTP_2) {
                throw new IllegalStateException("Benchmark connection did not negotiate HTTP/2");
            }
            setupComplete = true;
        } finally {
            if (!setupComplete) {
                server.stop();
            }
        }
    }

    @TearDown
    public void tearDown() {
        server.stop();
    }

    @Benchmark
    public HttpResponse<Void> exchange() throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.discarding());
    }
}
