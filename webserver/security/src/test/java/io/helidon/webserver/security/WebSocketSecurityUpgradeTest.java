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

package io.helidon.webserver.security;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.security.Security;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.context.ContextFeature;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.SecureHandler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http.spi.ProtocolUpgradeHandler;
import io.helidon.webserver.http1.Http1Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.websocket.WsRouting;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;
import io.helidon.websocket.WsUpgradeException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@ServerTest
class WebSocketSecurityUpgradeTest {
    private static final AtomicReference<CountDownLatch> WS_OPEN_LATCH =
            new AtomicReference<>(new CountDownLatch(1));
    private static final AtomicReference<CountDownLatch> FINALIZATION_FILTER_LATCH =
            new AtomicReference<>(new CountDownLatch(1));
    private static final AtomicBoolean WS_OPENED = new AtomicBoolean();
    private static final AtomicInteger UPGRADE_GATE_INVOKED = new AtomicInteger();
    private static final AtomicInteger HTTP_HANDLER_INVOKED = new AtomicInteger();
    private static final AtomicInteger SERVICE_UPGRADE_GATE_INVOKED = new AtomicInteger();
    private static final AtomicInteger SERVICE_HTTP_HANDLER_INVOKED = new AtomicInteger();
    private static final AtomicInteger HTTP1_UPGRADE_GATE_INVOKED = new AtomicInteger();
    private static final AtomicInteger HTTP1_HTTP_HANDLER_INVOKED = new AtomicInteger();
    private static final AtomicInteger BOUNDARY_FIRST_GATE_INVOKED = new AtomicInteger();
    private static final AtomicInteger BOUNDARY_HTTP_HANDLER_INVOKED = new AtomicInteger();
    private static final AtomicInteger BOUNDARY_SECOND_GATE_INVOKED = new AtomicInteger();
    private static final AtomicInteger FINALIZATION_WHEN_SENT_INVOKED = new AtomicInteger();
    private static final AtomicInteger FINALIZATION_WHEN_SENT_STATUS = new AtomicInteger();
    private static final AtomicInteger FINALIZATION_FILTER_INVOKED = new AtomicInteger();
    private static final AtomicInteger FINALIZATION_FILTER_STATUS = new AtomicInteger();
    private static final AtomicInteger FALLBACK_HTTP_HANDLER_INVOKED = new AtomicInteger();
    private static final AtomicInteger FALLBACK_UPGRADE_GATE_INVOKED = new AtomicInteger();
    private static final AtomicInteger CHAIN_PREFILTER_INVOKED = new AtomicInteger();
    private static final AtomicInteger CHAIN_DENY_GATE_INVOKED = new AtomicInteger();
    private static final AtomicInteger CHAIN_HTTP_HANDLER_INVOKED = new AtomicInteger();
    private static final AtomicInteger SPLIT_CHAIN_PREFILTER_INVOKED = new AtomicInteger();
    private static final AtomicInteger SPLIT_CHAIN_DENY_GATE_INVOKED = new AtomicInteger();
    private static final AtomicInteger SPLIT_CHAIN_HTTP_HANDLER_INVOKED = new AtomicInteger();
    private static final AtomicInteger PLAIN_SECURITY_PREFILTER_INVOKED = new AtomicInteger();
    private static final AtomicInteger PLAIN_SECURITY_HTTP_HANDLER_INVOKED = new AtomicInteger();
    private static final AtomicInteger WRAPPED_UPGRADE_GATE_INVOKED = new AtomicInteger();
    private static final AtomicInteger WRAPPED_HTTP_HANDLER_INVOKED = new AtomicInteger();
    private static final AtomicInteger REJECT_GATE_INVOKED = new AtomicInteger();
    private static final AtomicInteger REJECT_HTTP_HANDLER_INVOKED = new AtomicInteger();
    private static final AtomicInteger REROUTE_GATE_INVOKED = new AtomicInteger();
    private static final AtomicInteger REROUTED_HTTP_HANDLER_INVOKED = new AtomicInteger();
    private static final String JACK_AUTHORIZATION = "Basic " + Base64.getEncoder()
            .encodeToString("jack:jackIsGreat".getBytes(StandardCharsets.US_ASCII));

    private final int port;

