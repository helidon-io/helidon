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

import static io.helidon.common.http.Http.Header.ORIGIN;
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
import static io.helidon.cors.CustomMatchers.notPresent;
import static io.helidon.cors.CustomMatchers.present;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CORSTest extends AbstractCORSTest {

    private static final String CONTEXT_ROOT = "/greet";
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


    @Override
    String fooOrigin() {
        return "http://foo.bar";
    }

    @Override
    WebClient client() {
        return client;
    }

    @Override
    String contextRoot() {
        return CONTEXT_ROOT;
    }

    @Override
    String fooHeader() {
        return "X-foo";
    }

    @Test
    void test1PreFlightAllowedOrigin() throws ExecutionException, InterruptedException {
        String origin = fooOrigin();
        WebClientResponse res = runTest1PreFlightAllowedOrigin();

        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), present(is(origin)));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_METHODS), present(is("PUT")));
        assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), notPresent());
        assertThat(res.headers().first(ACCESS_CONTROL_MAX_AGE), present(is("3600")));
    }
}
