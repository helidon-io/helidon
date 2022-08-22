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
package io.helidon.reactive.webserver.cors;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.reactive.webclient.WebClient;
import io.helidon.reactive.webclient.WebClientRequestBuilder;
import io.helidon.reactive.webclient.WebClientRequestHeaders;
import io.helidon.reactive.webclient.WebClientResponse;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_ALLOW_METHODS;
import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_MAX_AGE;
import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_REQUEST_METHOD;
import static io.helidon.common.http.Http.Header.ORIGIN;
import static io.helidon.reactive.webserver.cors.CorsTestServices.SERVICE_1;
import static io.helidon.reactive.webserver.cors.CorsTestServices.SERVICE_2;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNot.not;

public abstract class AbstractCorsTest {

    abstract String contextRoot();

    abstract WebClient client();

    abstract String fooOrigin();

    abstract String fooHeader();

    @Test
    public void testSimple() throws Exception {

        WebClientResponse response = client().get()
                .path(contextRoot())
                .accept(HttpMediaType.TEXT_PLAIN)
                .request()
                .toCompletableFuture()
                .get();

        Http.Status result = response.status();

        assertThat(result.code(), is(Http.Status.OK_200.code()));
    }
    @Test
    void test1PreFlightAllowedHeaders1() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client()
                .options()
                .path(TestUtil.path(SERVICE_1));

