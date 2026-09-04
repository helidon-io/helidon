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

package io.helidon.webclient.http3;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class UdpImpairmentProxy implements AutoCloseable {
    private static final Duration PROCESS_PHASE_TIMEOUT = Duration.ofSeconds(10);
    private static final List<String> METRIC_NAMES = List.of(
            "seed",
            "receivedClient",
            "receivedServer",
            "forwardedClient",
            "forwardedServer",
            "droppedLoss",
            "droppedOverflow",
            "droppedNoClient",
            "reorderDelayed",
            "actuallyReordered",
            "stallTriggered",
            "stalledDatagrams",
            "maxQueueDepth",
            "maxQueueBytes",
            "queuedDatagrams",
            "queuedBytes");

    private final Process process;
    private final BufferedReader output;
    private final BufferedWriter input;
    private int port;
    private boolean stallObserved;
    private boolean stopped;

    private UdpImpairmentProxy(Process process) {
        this.process = process;
        this.output = process.inputReader(StandardCharsets.UTF_8);
        this.input = process.outputWriter(StandardCharsets.UTF_8);
    }

    static UdpImpairmentProxy start(Http3BenchmarkEnvironment environment,
                                    Config config,
                                    long seed) throws Exception {
        String address = environment.address().getHostAddress();
        Process process = new ProcessBuilder(
                ForkedTestProcess.javaExecutable(),
                "-cp",
                ForkedTestProcess.classPath(),
                UdpImpairmentProxyMain.class.getName(),
                address,
                address,
                Integer.toString(environment.port()),
                Long.toString(seed),
                Long.toString(config.delayMillis),
                Long.toString(config.jitterMillis),
                Integer.toString(config.lossPermille),
                Integer.toString(config.reorderPermille),
                Long.toString(config.reorderDelayMillis),
                Long.toString(config.stallAfterDatagrams),
                Long.toString(config.stallMillis),
                Integer.toString(config.maxQueuedDatagrams))
                .redirectErrorStream(true)
                .start();
        UdpImpairmentProxy proxy = new UdpImpairmentProxy(process);
        try {
            String ready = proxy.readLine("readiness");
            if (ready == null || !ready.startsWith("READY ")) {
                throw new IllegalStateException("UDP impairment proxy failed before readiness: " + ready);
            }
            proxy.port = Integer.parseInt(ready.substring("READY ".length()));
            return proxy;
        } catch (Exception | Error failure) {
            try {
                proxy.close();
            } catch (Exception | Error cleanupFailure) {
                if (cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    static List<String> metricNames() {
        return METRIC_NAMES;
    }

    int port() {
        return port;
    }

    StallArm armClientStall(long afterDatagrams, long stallMillis) throws Exception {
        send("ARM_CLIENT_STALL " + afterDatagrams + " " + stallMillis);
        String event = readLine("stall arming");
        if (event == null || !event.startsWith("ARMED clientReceived=")) {
            throw new IllegalStateException("UDP impairment proxy did not arm the requested stall: " + event);
        }
        String[] fields = event.split(" ");
        if (fields.length != 3
                || !"ARMED".equals(fields[0])
                || !fields[1].startsWith("clientReceived=")
                || !fields[2].startsWith("serverReceived=")) {
            throw new IllegalStateException("UDP impairment proxy reported malformed stall baselines: " + event);
        }
        return new StallArm(
                Long.parseLong(fields[1].substring("clientReceived=".length())),
                Long.parseLong(fields[2].substring("serverReceived=".length())));
    }

    void awaitClientStall(StallArm arm) throws Exception {
        if (stallObserved) {
            return;
        }
        String event = readLine("stall");
        if (event == null || !event.startsWith("STALL direction=client clientReceived=")) {
            throw new IllegalStateException("UDP impairment proxy did not report the configured stall: " + event);
        }
        String[] fields = event.split(" ");
        if (fields.length != 5
                || !"STALL".equals(fields[0])
                || !"direction=client".equals(fields[1])
                || !fields[2].startsWith("clientReceived=")
                || !fields[3].startsWith("serverReceived=")
                || !fields[4].startsWith("received=")) {
            throw new IllegalStateException("UDP impairment proxy reported a malformed client stall: " + event);
        }
        long clientReceived = Long.parseLong(fields[2].substring("clientReceived=".length()));
        long serverReceived = Long.parseLong(fields[3].substring("serverReceived=".length()));
        if (clientReceived <= arm.clientReceived) {
            throw new IllegalStateException("UDP impairment proxy stalled before a post-arm client datagram: " + event);
        }
        if (serverReceived != arm.serverReceived) {
            throw new IllegalStateException("Server traffic arrived between active-request arming and stall: " + event);
        }
        stallObserved = true;
    }

    Metrics snapshotMetrics() throws Exception {
        send("SNAPSHOT");
        return readMetrics("snapshot");
    }

    Metrics stopAndMetrics() throws Exception {
        send("STOP");
        Metrics metrics = readMetrics("final");
        if (!process.waitFor(PROCESS_PHASE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("UDP impairment proxy did not terminate after STOP");
        }
        stopped = true;
        if (process.exitValue() != 0) {
            throw new IllegalStateException("UDP impairment proxy exited with " + process.exitValue());
        }
        return metrics;
    }

    private void send(String command) throws IOException {
        input.write(command);
        input.newLine();
        input.flush();
    }

    private Metrics readMetrics(String phase) throws Exception {
        String metricsLine;
        while (true) {
            metricsLine = readLine(phase + " metrics");
            if (metricsLine != null && metricsLine.startsWith("STALL ")) {
                stallObserved = true;
                continue;
            }
            break;
        }
        if (metricsLine == null || !metricsLine.startsWith("METRICS ")) {
            throw new IllegalStateException("UDP impairment proxy did not report " + phase + " metrics: " + metricsLine);
        }
        Map<String, Long> metrics = new LinkedHashMap<>();
        for (String token : metricsLine.substring("METRICS ".length()).split(" ")) {
            int equals = token.indexOf('=');
            if (equals < 1 || equals == token.length() - 1) {
                throw new IllegalStateException("Malformed UDP impairment proxy metric: " + token);
            }
            String name = token.substring(0, equals);
            if (metrics.put(name, Long.parseLong(token.substring(equals + 1))) != null) {
                throw new IllegalStateException("Duplicate UDP impairment proxy metric: " + name);
            }
        }
        for (String metricName : METRIC_NAMES) {
            if (!metrics.containsKey(metricName)) {
                throw new IllegalStateException("Missing UDP impairment proxy metric: " + metricName);
            }
        }
        if (metrics.size() != METRIC_NAMES.size()) {
            throw new IllegalStateException("Unexpected UDP impairment proxy metric inventory: " + metrics.keySet());
        }
        return new Metrics(Collections.unmodifiableMap(metrics));
    }

    private String readLine(String phase) throws Exception {
        CompletableFuture<String> lineFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return output.readLine();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        try {
            return lineFuture.get(PROCESS_PHASE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            process.destroyForcibly();
            throw new TimeoutException("Timed out waiting for UDP impairment proxy " + phase);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("UDP impairment proxy " + phase + " failed", cause);
        } catch (InterruptedException e) {
            lineFuture.cancel(true);
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    @Override
    public void close() throws Exception {
        Throwable failure = null;
        boolean interrupted = Thread.interrupted();
        if (!stopped && process.isAlive()) {
            try {
                send("STOP");
            } catch (Exception | Error cleanupFailure) {
                failure = cleanupFailure;
            }
            long gracefulDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (process.isAlive() && System.nanoTime() < gracefulDeadline) {
                try {
                    process.waitFor(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException cleanupFailure) {
                    interrupted = true;
                    Thread.interrupted();
                }
            }
            if (process.isAlive()) {
                process.destroyForcibly();
                long forceDeadline = System.nanoTime() + PROCESS_PHASE_TIMEOUT.toNanos();
                while (process.isAlive() && System.nanoTime() < forceDeadline) {
                    try {
                        process.waitFor(100, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException cleanupFailure) {
                        interrupted = true;
                        Thread.interrupted();
                    }
                }
            }
            if (process.isAlive()) {
                var terminationFailure = new IllegalStateException("Could not terminate UDP impairment proxy");
                if (failure == null) {
                    failure = terminationFailure;
                } else {
                    failure.addSuppressed(terminationFailure);
                }
            }
        }
        try {
            input.close();
        } catch (Exception | Error cleanupFailure) {
            if (failure == null) {
                failure = cleanupFailure;
            } else if (cleanupFailure != failure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
        try {
            output.close();
        } catch (Exception | Error cleanupFailure) {
            if (failure == null) {
                failure = cleanupFailure;
            } else if (cleanupFailure != failure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof Exception exception) {
            throw exception;
        }
    }

    record Config(long delayMillis,
                  long jitterMillis,
                  int lossPermille,
                  int reorderPermille,
                  long reorderDelayMillis,
                  long stallAfterDatagrams,
                  long stallMillis,
                  int maxQueuedDatagrams) {
    }

    record StallArm(long clientReceived, long serverReceived) {
    }

    record Metrics(Map<String, Long> values) {
        long value(String name) {
            Long value = values.get(name);
            if (value == null) {
                throw new IllegalArgumentException("Unknown UDP impairment proxy metric: " + name);
            }
            return value;
        }
    }
}
