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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import io.helidon.common.configurable.AllowList;
import io.helidon.http.DirectHandler;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.Method;
import io.helidon.http.RequestedUriDiscoveryContext;
import io.helidon.http.Status;
import io.helidon.security.Security;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.context.ContextFeature;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.security.SecurityFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class RequestedUriDiscoveryErrorTest {
    private static final HeaderName EXCEPTION_HEADER = HeaderNames.create("X-Http-Exception");
    private static final HeaderName DIRECT_HANDLER_HOST_HEADER = HeaderNames.create("X-Direct-Handler-Host");
    private final HttpClient client;
    private final URI uri;

    RequestedUriDiscoveryErrorTest(URI uri) {
        this.uri = uri;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        var requestedUriDiscovery = RequestedUriDiscoveryContext.builder()
                .enabled(true)
                .addDiscoveryType(RequestedUriDiscoveryContext.RequestedUriDiscoveryType.FORWARDED)
                .addDiscoveryType(RequestedUriDiscoveryContext.RequestedUriDiscoveryType.X_FORWARDED)
                .trustedProxies(AllowList.builder().allowAll(true).build())
                .build();
        var directHandlers = DirectHandlers.builder()
                .addHandler(DirectHandler.EventType.BAD_REQUEST,
                            (request, _, _, _, _) -> DirectHandler.TransportResponse.builder()
                                    .status(Status.BAD_REQUEST_400)
                                    .header(DIRECT_HANDLER_HOST_HEADER,
                                            request.headers().first(HeaderNames.HOST).orElse("missing"))
                                    .build())
                .build();
        server.featuresDiscoverServices(false)
                .requestedUriDiscoveryContext(requestedUriDiscovery)
                .directHandlers(directHandlers)
                .addFeature(ContextFeature.create())
                .addFeature(SecurityFeature.builder()
                                    .security(Security.builder().build())
                                    .build());
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        routing.route(Method.GET, "/", (_, res) -> res.send("ok"))
                .route(Method.GET, "/http-exception", (_, _) -> {
                    throw new HttpException("Unprocessable", Status.UNPROCESSABLE_CONTENT_422)
                            .header(HeaderValues.create(EXCEPTION_HEADER, "present"));
                });
    }

    @Test
    void genericHttpExceptionReturnsConfiguredStatus() throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                                                            .timeout(Duration.ofSeconds(5))
                                                            .uri(uri.resolve("/http-exception"))
                                                            .GET()
                                                            .build(),
                                                    HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode(), is(Status.UNPROCESSABLE_CONTENT_422.code()));
        assertThat(response.headers().firstValue(EXCEPTION_HEADER.lowerCase()), is(Optional.of("present")));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
    }

    @Test
    void invalidXForwardedSchemeReturnsBadRequest() throws IOException, InterruptedException {
        HttpResponse<String> response = request("X-Forwarded-Proto", "not_a_scheme");

        assertThat(response.statusCode(), is(Status.BAD_REQUEST_400.code()));
        assertThat(response.headers().firstValue(DIRECT_HANDLER_HOST_HEADER.lowerCase()),
                   is(Optional.of(uri.getRawAuthority())));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
    }

    @Test
    void nonNumericXForwardedPortReturnsBadRequest() throws IOException, InterruptedException {
        HttpResponse<String> response = request("X-Forwarded-Port", "not-a-port");

        assertThat(response.statusCode(), is(Status.BAD_REQUEST_400.code()));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
    }

    @Test
    void incompleteQuotedForwardedValueReturnsBadRequest() throws IOException, InterruptedException {
        HttpResponse<String> response = request("Forwarded", "for=client.example;proto=https;ext=\"dangling\\");

        assertThat(response.statusCode(), is(Status.BAD_REQUEST_400.code()));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
    }

    @Test
    void nonNumericForwardedHostPortReturnsBadRequest() throws IOException, InterruptedException {
        HttpResponse<String> response = request("Forwarded",
                                                "for=client.example;host=\"public.example:not-a-port\";proto=https");

        assertThat(response.statusCode(), is(Status.BAD_REQUEST_400.code()));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
    }

    @Test
    void emptyForwardedHostPortIsAccepted() throws IOException, InterruptedException {
        HttpResponse<String> response = request("Forwarded",
                                                "for=client.example;host=\"public.example:\";proto=https");

        assertThat(response.statusCode(), is(Status.OK_200.code()));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
    }

    @Test
    void outOfRangeForwardedHostPortReturnsBadRequest() throws IOException, InterruptedException {
        HttpResponse<String> response = request("Forwarded",
                                                "for=client.example;host=\"public.example:65536\";proto=https");

        assertThat(response.statusCode(), is(Status.BAD_REQUEST_400.code()));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
    }

    private HttpResponse<String> request(String headerName, String headerValue) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder()
                                   .timeout(Duration.ofSeconds(5))
                                   .uri(uri)
                                   .header("X-Forwarded-For", "client.example, proxy.example")
                                   .header(headerName, headerValue)
                                   .GET()
                                   .build(),
                           HttpResponse.BodyHandlers.ofString());
    }
}
