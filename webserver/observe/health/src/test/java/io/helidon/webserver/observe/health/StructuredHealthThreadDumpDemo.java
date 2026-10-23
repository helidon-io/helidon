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

package io.helidon.webserver.observe.health;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.management.ObjectName;

import io.helidon.health.HealthCheckResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;

/**
 * Runs a real health request with two gated checks so a thread dump can be taken.
 */
public final class StructuredHealthThreadDumpDemo {
    private StructuredHealthThreadDumpDemo() {
    }

    /**
     * Start the demonstration; capture a JSON dump automatically or wait for a release-marker file.
     *
     * @param args one initially absent release-marker path, or {@code --dump-file} followed by an absolute output path
     * @throws Exception if setup, coordination, or the health request fails
     */
    public static void main(String[] args) throws Exception {
        boolean captureDump = args.length == 2 && args[0].equals("--dump-file");
        if (args.length != 1 && !captureDump) {
            throw new IllegalArgumentException("Supply a release-marker path or --dump-file <absolute-output.json>");
        }
        Path outputPath = Path.of(captureDump ? args[1] : args[0]);
        if (captureDump && !outputPath.isAbsolute()) {
            throw new IllegalArgumentException("Thread dump output path must be absolute: " + outputPath);
        }
        if (Files.exists(outputPath)) {
            throw new IllegalArgumentException("Output or release-marker file already exists: " + outputPath);
        }
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var database = StructuredHealthTest.check("database", () -> {
            entered.countDown();
            awaitRelease(release);
            return HealthCheckResponse.builder().status(HealthCheckResponse.Status.UP).build();
        });
        var cache = StructuredHealthTest.check("cache", () -> {
            entered.countDown();
            awaitRelease(release);
            return HealthCheckResponse.builder().status(HealthCheckResponse.Status.UP).build();
        });
        WebServer server = StructuredHealthTest.server(List.of(database, cache)).build().start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var request = executor.submit(() -> {
                Thread.currentThread().setName("health-demo-client");
                try (var response = Http1Client.builder().readTimeout(Duration.ofSeconds(130)).build()
                        .get("http://localhost:" + server.port() + "/observe/health").request()) {
                    if (response.status().code() != 200) {
                        throw new IllegalStateException("Health response: " + response.status());
                    }
                    return response.as(String.class);
                }
            });
            try {
                if (!entered.await(20, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Both checks did not enter within 20 seconds");
                }
                System.out.println("READY PID=" + ProcessHandle.current().pid() + " CHECKS=database,cache");
                System.out.flush();
                if (captureDump) {
                    // MXBean enums are represented as strings. Use JMX to keep this demo's dependency on java.management.
                    ManagementFactory.getPlatformMBeanServer().invoke(new ObjectName("com.sun.management:type=HotSpotDiagnostic"),
                                                                     "dumpThreads",
                                                                     new Object[] {outputPath.toString(), "JSON"},
                                                                     new String[] {"java.lang.String", "java.lang.String"});
                    System.out.println("DUMP " + outputPath);
                } else {
                    long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
                    while (!Files.exists(outputPath)) {
                        if (System.nanoTime() >= deadline) {
                            throw new IllegalStateException("Release-marker file not created within 90 seconds");
                        }
                        Thread.sleep(Duration.ofMillis(100));
                    }
                }
            } finally {
                release.countDown();
            }
            System.out.println("RESPONSE " + request.get(10, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            server.stop();
        }
    }

    private static void awaitRelease(CountDownLatch release) throws InterruptedException {
        if (!release.await(120, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Demo watchdog released stalled health check");
        }
    }
}
