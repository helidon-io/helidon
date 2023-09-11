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

package io.helidon.webserver.tests;

import java.util.stream.Stream;

import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientRequest;
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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Test for validating inbound headers (request headers)
 */
@ServerTest
class ValidateRequestHeadersTest {
    public static final String VALID_HEADER_NAME = "Valid-Header-Name";
    public static final String VALID_HEADER_VALUE = "Valid-Header-Value";
    private final Http1Client client;

    ValidateRequestHeadersTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        ServerConnectionSelector http1 = Http1ConnectionSelector.builder()
                .config(Http1Config.builder()
                                .validateRequestHeaders(true)
                                .validateResponseHeaders(false)
                                .build())
                .build();

        server.addConnectionSelector(http1);
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/test", ValidateRequestHeadersTest::requestHandler);
    }

    @ParameterizedTest
    @MethodSource("requestHeaders")
    void testHeadersFromResponse(String headerName, String headerValue, boolean expectsValid) {
        Http1ClientRequest request = client.get("/test");
        request.header(HeaderValues.create(HeaderNames.create(headerName), headerValue));
        HttpClientResponse response = request.submit("any");
        if (expectsValid) {
            assertThat(response.status(), is(Status.OK_200));
        } else {
            assertThat(response.status(), not(Status.OK_200));
        }
    }

    private static void requestHandler(ServerRequest request, ServerResponse response) {
        ServerRequestHeaders headers = request.headers();
        response.send("any");
    }

    private static Stream<Arguments> requestHeaders() {
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
                // arguments(VALID_HEADER_NAME, " Header Value ", true),
                // Invalid header values
                arguments(VALID_HEADER_NAME, "H\u001ceaderValue1", false),
                arguments(VALID_HEADER_NAME, "HeaderValue1, Header\u007fValue", false),
                arguments(VALID_HEADER_NAME, "HeaderValue1\u001f, HeaderValue2", false)
        );
    }
}
