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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.http.DirectHandler;
import io.helidon.http.RequestException;
import io.helidon.http.Status;
import io.helidon.logging.common.LogConfig;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@ResourceLock("webserver-error-loggers")
@ResourceLock("webserver-error-log-summary")
class ErrorLogLimitingTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    static {
        LogConfig.configureRuntime();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void repeatedFailuresKeepResponsesAndFirstThrowable(boolean http2) throws Exception {
        try (LogCapture logs = new LogCapture()) {
            try (TestServer server = new TestServer(builder -> builder.errorLog(log -> log.maxPerError(1)))) {
                for (int i = 0; i < 5; i++) {
                    assertError(server.request(http2, false, "/failure?input=" + i));
                }
            }
            assertThat("changing attacker-controlled messages must not bypass the limit", logs.failures(), hasSize(1));
            Throwable failure = logs.failures().getFirst().getThrown();
            assertThat(failure, instanceOf(RequestException.class));
            assertThat(failure.getCause(), instanceOf(IllegalStateException.class));
            assertThat(failure.getCause().getMessage(), is("input=0"));
            assertThat(failure.getCause().getStackTrace()[0].getMethodName(), is("fail"));
        }
    }

    @Test
    void differentFailureSitesAndTypesHaveSeparateAllowances() throws Exception {
        try (LogCapture logs = new LogCapture()) {
            try (TestServer server = new TestServer(builder -> builder.errorLog(log -> log.maxPerError(1)))) {
                for (String path : List.of("/failure", "/other-site", "/other-type")) {
                    assertError(server.request(false, false, path));
                    assertError(server.request(true, false, path));
                }
            }
            assertThat(logs.failures(), hasSize(3));
        }
    }

