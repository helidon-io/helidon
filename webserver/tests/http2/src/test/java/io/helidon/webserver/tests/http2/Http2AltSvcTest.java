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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webclient.http2.Http2ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.AltSvc;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.http2.Http2Upgrader;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.http2.Http2TestClient;
import io.helidon.webserver.testing.junit5.http2.Http2TestConnection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.GET;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class Http2AltSvcTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Header CUSTOM_ALT_SVC = HeaderValues.create(HeaderNames.ALT_SVC, "h2=\":443\"");

    private final WebServer server;
    private final Http2Client client;

    Http2AltSvcTest(WebServer server) {
        this.server = server;
        this.client = Http2Client.builder()
                .baseUri("http://localhost:" + server.port())
                .protocolConfig(Http2ClientProtocolConfig.builder()
                                        .priorKnowledge(true)
                                        .build())
                .build();
    }

    @SetUpServer
    static void server(WebServerConfig.Builder serverBuilder) {
        Http2Config http2Config = Http2Config.builder()
                .altSvc(AltSvc.builder()
                                .maxAge(Duration.ofSeconds(120))
                                .persist(true)
                                .buildPrototype())
                .build();

        serverBuilder.port(-1)
                .protocolsDiscoverServices(false)
                .addConnectionSelector(Http2ConnectionSelector.builder()
                                               .http2Config(http2Config)
                                               .build())
                .addConnectionSelector(Http1ConnectionSelector.builder()
                                               .config(Http1Config.create())
                                               .addUpgrader(Http2Upgrader.create(http2Config))
                                               .build());
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.error(RedirectException.class,
                     (req, res, throwable) -> res.status(Status.MOVED_PERMANENTLY_301)
                             .header(HeaderNames.LOCATION, "/ok")
                             .send())
                .route(Http2Route.route(GET, "/ok", (req, res) -> res.send("ok")))
                .route(Http2Route.route(GET, "/custom",
                                       (req, res) -> res.header(HeaderNames.ALT_SVC, "h2=\":443\"").send()))
                .route(Http2Route.route(GET, "/before-send-error", (req, res) -> {
                    res.beforeSend(() -> res.status(Status.BAD_REQUEST_400));
                    res.send();
                }))
                .route(Http2Route.route(GET, "/before-send-204", (req, res) -> {
                    res.header(HeaderNames.TRAILER, "X-Test");
                    res.beforeSend(() -> res.status(Status.NO_CONTENT_204));
                    res.send("body");
                }))
                .route(Http2Route.route(GET, "/before-send-205", (req, res) -> {
                    res.header(HeaderNames.TRAILER, "X-Test");
                    res.beforeSend(() -> res.status(Status.RESET_CONTENT_205));
                    res.send("body");
                }))
                .route(Http2Route.route(GET, "/before-send-304", (req, res) -> {
                    res.header(HeaderNames.TRAILER, "X-Test");
                    res.beforeSend(() -> res.status(Status.NOT_MODIFIED_304));
                    res.send("body");
                }))
                .route(Http2Route.route(GET, "/before-send-100", (req, res) -> {
                    res.header(HeaderNames.TRAILER, "X-Test");
                    res.beforeSend(() -> res.status(Status.CONTINUE_100));
                    res.send("body");
                }))
                .route(Http2Route.route(GET, "/stream", (req, res) -> {
                    try (var output = res.outputStream()) {
                        output.write('x');
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }))
                .route(Http2Route.route(GET, "/stream-error", (req, res) -> {
                    res.outputStream();
                    throw new IllegalStateException("test");
                }))
                .route(Http2Route.route(GET, "/stream-error-redirect", (req, res) -> {
                    res.outputStream();
                    throw new RedirectException();
                }))
                .route(Http2Route.route(GET, "/custom-stream-error", (req, res) -> {
                    res.header(CUSTOM_ALT_SVC);
                    res.outputStream();
                    throw new IllegalStateException("test");
                }))
                .route(Http2Route.route(GET, "/before-send-custom-stream", (req, res) -> {
                    res.beforeSend(() -> res.header(CUSTOM_ALT_SVC));
                    try (var output = res.outputStream()) {
                        output.write('x');
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }))
                .route(Http2Route.route(GET, "/error-redirect", (req, res) -> {
                    throw new RedirectException();
                }));
    }

    @AfterEach
    void cleanup() {
        client.closeResource();
    }

    @Test
    void shouldAdvertiseAltSvcOnSuccessfulResponse() {
        try (Http2ClientResponse response = client.get("/ok").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers().first(HeaderNames.ALT_SVC).orElse(null), is(expectedAltSvc(server.port())));
        }
    }

    @Test
    void shouldAdvertiseAltSvcAfterH2cUpgrade() {
        Http2Client upgradeClient = Http2Client.builder()
                .baseUri("http://localhost:" + server.port())
                .protocolConfig(Http2ClientProtocolConfig.builder()
                                        .priorKnowledge(false)
                                        .build())
                .build();
        try (Http2ClientResponse response = upgradeClient.get("/ok").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers().first(HeaderNames.ALT_SVC).orElse(null), is(expectedAltSvc(server.port())));
        } finally {
            upgradeClient.closeResource();
        }
    }

    @Test
    void shouldAdvertiseAltSvcOnRedirectFromErrorHandler() {
        try (Http2ClientResponse response = client.get("/error-redirect").followRedirects(false).request()) {
            assertThat(response.status(), is(Status.MOVED_PERMANENTLY_301));
            assertThat(response.headers().first(HeaderNames.LOCATION).orElse(null), is("/ok"));
            assertThat(response.headers().first(HeaderNames.ALT_SVC).orElse(null), is(expectedAltSvc(server.port())));
        }
    }

    @Test
    void shouldReadvertiseAltSvcOnRedirectAfterUnusedOutputStreamError() {
        try (Http2ClientResponse response = client.get("/stream-error-redirect").followRedirects(false).request()) {
            assertThat(response.status(), is(Status.MOVED_PERMANENTLY_301));
            assertThat(response.headers().first(HeaderNames.LOCATION).orElse(null), is("/ok"));
            assertThat(response.headers().first(HeaderNames.ALT_SVC).orElse(null), is(expectedAltSvc(server.port())));
        }
    }

    @Test
    void shouldPreserveApplicationAltSvcHeader() {
        try (Http2ClientResponse response = client.get("/custom").request()) {
            assertThat(response.headers().first(HeaderNames.ALT_SVC).orElse(null), is("h2=\":443\""));
        }
    }

    @Test
    void shouldUseStatusFromBeforeSendListener() {
        try (Http2ClientResponse response = client.get("/before-send-error").request()) {
            assertThat(response.status(), is(Status.BAD_REQUEST_400));
            assertThat(response.headers().contains(HeaderNames.ALT_SVC), is(false));
        }
    }

    @Test
    void shouldEndNoContentStatusesSelectedByBeforeSendWithHeaders(Http2TestClient testClient) {
        assertHeaderOnlyResponse(testClient, "/before-send-204", Status.NO_CONTENT_204, null, true);
        assertHeaderOnlyResponse(testClient, "/before-send-205", Status.RESET_CONTENT_205, "0", true);
        assertHeaderOnlyResponse(testClient, "/before-send-304", Status.NOT_MODIFIED_304, null, true);
        assertHeaderOnlyResponse(testClient, "/before-send-100", Status.INTERNAL_SERVER_ERROR_500, "0", false);
    }

    @Test
    void shouldAdvertiseAltSvcOnStreamingResponse() {
        try (Http2ClientResponse response = client.get("/stream").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers().first(HeaderNames.ALT_SVC).orElse(null), is(expectedAltSvc(server.port())));
        }
    }

    @Test
    void shouldNotAdvertiseAltSvcAfterUnusedOutputStreamError() {
        try (Http2ClientResponse response = client.get("/stream-error").request()) {
            assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
            assertThat(response.headers().contains(HeaderNames.ALT_SVC), is(false));
        }
    }

    @Test
    void shouldPreserveApplicationAltSvcAfterUnusedOutputStreamError() {
        try (Http2ClientResponse response = client.get("/custom-stream-error").request()) {
            assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
            assertThat(response.headers().first(HeaderNames.ALT_SVC).orElse(null), is(CUSTOM_ALT_SVC.get()));
        }
    }

    @Test
    void shouldPreserveAltSvcHeaderFromStreamingBeforeSendListener() {
        try (Http2ClientResponse response = client.get("/before-send-custom-stream").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers().first(HeaderNames.ALT_SVC).orElse(null), is(CUSTOM_ALT_SVC.get()));
        }
    }

    @Test
    void shouldNotAdvertiseAltSvcOnNotFound() {
        try (Http2ClientResponse response = client.get("/missing").request()) {
            assertThat(response.status(), is(Status.NOT_FOUND_404));
            assertThat(response.headers().contains(HeaderNames.ALT_SVC), is(false));
        }
    }

    private void assertHeaderOnlyResponse(Http2TestClient testClient,
                                          String path,
                                          Status expectedStatus,
                                          String expectedContentLength,
                                          boolean expectAltSvc) {
        try (Http2TestConnection connection = testClient.createConnection()) {
            connection.request(1, GET, path, WritableHeaders.create(), BufferData.empty());
            connection.assertSettings(TIMEOUT);
            connection.assertWindowsUpdate(0, TIMEOUT);
            connection.assertSettings(TIMEOUT);

            Http2FrameData frame = connection.assertNextFrame(Http2FrameType.HEADERS, TIMEOUT);
            assertThat(frame.header().flags(Http2FrameTypes.HEADERS).endOfStream(), is(true));
            Http2Headers responseHeaders = connection.assertHeaders(frame, 1);
            assertThat(responseHeaders.status(), is(expectedStatus));
            assertThat(responseHeaders.httpHeaders().first(HeaderNames.CONTENT_LENGTH).orElse(null),
                       is(expectedContentLength));
            assertThat(responseHeaders.httpHeaders().contains(HeaderNames.TRAILER), is(false));
            assertThat(responseHeaders.httpHeaders().first(HeaderNames.ALT_SVC).orElse(null),
                       is(expectAltSvc ? expectedAltSvc(server.port()) : null));
        }
    }

    private static String expectedAltSvc(int port) {
        return "h3=\"" + ":" + port + "\"; ma=120; persist=1";
    }

    private static final class RedirectException extends RuntimeException {
    }
}
