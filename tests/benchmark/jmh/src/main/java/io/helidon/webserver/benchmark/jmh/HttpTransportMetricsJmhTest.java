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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpTransportObserver;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.Handshake;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.Services;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import static io.helidon.http.HttpTransportObserver.ConnectionOutcome.REMOTE_CLOSE;
import static io.helidon.http.HttpTransportObserver.Direction.BIDIRECTIONAL;
import static io.helidon.http.HttpTransportObserver.Handshake.NONE;
import static io.helidon.http.HttpTransportObserver.Handshake.TLS;
import static io.helidon.http.HttpTransportObserver.HandshakeOutcome.SUCCESS;
import static io.helidon.http.HttpTransportObserver.Initiator.REMOTE;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_1_1;
import static io.helidon.http.HttpTransportObserver.Role.SERVER;
import static io.helidon.http.HttpTransportObserver.StreamOutcome.COMPLETED;
import static io.helidon.http.HttpTransportObserver.TRANSPORT_TCP;

/**
 * HTTP/1 publisher and HTTP transport metrics recorder benchmarks.
 *
 * <p>The WebServer benchmarks deliberately run without an observer so the same source can compare the public base with
 * the candidate's always-on publisher hooks. Recorder acquisition uses reflection only during trial setup so this source
 * also compiles on a base which does not yet contain {@code helidon-http-metrics}. Recorder modes include {@code noop},
 * direct {@code metrics}, and {@code composed-metrics}, which includes the production observer composition wrapper.
 * The stream state's {@code percentiles} setting explicitly configures six local percentiles when enabled and an empty
 * list when disabled; setup rejects an ignored override. Configuration uses the existing config API for source compatibility.
 */
