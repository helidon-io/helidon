/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
package io.helidon.nima.webserver.cors;

import io.helidon.common.LogConfig;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.HeaderValues;
import io.helidon.nima.webclient.ClientResponse;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientRequest;
import io.helidon.nima.webclient.http1.Http1ClientResponse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_ALLOW_METHODS;
import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_MAX_AGE;
import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_REQUEST_METHOD;
import static io.helidon.common.http.Http.Header.ORIGIN;
import static io.helidon.common.testing.http.HttpHeaderMatcher.hasHeader;
import static io.helidon.common.testing.http.HttpHeaderMatcher.noHeader;
import static io.helidon.nima.webserver.cors.CorsTestServices.SERVICE_1;
import static io.helidon.nima.webserver.cors.CorsTestServices.SERVICE_2;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNot.not;

abstract class AbstractCorsTest extends CorsRouting {

    @BeforeAll
    static void init() {
        LogConfig.configureRuntime();
    }

    abstract String contextRoot();

    abstract Http1Client client();

    abstract String fooOrigin();

    abstract String fooHeader();

    @Test
    void testSimple() {
        try (Http1ClientResponse response = client().get(contextRoot())
                .header(HeaderValues.ACCEPT_TEXT)
                .request()) {

            assertThat(response.status(), is(Http.Status.OK_200));
        }
    }

