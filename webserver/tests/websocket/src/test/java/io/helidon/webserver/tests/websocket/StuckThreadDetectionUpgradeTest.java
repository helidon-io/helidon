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

package io.helidon.webserver.tests.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.webserver.Router;
import io.helidon.webserver.StuckThreadDetectionFeature;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.spi.ServerFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpFeatures;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.websocket.WsRouting;
import io.helidon.websocket.WsCloseCodes;
import io.helidon.websocket.WsListener;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@ServerTest
class StuckThreadDetectionUpgradeTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final CountDownLatch UPGRADE_STARTED = new CountDownLatch(1);
    private static final CountDownLatch RELEASE_UPGRADE = new CountDownLatch(1);

    private final int port;

    StuckThreadDetectionUpgradeTest(WebServer server) {
        this.port = server.port();
    }

    @SetUpFeatures
    static List<ServerFeature> features() {
        return List.of(StuckThreadDetectionFeature.create(config -> config
                .threshold(Duration.ofMillis(20))
                .checkPeriod(Duration.ofMillis(5))));
    }

    @SetUpRoute
    static void routing(Router.RouterBuilder<?> router) {
        router.addRouting(WsRouting.builder().endpoint("/upgrade", new WsListener() {
            @Override
            public Optional<Headers> onHttpUpgrade(HttpPrologue prologue, Headers headers) {
                UPGRADE_STARTED.countDown();
                try {
                    RELEASE_UPGRADE.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return Optional.empty();
            }
        }));
    }

    @Test
    void completesDetectionWhenRequestUpgrades() throws Exception {
        var client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();

        try (TestLogHandler logs = new TestLogHandler()) {
            var webSocketFuture = client.newWebSocketBuilder()
                    .buildAsync(URI.create("ws://localhost:" + port + "/upgrade"), new WebSocket.Listener() { });
            try {
                assertThat("WebSocket upgrade did not start",
                           UPGRADE_STARTED.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                           is(true));

                LogRecord warning = logs.await(Level.WARNING);
                assertThat(warning.getMessage(), containsString("GET /upgrade HTTP/1.1"));

                RELEASE_UPGRADE.countDown();
                WebSocket webSocket = webSocketFuture.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

                LogRecord recovery = logs.await(Level.INFO);
                assertThat(recovery.getMessage(), containsString("GET /upgrade HTTP/1.1"));

                webSocket.sendClose(WsCloseCodes.NORMAL_CLOSE, "normal")
                        .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            } finally {
                RELEASE_UPGRADE.countDown();
            }
        }
    }

    private static final class TestLogHandler extends Handler implements AutoCloseable {
        private final LinkedBlockingQueue<LogRecord> records = new LinkedBlockingQueue<>();
        private final Logger logger = Logger.getLogger("io.helidon.webserver.StuckThreadDetectionFilter");
        private final Level originalLevel = logger.getLevel();
        private final boolean originalUseParentHandlers = logger.getUseParentHandlers();

        private TestLogHandler() {
            setLevel(Level.ALL);
            logger.setLevel(Level.ALL);
            logger.setUseParentHandlers(false);
            logger.addHandler(this);
        }

        @Override
        public void publish(LogRecord record) {
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
            long deadline = System.nanoTime() + TIMEOUT.toNanos();
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
