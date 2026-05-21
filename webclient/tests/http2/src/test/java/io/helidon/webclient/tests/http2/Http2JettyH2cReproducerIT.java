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

package io.helidon.webclient.tests.http2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.http.HeaderNames;
import io.helidon.http.HttpMediaTypes;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Manual integration-test reproducer for issue 11529.
 *
 * <p>Run explicitly with:
 * {@code mvn -Ptests -pl :helidon-webclient-tests-http2 -am verify -Dit.test=Http2JettyH2cReproducerIT}
 */
public class Http2JettyH2cReproducerIT {
    private static final StringBuilder PEER_LOG = new StringBuilder();
    private static final Lock PEER_LOG_LOCK = new ReentrantLock();
    private static final String APP_PATH = "/api/field-service/appcache/v1/rpc/get_properties";
    private static final int THREAD_COUNT = Integer.getInteger("issue11529.threads", 64);
    private static final int REQUEST_COUNT = Integer.getInteger("issue11529.requests", 512);
    private static final int RETRY_COUNT = Integer.getInteger("issue11529.retryCount", 1);
    private static final int PROGRESS_EVERY = Integer.getInteger("issue11529.progressEvery",
                                                                 Math.max(1, REQUEST_COUNT / 10));
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(THREAD_COUNT);
    private static final ExecutorService PEER_LOG_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final String REQUEST_BODY = """
            {"data":{"entity":2,"context":"all_fields_api_inventory","options":1,"entity_id":26030351,"provider_id":0,"page_id":1,"user_id":"1"}}
            """.trim();
    private static Process peerProcess;
    private static int peerPort;

    @BeforeAll
    static void beforeAll() throws Exception {
        peerPort = nextFreePort();
        peerProcess = startPeerProcess(peerPort);
        waitForPeer(peerPort, Duration.ofSeconds(20));
    }

    @AfterAll
    static void afterAll() throws InterruptedException {
        if (peerProcess != null) {
            peerProcess.destroy();
            if (!peerProcess.waitFor(10, TimeUnit.SECONDS)) {
                peerProcess.destroyForcibly();
                peerProcess.waitFor(10, TimeUnit.SECONDS);
            }
        }

        EXECUTOR.shutdown();
        if (!EXECUTOR.awaitTermination(30, TimeUnit.SECONDS)) {
            EXECUTOR.shutdownNow();
        }
        PEER_LOG_EXECUTOR.shutdown();
        if (!PEER_LOG_EXECUTOR.awaitTermination(30, TimeUnit.SECONDS)) {
            PEER_LOG_EXECUTOR.shutdownNow();
        }
    }

