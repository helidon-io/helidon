/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webclient.http1;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.concurrent.TimeUnit;

import io.helidon.http.Headers;
import io.helidon.http.Http;
import io.helidon.http.Http.HeaderName;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.media.EntityReader;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.MediaContextConfig;
import io.helidon.common.GenericType;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.spi.ServerConnectionSelector;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.noHeader;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test for validating client side outbound/inbound headers (request/response headers)
 */
@ServerTest
class ValidateHeadersTest {
    public static final String VALID_HEADER_NAME = "Valid-Header-Name";
    public static final String VALID_HEADER_VALUE = "Valid-Header-Value";
    private final String baseURI;

    ValidateHeadersTest(WebServer webServer) {
        baseURI = "http://localhost:" + webServer.port();
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        ServerConnectionSelector http1 = Http1ConnectionSelector.builder()
                .config(Http1Config.builder()
                                .validateRequestHeaders(false)
                                .validateResponseHeaders(false)
                                .build())
                .build();

        server.addConnectionSelector(http1);
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.put("/test", ValidateHeadersTest::headerValidationHandler);
    }

    @ParameterizedTest
    @MethodSource("customHeaders")
    void testRequestHeaders(String headerName, String headerValue, boolean expectsValid) {
        Http1Client client = Http1Client.create(clientConfig -> clientConfig.baseUri(baseURI)
                .protocolConfig(it -> {
                    it.validateRequestHeaders(true);
                    it.validateResponseHeaders(false);
                })
        );
        Http1ClientRequest request = client.put(baseURI + "/test");
        request.header(Http.Headers.create(Http.HeaderNames.create(headerName), headerValue));
        if (expectsValid) {
            HttpClientResponse response = request.request();
            assertThat(response.status(), is(Http.Status.OK_200));
        } else {
            assertThrows(IllegalArgumentException.class, () -> request.request());
        }
    }

    @ParameterizedTest
    @MethodSource("customHeaders")
    void testResponsetHeaders(String headerName, String headerValue, boolean expectsValid) {
        Http1Client client = Http1Client.create(clientConfig -> clientConfig.baseUri(baseURI)
                .protocolConfig(it -> {
                    it.validateRequestHeaders(false);
                    it.validateResponseHeaders(true);
                })
        );
        Http1ClientRequest request = client.put(baseURI + "/test");
        request.header(Http.Headers.create(Http.HeaderNames.create(headerName), headerValue));
        if (expectsValid) {
            HttpClientResponse response = request.request();
            assertThat(response.status(), is(Http.Status.OK_200));
            String responseHeaderValue = response.headers().get(Http.HeaderNames.create(headerName)).values();
            assertThat(responseHeaderValue, is(headerValue.trim()));
        } else {
            assertThrows(IllegalArgumentException.class, () -> request.request());
        }
    }

    @ParameterizedTest
    @MethodSource("customHeaders")
    void testOutputStreamResponsetHeaders(String headerName, String headerValue, boolean expectsValid) {
        Http1Client client = Http1Client.create(clientConfig -> clientConfig.baseUri(baseURI)
                .protocolConfig(it -> {
                    it.validateRequestHeaders(false);
                    it.validateResponseHeaders(true);
                })
                .sendExpectContinue(false)
        );
        Http1ClientRequest request = client.put(baseURI + "/test");
        request.header(Http.Headers.create(Http.HeaderNames.create(headerName), headerValue));
        if (expectsValid) {
            HttpClientResponse response = request.outputStream(it -> {
                it.write("Foo Bar".getBytes(StandardCharsets.UTF_8));
                it.close();
            });
            assertThat(response.status(), is(Http.Status.OK_200));
            String responseHeaderValue = response.headers().get(Http.HeaderNames.create(headerName)).values();
            assertThat(responseHeaderValue, is(headerValue.trim()));
        } else {
            assertThrows(
                    IllegalArgumentException.class, () -> request.outputStream(it -> {
                        it.write("Foo Bar".getBytes(StandardCharsets.UTF_8));
                        it.close();
                    })
            );
        }
    }

    @ParameterizedTest
    @MethodSource("customHeaders")
    void testDisableHeaderValidation(String headerName, String headerValue, boolean expectsValid) {
        Http1Client client = Http1Client.create(clientConfig -> clientConfig.baseUri(baseURI)
                .protocolConfig(it -> {
                    it.validateRequestHeaders(false);
                    it.validateResponseHeaders(false);
                })
        );
        Http1ClientRequest request = client.put(baseURI + "/test");
        request.header(Http.Headers.create(Http.HeaderNames.create(headerName), headerValue));
        HttpClientResponse response = request.request();
        assertThat(response.status(), is(Http.Status.OK_200));
        String responseHeaderValue = response.headers().get(Http.HeaderNames.create(headerName)).values();
        assertThat(responseHeaderValue, is(headerValue.trim()));
    }

    private static void headerValidationHandler(ServerRequest request, ServerResponse response) {
        ServerRequestHeaders headers = request.headers();
        request.headers().toMap().forEach((k, v) -> {
            if (k.contains("Header")) {
                response.headers().add(Http.Headers.create(Http.HeaderNames.create(k), v));
            }
        });
        response.send("any");
    }

    private static Stream<Arguments> customHeaders() {
        return Stream.of(
                // Invalid header names
                arguments("Header\u001aName", VALID_HEADER_VALUE, false),
                arguments("Header\u000EName", VALID_HEADER_VALUE, false),
                arguments("<Header?Name>", VALID_HEADER_VALUE, false),
                arguments("{Header=Name}", VALID_HEADER_VALUE, false),
                arguments("\"HeaderName\"", VALID_HEADER_VALUE, false),
                arguments("[\\HeaderName]", VALID_HEADER_VALUE, false),
                arguments("@Header,Name;", VALID_HEADER_VALUE, false),
                // Valid header names
                arguments("!#$Custom~%&\'*Header+^`|", VALID_HEADER_VALUE, true),
                arguments("Custom_0-9_a-z_A-Z_Header", VALID_HEADER_VALUE, true),
                // Valid header values
                arguments(VALID_HEADER_NAME, "Header Value", true),
                arguments(VALID_HEADER_NAME, "HeaderValue1\u0009, Header=Value2", true),
                arguments(VALID_HEADER_NAME, "Header\tValue", true),
                // Invalid header values
                arguments(VALID_HEADER_NAME, "HeaderV\u001calue1", false),
                arguments(VALID_HEADER_NAME, "HeaderValue1, Header\u007fValue", false),
                arguments(VALID_HEADER_NAME, "HeaderValue1\u001f, HeaderValue2", false)
        );
    }
}