public class HttpTransportMetricsJmhTest {
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SOCKET_READ_TIMEOUT_MILLIS = 5_000;
    private static final int RESPONSE_BUFFER_SIZE = 1_024;
    private static final String STREAM_DURATION = "helidon.http.streams.duration";
    private static final List<Double> STREAM_PERCENTILES = List.of(0.5, 0.75, 0.95, 0.98, 0.99, 0.999);
    private static final byte[] RESPONSE_BYTES = "OK".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RESPONSE_STATUS = "HTTP/1.1 200".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] KEEP_ALIVE_REQUEST = """
            GET /benchmark HTTP/1.1\r
            Host: localhost\r
            \r
            """.getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CLOSE_REQUEST = """
            GET /benchmark HTTP/1.1\r
            Host: localhost\r
            Connection: close\r
            \r
            """.getBytes(StandardCharsets.US_ASCII);

    @Benchmark
    public int disabledHttp1KeepAliveExchange(DisabledServerState state) throws IOException {
        return state.keepAliveExchange();
    }

    @Benchmark
    public int disabledHttp1ConnectionLifecycle(DisabledServerState state) throws IOException {
        return state.connectionLifecycle();
    }

    @Benchmark
    public StreamObservation observerHttp1StreamLifecycle(ObserverStreamState state) {
        return state.streamLifecycle();
    }

    @Benchmark
    public ConnectionObservation observerHttp1ConnectionLifecycle(ObserverClearConnectionState state) {
        return state.connectionLifecycle();
    }

    @Benchmark
    public ConnectionObservation observerTlsHttp1ConnectionLifecycle(ObserverTlsConnectionState state) {
        return state.connectionLifecycle();
    }

    private static ConnectionObservation completeConnection(HttpTransportObserver observer, Handshake handshake) {
        ConnectionObservation connection = observer.connectionOpened(SERVER, TRANSPORT_TCP, handshake);
        if (handshake == TLS) {
            connection.handshakeStarted().close(SUCCESS);
        }
        connection.protocolSelected(PROTOCOL_HTTP_1_1);
        StreamObservation stream = connection.streamOpened(BIDIRECTIONAL, REMOTE);
        stream.close(COMPLETED);
        connection.close(REMOTE_CLOSE);
        return connection;
    }

    private static List<Activity> connectionActivity(Handshake handshake) {
        List<Activity> result = new ArrayList<>();
        result.add(counter("helidon.http.connections.opened",
                           tags("role", "server", "transport", "tcp", "handshake", tagValue(handshake))));
        result.add(counter("helidon.http.connections.established",
                           tags("role", "server", "transport", "tcp", "protocol", "http/1.1")));
        if (handshake == TLS) {
            List<Tag> handshakeTags = tags("role", "server",
                                           "transport", "tcp",
                                           "handshake", "tls",
                                           "outcome", "success");
            result.add(counter("helidon.http.handshakes", handshakeTags));
            result.add(timer("helidon.http.handshakes.duration", handshakeTags));
        }
        result.addAll(streamActivity());
        List<Tag> closedTags = tags("role", "server",
                                    "transport", "tcp",
                                    "protocol", "http/1.1",
                                    "outcome", "remote-close");
        result.add(counter("helidon.http.connections.closed", closedTags));
        result.add(timer("helidon.http.connections.duration", closedTags));
        return List.copyOf(result);
    }

    private static List<Activity> streamActivity() {
        List<Tag> openedTags = tags("role", "server",
                                    "protocol", "http/1.1",
                                    "direction", "bidi",
                                    "initiator", "remote");
        List<Tag> closedTags = tags("role", "server",
                                    "protocol", "http/1.1",
                                    "direction", "bidi",
                                    "initiator", "remote",
                                    "outcome", "completed");
        return List.of(counter("helidon.http.streams.opened", openedTags),
                       counter("helidon.http.streams.closed", closedTags),
                       timer(STREAM_DURATION, closedTags));
    }

    private static MetricsConfig metricsConfig(boolean percentiles) {
        var values = ListNode.builder();
        if (percentiles) {
            STREAM_PERCENTILES.forEach(value -> values.addValue(value.toString()));
        }
        ObjectNode meter = ObjectNode.builder()
                .addValue("name-pattern", Pattern.quote(STREAM_DURATION))
                .addList("percentiles", values.build())
                .build();
        Config config = Config.just(ConfigSources.create(ObjectNode.builder()
                                                                 .addList("meters", ListNode.builder().addObject(meter).build())
                                                                 .build()));
        return MetricsConfig.builder().config(config).warnOnMultipleRegistries(false).build();
    }

    private static List<GaugeExpectation> connectionGauges() {
        return List.of(gauge("helidon.http.connections.active",
                             tags("role", "server", "transport", "tcp", "protocol", "http/1.1")));
    }

    private static List<GaugeExpectation> streamGauges() {
        return List.of(gauge("helidon.http.streams.active",
                             tags("role", "server",
                                  "protocol", "http/1.1",
                                  "direction", "bidi",
                                  "initiator", "remote")));
    }

    private static Activity counter(String name, List<Tag> tags) {
        return new Activity(name, tags, MeterType.COUNTER);
    }

    private static Activity timer(String name, List<Tag> tags) {
        return new Activity(name, tags, MeterType.TIMER);
    }

    private static GaugeExpectation gauge(String name, List<Tag> tags) {
        return new GaugeExpectation(name, tags);
    }

    private static List<Tag> tags(String... keyValues) {
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        List<Tag> result = new ArrayList<>(keyValues.length / 2);
        for (int i = 0; i < keyValues.length; i += 2) {
            result.add(metricsFactory.tagCreate(keyValues[i], keyValues[i + 1]));
        }
        return List.copyOf(result);
    }

    private static String tagValue(Handshake handshake) {
        return handshake == TLS ? "tls" : "none";
    }

    private static int readResponse(InputStream input, byte[] responseBuffer) throws IOException {
        int bytesRead = 0;
        int bodyStart = -1;
        while (bytesRead < responseBuffer.length) {
            int read = input.read(responseBuffer, bytesRead, responseBuffer.length - bytesRead);
            if (read == -1) {
                throw new EOFException("Response ended before the complete body was received");
            }
            bytesRead += read;
            if (bodyStart == -1) {
                bodyStart = bodyStart(responseBuffer, bytesRead);
            }
            if (bodyStart != -1 && bytesRead >= bodyStart + RESPONSE_BYTES.length) {
                verifyResponse(responseBuffer, bodyStart);
                return bytesRead;
            }
        }
        throw new IOException("Response exceeded benchmark buffer before the complete body was received");
    }

    private static int bodyStart(byte[] responseBuffer, int bytesRead) {
        for (int i = 3; i < bytesRead; i++) {
            if (responseBuffer[i - 3] == '\r'
                    && responseBuffer[i - 2] == '\n'
                    && responseBuffer[i - 1] == '\r'
                    && responseBuffer[i] == '\n') {
                return i + 1;
            }
        }
        return -1;
    }

    private static void verifyResponse(byte[] responseBuffer, int bodyStart) throws IOException {
        for (int i = 0; i < RESPONSE_STATUS.length; i++) {
            if (responseBuffer[i] != RESPONSE_STATUS[i]) {
                throw new IOException("Unexpected HTTP response status");
            }
        }
        for (int i = 0; i < RESPONSE_BYTES.length; i++) {
            if (responseBuffer[bodyStart + i] != RESPONSE_BYTES[i]) {
                throw new IOException("Unexpected HTTP response body");
            }
        }
    }

    private static void await(BooleanSupplier condition, String description) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("Timed out waiting for " + description);
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
    }

    /** Real WebServer with transport observation disabled. */
    @State(Scope.Benchmark)
    public static class DisabledServerState {
        private static final Header CONTENT_LENGTH = HeaderValues.createCached(HeaderNames.CONTENT_LENGTH,
                                                                                String.valueOf(RESPONSE_BYTES.length));

        private final byte[] keepAliveResponse = new byte[RESPONSE_BUFFER_SIZE];
        private final byte[] closeResponse = new byte[RESPONSE_BUFFER_SIZE];

        private WebServer server;
        private int serverPort;
        private Socket keepAliveSocket;
        private InputStream keepAliveInput;
        private OutputStream keepAliveOutput;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            server = WebServer.builder()
                    .featuresDiscoverServices(false)
                    .connectionOptions(builder -> builder
                            .readTimeout(Duration.ZERO)
                            .connectTimeout(Duration.ZERO))
                    .host(SERVER_HOST)
                    .routing(routing -> routing.get("/benchmark", new BenchmarkHandler()))
                    .build()
                    .start();
            serverPort = server.port();
            keepAliveSocket = newSocket();
            keepAliveInput = keepAliveSocket.getInputStream();
            keepAliveOutput = keepAliveSocket.getOutputStream();

            keepAliveExchange();
            connectionLifecycle();
        }

        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            try {
                if (keepAliveSocket != null) {
                    keepAliveSocket.close();
                }
            } finally {
                if (server != null) {
                    server.stop();
                }
            }
        }

        int keepAliveExchange() throws IOException {
            keepAliveOutput.write(KEEP_ALIVE_REQUEST);
            keepAliveOutput.flush();
            return readResponse(keepAliveInput, keepAliveResponse);
        }

        int connectionLifecycle() throws IOException {
            try (Socket socket = newSocket()) {
                OutputStream output = socket.getOutputStream();
                output.write(CLOSE_REQUEST);
                output.flush();
                InputStream input = socket.getInputStream();
                int bytesRead = readResponse(input, closeResponse);
                if (input.read() != -1) {
                    throw new IOException("Server kept a Connection: close response open");
                }
                return bytesRead;
            }
        }

        private Socket newSocket() throws IOException {
            Socket socket = new Socket(SERVER_HOST, serverPort);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(SOCKET_READ_TIMEOUT_MILLIS);
            return socket;
        }

        private static final class BenchmarkHandler implements Handler {
            @Override
            public void handle(ServerRequest req, ServerResponse res) {
                res.header(CONTENT_LENGTH);
                res.send(RESPONSE_BYTES);
            }
        }
    }

    /** No-TLS connection lifecycle recorder state. */
    @State(Scope.Benchmark)
    public static class ObserverClearConnectionState {
        /** Observer implementation selected for this fork. */
        @Param("noop")
        public String observerMode;

        private RecorderHarness harness;
        private ActivitySnapshot activity;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            harness = RecorderHarness.create(observerMode);
            connectionLifecycle();
            activity = harness.awaitAndSnapshot(connectionActivity(NONE), connectionGauges());
        }

        @TearDown(Level.Trial)
        public void tearDown() throws Exception {
            try {
                harness.verifyAdvanced(activity);
                harness.verifyGauges(connectionGauges(), 0);
            } finally {
                harness.close();
            }
        }

        ConnectionObservation connectionLifecycle() {
            return completeConnection(harness.observer(), NONE);
        }
    }

    /** TLS connection and handshake lifecycle recorder state. */
    @State(Scope.Benchmark)
    public static class ObserverTlsConnectionState {
        /** Observer implementation selected for this fork. */
        @Param("noop")
        public String observerMode;

        private RecorderHarness harness;
        private ActivitySnapshot activity;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            harness = RecorderHarness.create(observerMode);
            connectionLifecycle();
            activity = harness.awaitAndSnapshot(connectionActivity(TLS), connectionGauges());
        }

        @TearDown(Level.Trial)
        public void tearDown() throws Exception {
            try {
                harness.verifyAdvanced(activity);
                harness.verifyGauges(connectionGauges(), 0);
            } finally {
                harness.close();
            }
        }

        ConnectionObservation connectionLifecycle() {
            return completeConnection(harness.observer(), TLS);
        }
    }

    /** Steady-state HTTP/1 stream lifecycle recorder state. */
    @State(Scope.Benchmark)
    public static class ObserverStreamState {
        /** Observer implementation selected for this fork. */
        @Param("noop")
        public String observerMode;

        /** Whether to configure local percentiles for the stream duration timer. */
        @Param({"true"})
        public boolean percentiles = true;

        private RecorderHarness harness;
        private ConnectionObservation connection;
        private ActivitySnapshot activity;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            harness = RecorderHarness.create(observerMode, percentiles);
            connection = harness.observer().connectionOpened(SERVER, TRANSPORT_TCP, NONE);
            connection.protocolSelected(PROTOCOL_HTTP_1_1);
            streamLifecycle();
            activity = harness.awaitAndSnapshot(streamActivity(), streamGauges());
            harness.verifyStreamPercentiles(percentiles);
            streamLifecycle();
            harness.verifyAdvanced(activity);
            activity = harness.awaitAndSnapshot(streamActivity(), streamGauges());
            harness.verifyGauges(connectionGauges(), 1);
        }

        @TearDown(Level.Trial)
        public void tearDown() throws Exception {
            try {
                harness.verifyAdvanced(activity);
                harness.verifyGauges(streamGauges(), 0);
                harness.verifyGauges(connectionGauges(), 1);
            } finally {
                try {
                    connection.close(REMOTE_CLOSE);
                    harness.verifyGauges(connectionGauges(), 0);
                } finally {
                    harness.close();
                }
            }
        }

        StreamObservation streamLifecycle() {
            StreamObservation stream = connection.streamOpened(BIDIRECTIONAL, REMOTE);
            stream.close(COMPLETED);
            return stream;
        }
    }

    private enum MeterType {
        COUNTER,
        TIMER
    }

    private record Activity(String name, List<Tag> tags, MeterType type) {
        private long count(MeterRegistry registry) {
            return switch (type) {
            case COUNTER -> registry.counter(name, tags).map(Counter::count).orElse(-1L);
            case TIMER -> registry.timer(name, tags).map(Timer::count).orElse(-1L);
            };
        }
    }

    private record GaugeExpectation(String name, List<Tag> tags) {
        private Gauge<?> find(MeterRegistry registry) {
            return registry.gauge(name, tags).orElse(null);
        }
    }

    private record ActivitySnapshot(List<Activity> activities, long[] counts) {
    }

    private static final class RecorderHarness implements AutoCloseable {
        private static final String HTTP_TRANSPORT_METRICS = "io.helidon.http.metrics.HttpTransportMetrics";
        private static final String HTTP_TRANSPORT_METRICS_LEASE = HTTP_TRANSPORT_METRICS + "$Lease";

        private final MeterRegistry registry;
        private final HttpTransportObserver observer;
        private final Object lease;
        private final Method completion;

        private RecorderHarness(MeterRegistry registry,
                                HttpTransportObserver observer,
                                Object lease,
                                Method completion) {
            this.registry = registry;
            this.observer = observer;
            this.lease = lease;
            this.completion = completion;
        }

        private static RecorderHarness create(String observerMode) throws Exception {
            return create(observerMode, true);
        }

        private static RecorderHarness create(String observerMode, boolean percentiles) throws Exception {
            MeterRegistry registry = Services.get(MetricsFactory.class)
                    .createMeterRegistry(metricsConfig(percentiles));
            if ("noop".equals(observerMode)) {
                return new RecorderHarness(registry, HttpTransportObserver.noop(), null, null);
            }
            boolean composed = "composed-metrics".equals(observerMode);
            if (!"metrics".equals(observerMode) && !composed) {
                registry.close();
                throw new IllegalArgumentException("Unknown observer mode: " + observerMode);
            }
            try {
                Class<?> metricsType = Class.forName(HTTP_TRANSPORT_METRICS);
                Method acquire = metricsType.getMethod("acquire", MeterRegistry.class);
                Object lease = invoke(acquire, null, registry);
                if (!(lease instanceof HttpTransportObserver observer) || !(lease instanceof AutoCloseable)) {
                    throw new IllegalStateException("HTTP transport metrics lease does not implement its public contracts");
                }
                if (composed) {
                    observer = HttpTransportObserver.compose(List.of(observer));
                }
                Class<?> leaseType = Class.forName(HTTP_TRANSPORT_METRICS_LEASE);
                Method completion = leaseType.getMethod("completion");
                return new RecorderHarness(registry, observer, lease, completion);
            } catch (Exception | Error failure) {
                registry.close();
                throw failure;
            }
        }

        private HttpTransportObserver observer() {
            return observer;
        }

        private ActivitySnapshot awaitAndSnapshot(List<Activity> activities, List<GaugeExpectation> gauges) {
            if (lease == null) {
                if (!registry.meters().isEmpty()) {
                    throw new IllegalStateException("No-op observer unexpectedly registered meters");
                }
                return new ActivitySnapshot(activities, new long[activities.size()]);
            }
            await(() -> activities.stream().allMatch(activity -> activity.count(registry) > 0)
                            && gauges.stream().allMatch(gauge -> gauge.find(registry) != null),
                  "warmed HTTP transport meters");
            long[] counts = new long[activities.size()];
            for (int i = 0; i < counts.length; i++) {
                counts[i] = activities.get(i).count(registry);
            }
            return new ActivitySnapshot(activities, counts);
        }

        private void verifyAdvanced(ActivitySnapshot snapshot) {
            if (lease == null) {
                if (!registry.meters().isEmpty()) {
                    throw new IllegalStateException("No-op observer unexpectedly registered meters");
                }
                return;
            }
            List<Activity> activities = snapshot.activities();
            long[] counts = snapshot.counts();
            for (int i = 0; i < counts.length; i++) {
                long actual = activities.get(i).count(registry);
                if (actual <= counts[i]) {
                    throw new IllegalStateException("HTTP transport meter did not advance: " + activities.get(i).name());
                }
            }
        }

        private void verifyStreamPercentiles(boolean percentiles) {
            if (lease == null) {
                return;
            }
            Activity duration = streamActivity().getLast();
            Timer timer = registry.timer(STREAM_DURATION, duration.tags()).orElseThrow();
            List<Double> actual = new ArrayList<>();
            timer.snapshot().percentileValues().forEach(value -> actual.add(value.percentile()));
            List<Double> expected = percentiles ? STREAM_PERCENTILES : List.of();
            if (!actual.equals(expected)) {
                throw new IllegalStateException("Unexpected stream duration percentiles: expected "
                                                        + expected + ", actual " + actual);
            }
        }

        private void verifyGauges(List<GaugeExpectation> expectations, long expected) {
            if (lease == null) {
                return;
            }
            for (GaugeExpectation expectation : expectations) {
                Gauge<?> gauge = expectation.find(registry);
                if (gauge == null || gauge.value().longValue() != expected) {
                    throw new IllegalStateException("Unexpected HTTP transport gauge value: " + expectation.name());
                }
            }
        }

        @Override
        public void close() throws Exception {
            try {
                if (lease == null) {
                    if (!registry.meters().isEmpty()) {
                        throw new IllegalStateException("No-op observer unexpectedly registered meters");
                    }
                    return;
                }
                ((AutoCloseable) lease).close();
                CompletionStage<?> stage = (CompletionStage<?>) invoke(completion, lease);
                stage.toCompletableFuture().get(10, TimeUnit.SECONDS);
                if (!registry.meters().isEmpty()) {
                    throw new IllegalStateException("HTTP transport meters remained after lease completion");
                }
            } finally {
                registry.close();
            }
        }

        private static Object invoke(Method method, Object receiver, Object... arguments) throws Exception {
            try {
                return method.invoke(receiver, arguments);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception exception) {
                    throw exception;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw e;
            }
        }
    }
}
