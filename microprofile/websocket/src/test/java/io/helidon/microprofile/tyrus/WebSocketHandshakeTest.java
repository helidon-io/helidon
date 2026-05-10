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

package io.helidon.microprofile.tyrus;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.Configuration;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.OnMessage;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import org.glassfish.tyrus.spi.UpgradeResponse;
import org.junit.jupiter.api.Test;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

@AddBean(WebSocketHandshakeRejectionTest.HandshakeRejectingEndpoint.class)
@AddBean(WebSocketHandshakeRejectionTest.FilteredEndpoint.class)
@AddBean(WebSocketHandshakeRejectionTest.FilteredHeaderEndpoint.class)
@AddBean(WebSocketHandshakeRejectionTest.AuthenticatingEndpoint.class)
@AddBean(WebSocketHandshakeRejectionTest.FallbackEndpoint.class)
@AddBean(WebSocketHandshakeRejectionTest.RewrittenKeyEndpoint.class)
@AddBean(WebSocketHandshakeRejectionTest.RewrittenOriginEndpoint.class)
@AddExtension(WebSocketHandshakeRejectionTest.FilteringExtension.class)
@Configuration(configSources = "application.yaml")
class WebSocketHandshakeRejectionTest extends WebSocketBaseTest {
    private static final HeaderName WS_KEY = HeaderNames.create("Sec-WebSocket-Key");
    private static final String ORIGINAL_WS_KEY = "dGhlIHNhbXBsZSBub25jZQ==";
    private static final String ORIGINAL_WS_ACCEPT = "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=";
    private static final String FILTER_WS_KEY = "AQIDBAUGBwgJCgsMDQ4PEA==";
    private static final String FILTER_WS_ACCEPT = "C/0nmHhBztSRGR1CwL6Tf4ZjwpY=";
    private static final AtomicInteger FILTER_INVOCATIONS = new AtomicInteger();
    private static final AtomicInteger HEADER_FILTER_INVOCATIONS = new AtomicInteger();
    private static final AtomicInteger ORIGIN_FILTER_INVOCATIONS = new AtomicInteger();
    private static final AtomicInteger FALLBACK_FILTER_INVOCATIONS = new AtomicInteger();

    @Test
    void testHandshakeWithoutRequiredHeader() throws Exception {
        String statusLine = openHandshake(null);
        assertThat(statusLine, startsWith("HTTP/1.1 403"));
    }

    @Test
    void testHandshakeWithRequiredHeader() throws Exception {
        String statusLine = openHandshake("42");
        assertThat(statusLine, is("HTTP/1.1 101 Switching Protocols"));
    }

    @Test
    void testHandshakeCanBeBlockedByHttpFilter() throws Exception {
        FILTER_INVOCATIONS.set(0);

        String statusLine = openHandshake("/filtered", "42");

        assertThat(statusLine, startsWith("HTTP/1.1 403"));
        assertThat(FILTER_INVOCATIONS.get(), is(1));
    }

    @Test
    void testHandshakeUsesFilterUpdatedHeaders() throws Exception {
        HEADER_FILTER_INVOCATIONS.set(0);

        String statusLine = openHandshake("/inject", null);

        assertThat(statusLine, is("HTTP/1.1 101 Switching Protocols"));
        assertThat(HEADER_FILTER_INVOCATIONS.get(), is(1));
    }

    @Test
    void testHandshakeUsesFilterUpdatedHandshakeKey() throws Exception {
        HEADER_FILTER_INVOCATIONS.set(0);

        HandshakeResult result = openHandshakeResult("/rewrite-key", null, null);

        assertThat(result.statusLine(), is("HTTP/1.1 101 Switching Protocols"));
        assertThat(HEADER_FILTER_INVOCATIONS.get(), is(1));
        assertThat(result.headers(), containsString("Sec-WebSocket-Accept: " + FILTER_WS_ACCEPT + "\r\n"));
        assertThat(result.headers(), not(containsString("Sec-WebSocket-Accept: " + ORIGINAL_WS_ACCEPT + "\r\n")));
    }