    @Test
    void test1PreFlightAllowedHeaders1() {
        try (Http1ClientResponse response = client().method(Http.Method.OPTIONS)
                .uri(TestUtil.path(SERVICE_1))
                .header(ORIGIN.withValue("http://foo.bar"))
                .header(ACCESS_CONTROL_REQUEST_METHOD.withValue("PUT"))
                .header(ACCESS_CONTROL_REQUEST_HEADERS.withValue("X-foo"))
                .request()) {

            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN.withValue("http://foo.bar")));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_METHODS.withValue("PUT")));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_HEADERS.withValue("X-foo")));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_MAX_AGE.withValue("3600")));
        }
    }

    @Test
    void test1PreFlightAllowedHeaders2() {
        try (Http1ClientResponse response = client().method(Http.Method.OPTIONS)
                .uri(TestUtil.path(SERVICE_1))
                .header(ORIGIN.withValue("http://foo.bar"))
                .header(ACCESS_CONTROL_REQUEST_METHOD.withValue("PUT"))
                .header(ACCESS_CONTROL_REQUEST_HEADERS.withValue("X-foo, X-bar"))
                .request()) {

            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN.withValue("http://foo.bar")));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_METHODS.withValue("PUT")));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_HEADERS));
            assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS).values(), containsString("X-foo"));
            assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS).values(), containsString("X-bar"));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_MAX_AGE.withValue("3600")));
        }
    }

    @Test
    void test2PreFlightForbiddenOrigin() {
        Http.Status status;
        try (Http1ClientResponse response = client().method(Http.Method.OPTIONS)
                .uri(TestUtil.path(SERVICE_2))
                .header(ORIGIN.withValue("http://not.allowed"))
                .header(ACCESS_CONTROL_REQUEST_METHOD.withValue("PUT"))
                .request()) {

            status = response.status();
        }
        assertThat(status.code(), is(Http.Status.FORBIDDEN_403.code()));
        assertThat(status.reasonPhrase(), is("CORS origin is not in allowed list"));
    }

    @Test
    void test2PreFlightAllowedOrigin() {
        Http1ClientRequest request = client().method(Http.Method.OPTIONS)
                .uri(TestUtil.path(SERVICE_2));

        request.header(ORIGIN.withValue("http://foo.bar"));
        request.header(ACCESS_CONTROL_REQUEST_METHOD.withValue("PUT"));

        try (Http1ClientResponse response = request.request()) {

            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN.withValue("http://foo.bar")));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS.withValue("true")));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_METHODS.withValue("PUT")));
            assertThat(response.headers(), noHeader(ACCESS_CONTROL_ALLOW_HEADERS));
            assertThat(response.headers(), noHeader(ACCESS_CONTROL_ALLOW_HEADERS));
            assertThat(response.headers(), noHeader(ACCESS_CONTROL_MAX_AGE));
        }
    }

    @Test
    void test2PreFlightForbiddenMethod() {
        Http1ClientRequest request = client().method(Http.Method.OPTIONS)
                .uri(TestUtil.path(SERVICE_2));

        request.header(ORIGIN.withValue("http://foo.bar"));
        request.header(ACCESS_CONTROL_REQUEST_METHOD.withValue("POST"));

        Http.Status status;
        try (Http1ClientResponse response = request.request()) {
            status = response.status();
        }
        assertThat(status.code(), is(Http.Status.FORBIDDEN_403.code()));
        assertThat(status.reasonPhrase(), is("CORS origin is denied"));
    }

    @Test
    void test2PreFlightForbiddenHeader() {
        Http1ClientRequest request = client().method(Http.Method.OPTIONS)
                .uri(TestUtil.path(SERVICE_2));

        request.header(ORIGIN.withValue("http://foo.bar"));
        request.header(ACCESS_CONTROL_REQUEST_METHOD.withValue("PUT"));
        request.header(ACCESS_CONTROL_REQUEST_HEADERS.withValue("X-foo, X-bar, X-oops"));

        try (Http1ClientResponse response = request.request()) {
            Http.Status status = response.status();
            assertThat(status.code(), is(Http.Status.FORBIDDEN_403.code()));
            assertThat(status.reasonPhrase(), is("CORS headers not in allowed list"));
        }
    }

    @Test
    void test2PreFlightAllowedHeaders1() {
        Http1ClientRequest request = client().method(Http.Method.OPTIONS)
                .uri(TestUtil.path(contextRoot(), SERVICE_2));

        request.header(ORIGIN.withValue(fooOrigin()));
        request.header(ACCESS_CONTROL_REQUEST_METHOD.withValue("PUT"));
        request.header(ACCESS_CONTROL_REQUEST_HEADERS.withValue(fooHeader()));

        try (Http1ClientResponse response = request.request()) {

            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.headers()
                               .get(ACCESS_CONTROL_ALLOW_ORIGIN).value(), is(fooOrigin()));
            assertThat(response.headers()
                               .get(ACCESS_CONTROL_ALLOW_CREDENTIALS).value(), is("true"));
            assertThat(response.headers()
                               .get(ACCESS_CONTROL_ALLOW_METHODS).value(), is("PUT"));
            assertThat(response.headers()
                               .get(ACCESS_CONTROL_ALLOW_HEADERS).values(), containsString(fooHeader()));
            assertThat(response.headers(), noHeader(ACCESS_CONTROL_MAX_AGE));
        }
    }

    @Test
    void test2PreFlightAllowedHeaders2() {
        Http1ClientRequest request = client().method(Http.Method.OPTIONS)
                .uri(TestUtil.path(SERVICE_2));

        request.header(ORIGIN.withValue("http://foo.bar"));
        request.header(ACCESS_CONTROL_REQUEST_METHOD.withValue("PUT"));
        request.header(ACCESS_CONTROL_REQUEST_HEADERS.withValue("X-foo, X-bar"));

        try (Http1ClientResponse response = request.request()) {

            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN.withValue("http://foo.bar")));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS.withValue("true")));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_METHODS.withValue("PUT")));
            assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS).value(), containsString("X-foo"));
            assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS).value(), containsString("X-bar"));
            assertThat(response.headers(), noHeader(ACCESS_CONTROL_MAX_AGE));
        }
    }

    @Test
    void test2PreFlightAllowedHeaders3() {
        Http1ClientRequest request = client().method(Http.Method.OPTIONS)
                .uri(TestUtil.path(SERVICE_2));

        request.header(ORIGIN.withValue("http://foo.bar"));
        request.header(ACCESS_CONTROL_REQUEST_METHOD.withValue("PUT"));
        request.header(ACCESS_CONTROL_REQUEST_HEADERS.withValue("X-foo, X-bar"));
        request.header(ACCESS_CONTROL_REQUEST_HEADERS.withValue("X-foo, X-bar"));

        try (Http1ClientResponse response = request.request()) {

            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN.withValue("http://foo.bar")));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS.withValue("true")));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_METHODS.withValue("PUT")));
            assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS).value(), containsString("X-foo"));
            assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS).value(), containsString("X-bar"));
            assertThat(response.headers(), noHeader(ACCESS_CONTROL_MAX_AGE));
        }
    }

    @Test
    void test1ActualAllowedOrigin() {
        Http1ClientRequest request = client().method(Http.Method.PUT)
                .uri(TestUtil.path(SERVICE_1))
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN);

        request.header(ORIGIN.withValue("http://foo.bar"));
        request.header(ACCESS_CONTROL_REQUEST_METHOD.withValue("PUT"));

        try (ClientResponse response = request.submit("")) {

            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN.withValue("*")));
        }
    }

    @Test
    void test2ActualAllowedOrigin() {
        Http1ClientRequest request = client().method(Http.Method.PUT)
                .uri(TestUtil.path(SERVICE_2))
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN);

        request.header(ORIGIN.withValue("http://foo.bar"));

        try (ClientResponse response = request.submit("")) {

            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN.withValue("http://foo.bar")));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS.withValue("true")));
        }
    }

    @Test
    void test2ErrorResponse() {
        Http1ClientRequest request = client().get(TestUtil.path(SERVICE_2) + "/notfound")
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN);

        request.header(ORIGIN.withValue("http://foo.bar"));

        try (ClientResponse response = request.request()) {

            assertThat(response.status(), is(not(Http.Status.OK_200)));
            assertThat(response.headers(), noHeader(ACCESS_CONTROL_ALLOW_ORIGIN));
        }
    }

    ClientResponse runTest1PreFlightAllowedOrigin() {
        Http1ClientRequest request = client().method(Http.Method.OPTIONS)
                .uri(TestUtil.path(contextRoot(), SERVICE_1));

        request.header(ORIGIN.withValue(fooOrigin()));
        request.header(ACCESS_CONTROL_REQUEST_METHOD.withValue("PUT"));

        return request.request();
    }
}
