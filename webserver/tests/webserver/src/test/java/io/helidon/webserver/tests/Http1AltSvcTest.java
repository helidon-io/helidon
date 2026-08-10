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

package io.helidon.webserver.tests;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.AltSvc;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.spi.ServerConnectionSelector;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class Http1AltSvcTest {
    private static final Header LOCATION = HeaderValues.create(HeaderNames.LOCATION, "/ok");
    private static final Header CUSTOM_ALT_SVC = HeaderValues.create(HeaderNames.ALT_SVC, "h2=\":443\"");

    private final WebServer server;
    private final Http1Client client;

    Http1AltSvcTest(WebServer server, Http1Client client) {
        this.server = server;
        this.client = client;
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        ServerConnectionSelector http1 = Http1ConnectionSelector.builder()
                .config(Http1Config.builder()
                                .altSvc(AltSvc.builder()
                                                .maxAge(Duration.ofSeconds(120))
                                                .persist(true)
                                                .buildPrototype())
                                .build())
                .build();

        server.addConnectionSelector(http1);
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.error(RedirectException.class,
                     (req, res, throwable) -> res.status(Status.MOVED_PERMANENTLY_301)
                             .header(LOCATION)
                             .send())
                .get("/ok", (req, res) -> res.send("ok"))
                .get("/custom", (req, res) -> res.header(HeaderNames.ALT_SVC, "h2=\":443\"").send())
                .get("/before-send-custom", (req, res) -> {
                    res.beforeSend(() -> res.headers().setIfAbsent(CUSTOM_ALT_SVC));
                    res.send("ok");
                })
                .get("/before-send-error", (req, res) -> {
                    res.beforeSend(() -> res.status(Status.BAD_REQUEST_400));
                    res.send();
                })
                .get("/stream", (req, res) -> {
                    try (var output = res.outputStream()) {
                        output.write('x');
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .get("/stream-error", (req, res) -> {
                    res.outputStream();
                    throw new IllegalStateException("test");
                })
                .get("/stream-error-redirect", (req, res) -> {
                    res.outputStream();
                    throw new RedirectException();
                })
                .get("/custom-stream-error", (req, res) -> {
                    res.header(CUSTOM_ALT_SVC);
                    res.outputStream();
                    throw new IllegalStateException("test");
                })
                .get("/stream-late-status", (req, res) -> {
                    try (var output = res.outputStream()) {
                        res.status(Status.BAD_REQUEST_400);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .get("/error-redirect", (req, res) -> {
                    throw new RedirectException();
                });
    }

    @Test
    void shouldAdvertiseAltSvcOnSuccessfulResponse() {
        try (Http1ClientResponse response = client.get("/ok").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(expectedAltSvc(server.port())));
        }
    }

    @Test
    void shouldAdvertiseAltSvcOnRedirectFromErrorHandler() {
        try (Http1ClientResponse response = client.get("/error-redirect").followRedirects(false).request()) {
            assertThat(response.status(), is(Status.MOVED_PERMANENTLY_301));
            assertThat(response.headers(), hasHeader(LOCATION));
            assertThat(response.headers(), hasHeader(expectedAltSvc(server.port())));
        }
    }

    @Test
    void shouldReadvertiseAltSvcOnRedirectAfterUnusedOutputStreamError() {
        try (Http1ClientResponse response = client.get("/stream-error-redirect").followRedirects(false).request()) {
            assertThat(response.status(), is(Status.MOVED_PERMANENTLY_301));
            assertThat(response.headers(), hasHeader(LOCATION));
            assertThat(response.headers(), hasHeader(expectedAltSvc(server.port())));
        }
    }

    @Test
    void shouldPreserveApplicationAltSvcHeader() {
        try (Http1ClientResponse response = client.get("/custom").request()) {
            assertThat(response.headers().first(HeaderNames.ALT_SVC).orElse(null), is("h2=\":443\""));
        }
    }

    @Test
    void shouldPreserveAltSvcHeaderFromBeforeSendListener() {
        try (Http1ClientResponse response = client.get("/before-send-custom").request()) {
            assertThat(response.headers(), hasHeader(CUSTOM_ALT_SVC));
        }
    }

    @Test
    void shouldUseStatusFromBeforeSendListener() {
        try (Http1ClientResponse response = client.get("/before-send-error").request()) {
            assertThat(response.status(), is(Status.BAD_REQUEST_400));
            assertThat(response.headers().contains(HeaderNames.ALT_SVC), is(false));
        }
    }

    @Test
    void shouldAdvertiseAltSvcOnStreamingResponse() {
        try (Http1ClientResponse response = client.get("/stream").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(expectedAltSvc(server.port())));
        }
    }

    @Test
    void shouldNotAdvertiseAltSvcAfterUnusedOutputStreamError() {
        try (Http1ClientResponse response = client.get("/stream-error").request()) {
            assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
            assertThat(response.headers().contains(HeaderNames.ALT_SVC), is(false));
        }
    }

    @Test
    void shouldPreserveApplicationAltSvcAfterUnusedOutputStreamError() {
        try (Http1ClientResponse response = client.get("/custom-stream-error").request()) {
            assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
            assertThat(response.headers(), hasHeader(CUSTOM_ALT_SVC));
        }
    }

    @Test
    void shouldNotAdvertiseAltSvcAfterLateStreamingErrorStatus() {
        try (Http1ClientResponse response = client.get("/stream-late-status").request()) {
            assertThat(response.status(), is(Status.BAD_REQUEST_400));
            assertThat(response.headers().contains(HeaderNames.ALT_SVC), is(false));
        }
    }

    @Test
    void shouldNotAdvertiseAltSvcOnNotFound() {
        try (Http1ClientResponse response = client.get("/missing").request()) {
            assertThat(response.status(), is(Status.NOT_FOUND_404));
            assertThat(response.headers().contains(HeaderNames.ALT_SVC), is(false));
        }
    }

    private static Header expectedAltSvc(int port) {
        return HeaderValues.create(HeaderNames.ALT_SVC, "h3=\"" + ":" + port + "\"; ma=120; persist=1");
    }

    private static final class RedirectException extends RuntimeException {
    }
}
