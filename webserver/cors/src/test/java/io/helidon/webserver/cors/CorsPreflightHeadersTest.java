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

package io.helidon.webserver.cors;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.noHeader;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_METHODS;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_MAX_AGE;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_REQUEST_METHOD;
import static io.helidon.http.HeaderNames.ORIGIN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

@ServerTest
class CorsPreflightHeadersTest {
    private final Http1Client client;

    CorsPreflightHeadersTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    static void server(WebServerConfig.Builder builder) {
        builder.featuresDiscoverServices(false)
                .clearFeatures()
                .addFeature(CorsFeature.create(cors -> cors
                        .enabled(true)
                        .addDefaults(false)
                        .addPath(CorsPathConfig.builder()
                                         .pathPattern("/greet")
                                         .clearAllowOrigins()
                                         .addAllowOrigin("http://foo.bar")
                                         .clearAllowMethods()
                                         .addAllowMethod(Method.PUT)
                                         .clearAllowHeaders()
                                         .addAllowHeader("X-bar")
                                         .addAllowHeader("X-foo")
                                         .allowCredentials(true)
                                         .maxAge(Duration.ofSeconds(-1))
                                         .build())));
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.put("/greet", (req, res) -> res.send());
    }

    static Stream<List<String>> allowedPreflightHeaders() {
        return Stream.of(List.of("X-foo,X-bar"),
                         List.of("X-foo, X-bar"),
                         List.of("X-foo,\tX-bar"),
                         List.of("X-foo \t,\t X-bar"),
                         List.of("X-foo,  X-bar"),
                         List.of(", X-foo,, \t,X-bar,"),
                         List.of("X-foo", "X-bar"),
                         List.of("X-foo,", ",\tX-bar,"));
    }

    static Stream<List<String>> forbiddenPreflightHeaders() {
        return Stream.of(List.of("X-foo,X-bar,X-oops"),
                         List.of("X-foo, X-bar, X-oops"),
                         List.of("X-foo,\tX-bar,\tX-oops"),
                         List.of(",X-foo,, X-bar, ,X-oops,"),
                         List.of("X-foo,X-bar", "X-oops"),
                         List.of("X-foo", "X-bar,X-oops"),
                         List.of(""),
                         List.of(", \t,"));
    }

    @ParameterizedTest
    @MethodSource("forbiddenPreflightHeaders")
    void testPreFlightForbiddenHeader(List<String> headers) {
        Http1ClientRequest request = client.method(Method.OPTIONS).uri("/greet");
        request.header(ORIGIN, "http://foo.bar");
        request.header(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        request.header(ACCESS_CONTROL_REQUEST_HEADERS, headers);

        try (Http1ClientResponse response = request.request()) {
            assertThat(response.status().code(), is(Status.FORBIDDEN_403.code()));
            assertThat(response.status().reasonPhrase(), is("Forbidden"));
        }
    }

    @ParameterizedTest
    @MethodSource("allowedPreflightHeaders")
    void testPreFlightAllowedHeaders(List<String> headers) {
        Http1ClientRequest request = client.method(Method.OPTIONS).uri("/greet");
        request.header(ORIGIN, "http://foo.bar");
        request.header(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        request.header(ACCESS_CONTROL_REQUEST_HEADERS, headers);

        try (Http1ClientResponse response = request.request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "http://foo.bar"));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_METHODS, "PUT"));
            assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS).allValues(), containsInAnyOrder("X-foo", "X-bar"));
            assertThat(response.headers(), noHeader(ACCESS_CONTROL_MAX_AGE));
        }
    }

    @Test
    void testPreFlightRepeatedHeaders() {
        Http1ClientRequest request = client.method(Method.OPTIONS).uri("/greet");
        request.header(ORIGIN, "http://foo.bar");
        request.header(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        request.header(ACCESS_CONTROL_REQUEST_HEADERS, List.of("X-foo, X-bar", "X-foo,X-bar"));

        try (Http1ClientResponse response = request.request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "http://foo.bar"));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_METHODS, "PUT"));
            assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS).allValues(),
                       containsInAnyOrder("X-foo", "X-bar", "X-foo", "X-bar"));
            assertThat(response.headers(), noHeader(ACCESS_CONTROL_MAX_AGE));
        }
    }
}
