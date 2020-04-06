/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.cors;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.WebServer;

import static io.helidon.cors.CORSTestServices.SERVICE_1;
import static io.helidon.cors.CORSTestServices.SERVICE_2;
import static io.helidon.cors.CORSTestServices.SERVICE_3;
import static io.helidon.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.helidon.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.helidon.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_METHODS;
import static io.helidon.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.helidon.cors.CrossOriginConfig.ACCESS_CONTROL_MAX_AGE;
import static io.helidon.cors.CrossOriginConfig.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.helidon.cors.CrossOriginConfig.ACCESS_CONTROL_REQUEST_METHOD;
import static io.helidon.cors.CrossOriginConfig.ORIGIN;
import static io.helidon.cors.CustomMatchers.notPresent;
import static io.helidon.cors.CustomMatchers.present;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class CORSTest {

    private static final String CONTEXT_ROOT = "/greet";
    private static WebServer server;
    private static WebClient client;

    @BeforeAll
    public static void startup() throws InterruptedException, ExecutionException, TimeoutException {
        server = TestUtil.startupServerWithApps();
        client = TestUtil.startupClient(server);
    }

    @AfterAll
    public static void shutdown() {
        TestUtil.shutdownServer(server);
    }

    @Test
    public void testSimple() throws Exception {

        WebClientResponse response = client.get()
                .path(CONTEXT_ROOT)
                .accept(MediaType.TEXT_PLAIN)
                .request()
                .toCompletableFuture()
                .get();

        Http.ResponseStatus result = response.status();

        assertThat(result.code(), is(Http.Status.OK_200.code()));
    }

    @Test
    void test1PreFlightAllowedOrigin() throws ExecutionException, InterruptedException {
        String origin = "http://foo.bar";
        WebClientResponse res = TestUtil.runTest1PreFlightAllowedOrigin(client, CONTEXT_ROOT, origin);

        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), present(is(origin)));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_METHODS), present(is("PUT")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), notPresent());
        assertThat(res.headers().first(ACCESS_CONTROL_MAX_AGE), present(is("3600")));
    }

    @Test
    void test1PreFlightAllowedHeaders1() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .method(Http.Method.OPTIONS.name())
                .path(TestUtil.path(SERVICE_1));

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        headers.add(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), present(is("http://foo.bar")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_METHODS), present(is("PUT")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), present(is("X-foo")));
        assertThat(res.headers().first(ACCESS_CONTROL_MAX_AGE), present(is("3600")));
    }

    @Test
    void test1PreFlightAllowedHeaders2() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .method(Http.Method.OPTIONS.name())
                .path(TestUtil.path(SERVICE_1));

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        headers.add(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), present(is("http://foo.bar")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_METHODS), present(is("PUT")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), present(containsString("X-foo")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), present(containsString("X-bar")));
        assertThat(res.headers().first(ACCESS_CONTROL_MAX_AGE), present(is("3600")));
    }

    @Test
    void test2PreFlightForbiddenOrigin() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .method(Http.Method.OPTIONS.name())
                .path(TestUtil.path(SERVICE_2));

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://not.allowed");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.FORBIDDEN_403));
    }

    @Test
    void test2PreFlightAllowedOrigin() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .method(Http.Method.OPTIONS.name())
                .path(TestUtil.path(SERVICE_2));

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();


        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), present(is("http://foo.bar")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_CREDENTIALS), present(is("true")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_METHODS), present(is("PUT")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), notPresent());
        assertThat(res.headers().first(ACCESS_CONTROL_MAX_AGE), notPresent());
    }

    @Test
    void test2PreFlightForbiddenMethod() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .method(Http.Method.OPTIONS.name())
                .path(TestUtil.path(SERVICE_2));

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "POST");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.FORBIDDEN_403));
    }

    @Test
    void test2PreFlightForbiddenHeader() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .method(Http.Method.OPTIONS.name())
                .path(TestUtil.path(SERVICE_2));

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        headers.add(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar, X-oops");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.FORBIDDEN_403));
    }

    @Test
    void test2PreFlightAllowedHeaders1() throws ExecutionException, InterruptedException {
        TestUtil.test2PreFlightAllowedHeaders1(client, CONTEXT_ROOT,"http://foo.bar", "X-foo");
    }

    @Test
    void test2PreFlightAllowedHeaders2() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .method(Http.Method.OPTIONS.name())
                .path(TestUtil.path(SERVICE_2));

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        headers.add(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), present(is("http://foo.bar")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_CREDENTIALS), present(is("true")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_METHODS), present(is("PUT")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), present(containsString("X-foo")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), present(containsString("X-bar")));
        assertThat(res.headers().first(ACCESS_CONTROL_MAX_AGE), notPresent());
    }

    @Test
    void test2PreFlightAllowedHeaders3() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .method(Http.Method.OPTIONS.name())
                .path(TestUtil.path(SERVICE_2));

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        headers.add(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar");
        headers.add(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), present(is("http://foo.bar")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_CREDENTIALS), present(is("true")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_METHODS), present(is("PUT")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), present(containsString("X-foo")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), present(containsString("X-bar")));
        assertThat(res.headers().first(ACCESS_CONTROL_MAX_AGE), notPresent());
    }

    @Test
    void test1ActualAllowedOrigin() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .put()
                .path(TestUtil.path(SERVICE_1))
                .contentType(MediaType.TEXT_PLAIN);

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        WebClientResponse res = reqBuilder
                .submit("")
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), present(is("*")));
    }

    @Test
    void test2ActualAllowedOrigin() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .put()
                .path(TestUtil.path(SERVICE_2))
                .contentType(MediaType.TEXT_PLAIN);

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");

        WebClientResponse res = reqBuilder
                .submit("")
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), present(is("http://foo.bar")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_CREDENTIALS), present(is("true")));
    }

    @Test
    void test3PreFlightAllowedOrigin() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .method(Http.Method.OPTIONS.name())
                .path(TestUtil.path(SERVICE_3));

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        WebClientResponse res = reqBuilder
                .submit("")
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), present(is("http://foo.bar")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_METHODS), present(is("PUT")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), notPresent());
        assertThat(res.headers().first(ACCESS_CONTROL_MAX_AGE), present(is("3600")));
    }

    @Test
    void test3ActualAllowedOrigin() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .put()
                .path(TestUtil.path(SERVICE_3))
                .contentType(MediaType.TEXT_PLAIN);

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        WebClientResponse res = reqBuilder
                .submit("")
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), present(is("http://foo.bar")));
    }
}
