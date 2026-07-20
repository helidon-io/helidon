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

package io.helidon.webserver.tests.http2;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.webserver.StuckThreadDetectionFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Route;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.spi.ServerFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpFeatures;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.GET;
import static io.helidon.http.Method.PUT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@ServerTest
class StuckThreadDetectionTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String PATH = "/stuck-thread-detection";
    private static final CountDownLatch REQUEST_STARTED = new CountDownLatch(1);
    private static final CountDownLatch RELEASE_REQUEST = new CountDownLatch(1);

    private final URI uri;

    StuckThreadDetectionTest(URI uri) {
        this.uri = uri;
    }

    @SetUpFeatures
    static List<ServerFeature> features() {
        return List.of(StuckThreadDetectionFeature.create(config -> config
                .threshold(Duration.ofMillis(20))
                .checkPeriod(Duration.ofMillis(5))));
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.route(Http1Route.route(GET, PATH, (req, res) -> res.send()))
                .route(Http2Route.route(PUT, PATH, (req, res) -> {
                    REQUEST_STARTED.countDown();
                    try {
                        RELEASE_REQUEST.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    res.send();
                }));
    }

    @Test
    void tracksHttp2BusinessLogic() throws Exception {
        var client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        URI resource = uri.resolve(PATH);
        client.send(HttpRequest.newBuilder()
                            .GET()
                            .version(HttpClient.Version.HTTP_2)
                            .uri(resource)
                            .build(),
                    HttpResponse.BodyHandlers.discarding());

        try (TestLogHandler logs = new TestLogHandler()) {
            var response = client.sendAsync(HttpRequest.newBuilder()
                                                     .version(HttpClient.Version.HTTP_2)
                                                     .uri(resource)
                                                     .PUT(HttpRequest.BodyPublishers.noBody())
                                                     .build(),
                                             HttpResponse.BodyHandlers.discarding());
            try {
                assertThat("HTTP/2 request handler did not start",
                           REQUEST_STARTED.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                           is(true));

                LogRecord warning = logs.await(Level.WARNING);
                assertThat(warning.getMessage(), containsString("PUT " + PATH + " HTTP/2.0"));

                RELEASE_REQUEST.countDown();
                assertThat(response.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).statusCode(), is(200));

                LogRecord recovery = logs.await(Level.INFO);
                assertThat(recovery.getMessage(), containsString("PUT " + PATH + " HTTP/2.0"));
            } finally {
                RELEASE_REQUEST.countDown();
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
