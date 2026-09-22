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

package io.helidon.webclient.tests.http3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Pattern;

import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.quic.QuicConnection;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webserver.http3.Http3RawTestServer;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class Http3SessionPoolJmh {
    private static final String PREFIX = "http3.session.pool.jmh.";
    private static final String FAST_PATH = "/fast";
    private static final String DELAYED_PATH = "/delayed";
    private static final String CONNECTION_ID_PATH = "/connection-id";
    private static final String HOLD_PATH = "/hold";
    private static final String FULL_POOL_FIRST_HOLD_PATH = "/full-pool-hold-first";
    private static final String FULL_POOL_PREFERRED_HOLD_PATH = "/full-pool-hold-preferred";
    private static final String FULL_POOL_WAKE_PATH = "/full-pool-wake";
    private static final int CONCURRENT_REQUESTS = 2;
    private static final long DELAY_NANOS = TimeUnit.MILLISECONDS.toNanos(1);
    private static final byte[] EMPTY_BODY = new byte[0];

    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            throw new IllegalArgumentException("Expected at most one HTTP/3 session-pool benchmark method expression");
        }
        String methodExpression = args.length == 0
                ? System.getProperty(PREFIX + "include", "oneSessionCacheHit")
                : args[0];
        if (methodExpression.isBlank()) {
            throw new IllegalArgumentException("HTTP/3 session-pool benchmark include must not be blank");
        }
        String include = "^" + Pattern.quote(Http3SessionPoolJmh.class.getName())
                + "\\.(?:" + methodExpression + ")$";
        Pattern.compile(include);

        int forks = Integer.getInteger(PREFIX + "forks", 1);
        int threads = Integer.getInteger(PREFIX + "threads", 1);
        int warmupIterations = Integer.getInteger(PREFIX + "warmupIterations", 3);
        long warmupMillis = Long.getLong(PREFIX + "warmupMillis", 1_000L);
        int measurementIterations = Integer.getInteger(PREFIX + "measurementIterations", 5);
        long measurementMillis = Long.getLong(PREFIX + "measurementMillis", 1_000L);
        if (forks < 0 || threads < 1 || warmupIterations < 0 || warmupMillis < 1
                || measurementIterations < 1 || measurementMillis < 1) {
            throw new IllegalArgumentException("Invalid HTTP/3 session-pool benchmark runner configuration");
        }

        String result = System.getProperty(PREFIX + "result", "./target/http3-session-pool-jmh-result.json");
        if (result.isBlank()) {
            throw new IllegalArgumentException("HTTP/3 session-pool benchmark result must not be blank");
        }
        ChainedOptionsBuilder optionsBuilder = new OptionsBuilder()
                .include(include)
                .forks(forks)
                .threads(threads)
                .warmupIterations(warmupIterations)
                .warmupTime(TimeValue.milliseconds(warmupMillis))
                .measurementIterations(measurementIterations)
                .measurementTime(TimeValue.milliseconds(measurementMillis))
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .addProfiler(GCProfiler.class)
                .shouldFailOnError(true);
        String output = System.getProperty(PREFIX + "output");
        if (output != null && !output.isBlank()) {
            optionsBuilder.output(output);
        }
        Options options = optionsBuilder.build();
        new Runner(options).run();
    }

    @Benchmark
    public int oneSessionCacheHit(OneSessionState state) {
        return request(state.scenario.client(), FAST_PATH);
    }

    @Benchmark
    @OperationsPerInvocation(CONCURRENT_REQUESTS)
    public int multiSessionSelection(MultiSessionState state) {
        return concurrentRequests(state.scenario.client(), state.scenario.executor(), DELAYED_PATH);
    }

    @Benchmark
    public int saturatedFullPoolWaitingAndReuse(SaturatedPoolState state) {
        return state.releaseNonPreferredAndAwait();
    }

    @Benchmark
    @OperationsPerInvocation(CONCURRENT_REQUESTS)
    public int saturatedSingleSessionWaitingAndReuse(SingleSessionSaturatedPoolState state) {
        return concurrentRequests(state.scenario.client(), state.scenario.executor(), DELAYED_PATH);
    }

    @Benchmark
    @OperationsPerInvocation(CONCURRENT_REQUESTS)
    public int belowCapacityExpansionUnderConcurrentLoad(ExpansionState state) {
        try (Http3ClientResponse heldResponse = state.client.get(HOLD_PATH).request()) {
            requireOk(heldResponse);
            state.expandedConnectionId = requestConnectionId(state.client, CONNECTION_ID_PATH);
            state.hold.complete();
            state.heldConnectionId = heldResponse.as(String.class);
            return Status.OK_200.code() * CONCURRENT_REQUESTS;
        } finally {
            state.hold.completeIfRegistered();
        }
    }

    private static int concurrentRequests(Http3Client client, ExecutorService executor, String path) {
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Integer>> requests = new ArrayList<>(CONCURRENT_REQUESTS);
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            requests.add(CompletableFuture.supplyAsync(() -> {
                await(start);
                return request(client, path);
            }, executor));
        }
        start.countDown();
        int result = 0;
        for (CompletableFuture<Integer> request : requests) {
            result += request.join();
        }
        return result;
    }

    private static int request(Http3Client client, String path) {
        try (Http3ClientResponse response = client.get(path).request()) {
            requireOk(response);
            return response.status().code();
        }
    }

    private static String requestConnectionId(Http3Client client, String path) {
        try (Http3ClientResponse response = client.get(path).request()) {
            requireOk(response);
            return response.as(String.class);
        }
    }

    private static void requireOk(Http3ClientResponse response) {
        if (response.status().code() != Status.OK_200.code()) {
            throw new IllegalStateException("Unexpected HTTP/3 benchmark response status: " + response.status());
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting concurrent HTTP/3 benchmark requests", e);
        }
    }

    @State(Scope.Benchmark)
    public static class OneSessionState {
        private Scenario scenario;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            scenario = Scenario.create(1);
            request(scenario.client(), FAST_PATH);
            scenario.server().requireConnectionCount(1);
            scenario.server().requireAcceptedConnectionCount(1);
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            try {
                scenario.server().requireConnectionCount(1);
                scenario.server().requireAcceptedConnectionCount(1);
            } finally {
                scenario.close();
            }
        }
    }

    @State(Scope.Benchmark)
    public static class MultiSessionState {
        private Scenario scenario;
        private Map<String, Long> requestsBeforeMeasurement;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            scenario = Scenario.create(2);
            scenario.openTwoSessions();
            requestsBeforeMeasurement = scenario.server().requestCounts();
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            try {
                scenario.server().requireConnectionCount(2);
                scenario.server().requireEveryConnectionUsedAfter(requestsBeforeMeasurement);
            } finally {
                scenario.close();
            }
        }
    }

    @State(Scope.Thread)
    public static class SaturatedPoolState {
        private BenchmarkServer server;
        private ExecutorService executor;
        private Http3Client client;
        private HeldResponse firstHold;
        private HeldResponse preferredHold;
        private Http3ClientResponse firstResponse;
        private Http3ClientResponse preferredResponse;
        private CompletableFuture<String> waitingRequest;
        private String firstConnectionId;
        private String preferredConnectionId;
        private String waitingConnectionId;
        private long wakeRequestsBefore;
        private boolean firstResponseConsumed;

        @Setup(Level.Trial)
        public void setupTrial() throws Exception {
            server = BenchmarkServer.create();
            executor = Executors.newVirtualThreadPerTaskExecutor();
            client = server.client(2);
        }

        @Setup(Level.Invocation)
        public void setupInvocation() throws Exception {
            firstHold = server.installHold(FULL_POOL_FIRST_HOLD_PATH);
            firstResponse = client.get(FULL_POOL_FIRST_HOLD_PATH).request();
            requireOk(firstResponse);
            firstConnectionId = firstHold.connectionId();

            preferredHold = server.installHold(FULL_POOL_PREFERRED_HOLD_PATH);
            preferredResponse = client.get(FULL_POOL_PREFERRED_HOLD_PATH).request();
            requireOk(preferredResponse);
            preferredConnectionId = preferredHold.connectionId();
            if (firstConnectionId.equals(preferredConnectionId)) {
                throw new IllegalStateException("HTTP/3 full-pool benchmark did not occupy both sessions");
            }
            server.requireAcceptedConnectionCount(2);

            wakeRequestsBefore = server.fullPoolWakeRequests();
            CountDownLatch requestStarted = new CountDownLatch(1);
            waitingRequest = CompletableFuture.supplyAsync(() -> {
                requestStarted.countDown();
                return requestConnectionId(client, FULL_POOL_WAKE_PATH);
            }, executor);
            await(requestStarted);
            try {
                waitingRequest.get(100, TimeUnit.MILLISECONDS);
                throw new IllegalStateException("HTTP/3 full-pool request completed while both sessions were held");
            } catch (TimeoutException _) {
                // Expected: both pooled sessions have exhausted peer stream credit.
            }
            waitingConnectionId = null;
            firstResponseConsumed = false;
        }

        @TearDown(Level.Invocation)
        public void tearDownInvocation() {
            try {
                if (waitingConnectionId != null) {
                    if (!waitingConnectionId.equals(firstConnectionId)) {
                        throw new IllegalStateException(
                                "HTTP/3 full-pool request did not wake on the released session: "
                                        + waitingConnectionId);
                    }
                    if (server.fullPoolWakeRequests() != wakeRequestsBefore + 1) {
                        throw new IllegalStateException("HTTP/3 full-pool benchmark opened the queued request "
                                                                + "more than once");
                    }
                    if (preferredHold.completed()) {
                        throw new IllegalStateException(
                                "HTTP/3 full-pool benchmark released the preferred session early");
                    }
                    server.requireAcceptedConnectionCount(2);
                }
            } finally {
                firstHold.completeIfRegistered();
                preferredHold.completeIfRegistered();
                if (!firstResponseConsumed) {
                    firstResponse.as(String.class);
                }
                preferredResponse.as(String.class);
                server.clearHold(FULL_POOL_FIRST_HOLD_PATH, firstHold);
                server.clearHold(FULL_POOL_PREFERRED_HOLD_PATH, preferredHold);
                firstResponse.close();
                preferredResponse.close();
            }
        }

        @TearDown(Level.Trial)
        public void tearDownTrial() {
            try {
                client.closeResource();
            } finally {
                try {
                    server.close();
                } finally {
                    executor.close();
                }
            }
        }

        private int releaseNonPreferredAndAwait() {
            firstHold.complete();
            String completedConnectionId = firstResponse.as(String.class);
            firstResponseConsumed = true;
            if (!completedConnectionId.equals(firstConnectionId)) {
                throw new IllegalStateException("Unexpected HTTP/3 held response connection: "
                                                        + completedConnectionId);
            }
            waitingConnectionId = waitingRequest.orTimeout(10, TimeUnit.SECONDS).join();
            return Status.OK_200.code();
        }
    }

    @State(Scope.Benchmark)
    public static class SingleSessionSaturatedPoolState {
        private Scenario scenario;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            scenario = Scenario.create(1);
            request(scenario.client(), FAST_PATH);
            scenario.server().requireConnectionCount(1);
            scenario.server().requireAcceptedConnectionCount(1);
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            try {
                scenario.server().requireConnectionCount(1);
                scenario.server().requireAcceptedConnectionCount(1);
            } finally {
                scenario.close();
            }
        }
    }

    @State(Scope.Thread)
    public static class ExpansionState {
        private BenchmarkServer server;
        private Http3Client client;
        private HeldResponse hold;
        private String initialConnectionId;
        private String heldConnectionId;
        private String expandedConnectionId;
        private long connectionsBeforeExpansion;

        @Setup(Level.Trial)
        public void setupTrial() throws Exception {
            server = BenchmarkServer.create();
        }

        @Setup(Level.Invocation)
        public void setupInvocation() {
            client = server.client(4);
            initialConnectionId = requestConnectionId(client, CONNECTION_ID_PATH);
            connectionsBeforeExpansion = server.acceptedConnectionCount();
            hold = server.installHold(HOLD_PATH);
            heldConnectionId = null;
            expandedConnectionId = null;
        }

        @TearDown(Level.Invocation)
        public void tearDownInvocation() {
            try {
                hold.completeIfRegistered();
                if (heldConnectionId == null || expandedConnectionId == null) {
                    throw new IllegalStateException("HTTP/3 below-capacity expansion invocation did not complete");
                }
                if (!initialConnectionId.equals(heldConnectionId)) {
                    throw new IllegalStateException("HTTP/3 expansion did not occupy the primed session: initial="
                                                            + initialConnectionId + ", held=" + heldConnectionId);
                }
                if (heldConnectionId.equals(expandedConnectionId)) {
                    throw new IllegalStateException("HTTP/3 expansion reused the session without stream credit: "
                                                            + heldConnectionId);
                }
                long expectedConnections = connectionsBeforeExpansion + 1;
                if (server.acceptedConnectionCount() != expectedConnections) {
                    throw new IllegalStateException("Unexpected HTTP/3 connections after expansion: expected="
                                                            + expectedConnections + ", actual="
                                                            + server.acceptedConnectionCount());
                }
            } finally {
                server.clearHold(HOLD_PATH, hold);
                client.closeResource();
                client = null;
                hold = null;
            }
        }

        @TearDown(Level.Trial)
        public void tearDownTrial() {
            server.close();
        }
    }

    private static final class Scenario implements AutoCloseable {
        private final BenchmarkServer server;
        private final ExecutorService executor;
        private final Http3Client client;

        private Scenario(BenchmarkServer server, ExecutorService executor, Http3Client client) {
            this.server = server;
            this.executor = executor;
            this.client = client;
        }

        @Override
        public void close() {
            try {
                client.closeResource();
            } finally {
                try {
                    server.close();
                } finally {
                    executor.close();
                }
            }
        }

        private static Scenario create(int capacity) throws Exception {
            BenchmarkServer server = null;
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            try {
                server = BenchmarkServer.create();
                return new Scenario(server, executor, server.client(capacity));
            } catch (Exception | Error e) {
                if (server != null) {
                    server.close();
                }
                executor.close();
                throw e;
            }
        }

        private void openTwoSessions() {
            HeldResponse hold = server.installHold(HOLD_PATH);
            try (Http3ClientResponse heldResponse = client.get(HOLD_PATH).request()) {
                requireOk(heldResponse);
                String secondConnectionId = requestConnectionId(client, CONNECTION_ID_PATH);
                hold.complete();
                String firstConnectionId = heldResponse.as(String.class);
                if (firstConnectionId.equals(secondConnectionId)) {
                    throw new IllegalStateException("HTTP/3 benchmark did not create a second pooled session");
                }
            } finally {
                hold.completeIfRegistered();
                server.clearHold(HOLD_PATH, hold);
            }
            server.requireConnectionCount(2);
        }

        private BenchmarkServer server() {
            return server;
        }

        private ExecutorService executor() {
            return executor;
        }

        private Http3Client client() {
            return client;
        }
    }

    private static final class BenchmarkServer implements AutoCloseable {
        private final Set<String> connections = ConcurrentHashMap.newKeySet();
        private final Map<String, LongAdder> requests = new ConcurrentHashMap<>();
        private final Map<String, HeldResponse> holds = new ConcurrentHashMap<>();
        private final AtomicLong acceptedConnections = new AtomicLong();
        private final LongAdder fullPoolWakeRequests = new LongAdder();
        private final Http3RawTestServer server;

        private BenchmarkServer() throws Exception {
            server = Http3RawTestServer.create(this::handle, this::connectionAccepted, 1);
        }

        @Override
        public void close() {
            server.close();
        }

        private static BenchmarkServer create() throws Exception {
            return new BenchmarkServer();
        }

        private static Http3RawTestServer.BufferedResponse emptyResponse() {
            return Http3RawTestServer.response(Status.OK_200.code(), WritableHeaders.create(), EMPTY_BODY);
        }

        private Http3RawTestServer.BufferedResponse handle(Http3Protocol.DecodedRequestHead request,
                                                           QuicConnection connection,
                                                           long ignoredStreamId,
                                                           Http3RawTestServer.StreamControl stream) {
            String connectionId = connection.childSocketId();
            connections.add(connectionId);
            requests.computeIfAbsent(connectionId, _ -> new LongAdder()).increment();
            String path = request.path().orElseThrow();
            return switch (path) {
                case FAST_PATH -> emptyResponse();
                case DELAYED_PATH -> {
                    LockSupport.parkNanos(DELAY_NANOS);
                    yield emptyResponse();
                }
                case CONNECTION_ID_PATH -> Http3RawTestServer.text(Status.OK_200.code(), connectionId);
                case HOLD_PATH, FULL_POOL_FIRST_HOLD_PATH, FULL_POOL_PREFERRED_HOLD_PATH -> {
                    HeldResponse currentHold = holds.get(path);
                    if (currentHold == null) {
                        throw new IllegalStateException("HTTP/3 benchmark hold is not installed for " + path);
                    }
                    currentHold.register(connectionId, stream);
                    yield null;
                }
                case FULL_POOL_WAKE_PATH -> {
                    fullPoolWakeRequests.increment();
                    yield Http3RawTestServer.text(Status.OK_200.code(), connectionId);
                }
                default -> Http3RawTestServer.text(Status.NOT_FOUND_404.code(), path);
            };
        }

        private Http3Client client(int capacity) {
            return Http3ClientTestSupport.strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .servicesDiscoverServices(false)
                    .connectionCacheSize(capacity)
                    .build();
        }

        private HeldResponse installHold(String path) {
            HeldResponse candidate = new HeldResponse();
            if (holds.putIfAbsent(path, candidate) != null) {
                throw new IllegalStateException("HTTP/3 benchmark hold is already installed for " + path);
            }
            return candidate;
        }

        private void clearHold(String path, HeldResponse expected) {
            if (!holds.remove(path, expected)) {
                throw new IllegalStateException("HTTP/3 benchmark hold changed unexpectedly for " + path);
            }
        }

        private int connectionCount() {
            return connections.size();
        }

        private long acceptedConnectionCount() {
            return acceptedConnections.get();
        }

        private long fullPoolWakeRequests() {
            return fullPoolWakeRequests.sum();
        }

        private Map<String, Long> requestCounts() {
            Map<String, Long> result = new ConcurrentHashMap<>();
            requests.forEach((connectionId, count) -> result.put(connectionId, count.sum()));
            return Map.copyOf(result);
        }

        private void requireConnectionCount(int expected) {
            int actual = connectionCount();
            if (actual != expected) {
                throw new IllegalStateException("Unexpected HTTP/3 benchmark connection count: expected="
                                                        + expected + ", actual=" + actual);
            }
        }

        private void requireAcceptedConnectionCount(long expected) {
            long actual = acceptedConnectionCount();
            if (actual != expected) {
                throw new IllegalStateException("Unexpected accepted HTTP/3 benchmark connection count: expected="
                                                        + expected + ", actual=" + actual);
            }
        }

        private void requireEveryConnectionUsedAfter(Map<String, Long> baseline) {
            Map<String, Long> current = requestCounts();
            if (!current.keySet().equals(baseline.keySet())) {
                throw new IllegalStateException("HTTP/3 benchmark connection set changed: before="
                                                        + baseline.keySet() + ", after=" + current.keySet());
            }
            for (Map.Entry<String, Long> entry : baseline.entrySet()) {
                long currentCount = current.get(entry.getKey());
                if (currentCount <= entry.getValue()) {
                    throw new IllegalStateException("HTTP/3 benchmark session was not selected: " + entry.getKey());
                }
            }
        }

        private void connectionAccepted(QuicConnection connection) {
            String connectionId = connection.childSocketId();
            connections.add(connectionId);
            acceptedConnections.incrementAndGet();
            connection.whenTerminated().whenComplete((_, _) -> {
                connections.remove(connectionId);
                requests.remove(connectionId);
            });
        }
    }

    private static final class HeldResponse {
        private final AtomicReference<Http3RawTestServer.StreamControl> stream = new AtomicReference<>();
        private final AtomicReference<String> connectionId = new AtomicReference<>();
        private final AtomicBoolean completed = new AtomicBoolean();

        private void register(String id, Http3RawTestServer.StreamControl streamControl) {
            if (!connectionId.compareAndSet(null, id) || !stream.compareAndSet(null, streamControl)) {
                throw new IllegalStateException("HTTP/3 benchmark hold stream is already registered");
            }
            streamControl.writeResponseHeaders(Status.OK_200.code(), WritableHeaders.create(), false);
        }

        private void complete() {
            String id = connectionId.get();
            Http3RawTestServer.StreamControl streamControl = stream.get();
            if (id == null || streamControl == null) {
                throw new IllegalStateException("HTTP/3 benchmark hold stream is not registered");
            }
            if (completed.compareAndSet(false, true)) {
                streamControl.writeData(id.getBytes(StandardCharsets.UTF_8), true);
            }
        }

        private void completeIfRegistered() {
            if (stream.get() != null) {
                complete();
            }
        }

        private String connectionId() {
            String id = connectionId.get();
            if (id == null) {
                throw new IllegalStateException("HTTP/3 benchmark hold stream is not registered");
            }
            return id;
        }

        private boolean completed() {
            return completed.get();
        }
    }
}
