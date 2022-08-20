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

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import io.helidon.common.http.Http;
import io.helidon.reactive.webclient.WebClient;
import io.helidon.reactive.webclient.WebClientResponse;
import io.helidon.reactive.webserver.WebServer;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_ALLOW_METHODS;
import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_MAX_AGE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class CorsTest extends AbstractCorsTest {

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
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_ORIGIN), CustomMatchers.present(is(origin)));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_METHODS), CustomMatchers.present(is("PUT")));
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_ALLOW_HEADERS), CustomMatchers.notPresent());
        MatcherAssert.assertThat(res.headers().first(ACCESS_CONTROL_MAX_AGE), CustomMatchers.present(is("3600")));
        assertThat(res.headers().all(ACCESS_CONTROL_ALLOW_ORIGIN, List::of).size(), is(1));
    }
}