    WebSocketSecurityUpgradeTest(WebServer server) {
        this.port = server.port();
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder serverBuilder) {
        Config config = Config.just(ConfigSources.classpath("security-application.yaml"));
        Security security = Security.builder(config.get("security"))
                .build();

        serverBuilder.featuresDiscoverServices(false)
                .addFeature(ContextFeature.create())
                .addFeature(SecurityFeature.builder()
                                    .security(security)
                                    .build())
                .routing(routing -> routing
                        .addFilter((chain, req, res) -> {
                            try {
                                chain.proceed();
                            } finally {
                                if ("/finalization".equals(req.prologue().uriPath().path())) {
                                    FINALIZATION_FILTER_INVOKED.incrementAndGet();
                                    FINALIZATION_FILTER_STATUS.set(res.status().code());
                                    FINALIZATION_FILTER_LATCH.get().countDown();
                                }
                            }
                        })
                        .get("/admin",
                             new CountingUpgradeGate(),
                             SecurityFeature.rolesAllowed("admin"),
                             (req, res) -> {
                                 HTTP_HANDLER_INVOKED.incrementAndGet();
                                 res.send("http-admin");
                             })
                        .route(Http1Route.route(Method.GET, "/http1", new Http1UpgradeGate()))
                        .route(Http1Route.route(Method.GET, "/http1", SecurityFeature.rolesAllowed("admin")))
                        .route(Http1Route.route(Method.GET, "/http1", (req, res) -> {
                            HTTP1_HTTP_HANDLER_INVOKED.incrementAndGet();
                            res.send("http1-admin");
                        }))
                        .get("/boundary",
                             new BoundaryFirstGate(),
                             (req, res) -> {
                                 BOUNDARY_HTTP_HANDLER_INVOKED.incrementAndGet();
                                 res.send("boundary-admin");
                             })
                        .get("/boundary",
                             new BoundarySecondGate(),
                             (req, res) -> res.send("boundary-second"))
                        .get("/finalization",
                             new FinalizationUpgradeGate(),
                             (req, res) -> res.send("finalization"))
                        .get("/http-fallback", (req, res) -> {
                            FALLBACK_HTTP_HANDLER_INVOKED.incrementAndGet();
                            res.status(Status.ACCEPTED_202);
                            res.send("http-fallback");
                        })
                        .get("/gated-fallback", new FallbackDenyingUpgradeGate())
                        .get("/chain",
                             new PlainNextingHandler(),
                             new ChainDenyingUpgradeGate(),
                             (req, res) -> {
                                 CHAIN_HTTP_HANDLER_INVOKED.incrementAndGet();
                                 res.send("chain");
                             })
                        .get("/split-chain", new SplitPlainNextingHandler())
                        .get("/split-chain",
                             new SplitChainDenyingUpgradeGate(),
                             (req, res) -> {
                                 SPLIT_CHAIN_HTTP_HANDLER_INVOKED.incrementAndGet();
                                 res.send("split-chain");
                             })
                        .get("/plain-before-security",
                             new PlainBeforeSecurityHandler(),
                             SecurityFeature.authenticate(),
                             (req, res) -> {
                                 PLAIN_SECURITY_HTTP_HANDLER_INVOKED.incrementAndGet();
                                 res.send("plain-before-security");
                             })
                        .get("/secure-wrap",
                             SecurityFeature.authenticate(),
                             SecureHandler.authenticate().wrap(new WrappedDenyingUpgradeGate()))
                        .get("/reroute", new ReroutingUpgradeGate())
                        .get("/rerouted-http", (req, res) -> {
                            REROUTED_HTTP_HANDLER_INVOKED.incrementAndGet();
                            res.status(Status.ACCEPTED_202);
                            res.send("rerouted-http");
                        })
                        .get("/reject",
                             new RejectUpgradeGate(),
                             SecurityFeature.authenticate(),
                             (req, res) -> {
                                 REJECT_HTTP_HANDLER_INVOKED.incrementAndGet();
                                 res.status(Status.ACCEPTED_202);
                                 res.send("http-reject");
                             })
                        .register("/service", new AdminService()))
                .addRouting(WsRouting.builder()
                                    .endpoint("/admin", new ProbeWsListener())
                                    .endpoint("/service", new ProbeWsListener())
                                    .endpoint("/http1", new ProbeWsListener())
                                    .endpoint("/boundary", new ProbeWsListener())
                                    .endpoint("/finalization", new ProbeWsListener())
                                    .endpoint("/chain", new ProbeWsListener())
                                    .endpoint("/split-chain", new ProbeWsListener())
                                    .endpoint("/plain-before-security", new ProbeWsListener())
                                    .endpoint("/secure-wrap", new ProbeWsListener())
                                    .endpoint("/headers", new ConflictingHeadersWsListener())
                                    .endpoint("/reroute", new ProbeWsListener())
                                    .endpoint("/reject", new RejectingWsListener()));
    }