    @Test
    void testHandshakeRejectsFilterUpdatedOrigin() throws Exception {
        ORIGIN_FILTER_INVOCATIONS.set(0);

        String statusLine = openHandshake("/rewrite-origin", "42", "http://localhost:" + port());

        assertThat(statusLine, startsWith("HTTP/1.1 403"));
        assertThat(ORIGIN_FILTER_INVOCATIONS.get(), is(1));
    }

    @Test
    void testHandshakeAuthenticationResponseDoesNotSwitchProtocols() throws Exception {
        String statusLine = openHandshake("/authenticate", "42");

        assertThat(statusLine, startsWith("HTTP/1.1 401"));
    }

    @Test
    void testHandshakeNotApplicableFallsBackToHttpRouting() throws Exception {
        FALLBACK_FILTER_INVOCATIONS.set(0);

        String statusLine = openHandshake("/fallback", "42");

        assertThat(statusLine, startsWith("HTTP/1.1 202"));
        assertThat(FALLBACK_FILTER_INVOCATIONS.get(), is(1));
    }

    @Test
    void testHandshakeAllowsSameAuthorityOrigin() throws Exception {
        String statusLine = openHandshake("/reject", "42", "http://localhost:" + port());

        assertThat(statusLine, is("HTTP/1.1 101 Switching Protocols"));
    }

    @Test
    void testHandshakeRejectsOtherOrigin() throws Exception {
        String statusLine = openHandshake("/reject", "42", "http://example.com");

        assertThat(statusLine, startsWith("HTTP/1.1 403"));
    }

    private String openHandshake(String tenantId) throws Exception {
        return openHandshake("/reject", tenantId);
    }

    private String openHandshake(String path, String tenantId) throws Exception {
        return openHandshake(path, tenantId, null);
    }

    private String openHandshake(String path, String tenantId, String origin) throws Exception {
        return openHandshakeResult(path, tenantId, origin).statusLine();
    }

