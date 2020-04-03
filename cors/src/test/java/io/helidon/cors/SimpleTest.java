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

import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.cors.CORSTestServices.Service1;
import io.helidon.cors.CORSTestServices.Service2;
import io.helidon.cors.CORSTestServices.Service3;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import static io.helidon.cors.CrossOrigin.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.helidon.cors.CrossOrigin.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.helidon.cors.CrossOrigin.ACCESS_CONTROL_ALLOW_METHODS;
import static io.helidon.cors.CrossOrigin.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.helidon.cors.CrossOrigin.ACCESS_CONTROL_MAX_AGE;
import static io.helidon.cors.CrossOrigin.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.helidon.cors.CrossOrigin.ACCESS_CONTROL_REQUEST_METHOD;
import static io.helidon.cors.CrossOrigin.ORIGIN;
import static io.helidon.cors.CustomMatchers.isNotPresent;
import static io.helidon.cors.CustomMatchers.isPresent;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class SimpleTest {

    private static WebServer server;
    private static WebClient client;

    @BeforeAll
    public static void startup() throws InterruptedException, ExecutionException, TimeoutException {
        Routing.Builder routingBuilder = TestUtil.prepRouting()
                .register(CORSSupport.builder())
                .register("/greet", () -> new GreetService());
        CORSTestServices.SERVICES.forEach(s -> routingBuilder.register(s.path(), s));

        server = TestUtil.startServer(0, routingBuilder);
        client = WebClient.builder()
                .baseUri("http://localhost:" + server.port())
                .build();
    }

    @AfterAll
    public static void shutdown() {
        TestUtil.shutdownServer(server);
    }

    @Test
    public void testSimple() throws Exception {

        WebClientResponse response = client.get()
                .path("/greet")
                .accept(MediaType.TEXT_PLAIN)
                .request()
                .toCompletableFuture()
                .get();

        String msg = response.content().as(String.class).toCompletableFuture().get();
        Http.ResponseStatus result = response.status();

        assertThat(result.code(), is(Http.Status.OK_200.code()));
    }

    @Test
    void test1PreFlightAllowedOrigin() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .method(Http.Method.OPTIONS.name())
                .path(Service1.PATH);

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), isPresent(is("http://foo.bar")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_METHODS), isPresent(is("PUT")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), isNotPresent());
        assertThat(res.headers().first(ACCESS_CONTROL_MAX_AGE), isPresent(is("3600")));
    }

    @Test
    void test1PreFlightAllowedHeaders1() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .method(Http.Method.OPTIONS.name())
                .path(Service1.PATH);

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        headers.add(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), isPresent(is("http://foo.bar")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_METHODS), isPresent(is("PUT")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), isPresent(is("X-foo")));
        assertThat(res.headers().first(ACCESS_CONTROL_MAX_AGE), isPresent(is("3600")));
    }

    @Test
    void test1PreFlightAllowedHeaders2() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .method(Http.Method.OPTIONS.name())
                .path(Service1.PATH);

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        headers.add(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), isPresent(is("http://foo.bar")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_METHODS), isPresent(is("PUT")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), isPresent(containsString("X-foo")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), isPresent(containsString("X-bar")));
        assertThat(res.headers().first(ACCESS_CONTROL_MAX_AGE), isPresent(is("3600")));
    }

    @Test
    void test2PreFlightForbiddenOrigin() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .method(Http.Method.OPTIONS.name())
                .path(Service2.PATH);

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
                .path(Service2.PATH);

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();


        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), isPresent(is("http://foo.bar")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_CREDENTIALS), isPresent(is("true")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_METHODS), isPresent(is("PUT")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), isNotPresent());
        assertThat(res.headers().first(ACCESS_CONTROL_MAX_AGE), isNotPresent());
    }

    @Test
    void test2PreFlightForbiddenMethod() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .method(Http.Method.OPTIONS.name())
                .path(Service2.PATH);

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
                .path(Service2.PATH);

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
        WebClientRequestBuilder reqBuilder = client
                .method(Http.Method.OPTIONS.name())
                .path(Service2.PATH);

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        headers.add(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), isPresent(is("http://foo.bar")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_CREDENTIALS), isPresent(is("true")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_METHODS), isPresent(is("PUT")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), isPresent(containsString("X-foo")));
        assertThat(res.headers().first(ACCESS_CONTROL_MAX_AGE), isNotPresent());
    }

    @Test
    void test2PreFlightAllowedHeaders2() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .method(Http.Method.OPTIONS.name())
                .path(Service2.PATH);

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
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), isPresent(is("http://foo.bar")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_CREDENTIALS), isPresent(is("true")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_METHODS), isPresent(is("PUT")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), isPresent(containsString("X-foo")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), isPresent(containsString("X-bar")));
        assertThat(res.headers().first(ACCESS_CONTROL_MAX_AGE), isNotPresent());
    }

    @Test
    void test1ActualAllowedOrigin() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .put()
                .path(Service1.PATH)
                .contentType(MediaType.TEXT_PLAIN);

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        WebClientResponse res = reqBuilder
                .submit("")
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), isPresent(is("*")));
    }

    @Test
    void test2ActualAllowedOrigin() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .put()
                .path(Service2.PATH)
                .contentType(MediaType.TEXT_PLAIN);

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");

        WebClientResponse res = reqBuilder
                .submit("")
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), isPresent(is("http://foo.bar")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_CREDENTIALS), isPresent(is("true")));
    }

    @Test
    void test3PreFlightAllowedOrigin() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .method(Http.Method.OPTIONS.name())
                .path(Service3.PATH);

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        WebClientResponse res = reqBuilder
                .submit("")
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), isPresent(is("http://foo.bar")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_METHODS), isPresent(is("PUT")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), isNotPresent());
        assertThat(res.headers().first(ACCESS_CONTROL_MAX_AGE), isPresent(is("3600")));
    }

    @Test
    void test3ActualAllowedOrigin() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .put()
                .path(Service3.PATH)
                .contentType(MediaType.TEXT_PLAIN);

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, "http://foo.bar");
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        WebClientResponse res = reqBuilder
                .submit("")
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), isPresent(is("http://foo.bar")));
    }
}
