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

package io.helidon.webclient.benchmark.jmh;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

import io.helidon.http.Method;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http1.Http1Route;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;
import io.helidon.webserver.http2.Http2Route;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.ThreadParams;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class WebClientConnectionTargetBenchmark {
    private static final int REQUESTS_PER_INVOCATION = 16;
    private static final int TARGETS_PER_THREAD = 4;
    private static final String PATH = "/target";

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1CacheHit(Http1State state, Blackhole blackhole) {
        for (int i = 0; i < REQUESTS_PER_INVOCATION; i++) {
            try (var response = state.http1Client.get(state.targetUri(0)).request()) {
                response.entity().consume();
                int status = response.status().code();
                if (status != 200) {
                    throw new IllegalStateException("Unexpected HTTP/1.1 status: " + response.status());
                }
                blackhole.consume(status);
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1OneOff(Http1State state, Blackhole blackhole) {
        for (int i = 0; i < REQUESTS_PER_INVOCATION; i++) {
            try (var response = state.http1Client.get(state.targetUri(0)).keepAlive(false).request()) {
                response.entity().consume();
                int status = response.status().code();
                if (status != 200) {
                    throw new IllegalStateException("Unexpected HTTP/1.1 status: " + response.status());
                }
                blackhole.consume(status);
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1DistinctTargetPerThread(Http1State state, ThreadParams threadParams, Blackhole blackhole) {
        int targetIndex = threadParams.getThreadIndex() * TARGETS_PER_THREAD;
        for (int i = 0; i < REQUESTS_PER_INVOCATION; i++) {
            try (var response = state.http1Client.get(state.targetUri(targetIndex)).request()) {
                response.entity().consume();
                int status = response.status().code();
                if (status != 200) {
                    throw new IllegalStateException("Unexpected HTTP/1.1 status: " + response.status());
                }
                blackhole.consume(status);
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1RotatingTargetsPerThread(Http1State state, ThreadParams threadParams, Blackhole blackhole) {
        int firstTargetIndex = threadParams.getThreadIndex() * TARGETS_PER_THREAD;
        for (int i = 0; i < REQUESTS_PER_INVOCATION; i++) {
            int targetIndex = firstTargetIndex + i % TARGETS_PER_THREAD;
            try (var response = state.http1Client.get(state.targetUri(targetIndex)).request()) {
                response.entity().consume();
                int status = response.status().code();
                if (status != 200) {
                    throw new IllegalStateException("Unexpected HTTP/1.1 status: " + response.status());
                }
                blackhole.consume(status);
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http2CacheHit(Http2State state, Blackhole blackhole) {
        for (int i = 0; i < REQUESTS_PER_INVOCATION; i++) {
            try (var response = state.http2Client.get(state.targetUri(0)).request()) {
                response.entity().consume();
                int status = response.status().code();
                if (status != 200) {
                    throw new IllegalStateException("Unexpected HTTP/2 status: " + response.status());
                }
                blackhole.consume(status);
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http2DistinctTargetPerThread(Http2State state, ThreadParams threadParams, Blackhole blackhole) {
        int targetIndex = threadParams.getThreadIndex() * TARGETS_PER_THREAD;
        for (int i = 0; i < REQUESTS_PER_INVOCATION; i++) {
            try (var response = state.http2Client.get(state.targetUri(targetIndex)).request()) {
                response.entity().consume();
                int status = response.status().code();
                if (status != 200) {
                    throw new IllegalStateException("Unexpected HTTP/2 status: " + response.status());
                }
                blackhole.consume(status);
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http2RotatingTargetsPerThread(Http2State state, ThreadParams threadParams, Blackhole blackhole) {
        int firstTargetIndex = threadParams.getThreadIndex() * TARGETS_PER_THREAD;
        for (int i = 0; i < REQUESTS_PER_INVOCATION; i++) {
            int targetIndex = firstTargetIndex + i % TARGETS_PER_THREAD;
            try (var response = state.http2Client.get(state.targetUri(targetIndex)).request()) {
                response.entity().consume();
                int status = response.status().code();
                if (status != 200) {
                    throw new IllegalStateException("Unexpected HTTP/2 status: " + response.status());
                }
                blackhole.consume(status);
            }
        }
    }

    private static String[] targetUris(int targetCount, int serverPort) {
        String[] result = new String[targetCount];
        for (int i = 0; i < targetCount; i++) {
            result[i] = "http://target-" + i + ".example:" + serverPort + PATH;
        }
        return result;
    }

    @State(Scope.Benchmark)
    public static class ServerState {
        private WebServer server;

        @Setup(Level.Trial)
        public void setup() {
            Http2Config http2Config = Http2Config.create();
            server = WebServer.builder()
                    .addProtocol(http2Config)
                    .addConnectionSelector(Http2ConnectionSelector.builder()
                                                   .http2Config(http2Config)
                                                   .build())
                    .host("127.0.0.1")
                    .routing(builder -> builder
                            .route(Http1Route.route(Method.GET, PATH,
                                                    (_, response) -> response.send("ok")))
                            .route(Http2Route.route(Method.GET, PATH,
                                                    (_, response) -> response.send("ok"))))
                    .build()
                    .start();
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            server.stop();
        }
    }

    @State(Scope.Benchmark)
    public static class Http1State {
        @Param({"1", "64"})
        public int prefilledTargetCount;

        private Http1Client http1Client;
        private String[] targetUris;

        @Setup(Level.Trial)
        public void setup(ServerState serverState) {
            http1Client = Http1Client.builder()
                    .shareConnectionCache(false)
                    .servicesDiscoverServices(false)
                    .proxy(Proxy.noProxy())
                    .dnsResolver((_, _) -> InetAddress.ofLiteral("127.0.0.1"))
                    .build();
            targetUris = targetUris(prefilledTargetCount, serverState.server.port());
            for (String targetUri : targetUris) {
                try (var response = http1Client.get(targetUri).request()) {
                    response.entity().consume();
                    if (response.status().code() != 200) {
                        throw new IllegalStateException("Unexpected HTTP/1.1 status: " + response.status());
                    }
                }
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            http1Client.closeResource();
        }

        private String targetUri(int index) {
            return targetUris[index];
        }
    }

    @State(Scope.Benchmark)
    public static class Http2State {
        @Param({"1", "64"})
        public int prefilledTargetCount;

        private Http2Client http2Client;
        private String[] targetUris;

        @Setup(Level.Trial)
        public void setup(ServerState serverState) {
            http2Client = Http2Client.builder()
                    .shareConnectionCache(false)
                    .servicesDiscoverServices(false)
                    .proxy(Proxy.noProxy())
                    .dnsResolver((_, _) -> InetAddress.ofLiteral("127.0.0.1"))
                    .protocolConfig(builder -> builder.priorKnowledge(true))
                    .build();
            targetUris = targetUris(prefilledTargetCount, serverState.server.port());
            for (String targetUri : targetUris) {
                try (var response = http2Client.get(targetUri).request()) {
                    response.entity().consume();
                    if (response.status().code() != 200) {
                        throw new IllegalStateException("Unexpected HTTP/2 status: " + response.status());
                    }
                }
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            http2Client.closeResource();
        }

        private String targetUri(int index) {
            return targetUris[index];
        }
    }
}
