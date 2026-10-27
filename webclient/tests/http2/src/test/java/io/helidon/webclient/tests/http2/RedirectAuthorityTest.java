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

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClient;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http2.Http2Config;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedirectAuthorityTest {
    private static final String BODY = "redirect-body";

    @TestFactory
    Stream<DynamicTest> redirectAuthority() {
        return redirectTests(false);
    }

    @TestFactory
    Stream<DynamicTest> redirectHttpsAuthority() {
        return redirectTests(true);
    }

    @Test
    void expectContinueTimeoutClosesResources() throws Exception {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var gate = new ResponseGate();
        RedirectFixture fixture = null;
        try {
            try (var resources = new RedirectFixture(Protocol.HTTP1, Exchange.EXPECT_CONTINUE,
                                                     Location.ABSOLUTE_UNDERSCORE, false, gate)) {
                fixture = resources;
                var exchange = submitExpectContinue(executor, resources);
                try {
                    assertThat("Source observed the Expect-continue request",
                               gate.observed.await(5, TimeUnit.SECONDS), is(true));
                    // The handler cannot respond until this caller releases the gate after the asserted timeout.
                    assertThrows(TimeoutException.class, () -> awaitExchange(exchange, Duration.ZERO));
                } finally {
                    exchange.cancel(true);
                    gate.release.countDown();
                }
            }
        } finally {
            terminate(executor);
        }
        assertThat("Source server stopped after timeout", fixture.source.isRunning(), is(false));
        assertThat("Target server stopped after timeout", fixture.target.isRunning(), is(false));
    }

    private static Stream<DynamicTest> redirectTests(boolean secure) {
        return Arrays.stream(Protocol.values())
                .flatMap(protocol -> Arrays.stream(Exchange.values())
                        // A cleartext upgrade probe has no entity and does not send Expect.
                        .filter(exchange -> secure || protocol != Protocol.HTTP2_FALLBACK
                                || exchange != Exchange.EXPECT_CONTINUE)
                        .flatMap(exchange -> Arrays.stream(Location.values())
                                .filter(location -> secure || location != Location.SAME_AUTHORITY_SCHEME_RELATIVE)
                                .map(location -> DynamicTest.dynamicTest(protocol + " " + exchange + " " + location,
                                        () -> redirect(protocol, exchange, location, secure)))));
    }

    private static void redirect(Protocol protocol, Exchange exchange, Location location, boolean secure) throws Exception {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            try (var fixture = new RedirectFixture(protocol, exchange, location, secure, null)) {
                if (exchange == Exchange.EXPECT_CONTINUE) {
                    awaitExchange(submitExpectContinue(executor, fixture), Duration.ofSeconds(10));
                } else {
                    fixture.exchange();
                }
            }
        } finally {
            terminate(executor);
        }
    }

    private static Future<?> submitExpectContinue(ExecutorService executor, RedirectFixture fixture) {
        return executor.submit(() -> {
            long started = System.nanoTime();
            fixture.exchange();
            assertThat("Expect-continue exchange elapsed time",
                       System.nanoTime() - started,
                       lessThan(TimeUnit.SECONDS.toNanos(10)));
            return null;
        });
    }

    private static void awaitExchange(Future<?> exchange, Duration timeout) throws Exception {
        try {
            // A passing exchange must finish before the 30-second continue wait can expire.
            exchange.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } finally {
            exchange.cancel(true);
        }
    }

    private static void terminate(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat("Expect-continue exchange worker terminated", executor.awaitTermination(10, TimeUnit.SECONDS), is(true));
    }

    private static WebServer server(Protocol protocol, boolean secure, HttpRouting.Builder routing) {
        var builder = WebServer.builder()
                .host("127.0.0.1")
                .port(-1)
                .protocolsDiscoverServices(false)
                .addProtocol(Http1Config.builder().continueImmediately(false).build())
                .routing(routing);
        if (secure) {
            var keys = Keys.builder()
                    .keystore(store -> store.passphrase("password").keystore(Resource.create("server.p12")))
                    .build();
            builder.tls(Tls.builder().privateKey(keys).privateKeyCertChain(keys).build());
        }
        if (protocol == Protocol.HTTP2) {
            builder.addProtocol(Http2Config.builder().build());
        }
        var server = builder.build();
        try {
            return server.start();
        } catch (Exception | Error failure) {
            try {
                server.stop();
            } catch (Exception | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private static String capture(String server, ServerRequest request) throws IOException {
        String body = request.prologue().method().equals(Method.GET)
                ? ""
                : new String(request.content().inputStream().readAllBytes(), StandardCharsets.UTF_8);
        return server + "|" + request.authority() + "|" + request.localPeer().port()
                + "|" + request.prologue().protocolVersion() + "|" + request.isSecure()
                + "|" + request.prologue().method() + "|" + body;
    }

    private enum Protocol {
        HTTP1,
        HTTP2,
        HTTP2_FALLBACK
    }

    private enum Exchange {
        GET,
        ENTITY,
        OUTPUT_STREAM,
        EXPECT_CONTINUE
    }

    private enum Location {
        ABSOLUTE_UNDERSCORE,
        ABSOLUTE_HOST,
        SCHEME_RELATIVE,
        SAME_AUTHORITY_SCHEME_RELATIVE,
        PATH_RELATIVE
    }

    private static class ResponseGate {
        private final CountDownLatch observed = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
    }

    private static class RedirectFixture implements AutoCloseable {
        private final Exchange exchange;
        private WebServer target;
        private WebServer source;
        private HttpClient<?> client;
        private String expected;

        private RedirectFixture(Protocol protocol, Exchange exchange, Location location, boolean secure, ResponseGate gate)
                throws Exception {
            this.exchange = exchange;
            try {
                String scheme = secure ? "https" : "http";
                String sourceHost = secure ? "source_host" : "source-host";
                // The H2 307 upload probe waits for a timeout; use 302 to isolate early redirect routing.
                boolean keepEntity = exchange != Exchange.GET && protocol != Protocol.HTTP2_FALLBACK
                        && (protocol != Protocol.HTTP2 || exchange != Exchange.EXPECT_CONTINUE);
                target = server(protocol, secure, HttpRouting.builder()
                        .any("/target", (req, res) -> res.send(capture("target", req))));
                String redirectLocation = switch (location) {
                    case ABSOLUTE_UNDERSCORE -> scheme + "://redirect_host:" + target.port() + "/target";
                    case ABSOLUTE_HOST -> scheme + "://redirect-host:" + target.port() + "/target";
                    case SCHEME_RELATIVE -> "//redirect_host:" + target.port() + "/target";
                    case SAME_AUTHORITY_SCHEME_RELATIVE -> "//" + sourceHost + ":";
                    case PATH_RELATIVE -> "/target";
                };
                source = server(protocol, secure, HttpRouting.builder()
                        .any("/redirect", (req, res) -> {
                            if (exchange == Exchange.EXPECT_CONTINUE
                                    && !req.headers().first(HeaderNames.EXPECT).orElse("").equalsIgnoreCase("100-continue")) {
                                res.status(Status.BAD_REQUEST_400).send("Expected an Expect: 100-continue request");
                                return;
                            }
                            if (gate != null) {
                                gate.observed.countDown();
                                gate.release.await();
                            }
                            if (exchange == Exchange.ENTITY || exchange == Exchange.OUTPUT_STREAM) {
                                req.content().inputStream().readAllBytes();
                            }
                            res.status(keepEntity ? Status.TEMPORARY_REDIRECT_307 : Status.FOUND_302)
                                    .header(HeaderNames.LOCATION, location == Location.SAME_AUTHORITY_SCHEME_RELATIVE
                                            ? redirectLocation + req.localPeer().port() + "/target"
                                            : redirectLocation)
                                    .send();
                        })
                        .any("/target", (req, res) -> res.send(capture("source", req))));
                var loopback = InetAddress.getByName("127.0.0.1");
                String baseUri = scheme + "://" + sourceHost + ":" + source.port();
                var clientTls = Tls.builder()
                        .enabled(secure)
                        .trustAll(true)
                        .endpointIdentificationAlgorithm(Tls.ENDPOINT_IDENTIFICATION_NONE)
                        .build();
                if (protocol == Protocol.HTTP1) {
                    client = Http1Client.builder()
                            .baseUri(baseUri)
                            .tls(clientTls)
                            .dnsResolver((host, lookup) -> loopback)
                            .proxy(Proxy.noProxy())
                            .connectTimeout(Duration.ofSeconds(5))
                            .readTimeout(Duration.ofSeconds(gate == null ? 5 : 30))
                            .followCrossOriginEntityRedirects(true)
                            .build();
                } else {
                    client = Http2Client.builder()
                            .baseUri(baseUri)
                            .tls(clientTls)
                            .dnsResolver((host, lookup) -> loopback)
                            .proxy(Proxy.noProxy())
                            .connectTimeout(Duration.ofSeconds(5))
                            .readTimeout(Duration.ofSeconds(gate == null ? 5 : 30))
                            .followCrossOriginEntityRedirects(true)
                            .protocolConfig(config -> config.priorKnowledge(protocol == Protocol.HTTP2))
                            .build();
                }
                boolean relative = location == Location.PATH_RELATIVE
                        || location == Location.SAME_AUTHORITY_SCHEME_RELATIVE;
                String host = switch (location) {
                    case ABSOLUTE_UNDERSCORE, SCHEME_RELATIVE -> "redirect_host";
                    case ABSOLUTE_HOST -> "redirect-host";
                    case PATH_RELATIVE, SAME_AUTHORITY_SCHEME_RELATIVE -> sourceHost;
                };
                int port = relative ? source.port() : target.port();
                expected = (relative ? "source" : "target") + "|" + host + ":" + port + "|" + port
                        + "|" + (protocol == Protocol.HTTP2 ? "2.0" : "1.1")
                        + "|" + secure
                        + "|" + (keepEntity ? "PUT|" + BODY : "GET|");
            } catch (Exception | Error failure) {
                try {
                    close();
                } catch (Exception | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        @Override
        public void close() {
            try {
                if (client != null) {
                    client.closeResource();
                }
            } finally {
                try {
                    if (source != null) {
                        source.stop();
                    }
                } finally {
                    if (target != null) {
                        target.stop();
                    }
                }
            }
        }

        private void exchange() {
            var request = client.method(exchange == Exchange.GET ? Method.GET : Method.PUT)
                    .uri("/redirect")
                    .sendExpectContinue(exchange == Exchange.EXPECT_CONTINUE);
            if (exchange == Exchange.EXPECT_CONTINUE) {
                request.readContinueTimeout(Duration.ofSeconds(30));
            }
            HttpClientResponse response = switch (exchange) {
                case GET -> request.request();
                case ENTITY -> request.submit(BODY);
                case OUTPUT_STREAM, EXPECT_CONTINUE -> request.outputStream(output -> {
                    try (output) {
                        output.write(BODY.getBytes(StandardCharsets.UTF_8));
                    }
                });
            };
            try (response) {
                String captured = response.as(String.class);
                assertThat(captured, response.status(), is(Status.OK_200));
                assertThat(captured, is(expected));
            }
        }
    }
}
