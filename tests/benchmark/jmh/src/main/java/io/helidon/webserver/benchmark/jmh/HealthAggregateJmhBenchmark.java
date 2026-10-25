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

import java.time.Duration;

import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.health.HealthObserver;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * End-to-end aggregate health requests with immediate or independently blocking checks.
 * The same public API fixture runs against sequential and concurrent health implementations.
 * Each invocation includes HTTP transport, health aggregation, detailed JSON serialization and full entity consumption.
 * TCP_NODELAY is enabled on both ends of the reused connection with identical settings for both revisions.
 * JSON validation runs during trial setup, outside measurement. The blocking workload uses sleep, so its observed delay
 * also depends on operating-system timer resolution and scheduling; it does not represent CPU-bound checks.
 */
@State(Scope.Thread)
public class HealthAggregateJmhBenchmark {
    @Param({"1", "4", "8"})
    public int checkCount;

    @Param({"0", "1"})
    public int delayMillis;

    private WebServer server;
    private Http1Client client;

    @Setup(Level.Trial)
    public void setup() {
        if (checkCount < 1 || delayMillis < 0) {
            throw new IllegalArgumentException("Positive check count and nonnegative delay required");
        }
        var health = HealthObserver.builder().details(true).useSystemServices(false);
        for (int i = 0; i < checkCount; i++) {
            health.addCheck(new BenchmarkCheck(i, delayMillis));
        }
        try {
            server = WebServer.builder()
                    .host("127.0.0.1")
                    .port(0)
                    .connectionOptions(options -> options.tcpNoDelay(true))
                    .featuresDiscoverServices(false)
                    .addFeature(ObserveFeature.builder()
                                        .observersDiscoverServices(false)
                                        .addObserver(health.build())
                                        .build())
                    .build()
                    .start();
            client = Http1Client.builder()
                    .baseUri("http://127.0.0.1:" + server.port())
                    .socketOptions(options -> options.tcpNoDelay(true))
                    .servicesDiscoverServices(false)
                    .shareConnectionCache(false)
                    .keepAlive(true)
                    .connectTimeout(Duration.ofSeconds(5))
                    .readTimeout(Duration.ofSeconds(5))
                    .build();
            verifyResponse(aggregateHealth());
        } catch (RuntimeException | Error e) {
            tearDown();
            throw e;
        }
    }

    @Benchmark
    public String aggregateHealth() {
        try (var response = client.get("/observe/health").request()) {
            String entity = response.as(String.class);
            if (response.status().code() != 200) {
                throw new IllegalStateException("Unexpected health status: " + response.status() + ", entity: " + entity);
            }
            return entity;
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        try {
            if (client != null) {
                client.closeResource();
            }
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    private void verifyResponse(String entity) {
        JsonObject result = JsonParser.create(entity).readJsonObject();
        var checks = result.arrayValue("checks").orElseThrow();
        if (!result.stringValue("status").orElseThrow().equals("UP") || checks.size() != checkCount) {
            throw new IllegalStateException("Unexpected aggregate health response: " + entity);
        }
        for (int i = 0; i < checkCount; i++) {
            JsonObject check = (JsonObject) checks.values().get(i);
            if (!check.stringValue("name").orElseThrow().equals("check-" + i)
                    || !check.stringValue("status").orElseThrow().equals("UP")
                    || !check.objectValue("data").orElseThrow().stringValue("value").orElseThrow().equals("value-" + i)) {
                throw new IllegalStateException("Unexpected health check at index " + i + ": " + entity);
            }
        }
    }

    private static final class BenchmarkCheck implements HealthCheck {
        private final String name;
        private final String value;
        private final int delayMillis;

        private BenchmarkCheck(int index, int delayMillis) {
            this.name = "check-" + index;
            this.value = "value-" + index;
            this.delayMillis = delayMillis;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public HealthCheckResponse call() {
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Benchmark check interrupted", e);
                }
            }
            return HealthCheckResponse.builder().status(HealthCheckResponse.Status.UP).detail("value", value).build();
        }
    }
}
