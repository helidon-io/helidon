/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.common.http.Http;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestTwoCORSConfigs {

    private static WebServer server;
    private static WebClient client;

    @BeforeAll
    public static void startup() throws InterruptedException, ExecutionException, TimeoutException {
        server = TestUtil.startupServerWithApps();
        client = TestUtil.startupClient(server);
    }

    @Test
    void test1PreFlightAllowedOriginOtherGreeting() throws ExecutionException, InterruptedException {
        WebClientResponse res = TestUtil.runTest1PreFlightAllowedOrigin(client, TestUtil.OTHER_GREETING_PATH,
                "http://otherfoo.bar");

        assertThat(res.status(), is(Http.Status.FORBIDDEN_403));
    }

    @Test
    void test3PreFlightAllowedOrigin() throws ExecutionException, InterruptedException {
        TestUtil.test3PreFlightAllowedOrigin(client);
    }

    @AfterAll
    public static void shutdown() {
        TestUtil.shutdownServer(server);
    }


}
