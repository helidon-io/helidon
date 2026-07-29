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
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.StuckThreadDetectionFeature;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class StuckThreadDetectionJmhTest {
    private static final String SERVER_HOST = "127.0.0.1";
    private static final Header CONTENT_TYPE = HeaderValues.createCached(HeaderNames.CONTENT_TYPE,
                                                                         "text/plain; charset=UTF-8");
    private static final Header CONTENT_LENGTH = HeaderValues.createCached(HeaderNames.CONTENT_LENGTH, "2");
    private static final Header SERVER = HeaderValues.createCached(HeaderNames.SERVER, "Helidon");
    private static final byte[] RESPONSE_BYTES = "OK".getBytes(StandardCharsets.UTF_8);

    private WebServer disabledServer;
    private WebServer enabledServer;
    private HttpClient httpClient;
    private HttpRequest disabledRequest;
    private HttpRequest enabledRequest;

    @Setup
    public void setup() {
        LogConfig.configureRuntime();

        disabledServer = serverBuilder()
                .build()
                .start();
        enabledServer = serverBuilder()
                .addFeature(StuckThreadDetectionFeature.create(builder -> builder
                        .threshold(Duration.ofDays(1))
                        .checkPeriod(Duration.ofDays(1))))
                .build()
                .start();

        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        disabledRequest = request(disabledServer.port());
        enabledRequest = request(enabledServer.port());
    }

    @TearDown
    public void tearDown() {
        disabledServer.stop();
        enabledServer.stop();
    }

    @Benchmark
    public void disabled(Blackhole bh) throws IOException, InterruptedException {
        bh.consume(send(disabledRequest));
    }

    @Benchmark
    public void enabled(Blackhole bh) throws IOException, InterruptedException {
        bh.consume(send(enabledRequest));
    }

    private HttpResponse<byte[]> send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("Unexpected status: " + response.statusCode());
        }
        return response;
    }

    private static HttpRequest request(int serverPort) {
        return HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://" + SERVER_HOST + ":" + serverPort + "/direct/42"))
                .build();
    }

    private static WebServerConfig.Builder serverBuilder() {
        return WebServer.builder()
                .connectionOptions(builder -> builder
                        .readTimeout(Duration.ZERO)
                        .connectTimeout(Duration.ZERO)
                        .socketSendBufferSize(64000)
                        .socketReceiveBufferSize(64000))
                .writeQueueLength(4000)
                .host(SERVER_HOST)
                .backlog(8192)
                .routing(router -> router.get("/direct/{id}", new OkHandler()));
    }

    private static class OkHandler implements Handler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            res.header(CONTENT_LENGTH);
            res.header(CONTENT_TYPE);
            res.header(SERVER);
            res.send(RESPONSE_BYTES);
        }
    }
}