    @BeforeEach
    void resetState() {
        WS_OPEN_LATCH.set(new CountDownLatch(1));
        FINALIZATION_FILTER_LATCH.set(new CountDownLatch(1));
        WS_OPENED.set(false);
        UPGRADE_GATE_INVOKED.set(0);
        HTTP_HANDLER_INVOKED.set(0);
        SERVICE_UPGRADE_GATE_INVOKED.set(0);
        SERVICE_HTTP_HANDLER_INVOKED.set(0);
        HTTP1_UPGRADE_GATE_INVOKED.set(0);
        HTTP1_HTTP_HANDLER_INVOKED.set(0);
        BOUNDARY_FIRST_GATE_INVOKED.set(0);
        BOUNDARY_HTTP_HANDLER_INVOKED.set(0);
        BOUNDARY_SECOND_GATE_INVOKED.set(0);
        FINALIZATION_WHEN_SENT_INVOKED.set(0);
        FINALIZATION_WHEN_SENT_STATUS.set(0);
        FINALIZATION_FILTER_INVOKED.set(0);
        FINALIZATION_FILTER_STATUS.set(0);
        FALLBACK_HTTP_HANDLER_INVOKED.set(0);
        FALLBACK_UPGRADE_GATE_INVOKED.set(0);
        CHAIN_PREFILTER_INVOKED.set(0);
        CHAIN_DENY_GATE_INVOKED.set(0);
        CHAIN_HTTP_HANDLER_INVOKED.set(0);
        SPLIT_CHAIN_PREFILTER_INVOKED.set(0);
        SPLIT_CHAIN_DENY_GATE_INVOKED.set(0);
        SPLIT_CHAIN_HTTP_HANDLER_INVOKED.set(0);
        PLAIN_SECURITY_PREFILTER_INVOKED.set(0);
        PLAIN_SECURITY_HTTP_HANDLER_INVOKED.set(0);
        WRAPPED_UPGRADE_GATE_INVOKED.set(0);
        WRAPPED_HTTP_HANDLER_INVOKED.set(0);
        REJECT_GATE_INVOKED.set(0);
        REJECT_HTTP_HANDLER_INVOKED.set(0);
        REROUTE_GATE_INVOKED.set(0);
        REROUTED_HTTP_HANDLER_INVOKED.set(0);
    }

