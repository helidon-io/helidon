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

package io.helidon.declarative.tests.cors;

import java.time.Duration;
import java.util.OptionalLong;

import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.testing.junit5.ServerTest;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeaderValue;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.noHeader;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_METHODS;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_MAX_AGE;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_REQUEST_METHOD;
import static io.helidon.http.HeaderNames.HOST;
import static io.helidon.http.HeaderNames.ORIGIN;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

@ServerTest
public class DeclarativeCorsTest {
    private final Http1Client client;

    public DeclarativeCorsTest(Http1Client client) {
        this.client = client;
    }

    @Test
    public void test1PreFlightAllowedOrigin() {
        var res = client.options("/cors1")
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .request(Void.class);

        assertThat(res.status(), is(Status.OK_200));

        var headers = res.headers();
        assertThat(headers, hasHeaderValue(ACCESS_CONTROL_ALLOW_ORIGIN, is("*")));
        assertThat(headers, hasHeaderValue(ACCESS_CONTROL_ALLOW_METHODS, is("*")));
        assertThat(headers, noHeader(ACCESS_CONTROL_ALLOW_HEADERS));
        assertThat(headers, hasHeaderValue(ACCESS_CONTROL_MAX_AGE, is("3600")));
    }

    @Test
    public void test2PreFlightForbiddenOrigin() {
        var res = client.options("/cors2")
                .header(ORIGIN, "http://not.allowed")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .readTimeout(Duration.ofHours(1))
                .request(String.class);

        assertThat(res.status(), is(Status.FORBIDDEN_403));
        assertThat(res.headers().contentLength(), is(OptionalLong.of(0L)));
    }

    @Test
    public void test2PreFlightAllowedOrigin() {
        var res = client.options("/cors2")
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .request(Void.class);

        assertThat(res.status(), is(Status.OK_200));

        var headers = res.headers();
        assertThat(headers, hasHeaderValue(ACCESS_CONTROL_ALLOW_ORIGIN, is("http://foo.bar")));
        assertThat(headers, hasHeaderValue(ACCESS_CONTROL_ALLOW_CREDENTIALS, is("true")));
        assertThat(headers, hasHeaderValue(ACCESS_CONTROL_ALLOW_METHODS, is("PUT")));
        assertThat(headers, noHeader(ACCESS_CONTROL_ALLOW_HEADERS));
        assertThat(headers, noHeader(ACCESS_CONTROL_MAX_AGE));
    }

    @Test
    public void test2PreFlightForbiddenMethod() {
        var res = client.options("/cors2")
                .header(ORIGIN, "http://not.allowed")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .readTimeout(Duration.ofHours(1))
                .request(Void.class);

        assertThat(res.status(), is(Status.FORBIDDEN_403));
        assertThat(res.headers().contentLength(), is(OptionalLong.of(0L)));
    }

    @Test
    public void test2PreFlightForbiddenHeader() {
        var res = client.options("/cors2")
                .header(ORIGIN, "http://not.allowed")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo", "X-bar", "X-oops")
                .request(Void.class);

        assertThat(res.status(), is(Status.FORBIDDEN_403));
        assertThat(res.headers().contentLength(), is(OptionalLong.of(0L)));
    }

    @Test
    public void test3PreFlightOrigin() {
        var res = client.options("/cors3")
                .header(ORIGIN, "http://not.allowed")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "HEAD")
                .readTimeout(Duration.ofHours(1))
                .request(String.class);

        assertThat(res.status(), is(Status.OK_200));
        assertThat(res.headers().contentLength(), is(OptionalLong.of(0L)));
    }

    @Test
    public void test3PreFlightMethod() {
        var res = client.options("/cors3")
                .header(ORIGIN, "http://not.allowed")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .request(Void.class);

        assertThat(res.status(), is(Status.OK_200));
        assertThat(res.headers().contentLength(), is(OptionalLong.of(0L)));
    }

    @Test
    public void test3PreFlightForbiddenHeader() {
        var res = client.options("/cors3")
                .header(ORIGIN, "http://not.allowed")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "HEAD")
                .header(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo", "X-bar", "X-oops")
                .request(Void.class);

        assertThat(res.status(), is(Status.OK_200));
        assertThat(res.headers().contentLength(), is(OptionalLong.of(0L)));
    }

    @Test
    void test1PreFlightAllowedHeaders1() {
        var res = client.options("/cors1")
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo")
                .request(Void.class);

        assertThat(res.status(), is(Status.OK_200));

        var headers = res.headers();
        assertThat(headers, hasHeaderValue(ACCESS_CONTROL_ALLOW_ORIGIN, is("*")));
        assertThat(headers, hasHeaderValue(ACCESS_CONTROL_ALLOW_METHODS, is("*")));
        assertThat(headers, hasHeaderValue(ACCESS_CONTROL_ALLOW_HEADERS, is("X-foo")));
        assertThat(headers, hasHeaderValue(ACCESS_CONTROL_MAX_AGE, is("3600")));
    }

    @Test
    void test1PreFlightAllowedHeaders2() {
        var res = client.options("/cors1")
                .header(ORIGIN, "*")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "*")
                .header(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo", "X-bar")
                .request(Void.class);

        assertThat(res.status(), is(Status.OK_200));

        var headers = res.headers();
        assertThat(headers, hasHeaderValue(ACCESS_CONTROL_ALLOW_ORIGIN, is("*")));
        assertThat(headers, hasHeaderValue(ACCESS_CONTROL_ALLOW_METHODS, is("*")));
        assertThat(headers, hasHeader(ACCESS_CONTROL_ALLOW_HEADERS, contains("X-foo", "X-bar")));
        assertThat(headers, hasHeaderValue(ACCESS_CONTROL_MAX_AGE, is("3600")));
    }

    @Test
    void test4Get() {
        /*
        GET with origin must be forbidden, as we only allow PUT method
         */
        var res = client.get("/cors4")
                .header(ORIGIN, "http://foo.com")
                .header(HOST, "here.com")
                .request(String.class);

        assertThat("HTTP response", res.status(), is(Status.FORBIDDEN_403));
    }
}
