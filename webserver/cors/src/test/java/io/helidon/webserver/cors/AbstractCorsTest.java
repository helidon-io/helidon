/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webclient.http1.Http1ClientResponse;

import org.junit.jupiter.api.Test;

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
import static io.helidon.webserver.cors.CorsTestServices.SERVICE_1;
import static io.helidon.webserver.cors.CorsTestServices.SERVICE_2;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNot.not;

abstract class AbstractCorsTest extends CorsRouting {

    abstract String contextRoot();

    abstract Http1Client client();

    abstract String fooOrigin();

    abstract String fooHeader();

    @Test
    void testSimple() {
        try (Http1ClientResponse response = client().get(contextRoot())
                .header(HeaderValues.ACCEPT_TEXT)
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
        }
    }

    @Test
    void test1PreFlightAllowedHeaders1() {
        try (Http1ClientResponse response = client().method(Method.OPTIONS)
                .uri(TestUtil.path(SERVICE_1))
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "http://foo.bar"));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_METHODS, "PUT"));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_HEADERS, "X-foo"));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_MAX_AGE, "3600"));
        }
    }

    @Test
    void test1PreFlightAllowedHeaders2() {
        try (Http1ClientResponse response = client().method(Method.OPTIONS)
                .uri(TestUtil.path(SERVICE_1))
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "http://foo.bar"));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_METHODS, "PUT"));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_HEADERS));
            assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS).values(), containsString("X-foo"));
            assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS).values(), containsString("X-bar"));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_MAX_AGE, "3600"));
        }
    }

    @Test
    void test2PreFlightForbiddenOrigin() {
        Status status;
        try (Http1ClientResponse response = client().method(Method.OPTIONS)
                .uri(TestUtil.path(SERVICE_2))
                .header(ORIGIN, "http://not.allowed")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .request()) {

            status = response.status();
        }
        assertThat(status.code(), is(Status.FORBIDDEN_403.code()));
        assertThat(status.reasonPhrase(), is("CORS origin is not in allowed list"));
    }

    @Test
    void test2PreFlightAllowedOrigin() {
        Http1ClientRequest request = client().method(Method.OPTIONS)
                .uri(TestUtil.path(SERVICE_2));

        request.header(ORIGIN, "http://foo.bar");
        request.header(ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        try (Http1ClientResponse response = request.request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "http://foo.bar"));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_METHODS, "PUT"));
            assertThat(response.headers(), noHeader(ACCESS_CONTROL_ALLOW_HEADERS));
            assertThat(response.headers(), noHeader(ACCESS_CONTROL_ALLOW_HEADERS));
            assertThat(response.headers(), noHeader(ACCESS_CONTROL_MAX_AGE));
        }
    }

    @Test
    void test2PreFlightForbiddenMethod() {
        Http1ClientRequest request = client().method(Method.OPTIONS)
                .uri(TestUtil.path(SERVICE_2));

        request.header(ORIGIN, "http://foo.bar");
        request.header(ACCESS_CONTROL_REQUEST_METHOD, "POST");

        Status status;
        try (Http1ClientResponse response = request.request()) {
            status = response.status();
        }
        assertThat(status.code(), is(Status.FORBIDDEN_403.code()));
        assertThat(status.reasonPhrase(), is("CORS origin is denied"));
    }

    @Test
    void test2PreFlightForbiddenHeader() {
        Http1ClientRequest request = client().method(Method.OPTIONS)
                .uri(TestUtil.path(SERVICE_2));

        request.header(ORIGIN, "http://foo.bar");
        request.header(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        request.header(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar, X-oops");

        try (Http1ClientResponse response = request.request()) {
            Status status = response.status();
            assertThat(status.code(), is(Status.FORBIDDEN_403.code()));
            assertThat(status.reasonPhrase(), is("CORS headers not in allowed list"));
        }
    }

    @Test
    void test2PreFlightAllowedHeaders1() {
        Http1ClientRequest request = client().method(Method.OPTIONS)
                .uri(TestUtil.path(contextRoot(), SERVICE_2));

        request.header(ORIGIN, fooOrigin());
        request.header(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        request.header(ACCESS_CONTROL_REQUEST_HEADERS, fooHeader());

        try (Http1ClientResponse response = request.request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers()
                               .get(ACCESS_CONTROL_ALLOW_ORIGIN).get(), is(fooOrigin()));
            assertThat(response.headers()
                               .get(ACCESS_CONTROL_ALLOW_CREDENTIALS).get(), is("true"));
            assertThat(response.headers()
                               .get(ACCESS_CONTROL_ALLOW_METHODS).get(), is("PUT"));
            assertThat(response.headers()
                               .get(ACCESS_CONTROL_ALLOW_HEADERS).values(), containsString(fooHeader()));
            assertThat(response.headers(), noHeader(ACCESS_CONTROL_MAX_AGE));
        }
    }

    @Test
    void test2PreFlightAllowedHeaders2() {
        Http1ClientRequest request = client().method(Method.OPTIONS)
                .uri(TestUtil.path(SERVICE_2));

        request.header(ORIGIN, "http://foo.bar");
        request.header(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        request.header(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar");

        try (Http1ClientResponse response = request.request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "http://foo.bar"));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_METHODS, "PUT"));
            assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS).get(), containsString("X-foo"));
            assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS).get(), containsString("X-bar"));
            assertThat(response.headers(), noHeader(ACCESS_CONTROL_MAX_AGE));
        }
    }

    @Test
    void test2PreFlightAllowedHeaders3() {
        Http1ClientRequest request = client().method(Method.OPTIONS)
                .uri(TestUtil.path(SERVICE_2));

        request.header(ORIGIN, "http://foo.bar");
        request.header(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        request.header(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar");
        request.header(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar");

        try (Http1ClientResponse response = request.request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "http://foo.bar"));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_METHODS, "PUT"));
            assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS).get(), containsString("X-foo"));
            assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS).get(), containsString("X-bar"));
            assertThat(response.headers(), noHeader(ACCESS_CONTROL_MAX_AGE));
        }
    }

    @Test
    void test1ActualAllowedOrigin() {
        Http1ClientRequest request = client().method(Method.PUT)
                .uri(TestUtil.path(SERVICE_1))
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN);

        request.header(ORIGIN, "http://foo.bar");
        request.header(ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        try (HttpClientResponse response = request.submit("")) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*"));
        }
    }

    @Test
    void test2ActualAllowedOrigin() {
        Http1ClientRequest request = client().method(Method.PUT)
                .uri(TestUtil.path(SERVICE_2))
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN);

        request.header(ORIGIN, "http://foo.bar");

        try (HttpClientResponse response = request.submit("")) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "http://foo.bar"));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        }
    }

    @Test
    void test2ErrorResponse() {
        Http1ClientRequest request = client().get(TestUtil.path(SERVICE_2) + "/notfound")
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN);

        request.header(ORIGIN, "http://foo.bar");

        try (HttpClientResponse response = request.request()) {

            assertThat(response.status(), is(not(Status.OK_200)));
            assertThat(response.headers(), noHeader(ACCESS_CONTROL_ALLOW_ORIGIN));
        }
    }

    HttpClientResponse runTest1PreFlightAllowedOrigin() {
        Http1ClientRequest request = client().method(Method.OPTIONS)
                .uri(TestUtil.path(contextRoot(), SERVICE_1));

        request.header(ORIGIN, fooOrigin());
        request.header(ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        return request.request();
    }
}