    private HandshakeResult openHandshakeResult(String path, String tenantId, String origin) throws Exception {
        int serverPort = port();

        try (var socket = new java.net.Socket("localhost", serverPort);
             Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                                                                              StandardCharsets.UTF_8))) {
            socket.setSoTimeout(5000);
            writer.write("GET " + path + " HTTP/1.1\r\n");
            writer.write("Host: localhost:" + serverPort + "\r\n");
            writer.write("Upgrade: websocket\r\n");
            writer.write("Connection: Upgrade\r\n");
            writer.write("Sec-WebSocket-Key: " + ORIGINAL_WS_KEY + "\r\n");
            writer.write("Sec-WebSocket-Version: 13\r\n");
            if (tenantId != null) {
                writer.write("X-Tenant-Id: " + tenantId + "\r\n");
            }
            if (origin != null) {
                writer.write("Origin: " + origin + "\r\n");
            }
            writer.write("\r\n");
            writer.flush();

            String statusLine = reader.readLine();
            StringBuilder headers = new StringBuilder();
            String headerLine;
            do {
                headerLine = reader.readLine();
                if (headerLine != null) {
                    headers.append(headerLine).append("\r\n");
                }
            } while (headerLine != null && !headerLine.isEmpty());
            return new HandshakeResult(statusLine, headers.toString());
        }
    }

    private record HandshakeResult(String statusLine, String headers) {
    }

    @ServerEndpoint("/filtered")
    public static class FilteredEndpoint {

        @OnMessage
        public String onMessage(String message) {
            return message;
        }
    }

    @ServerEndpoint(value = "/inject", configurator = RejectingConfigurator.class)
    public static class FilteredHeaderEndpoint {

        @OnMessage
        public String onMessage(String message) {
            return message;
        }
    }

    @ServerEndpoint(value = "/reject", configurator = RejectingConfigurator.class)
    public static class HandshakeRejectingEndpoint {

        @OnMessage
        public String onMessage(String message) {
            return message;
        }
    }

    @ServerEndpoint(value = "/authenticate", configurator = AuthenticatingConfigurator.class)
    public static class AuthenticatingEndpoint {

        @OnMessage
        public String onMessage(String message) {
            return message;
        }
    }

    @ServerEndpoint("/fallback")
    public static class FallbackEndpoint {

        @OnMessage
        public String onMessage(String message) {
            return message;
        }
    }

    @ServerEndpoint(value = "/rewrite-key", configurator = RejectingConfigurator.class)
    public static class RewrittenKeyEndpoint {

        @OnMessage
        public String onMessage(String message) {
            return message;
        }
    }

    @ServerEndpoint("/rewrite-origin")
    public static class RewrittenOriginEndpoint {

        @OnMessage
        public String onMessage(String message) {
            return message;
        }
    }

    public static class RejectingConfigurator extends ServerEndpointConfig.Configurator {

        @Override
        public void modifyHandshake(ServerEndpointConfig sec,
                                    HandshakeRequest request,
                                    HandshakeResponse response) {
            List<String> tenantIdHeaders = request.getHeaders().get("X-Tenant-Id");
            if (tenantIdHeaders == null || tenantIdHeaders.isEmpty()) {
                UpgradeResponse upgradeResponse = (UpgradeResponse) response;
                upgradeResponse.setReasonPhrase("Forbidden");
                upgradeResponse.setStatus(403);
            }
        }
    }

    public static class AuthenticatingConfigurator extends ServerEndpointConfig.Configurator {

        @Override
        public void modifyHandshake(ServerEndpointConfig sec,
                                    HandshakeRequest request,
                                    HandshakeResponse response) {
            UpgradeResponse upgradeResponse = (UpgradeResponse) response;
            upgradeResponse.setReasonPhrase("Unauthorized");
            upgradeResponse.setStatus(401);
            upgradeResponse.getHeaders().put(HeaderNames.WWW_AUTHENTICATE.defaultCase(), List.of("Bearer realm=\"test\""));
        }
    }

    public static class FilteringExtension implements Extension {
        void registerFilter(@Observes @Priority(LIBRARY_BEFORE + 10) @Initialized(ApplicationScoped.class) Object event,
                            ServerCdiExtension server) {
            server.serverRoutingBuilder()
                    .get("/fallback", (req, res) -> res.status(Status.ACCEPTED_202).send())
                    .addFilter((chain, req, res) -> {
                        if ("/filtered".equals(req.prologue().uriPath().path())
                                && req.headers().contains(HeaderNames.UPGRADE)) {
                            FILTER_INVOCATIONS.incrementAndGet();
                            res.status(Status.FORBIDDEN_403);
                            res.send();
                            return;
                        }
                        if ("/inject".equals(req.prologue().uriPath().path())
                                && req.headers().contains(HeaderNames.UPGRADE)) {
                            HEADER_FILTER_INVOCATIONS.incrementAndGet();
                            req.header(HeaderValues.create(HeaderNames.create("X-Tenant-Id"), "42"));
                        }
                        if ("/rewrite-key".equals(req.prologue().uriPath().path())
                                && req.headers().contains(HeaderNames.UPGRADE)) {
                            HEADER_FILTER_INVOCATIONS.incrementAndGet();
                            req.header(HeaderValues.create(HeaderNames.create("X-Tenant-Id"), "42"));
                            req.header(HeaderValues.create(WS_KEY, FILTER_WS_KEY));
                        }
                        if ("/rewrite-origin".equals(req.prologue().uriPath().path())
                                && req.headers().contains(HeaderNames.UPGRADE)) {
                            ORIGIN_FILTER_INVOCATIONS.incrementAndGet();
                            req.header(HeaderValues.create(HeaderNames.ORIGIN, "http://example.com"));
                        }
                        if ("/fallback".equals(req.prologue().uriPath().path())
                                && req.headers().contains(HeaderNames.UPGRADE)) {
                            FALLBACK_FILTER_INVOCATIONS.incrementAndGet();
                            req.header(HeaderValues.create(HeaderNames.UPGRADE, "not-websocket"));
                        }
                        chain.proceed();
                    });
        }
    }
}