    @Test
    void overallAllowanceIsSharedAcrossSocketsAndProtocols() throws Exception {
        try (LogCapture logs = new LogCapture()) {
            try (TestServer server = new TestServer(builder -> builder.errorLog(log -> log
                    .maxPerError(10)
                    .maxTotal(2)))) {
                assertError(server.request(false, false, "/failure"));
                assertError(server.request(true, true, "/other-site"));
                assertError(server.request(true, false, "/other-type"));
                assertError(server.request(false, true, "/other-type"));
            }
            assertThat("both listeners and protocols must share the server's flood budget", logs.failures(), hasSize(2));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void expectedErrorsAndExplicitStatusDoNotConsumeAllowance(boolean http2) throws Exception {
        try (LogCapture logs = new LogCapture()) {
            try (TestServer server = new TestServer(builder -> builder.errorLog(log -> log.maxTotal(1)))) {
                assertThat(server.request(http2, false, "/bad-request").status(), is(400));
                assertThat(server.request(http2, false, "/missing").status(), is(404));
                Response explicit = server.request(http2, false, "/explicit-500");
                assertThat(explicit.status(), is(500));
                assertThat(explicit.body(), is("application response"));
                assertError(server.request(http2, false, "/failure"));
            }
            assertThat(logs.failures(), hasSize(1));
            assertThat(logs.failures().getFirst().getThrown().getCause(), instanceOf(IllegalStateException.class));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void disabledLimitingLogsEveryFailure(boolean http2) throws Exception {
        try (LogCapture logs = new LogCapture()) {
            try (TestServer server = new TestServer(builder -> builder.errorLog(log -> log.enabled(false)))) {
                for (int i = 0; i < 4; i++) {
                    assertError(server.request(http2, false, "/failure"));
                }
            }
            assertThat(logs.failures(), hasSize(4));
        }
    }

    @Test
    void reportsSuppressionWhenTrafficStops() throws Exception {
        try (LogCapture logs = new LogCapture();
             TestServer server = new TestServer(builder -> builder.errorLog(log -> log
                     .window(Duration.ofSeconds(5))
                     .maxPerError(1)))) {
            assertError(server.request(false, false, "/failure"));
            assertError(server.request(false, false, "/failure"));
            assertError(server.request(false, false, "/failure"));

            LogRecord summary = logs.summaries.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            assertThat("suppression must be reported without another request", summary, notNullValue());
            assertThat(summary.getMessage(), containsString("Suppressed 2 internal server error log entries"));
            assertThat(summary.getMessage(), containsString("per-error limit: 2"));
            assertThat(logs.failures(), hasSize(1));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingSendAndRerouteLoopDiagnosticsAreLimited(boolean http2) throws Exception {
        try (LogCapture logs = new LogCapture()) {
            try (TestServer server = new TestServer(builder -> builder.errorLog(log -> log.maxPerError(1)))) {
                for (int i = 0; i < 4; i++) {
                    assertError(server.request(http2, false, "/not-sent?input=" + i));
                    Response reroute = server.request(http2, false, "/reroute-loop?input=" + i);
                    assertThat(reroute.status(), is(500));
                    assertThat(reroute.body(), is("Too many reroutes"));
                }
            }
            List<LogRecord> missingSend = logs.diagnostics("io.helidon.webserver.http.HttpRoutingImpl", "A route MUST");
            assertThat("missing-send diagnostic must not include the changing URI in its identity", missingSend, hasSize(1));
            assertThat(missingSend.getFirst().getThrown().getCause().getMessage(), containsString("/not-sent"));
            List<LogRecord> reroutes = logs.diagnostics("io.helidon.webserver.http.HttpRoutingImpl",
                                                      "Maximum reroute count exceeded");
            assertThat(reroutes, hasSize(1));
            assertThat(reroutes.getFirst().getThrown().getMessage(), is("Too many reroutes"));
            assertThat("the resulting direct-handler logs must also be limited", logs.failures(), hasSize(2));
        }
    }

    @Test
    void invalidFinalInformationalResponseDiagnosticIsLimited() throws Exception {
        try (LogCapture logs = new LogCapture()) {
            try (TestServer server = new TestServer(builder -> builder.errorLog(log -> log.maxPerError(1)))) {
                for (int i = 0; i < 4; i++) {
                    Response response = server.request(true, false, "/informational?input=" + i);
                    assertThat(response.status(), is(500));
                    assertThat(response.body(), is(""));
                    assertThat(response.contentLength(), is(0L));
                }
            }
            assertThat(logs.diagnostics("io.helidon.webserver.http2.Http2ServerResponse",
                                        "Attempt to send a final informational response"), hasSize(1));
        }
    }

    @Test
    void noEntityStatusWithEntityDiagnosticIsLimited() throws Exception {
        try (LogCapture logs = new LogCapture()) {
            try (TestServer server = new TestServer(builder -> builder.errorLog(log -> log.maxPerError(1)))) {
                for (int i = 0; i < 4; i++) {
                    assertError(server.request(false, false, "/no-entity?input=" + i));
                }
            }
            List<LogRecord> noEntity = logs.diagnostics("io.helidon.webserver.http1.Http1ServerResponse",
                                                       "Attempt to send a no-entity status with entity");
            assertThat(noEntity, hasSize(1));
            assertThat(noEntity.getFirst().getThrown().getMessage(), containsString(Status.NO_CONTENT_204.text()));
            assertThat(logs.diagnostics("io.helidon.webserver.http.HttpRoutingImpl", "A route MUST"), hasSize(1));
            assertThat(logs.failures(), hasSize(1));
        }
    }

    private static void routing(HttpRouting.Builder routing) {
        routing.get("/failure", ErrorLogLimitingTest::fail)
                .get("/other-site", (_, _) -> {
                    throw new IllegalStateException("different failure site");
                })
                .get("/other-type", (_, _) -> {
                    throw new IllegalArgumentException("different failure type");
                })
                .get("/bad-request", (_, _) -> {
                    throw RequestException.builder()
                            .type(DirectHandler.EventType.BAD_REQUEST)
                            .message("expected bad request")
                            .build();
                })
                .get("/explicit-500", (_, response) -> response
                        .status(Status.INTERNAL_SERVER_ERROR_500)
                        .send("application response"))
                .get("/not-sent", (_, _) -> { })
                .get("/reroute-loop", (_, response) -> response.reroute("/reroute-loop"))
                .get("/informational", (_, response) -> response.status(Status.CONTINUE_100).send())
                .get("/no-entity", (_, response) -> response.status(Status.NO_CONTENT_204).send(new byte[] {1}));
    }

    private static void fail(ServerRequest request, ServerResponse response) {
        throw new IllegalStateException("input=" + request.query().first("input").orElse("none"));
    }

    private static void assertError(Response response) {
        assertThat(response.status(), is(500));
        assertThat(response.body(), is("Internal Server Error"));
        assertThat(response.contentLength(), is((long) "Internal Server Error".length()));
    }

    private record Response(int status, String body, long contentLength) {
    }

    private static final class TestServer implements AutoCloseable {
        private final WebServer server;
        private final HttpClient http1 = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(TIMEOUT)
                .build();
        private final Http2Client http2;
        private final Http2Client secondHttp2;

        private TestServer(Consumer<WebServerConfig.Builder> configure) {
            WebServerConfig.Builder builder = WebServer.builder()
                    .port(0)
                    .routing(ErrorLogLimitingTest::routing)
                    .putSocket("second", socket -> socket.port(0).routing(ErrorLogLimitingTest::routing));
            configure.accept(builder);
            server = builder.build().start();
            http2 = http2(server.port());
            secondHttp2 = http2(server.port("second"));
        }

        @Override
        public void close() {
            http2.closeResource();
            secondHttp2.closeResource();
            http1.close();
            server.stop();
        }

        private static Http2Client http2(int port) {
            return Http2Client.builder()
                    .baseUri("http://localhost:" + port)
                    .protocolConfig(Http2ClientProtocolConfig.builder().priorKnowledge(true).build())
                    .shareConnectionCache(false)
                    .build();
        }

        private Response request(boolean useHttp2, boolean secondSocket, String path) throws Exception {
            if (useHttp2) {
                Http2Client client = secondSocket ? secondHttp2 : http2;
                try (var response = client.get().uri(URI.create(path)).request()) {
                    return new Response(response.status().code(),
                                        response.entity().asOptional(String.class).orElse(""),
                                        response.headers().contentLength().orElseThrow());
                }
            }
            int port = secondSocket ? server.port("second") : server.port();
            HttpResponse<String> response = http1.send(HttpRequest.newBuilder()
                                                              .uri(URI.create("http://localhost:" + port + path))
                                                              .timeout(TIMEOUT)
                                                              .GET()
                                                              .build(),
                                                      HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(),
                                response.body(),
                                response.headers().firstValueAsLong("Content-Length").orElseThrow());
        }
    }

    private static final class LogCapture extends Handler implements AutoCloseable {
        private final Logger failuresLogger = Logger.getLogger("io.helidon.webserver.http.DirectHandlers");
        private final Logger summaryLogger = Logger.getLogger("io.helidon.webserver.ErrorLogLimiter");
        private final List<LoggerState> loggerStates = List.of(failuresLogger,
                                                              summaryLogger,
                                                              Logger.getLogger("io.helidon.webserver.http.HttpRoutingImpl"),
                                                              Logger.getLogger("io.helidon.webserver.http1.Http1ServerResponse"),
                                                              Logger.getLogger("io.helidon.webserver.http2.Http2ServerResponse"))
                .stream()
                .map(logger -> new LoggerState(logger, logger.getLevel()))
                .toList();
        private final List<LogRecord> records = new CopyOnWriteArrayList<>();
        private final BlockingQueue<LogRecord> summaries = new LinkedBlockingQueue<>();

        private LogCapture() {
            setLevel(Level.ALL);
            for (LoggerState state : loggerStates) {
                state.logger().setLevel(Level.ALL);
                state.logger().addHandler(this);
            }
        }

        @Override
        public void publish(LogRecord record) {
            if (record.getLoggerName().equals(summaryLogger.getName())) {
                summaries.add(record);
            } else {
                records.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            for (LoggerState state : loggerStates) {
                state.logger().removeHandler(this);
                state.logger().setLevel(state.level());
            }
        }

        private List<LogRecord> failures() {
            return diagnostics(failuresLogger.getName(), "Internal server error");
        }

        private List<LogRecord> diagnostics(String logger, String messagePrefix) {
            return records.stream()
                    .filter(record -> record.getLoggerName().equals(logger))
                    .filter(record -> record.getMessage().startsWith(messagePrefix))
                    .toList();
        }

        private record LoggerState(Logger logger, Level level) {
        }
    }
}
