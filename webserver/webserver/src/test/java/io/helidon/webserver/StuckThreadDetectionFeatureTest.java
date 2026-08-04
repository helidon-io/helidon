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

package io.helidon.webserver;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.buffers.DataReader;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.http1.Http1Prologue;
import io.helidon.webserver.spi.ServerFeature.ServerFeatureContext;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StuckThreadDetectionFeatureTest {
    @Test
    void defaultConfiguration() {
        StuckThreadDetectionConfig config = StuckThreadDetectionFeature.create().prototype();

        assertThat(config.enabled(), is(true));
        assertThat(config.threshold(), is(Duration.ofMinutes(10)));
        assertThat(config.checkPeriod(), is(Duration.ofMinutes(1)));
        assertThat(config.weight(), is(StuckThreadDetectionFeature.WEIGHT));
        assertThat(config.sockets().isEmpty(), is(true));
    }

    @Test
    void durationsMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                     () -> StuckThreadDetectionConfig.builder()
                             .threshold(Duration.ZERO)
                             .buildPrototype());
        assertThrows(IllegalArgumentException.class,
                     () -> StuckThreadDetectionConfig.builder()
                             .checkPeriod(Duration.ofSeconds(-1))
                             .buildPrototype());
    }

    @Test
    void automaticDiscoveryDoesNotEnableDetectionWithoutConfiguration() {
        var feature = new StuckThreadDetectionFeatureProvider()
                .create(Config.empty(), StuckThreadDetectionFeature.FEATURE_ID);
        var context = mock(ServerFeatureContext.class);

        feature.setup(context);

        assertThat(feature.prototype().enabled(), is(false));
        verify(context, never()).sockets();
    }

    @Test
    void explicitlyDisabledFeatureDoesNotEnableDetection() {
        Config config = Config.just(ConfigSources.create(Map.of("enabled", "false")));
        var feature = StuckThreadDetectionFeature.create(config);
        var context = mock(ServerFeatureContext.class);

        feature.setup(context);

        verify(context, never()).sockets();
    }

    @Test
    void configuredFeatureIsDiscovered() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "features.stuck-thread-detection.threshold", "PT2M",
                "features.stuck-thread-detection.check-period", "PT10S")));

        var webServerConfig = WebServer.builder()
                .config(config)
                .buildPrototype();
        var feature = webServerConfig.features()
                .stream()
                .filter(StuckThreadDetectionFeature.class::isInstance)
                .map(StuckThreadDetectionFeature.class::cast)
                .findFirst()
                .orElseThrow();

        assertThat(feature.prototype().enabled(), is(true));
        assertThat(feature.prototype().threshold(), is(Duration.ofMinutes(2)));
        assertThat(feature.prototype().checkPeriod(), is(Duration.ofSeconds(10)));
    }

    @Test
    void tracksRequestAcceptedBeforeAfterStart() throws Exception {
        var config = StuckThreadDetectionConfig.builder()
                .threshold(Duration.ofMillis(20))
                .checkPeriod(Duration.ofMillis(5))
                .buildPrototype();
        var filter = new StuckThreadDetectionFilter(config, WebServer.DEFAULT_SOCKET_NAME);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        RoutingRequest request = mock(RoutingRequest.class);
        when(request.prologue()).thenReturn(HttpPrologue.create("HTTP/1.1",
                                                               "HTTP",
                                                               "1.1",
                                                               Method.GET,
                                                               "/during-startup",
                                                               true));
        when(request.id()).thenReturn(13);
        when(request.serverSocketId()).thenReturn("server-socket");
        when(request.socketId()).thenReturn("connection-socket");

        filter.beforeStart();
        try (TestLogHandler logs = new TestLogHandler()) {
            Thread requestThread = Thread.ofVirtual().start(() -> {
                try {
                    filter.filter(() -> {
                        started.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                    }, request, mock(RoutingResponse.class));
                } catch (Throwable t) {
                    failure.set(t);
                }
            });
            try {
                assertThat("Request handler did not start", started.await(5, TimeUnit.SECONDS), is(true));

                filter.afterStart(mock(WebServer.class));

                LogRecord warning = logs.await(Level.WARNING);
                assertThat(warning.getMessage(), containsString("GET /during-startup HTTP/1.1"));
            } finally {
                release.countDown();
                requestThread.join(5000);
                filter.afterStop();
            }
        }
        assertThat("Request handler failed", failure.get(), is((Throwable) null));
    }

    @Test
    void reportsStuckRequestAndRecovery() throws Exception {
        var config = StuckThreadDetectionConfig.builder()
                .threshold(Duration.ofMillis(20))
                .checkPeriod(Duration.ofMillis(5))
                .buildPrototype();
        var filter = new StuckThreadDetectionFilter(config, WebServer.DEFAULT_SOCKET_NAME);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        FilterChain chain = () -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        };

        RoutingRequest request = mock(RoutingRequest.class);
        when(request.prologue()).thenReturn(HttpPrologue.create("HTTP/1.1",
                                                               "HTTP",
                                                               "1.1",
                                                               Method.GET,
                                                               "http://alice:s3cr3t@localhost/slow?secret=value",
                                                               true));
        when(request.id()).thenReturn(7);
        when(request.serverSocketId()).thenReturn("server-socket");
        when(request.socketId()).thenReturn("connection-socket");
        try (TestLogHandler logs = new TestLogHandler()) {
            filter.beforeStart();
            filter.afterStart(mock(WebServer.class));
            Thread requestThread = Thread.ofVirtual().start(() -> {
                try {
                    filter.filter(chain, request, mock(RoutingResponse.class));
                } catch (Throwable t) {
                    failure.set(t);
                }
            });
            try {
                assertThat("Request handler did not start", started.await(5, TimeUnit.SECONDS), is(true));

                LogRecord warning = logs.await(Level.WARNING);
                assertThat(warning.getMessage(), containsString("Request has been running for"));
                assertThat(warning.getMessage(), containsString("GET /slow HTTP/1.1"));
                assertThat(warning.getMessage(), containsString("request id: 7"));
                assertThat(warning.getMessage(), containsString("server socket: server-socket"));
                assertThat(warning.getMessage(), containsString("connection: connection-socket"));
                assertThat(warning.getMessage(), containsString("virtual: true"));
                assertThat(warning.getMessage(), containsString("\tat "));
                assertThat(warning.getMessage(), not(containsString("secret=value")));
                assertThat(warning.getMessage(), not(containsString("alice")));
                assertThat(warning.getMessage(), not(containsString("s3cr3t")));
                assertThat("Request was reported more than once",
                           logs.records.poll(50, TimeUnit.MILLISECONDS),
                           is((LogRecord) null));

                release.countDown();
                requestThread.join(5000);
                assertThat("Request handler did not finish", requestThread.isAlive(), is(false));
                assertThat("Request handler failed", failure.get(), is((Throwable) null));

                LogRecord recovery = logs.await(Level.INFO);
                assertThat(recovery.getMessage(), containsString("Request previously reported as stuck completed after"));
                assertThat(recovery.getMessage(), containsString("GET /slow HTTP/1.1"));
                assertThat(recovery.getMessage(), not(containsString("secret=value")));
                assertThat(recovery.getMessage(), not(containsString("alice")));
                assertThat(recovery.getMessage(), not(containsString("s3cr3t")));
            } finally {
                release.countDown();
                requestThread.join(5000);
                filter.afterStop();
            }
        }
    }

    @Test
    void reportsConnectAuthority() throws Exception {
        DataReader reader = DataReader.create(() -> "CONNECT example.com:443 HTTP/1.1\r\n"
                .getBytes(StandardCharsets.US_ASCII));
        HttpPrologue prologue = new Http1Prologue(reader, 100, true).readPrologue();

        assertConnectLog(prologue, "example.com:443", null);
    }

    @Test
    void doesNotReportUnverifiedConnectAuthority() throws Exception {
        HttpPrologue prologue = HttpPrologue.create("HTTP/1.1",
                                                    "HTTP",
                                                    "1.1",
                                                    Method.CONNECT,
                                                    "alice:s3cr3t@example.com:443",
                                                    true);

        assertConnectLog(prologue, null, "alice:s3cr3t");
    }

    private void assertConnectLog(HttpPrologue prologue, String expected, String unexpected) throws Exception {
        var config = StuckThreadDetectionConfig.builder()
                .threshold(Duration.ofMillis(20))
                .checkPeriod(Duration.ofMillis(5))
                .buildPrototype();
        var filter = new StuckThreadDetectionFilter(config, WebServer.DEFAULT_SOCKET_NAME);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        RoutingRequest request = mock(RoutingRequest.class);
        when(request.prologue()).thenReturn(prologue);
        when(request.id()).thenReturn(10);
        when(request.serverSocketId()).thenReturn("server-socket");
        when(request.socketId()).thenReturn("connection-socket");
        try (TestLogHandler logs = new TestLogHandler()) {
            filter.beforeStart();
            filter.afterStart(mock(WebServer.class));
            Thread requestThread = Thread.ofVirtual().start(() -> {
                try {
                    filter.filter(() -> {
                        started.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                    }, request, mock(RoutingResponse.class));
                } catch (Throwable t) {
                    failure.set(t);
                }
            });
            try {
                boolean handlerStarted = started.await(5, TimeUnit.SECONDS);
                if (!handlerStarted) {
                    assertThat("Request handler failed before it started", failure.get(), is((Throwable) null));
                }
                assertThat("Request handler did not start", handlerStarted, is(true));

                LogRecord warning = logs.await(Level.WARNING);
                if (expected != null) {
                    assertThat(warning.getMessage(), containsString("CONNECT " + expected + " HTTP/1.1"));
                }
                if (unexpected != null) {
                    assertThat(warning.getMessage(), not(containsString(unexpected)));
                }
            } finally {
                release.countDown();
                requestThread.join(5000);
                filter.afterStop();
            }
        }
        assertThat("Request handler failed", failure.get(), is((Throwable) null));
    }

    @Test
    void warningLoggingDoesNotBlockRequestCompletion() throws Exception {
        var config = StuckThreadDetectionConfig.builder()
                .threshold(Duration.ofMillis(20))
                .checkPeriod(Duration.ofMillis(5))
                .buildPrototype();
        var filter = new StuckThreadDetectionFilter(config, WebServer.DEFAULT_SOCKET_NAME);
        var releaseRequest = new CountDownLatch(1);
        var warningStarted = new CountDownLatch(1);
        var releaseWarning = new CountDownLatch(1);
        var requestCompleted = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        RoutingRequest request = mock(RoutingRequest.class);
        when(request.prologue()).thenReturn(HttpPrologue.create("HTTP/1.1",
                                                               "HTTP",
                                                               "1.1",
                                                               Method.GET,
                                                               "/logging",
                                                               true));
        when(request.id()).thenReturn(8);
        when(request.serverSocketId()).thenReturn("server-socket");
        when(request.socketId()).thenReturn("connection-socket");
        try (TestLogHandler logs = new TestLogHandler(warningStarted, releaseWarning)) {
            filter.beforeStart();
            filter.afterStart(mock(WebServer.class));
            Thread requestThread = Thread.ofVirtual().start(() -> {
                try {
                    filter.filter(() -> {
                        try {
                            releaseRequest.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                    }, request, mock(RoutingResponse.class));
                } catch (Throwable t) {
                    failure.set(t);
                } finally {
                    requestCompleted.countDown();
                }
            });
            try {
                assertThat("Warning logger was not entered", warningStarted.await(5, TimeUnit.SECONDS), is(true));
                releaseRequest.countDown();
                assertThat("Warning logging blocked request completion",
                           requestCompleted.await(5, TimeUnit.SECONDS),
                           is(true));
                assertThat("Request handler failed", failure.get(), is((Throwable) null));

                releaseWarning.countDown();
                LogRecord warning = logs.await(Level.WARNING);
                assertThat(warning.getMessage(), containsString("GET /logging HTTP/1.1"));
                LogRecord recovery = logs.await(Level.INFO);
                assertThat(recovery.getMessage(), containsString("GET /logging HTTP/1.1"));
            } finally {
                releaseRequest.countDown();
                releaseWarning.countDown();
                requestThread.join(5000);
                filter.afterStop();
            }
        }
    }

    @Test
    void boundsRecoveryBacklogWhenInfoLoggingBlocks() throws Exception {
        var config = StuckThreadDetectionConfig.builder()
                .threshold(Duration.ofNanos(1))
                .checkPeriod(Duration.ofNanos(1))
                .buildPrototype();
        var filter = new StuckThreadDetectionFilter(config, WebServer.DEFAULT_SOCKET_NAME);
        int requestCount = StuckThreadDetectionFilter.MAX_QUEUED_RECOVERIES + 2;
        var handlersStarted = new CountDownLatch(requestCount);
        var releaseFirstRequest = new CountDownLatch(1);
        var releaseRequests = new CountDownLatch(1);
        var probeStarted = new CountDownLatch(1);
        var releaseProbe = new CountDownLatch(1);
        var infoStarted = new CountDownLatch(1);
        var releaseInfo = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        var requestThreads = new ArrayList<Thread>(requestCount);
        var probeThread = new AtomicReference<Thread>();
        RoutingRequest request = mock(RoutingRequest.class);
        when(request.prologue()).thenReturn(HttpPrologue.create("HTTP/1.1",
                                                               "HTTP",
                                                               "1.1",
                                                               Method.GET,
                                                               "/recovery-backlog",
                                                               true));
        when(request.id()).thenReturn(11);
        when(request.serverSocketId()).thenReturn("server-socket");
        when(request.socketId()).thenReturn("connection-socket");
        RoutingRequest probeRequest = mock(RoutingRequest.class);
        when(probeRequest.prologue()).thenReturn(HttpPrologue.create("HTTP/1.1",
                                                                    "HTTP",
                                                                    "1.1",
                                                                    Method.GET,
                                                                    "/recovery-probe",
                                                                    true));
        when(probeRequest.id()).thenReturn(12);
        when(probeRequest.serverSocketId()).thenReturn("server-socket");
        when(probeRequest.socketId()).thenReturn("connection-socket");
        try (TestLogHandler logs = new TestLogHandler(Level.INFO, infoStarted, releaseInfo)) {
            filter.beforeStart();
            filter.afterStart(mock(WebServer.class));
            for (int i = 0; i < requestCount; i++) {
                CountDownLatch releaseRequest = i == 0 ? releaseFirstRequest : releaseRequests;
                requestThreads.add(Thread.ofVirtual().start(() -> {
                    try {
                        filter.filter(() -> {
                            handlersStarted.countDown();
                            try {
                                releaseRequest.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(e);
                            }
                        }, request, mock(RoutingResponse.class));
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    }
                }));
            }
            try {
                assertThat("Request handlers did not start", handlersStarted.await(5, TimeUnit.SECONDS), is(true));
                for (int i = 0; i < requestCount; i++) {
                    logs.await(Level.WARNING);
                }

                releaseFirstRequest.countDown();
                assertThat("Recovery logger was not entered", infoStarted.await(5, TimeUnit.SECONDS), is(true));
                releaseRequests.countDown();
                for (Thread requestThread : requestThreads) {
                    requestThread.join(5000);
                    assertThat("Blocked recovery logging blocked request completion", requestThread.isAlive(), is(false));
                }
                assertThat("Request handler failed", failure.get(), is((Throwable) null));

                probeThread.set(Thread.ofVirtual().start(() -> {
                    try {
                        filter.filter(() -> {
                            probeStarted.countDown();
                            try {
                                releaseProbe.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(e);
                            }
                        }, probeRequest, mock(RoutingResponse.class));
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    }
                }));
                assertThat("Probe request handler did not start", probeStarted.await(5, TimeUnit.SECONDS), is(true));

                releaseInfo.countDown();
                int recoveryLogsBeforeScan = 0;
                boolean sawOmittedSummary = false;
                while (true) {
                    LogRecord record = logs.records.poll(10, TimeUnit.SECONDS);
                    if (record == null) {
                        throw new AssertionError("Probe request was not reported");
                    }
                    if (record.getLevel().equals(Level.WARNING)
                            && record.getMessage().contains("/recovery-probe")) {
                        break;
                    }
                    if (record.getLevel().equals(Level.INFO)) {
                        recoveryLogsBeforeScan++;
                        if (record.getMessage().contains("bounded recovery queue was full")) {
                            assertThat(record.getMessage(), containsString("1 request recovery log message was omitted"));
                            sawOmittedSummary = true;
                        }
                    }
                }
                assertThat("Recovery backlog delayed the next stuck-request scan",
                           recoveryLogsBeforeScan <= StuckThreadDetectionFilter.MAX_RECOVERY_LOGS_PER_CYCLE,
                           is(true));

                releaseProbe.countDown();
                probeThread.get().join(5000);
                assertThat("Probe request did not finish", probeThread.get().isAlive(), is(false));
                boolean sawProbeRecovery = false;
                while (!sawOmittedSummary || !sawProbeRecovery) {
                    LogRecord record = logs.records.poll(10, TimeUnit.SECONDS);
                    if (record == null) {
                        throw new AssertionError("Recovery backlog was not fully reported");
                    }
                    if (record.getLevel().equals(Level.INFO)) {
                        if (record.getMessage().contains("bounded recovery queue was full")) {
                            assertThat(record.getMessage(), containsString("1 request recovery log message was omitted"));
                            sawOmittedSummary = true;
                        }
                        sawProbeRecovery |= record.getMessage().contains("/recovery-probe");
                    }
                }
                assertThat("Request handler failed", failure.get(), is((Throwable) null));
            } finally {
                releaseFirstRequest.countDown();
                releaseRequests.countDown();
                releaseProbe.countDown();
                releaseInfo.countDown();
                for (Thread requestThread : requestThreads) {
                    requestThread.join(5000);
                }
                if (probeThread.get() != null) {
                    probeThread.get().join(5000);
                }
                filter.afterStop();
            }
        }
    }

    @Test
    void stopPreservesCallerInterruptAndWaitsForMonitor() throws Exception {
        var filter = new StuckThreadDetectionFilter(StuckThreadDetectionFeature.create().prototype(),
                                                    WebServer.DEFAULT_SOCKET_NAME);
        var interrupted = new AtomicReference<Boolean>();
        filter.beforeStart();
        filter.afterStart(mock(WebServer.class));

        Thread stopThread = Thread.ofVirtual().start(() -> {
            Thread.currentThread().interrupt();
            filter.afterStop();
            interrupted.set(Thread.currentThread().isInterrupted());
        });

        stopThread.join(5000);
        assertThat("Feature stop did not finish", stopThread.isAlive(), is(false));
        assertThat("Feature stop did not restore caller interrupt", interrupted.get(), is(true));
    }

    @Test
    void stopDoesNotWaitIndefinitelyForBlockedLogger() throws Exception {
        var config = StuckThreadDetectionConfig.builder()
                .threshold(Duration.ofMillis(20))
                .checkPeriod(Duration.ofMillis(5))
                .buildPrototype();
        var filter = new StuckThreadDetectionFilter(config, WebServer.DEFAULT_SOCKET_NAME);
        var releaseRequest = new CountDownLatch(1);
        var warningStarted = new CountDownLatch(1);
        var releaseWarning = new CountDownLatch(1);
        RoutingRequest request = mock(RoutingRequest.class);
        when(request.prologue()).thenReturn(HttpPrologue.create("HTTP/1.1",
                                                               "HTTP",
                                                               "1.1",
                                                               Method.GET,
                                                               "/blocked-logger",
                                                               true));
        when(request.id()).thenReturn(9);
        when(request.serverSocketId()).thenReturn("server-socket");
        when(request.socketId()).thenReturn("connection-socket");
        try (TestLogHandler ignored = new TestLogHandler(warningStarted, releaseWarning, true)) {
            filter.beforeStart();
            filter.afterStart(mock(WebServer.class));
            Thread requestThread = Thread.ofVirtual().start(() -> {
                filter.filter(() -> {
                    try {
                        releaseRequest.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                }, request, mock(RoutingResponse.class));
            });
            assertThat("Warning logger was not entered", warningStarted.await(5, TimeUnit.SECONDS), is(true));

            Thread stopThread = Thread.ofVirtual().start(filter::afterStop);
            stopThread.join(3000);
            boolean stopped = !stopThread.isAlive();

            releaseWarning.countDown();
            releaseRequest.countDown();
            stopThread.join(5000);
            requestThread.join(5000);
            assertThat("Feature stop waited indefinitely for blocked logging", stopped, is(true));
        }
    }

    private static final class TestLogHandler extends Handler implements AutoCloseable {
        private final LinkedBlockingQueue<LogRecord> records = new LinkedBlockingQueue<>();
        private final Logger logger = Logger.getLogger(StuckThreadDetectionFilter.class.getName());
        private final Level originalLevel = logger.getLevel();
        private final boolean originalUseParentHandlers = logger.getUseParentHandlers();
        private final CountDownLatch warningStarted;
        private final CountDownLatch releaseWarning;
        private final boolean ignoreWarningInterrupt;
        private final Level blockedLevel;

        private TestLogHandler() {
            this(Level.WARNING, null, null, false);
        }

        private TestLogHandler(CountDownLatch warningStarted, CountDownLatch releaseWarning) {
            this(Level.WARNING, warningStarted, releaseWarning, false);
        }

        private TestLogHandler(CountDownLatch warningStarted,
                               CountDownLatch releaseWarning,
                               boolean ignoreWarningInterrupt) {
            this(Level.WARNING, warningStarted, releaseWarning, ignoreWarningInterrupt);
        }

        private TestLogHandler(Level blockedLevel,
                               CountDownLatch warningStarted,
                               CountDownLatch releaseWarning) {
            this(blockedLevel, warningStarted, releaseWarning, false);
        }

        private TestLogHandler(Level blockedLevel,
                               CountDownLatch warningStarted,
                               CountDownLatch releaseWarning,
                               boolean ignoreWarningInterrupt) {
            this.warningStarted = warningStarted;
            this.releaseWarning = releaseWarning;
            this.ignoreWarningInterrupt = ignoreWarningInterrupt;
            this.blockedLevel = blockedLevel;
            setLevel(Level.ALL);
            logger.setLevel(Level.ALL);
            logger.setUseParentHandlers(false);
            logger.addHandler(this);
        }

        @Override
        public void publish(LogRecord record) {
            if (warningStarted != null && record.getLevel().equals(blockedLevel)) {
                warningStarted.countDown();
                boolean interrupted = false;
                while (true) {
                    try {
                        releaseWarning.await();
                        break;
                    } catch (InterruptedException e) {
                        if (!ignoreWarningInterrupt) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        interrupted = true;
                    }
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            if (isLoggable(record)) {
                records.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            logger.removeHandler(this);
            logger.setUseParentHandlers(originalUseParentHandlers);
            logger.setLevel(originalLevel);
        }

        private LogRecord await(Level level) throws InterruptedException {
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new AssertionError("No " + level + " log record received");
                }
                LogRecord record = records.poll(remaining, TimeUnit.NANOSECONDS);
                if (record == null) {
                    throw new AssertionError("No " + level + " log record received");
                }
                if (record.getLevel().equals(level)) {
                    return record;
                }
            }
        }
    }
}