    @Test
    void unauthenticatedWebSocketUpgradeUsesHttpSecurity() throws Exception {
        String httpResponse = sendRequest(
                "GET /admin HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Connection: close\r\n"
                        + "\r\n");

        assertThat(httpResponse, containsString("HTTP/1.1 401"));
        assertThat(UPGRADE_GATE_INVOKED.get(), is(1));
        assertThat(HTTP_HANDLER_INVOKED.get(), is(0));

        UPGRADE_GATE_INVOKED.set(0);
        HTTP_HANDLER_INVOKED.set(0);

        String wsResponse = sendRequest(
                "GET /admin HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "\r\n");

        assertThat(wsResponse, containsString("HTTP/1.1 401"));
        assertThat(wsResponse, not(containsString("HTTP/1.1 101")));
        assertThat(UPGRADE_GATE_INVOKED.get(), is(1));
        assertThat(HTTP_HANDLER_INVOKED.get(), is(0));
        assertThat(WS_OPEN_LATCH.get().await(100, TimeUnit.MILLISECONDS), is(false));
        assertThat(WS_OPENED.get(), is(false));
    }

    @Test
    void authenticatedWebSocketUpgradeUsesHttpSecurity() throws Exception {
        String httpResponse = sendRequest(
                "GET /admin HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Connection: close\r\n"
                        + "Authorization: " + JACK_AUTHORIZATION + "\r\n"
                        + "\r\n");

        assertThat(httpResponse, containsString("HTTP/1.1 200"));
        assertThat(UPGRADE_GATE_INVOKED.get(), is(1));
        assertThat(HTTP_HANDLER_INVOKED.get(), is(1));

        UPGRADE_GATE_INVOKED.set(0);
        HTTP_HANDLER_INVOKED.set(0);

        String wsResponse = sendRequest(
                "GET /admin HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "Authorization: " + JACK_AUTHORIZATION + "\r\n"
                        + "\r\n");

        assertThat(wsResponse, containsString("HTTP/1.1 101"));
        assertThat(UPGRADE_GATE_INVOKED.get(), is(1));
        assertThat(HTTP_HANDLER_INVOKED.get(), is(0));
        assertThat(WS_OPEN_LATCH.get().await(5, TimeUnit.SECONDS), is(true));
        assertThat(WS_OPENED.get(), is(true));
    }

    @Test
    void webSocketUpgradeRequiresGetMethod() throws Exception {
        String wsResponse = sendRequest(
                "POST /admin HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "\r\n");

        assertThat(wsResponse, containsString("HTTP/1.1 400"));
        assertThat(wsResponse, not(containsString("HTTP/1.1 101")));
        assertThat(UPGRADE_GATE_INVOKED.get(), is(0));
        assertThat(HTTP_HANDLER_INVOKED.get(), is(0));
        assertThat(WS_OPEN_LATCH.get().await(100, TimeUnit.MILLISECONDS), is(false));
        assertThat(WS_OPENED.get(), is(false));
    }

    @Test
    void webSocketUpgradeIgnoresPlainHandlerBeforePolicy() throws Exception {
        String wsResponse = sendRequest(
                "GET /chain HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "\r\n");

        assertThat(wsResponse, containsString("HTTP/1.1 403"));
        assertThat(wsResponse, not(containsString("HTTP/1.1 101")));
        assertThat(CHAIN_PREFILTER_INVOKED.get(), is(0));
        assertThat(CHAIN_DENY_GATE_INVOKED.get(), is(1));
        assertThat(CHAIN_HTTP_HANDLER_INVOKED.get(), is(0));
        assertThat(WS_OPEN_LATCH.get().await(100, TimeUnit.MILLISECONDS), is(false));
        assertThat(WS_OPENED.get(), is(false));
    }

    @Test
    void webSocketUpgradeIgnoresSplitPlainHandlerBeforePolicy() throws Exception {
        String wsResponse = sendRequest(
                "GET /split-chain HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "\r\n");

        assertThat(wsResponse, containsString("HTTP/1.1 403"));
        assertThat(wsResponse, not(containsString("HTTP/1.1 101")));
        assertThat(SPLIT_CHAIN_PREFILTER_INVOKED.get(), is(0));
        assertThat(SPLIT_CHAIN_DENY_GATE_INVOKED.get(), is(1));
        assertThat(SPLIT_CHAIN_HTTP_HANDLER_INVOKED.get(), is(0));
        assertThat(WS_OPEN_LATCH.get().await(100, TimeUnit.MILLISECONDS), is(false));
        assertThat(WS_OPENED.get(), is(false));
    }

    @Test
    void webSocketUpgradeIgnoresPlainHandlerBeforeSecurity() throws Exception {
        String wsResponse = sendRequest(
                "GET /plain-before-security HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "\r\n");

        assertThat(wsResponse, containsString("HTTP/1.1 401"));
        assertThat(wsResponse, not(containsString("HTTP/1.1 101")));
        assertThat(PLAIN_SECURITY_PREFILTER_INVOKED.get(), is(0));
        assertThat(PLAIN_SECURITY_HTTP_HANDLER_INVOKED.get(), is(0));
        assertThat(WS_OPEN_LATCH.get().await(100, TimeUnit.MILLISECONDS), is(false));
        assertThat(WS_OPENED.get(), is(false));
    }

    @Test
    void serviceRegisteredWebSocketUpgradeUsesHttpSecurity() throws Exception {
        String httpResponse = sendRequest(
                "GET /service HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Connection: close\r\n"
                        + "\r\n");

        assertThat(httpResponse, containsString("HTTP/1.1 401"));
        assertThat(SERVICE_UPGRADE_GATE_INVOKED.get(), is(1));
        assertThat(SERVICE_HTTP_HANDLER_INVOKED.get(), is(0));

        SERVICE_UPGRADE_GATE_INVOKED.set(0);
        SERVICE_HTTP_HANDLER_INVOKED.set(0);

        String wsResponse = sendRequest(
                "GET /service HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "\r\n");

        assertThat(wsResponse, containsString("HTTP/1.1 401"));
        assertThat(wsResponse, not(containsString("HTTP/1.1 101")));
        assertThat(SERVICE_UPGRADE_GATE_INVOKED.get(), is(1));
        assertThat(SERVICE_HTTP_HANDLER_INVOKED.get(), is(0));
        assertThat(WS_OPEN_LATCH.get().await(100, TimeUnit.MILLISECONDS), is(false));
        assertThat(WS_OPENED.get(), is(false));

        WS_OPEN_LATCH.set(new CountDownLatch(1));
        WS_OPENED.set(false);
        SERVICE_UPGRADE_GATE_INVOKED.set(0);
        SERVICE_HTTP_HANDLER_INVOKED.set(0);

        String authenticatedWsResponse = sendRequest(
                "GET /service HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "Authorization: " + JACK_AUTHORIZATION + "\r\n"
                        + "\r\n");

        assertThat(authenticatedWsResponse, containsString("HTTP/1.1 101"));
        assertThat(SERVICE_UPGRADE_GATE_INVOKED.get(), is(1));
        assertThat(SERVICE_HTTP_HANDLER_INVOKED.get(), is(0));
        assertThat(WS_OPEN_LATCH.get().await(5, TimeUnit.SECONDS), is(true));
        assertThat(WS_OPENED.get(), is(true));
    }

    @Test
    void http1RouteWebSocketUpgradeUsesHttpSecurity() throws Exception {
        String httpResponse = sendRequest(
                "GET /http1 HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Connection: close\r\n"
                        + "\r\n");

        assertThat(httpResponse, containsString("HTTP/1.1 401"));
        assertThat(HTTP1_UPGRADE_GATE_INVOKED.get(), is(1));
        assertThat(HTTP1_HTTP_HANDLER_INVOKED.get(), is(0));

        HTTP1_UPGRADE_GATE_INVOKED.set(0);
        HTTP1_HTTP_HANDLER_INVOKED.set(0);

        String wsResponse = sendRequest(
                "GET /http1 HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "\r\n");

        assertThat(wsResponse, containsString("HTTP/1.1 401"));
        assertThat(wsResponse, not(containsString("HTTP/1.1 101")));
        assertThat(HTTP1_UPGRADE_GATE_INVOKED.get(), is(1));
        assertThat(HTTP1_HTTP_HANDLER_INVOKED.get(), is(0));
        assertThat(WS_OPEN_LATCH.get().await(100, TimeUnit.MILLISECONDS), is(false));
        assertThat(WS_OPENED.get(), is(false));

        WS_OPEN_LATCH.set(new CountDownLatch(1));
        WS_OPENED.set(false);
        HTTP1_UPGRADE_GATE_INVOKED.set(0);
        HTTP1_HTTP_HANDLER_INVOKED.set(0);

        String authenticatedWsResponse = sendRequest(
                "GET /http1 HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "Authorization: " + JACK_AUTHORIZATION + "\r\n"
                        + "\r\n");

        assertThat(authenticatedWsResponse, containsString("HTTP/1.1 101"));
        assertThat(HTTP1_UPGRADE_GATE_INVOKED.get(), is(1));
        assertThat(HTTP1_HTTP_HANDLER_INVOKED.get(), is(0));
        assertThat(WS_OPEN_LATCH.get().await(5, TimeUnit.SECONDS), is(true));
        assertThat(WS_OPENED.get(), is(true));
    }

    @Test
    void upgradeRoutingIgnoresNormalHandlerBetweenPolicies() throws Exception {
        String httpResponse = sendRequest(
                "GET /boundary HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Connection: close\r\n"
                        + "\r\n");

        assertThat(httpResponse, containsString("HTTP/1.1 200"));
        assertThat(BOUNDARY_FIRST_GATE_INVOKED.get(), is(1));
        assertThat(BOUNDARY_HTTP_HANDLER_INVOKED.get(), is(1));
        assertThat(BOUNDARY_SECOND_GATE_INVOKED.get(), is(0));

        BOUNDARY_FIRST_GATE_INVOKED.set(0);
        BOUNDARY_HTTP_HANDLER_INVOKED.set(0);
        BOUNDARY_SECOND_GATE_INVOKED.set(0);

        String wsResponse = sendRequest(
                "GET /boundary HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "\r\n");

        assertThat(wsResponse, containsString("HTTP/1.1 403"));
        assertThat(wsResponse, not(containsString("HTTP/1.1 101")));
        assertThat(BOUNDARY_FIRST_GATE_INVOKED.get(), is(1));
        assertThat(BOUNDARY_HTTP_HANDLER_INVOKED.get(), is(0));
        assertThat(BOUNDARY_SECOND_GATE_INVOKED.get(), is(1));
        assertThat(WS_OPEN_LATCH.get().await(100, TimeUnit.MILLISECONDS), is(false));
        assertThat(WS_OPENED.get(), is(false));
    }

    @Test
    void successfulUpgradeSendsGateResponse() throws Exception {
        String wsResponse = sendRequest(
                "GET /finalization HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "\r\n");

        assertThat(wsResponse, containsString("HTTP/1.1 101"));
        assertThat(wsResponse, containsString("X-Upgrade-Gate: allowed"));
        assertThat(FINALIZATION_WHEN_SENT_INVOKED.get(), is(1));
        assertThat(FINALIZATION_WHEN_SENT_STATUS.get(), is(Status.SWITCHING_PROTOCOLS_101.code()));
        assertThat(FINALIZATION_FILTER_LATCH.get().await(5, TimeUnit.SECONDS), is(true));
        assertThat(FINALIZATION_FILTER_INVOKED.get(), is(1));
        assertThat(FINALIZATION_FILTER_STATUS.get(), is(Status.SWITCHING_PROTOCOLS_101.code()));
        assertThat(WS_OPEN_LATCH.get().await(5, TimeUnit.SECONDS), is(true));
        assertThat(WS_OPENED.get(), is(true));
    }

    @Test
    void listenerHeadersCannotOverrideWebSocketHandshake() throws Exception {
        String wsResponse = sendRequest(
                "GET /headers HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "\r\n");

        assertThat(wsResponse, containsString("HTTP/1.1 101"));
        assertThat(wsResponse, containsString("Connection: Upgrade\r\n"));
        assertThat(wsResponse, containsString("Upgrade: websocket\r\n"));
        assertThat(wsResponse, containsString("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r\n"));
        assertThat(wsResponse, containsString("X-Listener: kept\r\n"));
        assertThat(wsResponse, not(containsString("Connection: close\r\n")));
        assertThat(wsResponse, not(containsString("Upgrade: h2c\r\n")));
        assertThat(wsResponse, not(containsString("Sec-WebSocket-Accept: invalid\r\n")));
    }

    @Test
    void secureHandlerWrapPreservesWrappedUpgradePolicy() throws Exception {
        String httpResponse = sendRequest(
                "GET /secure-wrap HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Connection: close\r\n"
                        + "Authorization: " + JACK_AUTHORIZATION + "\r\n"
                        + "\r\n");

        assertThat(httpResponse, containsString("HTTP/1.1 200"));
        assertThat(WRAPPED_HTTP_HANDLER_INVOKED.get(), is(1));
        assertThat(WRAPPED_UPGRADE_GATE_INVOKED.get(), is(0));

        WRAPPED_HTTP_HANDLER_INVOKED.set(0);

        String wsResponse = sendRequest(
                "GET /secure-wrap HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "Authorization: " + JACK_AUTHORIZATION + "\r\n"
                        + "\r\n");

        assertThat(wsResponse, containsString("HTTP/1.1 403"));
        assertThat(wsResponse, not(containsString("HTTP/1.1 101")));
        assertThat(WRAPPED_UPGRADE_GATE_INVOKED.get(), is(1));
        assertThat(WRAPPED_HTTP_HANDLER_INVOKED.get(), is(0));
        assertThat(WS_OPEN_LATCH.get().await(100, TimeUnit.MILLISECONDS), is(false));
        assertThat(WS_OPENED.get(), is(false));
    }

    @Test
    void webSocketUpgradeWithoutWebSocketRouteFallsBackToHttpRoute() throws Exception {
        String response = sendRequest(
                "GET /http-fallback HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "\r\n");

        assertThat(response, containsString("HTTP/1.1 202"));
        assertThat(response, not(containsString("HTTP/1.1 101")));
        assertThat(FALLBACK_HTTP_HANDLER_INVOKED.get(), is(1));
        assertThat(WS_OPEN_LATCH.get().await(100, TimeUnit.MILLISECONDS), is(false));
        assertThat(WS_OPENED.get(), is(false));
    }

    @Test
    void webSocketUpgradeWithoutWebSocketRouteSkipsHttpUpgradeGateAndFallsBackToHttpRoute() throws Exception {
        String response = sendRequest(
                "GET /gated-fallback HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "\r\n");

        assertThat(response, containsString("HTTP/1.1 202"));
        assertThat(response, not(containsString("HTTP/1.1 101")));
        assertThat(FALLBACK_UPGRADE_GATE_INVOKED.get(), is(0));
        assertThat(FALLBACK_HTTP_HANDLER_INVOKED.get(), is(1));
        assertThat(WS_OPEN_LATCH.get().await(100, TimeUnit.MILLISECONDS), is(false));
        assertThat(WS_OPENED.get(), is(false));
    }

    @Test
    void upgradePolicyRerouteFallsBackToOrdinaryHttpRouting() throws Exception {
        String response = sendRequest(
                "GET /reroute HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "\r\n");

        assertThat(response, containsString("HTTP/1.1 202"));
        assertThat(response, not(containsString("HTTP/1.1 101")));
        assertThat(REROUTE_GATE_INVOKED.get(), is(1));
        assertThat(REROUTED_HTTP_HANDLER_INVOKED.get(), is(1));
        assertThat(WS_OPEN_LATCH.get().await(100, TimeUnit.MILLISECONDS), is(false));
        assertThat(WS_OPENED.get(), is(false));
    }

    @Test
    void listenerRejectionAfterSecurityDoesNotFallBackToHttpRoute() throws Exception {
        String response = sendRequest(
                "GET /reject HTTP/1.1\r\n"
                        + "Host: localhost:" + port + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "Authorization: " + JACK_AUTHORIZATION + "\r\n"
                        + "\r\n");

        assertThat(response, containsString("HTTP/1.1 400"));
        assertThat(response, not(containsString("HTTP/1.1 101")));
        assertThat(response, not(containsString("HTTP/1.1 202")));
        assertThat(REJECT_GATE_INVOKED.get(), is(1));
        assertThat(REJECT_HTTP_HANDLER_INVOKED.get(), is(0));
        assertThat(WS_OPEN_LATCH.get().await(100, TimeUnit.MILLISECONDS), is(false));
        assertThat(WS_OPENED.get(), is(false));
    }

    private String sendRequest(String request) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();

            InputStream input = socket.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int state = 0;

            while (true) {
                int next = input.read();
                if (next == -1) {
                    throw new EOFException("Connection closed before HTTP response headers completed");
                }

                buffer.write(next);

                if (state == 0) {
                    state = next == '\r' ? 1 : 0;
                } else if (state == 1) {
                    state = next == '\n' ? 2 : (next == '\r' ? 1 : 0);
                } else if (state == 2) {
                    state = next == '\r' ? 3 : 0;
                } else {
                    if (next == '\n') {
                        return buffer.toString(StandardCharsets.US_ASCII);
                    }
                    state = next == '\r' ? 1 : 0;
                }
            }
        }
    }

    private static final class CountingUpgradeGate implements Handler, ProtocolUpgradeHandler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            UPGRADE_GATE_INVOKED.incrementAndGet();
            res.next();
        }

        @Override
        public void handleProtocolUpgrade(ServerRequest req, ServerResponse res) {
            handle(req, res);
        }
    }

    private static final class AdminService implements HttpService {
        @Override
        public void routing(HttpRules rules) {
            rules.get(new ServiceUpgradeGate(),
                      SecurityFeature.rolesAllowed("admin"),
                      (req, res) -> {
                          SERVICE_HTTP_HANDLER_INVOKED.incrementAndGet();
                          res.send("service-admin");
                      });
        }
    }

    private static final class ServiceUpgradeGate implements Handler, ProtocolUpgradeHandler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            SERVICE_UPGRADE_GATE_INVOKED.incrementAndGet();
            res.next();
        }

        @Override
        public void handleProtocolUpgrade(ServerRequest req, ServerResponse res) {
            handle(req, res);
        }
    }

    private static final class Http1UpgradeGate implements Handler, ProtocolUpgradeHandler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            HTTP1_UPGRADE_GATE_INVOKED.incrementAndGet();
            res.next();
        }

        @Override
        public void handleProtocolUpgrade(ServerRequest req, ServerResponse res) {
            handle(req, res);
        }
    }

    private static final class BoundaryFirstGate implements Handler, ProtocolUpgradeHandler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            BOUNDARY_FIRST_GATE_INVOKED.incrementAndGet();
            res.next();
        }

        @Override
        public void handleProtocolUpgrade(ServerRequest req, ServerResponse res) {
            handle(req, res);
        }
    }

    private static final class BoundarySecondGate implements Handler, ProtocolUpgradeHandler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            BOUNDARY_SECOND_GATE_INVOKED.incrementAndGet();
            res.status(Status.FORBIDDEN_403);
            res.send();
        }

        @Override
        public void handleProtocolUpgrade(ServerRequest req, ServerResponse res) {
            handle(req, res);
        }
    }

    private static final class FinalizationUpgradeGate implements Handler, ProtocolUpgradeHandler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            res.header("X-Upgrade-Gate", "allowed");
            res.whenSent(() -> {
                FINALIZATION_WHEN_SENT_INVOKED.incrementAndGet();
                FINALIZATION_WHEN_SENT_STATUS.set(res.status().code());
            });
            res.next();
        }

