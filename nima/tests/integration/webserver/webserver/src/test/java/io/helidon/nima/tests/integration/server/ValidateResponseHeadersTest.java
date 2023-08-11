/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.tests.integration.server;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderName;
import io.helidon.common.http.ServerRequestHeaders;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webclient.api.HttpClientResponse;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientRequest;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.WebServerConfig;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.nima.webserver.http1.Http1Config;
import io.helidon.nima.webserver.http1.Http1ConnectionSelector;
import io.helidon.nima.webserver.spi.ServerConnectionSelector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Test for validating outbound headers (response headers)
 */
@ServerTest
class ValidateResponseHeadersTest {
    public static final String HEADER_NAME_VALUE_DELIMETER = "->";
    public static final String VALID_HEADER_NAME = "Valid-Header-Name";
    public static final String VALID_HEADER_VALUE = "Valid-Header-Value";
    private final Http1Client client;

    ValidateResponseHeadersTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        ServerConnectionSelector http1 = Http1ConnectionSelector.builder()
                .config(Http1Config.builder()
                                .validateRequestHeaders(false)
                                .validateResponseHeaders(true)
                                .build())
                .build();

        server.addConnectionSelector(http1);
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/test", ValidateResponseHeadersTest::responseHandler)
                .get("/testOutputStream", ValidateResponseHeadersTest::responseHandlerForOutputStream);
    }

    @ParameterizedTest
    @MethodSource("responseHeaders")
    void testHeadersFromResponse(String headerName, String headerValue, boolean expectsValid) {
        String headerNameAndValue = headerName + HEADER_NAME_VALUE_DELIMETER + headerValue;
        Http1ClientRequest request = client.get("/test");
        HttpClientResponse response = request.submit(headerNameAndValue);
        if (expectsValid) {
            assertThat(response.status(), is(Http.Status.OK_200));
            String responseHeaderValue = response.headers().get(Http.HeaderNames.create(headerName)).values();
            assertThat(responseHeaderValue, is(headerValue.trim()));
        } else {
            assertThat(response.status(), not(Http.Status.OK_200));
        }
    }

    @ParameterizedTest
    @MethodSource("responseHeaders")
    void testHeadersFromResponseOutputStream(String headerName, String headerValue, boolean expectsValid) {
        String headerNameAndValue = headerName + HEADER_NAME_VALUE_DELIMETER + headerValue;
        Http1ClientRequest request = client.get("/testOutputStream");
        HttpClientResponse response = request.submit(headerNameAndValue);
        if (expectsValid) {
            assertThat(response.status(), is(Http.Status.OK_200));
            String responseHeaderValue = response.headers().get(Http.HeaderNames.create(headerName)).values();
            assertThat(responseHeaderValue, is(headerValue.trim()));
        } else {
            assertThat(response.status(), not(Http.Status.OK_200));
        }
    }

    private static void responseHandler(ServerRequest request, ServerResponse response) {
        setHeader(request, response);
        response.send("any");
    }

    private static void responseHandlerForOutputStream(ServerRequest request, ServerResponse response) {
        setHeader(request, response);
        try (OutputStream outputStream = response.outputStream()) {
            outputStream.write("any".getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void setHeader(ServerRequest request, ServerResponse response) {
        ServerRequestHeaders headers = request.headers();
        String[] header = request.content().as(String.class).split(HEADER_NAME_VALUE_DELIMETER);
        response.headers().add(Http.Headers.create(Http.HeaderNames.create(header[0]), header[1]));
    }

    private static Stream<Arguments> responseHeaders() {
        return Stream.of(
                // Invalid header names
                arguments("Header\u001aName", VALID_HEADER_VALUE, false),
                arguments("Header\u000EName", VALID_HEADER_VALUE, false),
                arguments("HeaderName\r\n", VALID_HEADER_VALUE, false),
                arguments("(Header:Name)", VALID_HEADER_VALUE, false),
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
                arguments(VALID_HEADER_NAME, "H\u001ceaderValue1", false),
                arguments(VALID_HEADER_NAME, "HeaderValue1, Header\u007fValue", false),
                arguments(VALID_HEADER_NAME, "HeaderValue1\u001f, HeaderValue2", false)
        );
    }
}
