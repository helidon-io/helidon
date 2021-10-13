/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import io.helidon.common.http.Http;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestTwoCorsConfigs extends AbstractCorsTest {

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

    @Test
    void test1PreFlightAllowedOriginOtherGreeting() throws ExecutionException, InterruptedException {
        WebClientResponse res = runTest1PreFlightAllowedOrigin();

        Http.ResponseStatus status = res.status();
        assertThat(status.code(), is(Http.Status.FORBIDDEN_403.code()));
        assertThat(status.reasonPhrase(), is("CORS origin is denied"));
    }

    @AfterAll
    public static void shutdown() {
        TestUtil.shutdownServer(server);
    }


    @Override
    String contextRoot() {
        return TestUtil.OTHER_GREETING_PATH;
    }

    @Override
    WebClient client() {
        return client;
    }

    @Override
    String fooOrigin() {
        return "http://otherfoo.bar";
    }

    @Override
    String fooHeader() {
        return "X-otherfoo";
    }
}