    private static void invoke(Http2Client client,
                               AtomicInteger ok,
                               AtomicInteger fail,
                               AtomicInteger completed,
                               Map<Integer, AtomicInteger> non200Statuses,
                               Map<String, AtomicInteger> errorTypes,
                               Lock latenciesLock,
                               List<Long> latenciesMs) {
        ensurePeerAlive();

        try {
            URI uri = URI.create("http://127.0.0.1:" + peerPort + APP_PATH);
            for (int attempt = 0; attempt <= RETRY_COUNT; attempt++) {
                long requestStarted = System.nanoTime();
                try (HttpClientResponse response = client.post()
                        .uri(uri)
                        .contentType(HttpMediaTypes.JSON_UTF_8)
                        .header(HeaderNames.ACCEPT, "application/json")
                        .submit(REQUEST_BODY)) {

                    int status = response.status().code();
                    response.inputStream().readAllBytes();
                    addLatency(latenciesLock, latenciesMs, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestStarted));
                    if (status == 200) {
                        ok.incrementAndGet();
                    } else {
                        fail.incrementAndGet();
                        non200Statuses.computeIfAbsent(status, ignored -> new AtomicInteger()).incrementAndGet();
                    }
                    return;
                } catch (Exception e) {
                    if (retryable(e) && attempt < RETRY_COUNT) {
                        continue;
                    }
                    fail.incrementAndGet();
                    String error = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
                    errorTypes.computeIfAbsent(error, ignored -> new AtomicInteger()).incrementAndGet();
                    return;
                }
            }
        } finally {
            int done = completed.incrementAndGet();
            if (done % PROGRESS_EVERY == 0 || done == REQUEST_COUNT) {
                System.out.println("PROGRESS completed " + done + " / " + REQUEST_COUNT);
                System.out.flush();
            }
        }
    }

    private static Process startPeerProcess(int port) throws IOException {
        String classPath = System.getProperty("surefire.test.class.path");
        if (classPath == null || classPath.isBlank()) {
            classPath = System.getProperty("java.class.path");
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
                javaBinary(),
                "-Dissue11529.jetty.sharedHeaders=" + Integer.getInteger("issue11529.jetty.sharedHeaders", 0),
                "-Dissue11529.jetty.varyingHeaders=" + Integer.getInteger("issue11529.jetty.varyingHeaders", 0),
                "-Dissue11529.maxConcurrentStreams=" + Integer.getInteger("issue11529.maxConcurrentStreams", -1),
                "-Dissue11529.chunkCount=" + Integer.getInteger("issue11529.chunkCount", 1),
                "-Dissue11529.chunkDelayMillis=" + Integer.getInteger("issue11529.chunkDelayMillis", 0),
                "-cp",
                classPath,
                JettyH2cPeerMain.class.getName(),
                Integer.toString(port));
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        PEER_LOG_EXECUTOR.submit(() -> consumePeerLog(process));
        return process;
    }

    private static void waitForPeer(int port, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            ensurePeerAlive();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
                return;
            } catch (IOException ignored) {
                Thread.sleep(100);
            }
        }
        fail("Jetty h2c peer did not start within " + timeout + "\nPeer log:\n" + peerLog());
    }

    private static void consumePeerLog(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(),
                                                                              StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendPeerLog(line);
            }
        } catch (IOException e) {
            appendPeerLog("Failed to read peer log: " + e);
        }
    }

    private static void ensurePeerAlive() {
        if (peerProcess != null && !peerProcess.isAlive()) {
            fail("Jetty h2c peer exited unexpectedly with code " + peerProcess.exitValue()
                         + "\nPeer log:\n" + peerLog());
        }
    }

    private static String peerLog() {
        PEER_LOG_LOCK.lock();
        try {
            return PEER_LOG.toString();
        } finally {
            PEER_LOG_LOCK.unlock();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to start test wave", e);
        }
    }

    private static int nextFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String javaBinary() {
        return System.getProperty("java.home") + "/bin/java";
    }

    private static boolean retryable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof UncheckedIOException) {
                return true;
            }
            if (current.getClass().getSimpleName().contains("StreamTimeoutException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String loadReport(int ok,
                                     int fail,
                                     long totalMs,
                                     Map<Integer, AtomicInteger> non200Statuses,
                                     Map<String, AtomicInteger> errorTypes,
                                     Lock latenciesLock,
                                     List<Long> latenciesMs) {
        StringBuilder report = new StringBuilder();
        report.append("\n=== WireMock-like h2c Load Report ===\n");
        report.append("Requests total: ").append(REQUEST_COUNT).append('\n');
        report.append("Success (200):  ").append(ok).append('\n');
        report.append("Failed:         ").append(fail).append('\n');
        report.append("Duration ms:    ").append(totalMs).append('\n');
        if (totalMs > 0) {
            report.append(String.format("RPS:            %.2f%n", REQUEST_COUNT * 1000.0 / totalMs));
        } else {
            report.append("RPS:            0.00\n");
        }

        List<Long> sorted = snapshotLatencies(latenciesLock, latenciesMs);
        if (!sorted.isEmpty()) {
            sorted.sort(Comparator.naturalOrder());
            report.append("Latency ms p50: ").append(percentile(sorted, 50)).append('\n');
            report.append("Latency ms p95: ").append(percentile(sorted, 95)).append('\n');
            report.append("Latency ms max: ").append(sorted.get(sorted.size() - 1)).append('\n');
        }

        if (!non200Statuses.isEmpty()) {
            report.append("Non-200 statuses:\n");
            non200Statuses.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> report.append("  ")
                            .append(entry.getKey())
                            .append(" -> ")
                            .append(entry.getValue().get())
                            .append('\n'));
        }

        if (!errorTypes.isEmpty()) {
            report.append("Exceptions:\n");
            errorTypes.entrySet().stream()
                    .sorted((left, right) -> Integer.compare(right.getValue().get(), left.getValue().get()))
                    .forEach(entry -> report.append("  ")
                            .append(entry.getKey())
                            .append(" -> ")
                            .append(entry.getValue().get())
                            .append('\n'));
        }

        return report.toString();
    }

    private static void appendPeerLog(String line) {
        PEER_LOG_LOCK.lock();
        try {
            PEER_LOG.append(line).append(System.lineSeparator());
        } finally {
            PEER_LOG_LOCK.unlock();
        }
    }

    private static void addLatency(Lock latenciesLock, List<Long> latenciesMs, long latencyMs) {
        latenciesLock.lock();
        try {
            latenciesMs.add(latencyMs);
        } finally {
            latenciesLock.unlock();
        }
    }

    private static List<Long> snapshotLatencies(Lock latenciesLock, List<Long> latenciesMs) {
        latenciesLock.lock();
        try {
            return new ArrayList<>(latenciesMs);
        } finally {
            latenciesLock.unlock();
        }
    }

    private static long percentile(List<Long> sorted, int percentile) {
        int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    @Test
    void priorKnowledgePostAgainstJettyH2cWireMockLikeLoad()
            throws Exception {

        Http2Client client = Http2Client.builder()
                .protocolConfig(Http2ClientProtocolConfig.builder()
                                        .priorKnowledge(true)
                                        .build())
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .readContinueTimeout(Duration.ofSeconds(5))
                .build();

        try {
            AtomicInteger ok = new AtomicInteger();
            AtomicInteger fail = new AtomicInteger();
            AtomicInteger completed = new AtomicInteger();
            Map<String, AtomicInteger> errorTypes = new ConcurrentHashMap<>();
            Map<Integer, AtomicInteger> non200Statuses = new ConcurrentHashMap<>();
            Lock latenciesLock = new ReentrantLock();
            List<Long> latenciesMs = new ArrayList<>(REQUEST_COUNT);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>(REQUEST_COUNT);

            System.out.printf("Starting WireMock-like h2c load: url=http://127.0.0.1:%d%s threads=%d requests=%d clients=%d%n",
                              peerPort,
                              APP_PATH,
                              THREAD_COUNT,
                              REQUEST_COUNT,
                              1);
            System.out.flush();

            long startedAt = System.nanoTime();
            for (int requestIndex = 0; requestIndex < REQUEST_COUNT; requestIndex++) {
                futures.add(EXECUTOR.submit(() -> {
                    await(start);
                    invoke(client, ok, fail, completed, non200Statuses, errorTypes, latenciesLock, latenciesMs);
                }));
            }

            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }

            long totalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            String report = loadReport(ok.get(), fail.get(), totalMs, non200Statuses, errorTypes, latenciesLock, latenciesMs);
            System.out.println(report);
            System.out.flush();

            if (fail.get() > 0 || !non200Statuses.isEmpty()) {
                fail(report + "\nPeer log:\n" + peerLog());
            }
        } finally {
            client.closeResource();
        }
    }

    /**
     * Out-of-process non-Helidon h2c peer used by the issue 11529 reproducer.
     */
    public static final class JettyH2cPeerMain {
        private static final int SHARED_HEADER_COUNT = Integer.getInteger("issue11529.jetty.sharedHeaders", 0);
        private static final int VARYING_HEADER_COUNT = Integer.getInteger("issue11529.jetty.varyingHeaders", 0);
        private static final int MAX_CONCURRENT_STREAMS = Integer.getInteger("issue11529.maxConcurrentStreams", -1);
        private static final int LARGE_HEADER_REPEAT = Integer.getInteger("issue11529.jetty.headerRepeat", 16);
        private static final int CHUNK_COUNT = Integer.getInteger("issue11529.chunkCount", 1);
        private static final int CHUNK_DELAY_MILLIS = Integer.getInteger("issue11529.chunkDelayMillis", 0);

        private JettyH2cPeerMain() {
        }

        public static void main(String[] args) throws Exception {
            int port = Integer.parseInt(args[0]);

            Server server = new Server();

            HttpConfiguration httpConfiguration = new HttpConfiguration();
            httpConfiguration.setRequestHeaderSize(256 * 1024);
            httpConfiguration.setResponseHeaderSize(256 * 1024);
            HTTP2CServerConnectionFactory h2c = new HTTP2CServerConnectionFactory(httpConfiguration);
            h2c.setMaxHeaderBlockFragment(256 * 1024);
            if (MAX_CONCURRENT_STREAMS > 0) {
                h2c.setMaxConcurrentStreams(MAX_CONCURRENT_STREAMS);
            }

            ServerConnector connector = new ServerConnector(server, h2c);
            connector.setPort(port);
            connector.setIdleTimeout(300_000);
            server.addConnector(connector);

            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
            context.setContextPath("/");
            ServletHolder servlet = new ServletHolder(new StressServlet());
            context.addServlet(servlet, APP_PATH);
            context.addServlet(servlet, "/rpc");
            server.setHandler(context);

            server.start();
            System.out.println("READY " + connector.getLocalPort()
                                       + " maxConcurrentStreams="
                                       + h2c.getMaxConcurrentStreams());
            System.out.flush();
            server.join();
        }

        private static String valueOrDefault(String value, String defaultValue) {
            return value == null ? defaultValue : value;
        }

        private static String largeHeaderValue(String execId, int i) {
            String seed = "issue-11529-jetty-" + execId + "-" + i + "-";
            return seed.repeat(LARGE_HEADER_REPEAT);
        }

        private static String responseBody(String execId, String waveId, int bodySize) {
            return "{\"status\":\"ok\""
                    + ",\"execId\":\"" + execId + "\""
                    + ",\"wave\":\"" + waveId + "\""
                    + ",\"bodySize\":" + bodySize
                    + "}";
        }

        static String responseChunk(String execId, String waveId, int bodySize, int chunk) {
            return "{\"execId\":\"" + execId
                    + "\",\"wave\":\"" + waveId
                    + "\",\"bodySize\":" + bodySize
                    + ",\"chunk\":\"" + String.format("%03d", chunk)
                    + "\"}";
        }

        private static final class StressServlet extends HttpServlet {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
                try {
                    String execId = valueOrDefault(req.getParameter("execId"), "00");
                    String waveId = valueOrDefault(req.getParameter("wave"), "00");
                    byte[] requestBody = req.getInputStream().readAllBytes();

                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.setContentType("application/json");
                    for (int i = 0; i < SHARED_HEADER_COUNT; i++) {
                        resp.addHeader("issue-11529-jetty-shared-" + i, largeHeaderValue("shared", i));
                    }
                    for (int i = 0; i < VARYING_HEADER_COUNT; i++) {
                        resp.addHeader("issue-11529-jetty-vary-" + i, largeHeaderValue(execId + "-" + waveId, i));
                    }

                    if (CHUNK_COUNT <= 1) {
                        resp.getOutputStream()
                                .write(responseBody(execId, waveId, requestBody.length).getBytes(StandardCharsets.UTF_8));
                    } else {
                        for (int i = 0; i < CHUNK_COUNT; i++) {
                            resp.getOutputStream()
                                    .write((responseChunk(execId, waveId, requestBody.length, i) + "\n")
                                                   .getBytes(StandardCharsets.UTF_8));
                            resp.flushBuffer();
                            if (CHUNK_DELAY_MILLIS > 0) {
                                Thread.sleep(CHUNK_DELAY_MILLIS);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while streaming response", e);
                } catch (IOException | RuntimeException t) {
                    t.printStackTrace(System.out);
                    System.out.flush();
                    throw t;
                }
            }
        }
    }
}