        @Override
        public void handleProtocolUpgrade(ServerRequest req, ServerResponse res) {
            handle(req, res);
        }
    }

    private static final class FallbackDenyingUpgradeGate implements Handler, ProtocolUpgradeHandler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            FALLBACK_HTTP_HANDLER_INVOKED.incrementAndGet();
            res.status(Status.ACCEPTED_202);
            res.send("gated-fallback");
        }

        @Override
        public void handleProtocolUpgrade(ServerRequest req, ServerResponse res) {
            FALLBACK_UPGRADE_GATE_INVOKED.incrementAndGet();
            res.status(Status.FORBIDDEN_403);
            res.send();
        }
    }

    private static final class PlainNextingHandler implements Handler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            CHAIN_PREFILTER_INVOKED.incrementAndGet();
            res.next();
        }
    }

    private static final class ChainDenyingUpgradeGate implements Handler, ProtocolUpgradeHandler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            CHAIN_HTTP_HANDLER_INVOKED.incrementAndGet();
            res.send("chain");
        }

        @Override
        public void handleProtocolUpgrade(ServerRequest req, ServerResponse res) {
            CHAIN_DENY_GATE_INVOKED.incrementAndGet();
            res.status(Status.FORBIDDEN_403);
            res.send();
        }
    }

    private static final class SplitPlainNextingHandler implements Handler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            SPLIT_CHAIN_PREFILTER_INVOKED.incrementAndGet();
            res.next();
        }
    }

    private static final class PlainBeforeSecurityHandler implements Handler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            PLAIN_SECURITY_PREFILTER_INVOKED.incrementAndGet();
            res.next();
        }
    }

    private static final class SplitChainDenyingUpgradeGate implements Handler, ProtocolUpgradeHandler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            SPLIT_CHAIN_HTTP_HANDLER_INVOKED.incrementAndGet();
            res.send("split-chain");
        }

        @Override
        public void handleProtocolUpgrade(ServerRequest req, ServerResponse res) {
            SPLIT_CHAIN_DENY_GATE_INVOKED.incrementAndGet();
            res.status(Status.FORBIDDEN_403);
            res.send();
        }
    }

    private static final class WrappedDenyingUpgradeGate implements Handler, ProtocolUpgradeHandler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            WRAPPED_HTTP_HANDLER_INVOKED.incrementAndGet();
            res.send("wrapped");
        }

        @Override
        public void handleProtocolUpgrade(ServerRequest req, ServerResponse res) {
            WRAPPED_UPGRADE_GATE_INVOKED.incrementAndGet();
            res.status(Status.FORBIDDEN_403);
            res.send();
        }
    }

    private static final class RejectUpgradeGate implements Handler, ProtocolUpgradeHandler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            REJECT_GATE_INVOKED.incrementAndGet();
            res.next();
        }

        @Override
        public void handleProtocolUpgrade(ServerRequest req, ServerResponse res) {
            handle(req, res);
        }
    }

    private static final class ReroutingUpgradeGate implements Handler, ProtocolUpgradeHandler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            res.send("reroute-http");
        }

        @Override
        public void handleProtocolUpgrade(ServerRequest req, ServerResponse res) {
            REROUTE_GATE_INVOKED.incrementAndGet();
            res.reroute("/rerouted-http");
        }
    }

    private static final class ProbeWsListener implements WsListener {
        @Override
        public void onOpen(WsSession session) {
            WS_OPENED.set(true);
            WS_OPEN_LATCH.get().countDown();
        }
    }

    private static final class RejectingWsListener implements WsListener {
        @Override
        public Optional<Headers> onHttpUpgrade(HttpPrologue prologue, Headers headers) {
            throw new WsUpgradeException("Rejected");
        }
    }

    private static final class ConflictingHeadersWsListener implements WsListener {
        @Override
        public Optional<Headers> onHttpUpgrade(HttpPrologue prologue, Headers headers) {
            WritableHeaders<?> upgradeHeaders = WritableHeaders.create()
                    .set(HeaderNames.CONNECTION, "close")
                    .set(HeaderNames.UPGRADE, "h2c")
                    .set(HeaderNames.create("Sec-WebSocket-Accept"), "invalid")
                    .set(HeaderNames.create("X-Listener"), "kept");
            return Optional.of(upgradeHeaders);
        }
    }
}
