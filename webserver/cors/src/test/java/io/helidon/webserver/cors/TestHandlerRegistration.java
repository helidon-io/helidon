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
package io.helidon.webserver.cors;

import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.WebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static io.helidon.common.http.Http.Header.ORIGIN;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_METHODS;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_REQUEST_METHOD;
import static io.helidon.webserver.cors.CustomMatchers.present;
import static io.helidon.webserver.cors.TestUtil.path;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestHandlerRegistration {

    static final String CORS4_CONTEXT_ROOT = "/cors4";

    private static WebServer server;
    private WebClient client;


    @BeforeAll
    public static void startup() throws InterruptedException, ExecutionException, TimeoutException {
        server = TestUtil.startupServerWithApps();
    }

    @BeforeEach
    public void startupClient() {
        client = TestUtil.startupClient(server);
    }

    @AfterAll
    public static void shutdown() {
        TestUtil.shutdownServer(server);
    }

        @Test
    void test4PreFlightAllowedHeaders2() throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .options()
                .path(CORS4_CONTEXT_ROOT);

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
    }
}