        WebClientRequestHeaders headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        headers.add(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), CustomMatchers.present(is("http://foo.bar")));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_METHODS), CustomMatchers.present(is("PUT")));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), CustomMatchers.present(is("X-foo")));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_MAX_AGE), CustomMatchers.present(is("3600")));
    }

    @Test
    void test1PreFlightAllowedHeaders2() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client()
                .options()
                .path(TestUtil.path(SERVICE_1));

        WebClientRequestHeaders headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        headers.add(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), CustomMatchers.present(is("http://foo.bar")));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_METHODS), CustomMatchers.present(is("PUT")));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), CustomMatchers.present(containsString("X-foo")));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), CustomMatchers.present(containsString("X-bar")));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_MAX_AGE), CustomMatchers.present(is("3600")));
    }

    @Test
    void test2PreFlightForbiddenOrigin() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client()
                .options()
                .path(TestUtil.path(SERVICE_2));

        WebClientRequestHeaders headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://not.allowed");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();

        Http.Status status = res.status();
        assertThat(status.code(), is(Http.Status.FORBIDDEN_403.code()));
        assertThat(status.reasonPhrase(), is("CORS origin is not in allowed list"));
    }

    @Test
    void test2PreFlightAllowedOrigin() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client()
                .options()
                .path(TestUtil.path(SERVICE_2));

        WebClientRequestHeaders headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();


        assertThat(res.status(), is(Http.Status.OK_200));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), CustomMatchers.present(is("http://foo.bar")));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_CREDENTIALS), CustomMatchers.present(is("true")));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_METHODS), CustomMatchers.present(is("PUT")));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), CustomMatchers.notPresent());
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_MAX_AGE), CustomMatchers.notPresent());
    }

    @Test
    void test2PreFlightForbiddenMethod() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client()
                .options()
                .path(TestUtil.path(SERVICE_2));

        WebClientRequestHeaders headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "POST");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();

        Http.Status status = res.status();
        assertThat(status.code(), is(Http.Status.FORBIDDEN_403.code()));
        assertThat(status.reasonPhrase(), is("CORS origin is denied"));
    }

    @Test
    void test2PreFlightForbiddenHeader() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client()
                .options()
                .path(TestUtil.path(SERVICE_2));

        WebClientRequestHeaders headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        headers.add(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar, X-oops");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();

        Http.Status status = res.status();
        assertThat(status.code(), is(Http.Status.FORBIDDEN_403.code()));
        assertThat(status.reasonPhrase(), is("CORS headers not in allowed list"));
    }

    @Test
    void test2PreFlightAllowedHeaders1() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client()
                .options()
                .path(TestUtil.path(contextRoot(), SERVICE_2));

        WebClientRequestHeaders headers = reqBuilder.headers();
        headers.add(ORIGIN, fooOrigin());
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        headers.add(ACCESS_CONTROL_REQUEST_HEADERS, fooHeader());

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        MatcherAssert.assertThat(res.headers()
                .first(ACCESS_CONTROL_ALLOW_ORIGIN), CustomMatchers.present(is(fooOrigin())));
        MatcherAssert.assertThat(res.headers()
                .first(ACCESS_CONTROL_ALLOW_CREDENTIALS), CustomMatchers.present(is("true")));
        MatcherAssert.assertThat(res.headers()
                .first(ACCESS_CONTROL_ALLOW_METHODS), CustomMatchers.present(is("PUT")));
        MatcherAssert.assertThat(res.headers()
                .first(ACCESS_CONTROL_ALLOW_HEADERS), CustomMatchers.present(containsString(fooHeader())));
        MatcherAssert.assertThat(res.headers()
                .first(ACCESS_CONTROL_MAX_AGE), CustomMatchers.notPresent());
    }

    @Test
    void test2PreFlightAllowedHeaders2() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client()
                .options()
                .path(TestUtil.path(SERVICE_2));

        WebClientRequestHeaders headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        headers.add(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), CustomMatchers.present(is("http://foo.bar")));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_CREDENTIALS), CustomMatchers.present(is("true")));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_METHODS), CustomMatchers.present(is("PUT")));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), CustomMatchers.present(containsString("X-foo")));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), CustomMatchers.present(containsString("X-bar")));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_MAX_AGE), CustomMatchers.notPresent());
    }

    @Test
    void test2PreFlightAllowedHeaders3() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client()
                .options()
                .path(TestUtil.path(SERVICE_2));

        WebClientRequestHeaders headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        headers.add(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar");
        headers.add(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), CustomMatchers.present(is("http://foo.bar")));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_CREDENTIALS), CustomMatchers.present(is("true")));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_METHODS), CustomMatchers.present(is("PUT")));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), CustomMatchers.present(containsString("X-foo")));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), CustomMatchers.present(containsString("X-bar")));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_MAX_AGE), CustomMatchers.notPresent());
    }

    @Test
    void test1ActualAllowedOrigin() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client()
                .put()
                .path(TestUtil.path(SERVICE_1))
                .contentType(MediaTypes.TEXT_PLAIN);

        WebClientRequestHeaders headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        WebClientResponse res = reqBuilder
                .submit("")
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), CustomMatchers.present(is("*")));
    }

    @Test
    void test2ActualAllowedOrigin() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client()
                .put()
                .path(TestUtil.path(SERVICE_2))
                .contentType(MediaTypes.TEXT_PLAIN);

        WebClientRequestHeaders headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");

        WebClientResponse res = reqBuilder
                .submit("")
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), CustomMatchers.present(is("http://foo.bar")));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_CREDENTIALS), CustomMatchers.present(is("true")));
    }


    @Test
    void test2ErrorResponse() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client()
                .get()
                .path(TestUtil.path(SERVICE_2) + "/notfound")
                .contentType(MediaTypes.TEXT_PLAIN);

        WebClientRequestHeaders headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");

        WebClientResponse res = reqBuilder
                .submit()
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(not(Http.Status.OK_200)));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), is(Optional.empty()));
    }

    WebClientResponse runTest1PreFlightAllowedOrigin() throws ExecutionException,
            InterruptedException {
        WebClientRequestBuilder reqBuilder = client()
                .options()
                .path(TestUtil.path(contextRoot(), SERVICE_1));

        WebClientRequestHeaders headers = reqBuilder.headers();
        headers.add(ORIGIN, fooOrigin());
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();

        return res;
    }
}
