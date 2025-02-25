/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests;

import java.util.Collections;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webserver.ErrorHandling;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test that no unwanted request data is leaked back (reflected) in response to a
 * bad request. There are no routes defined for this test.
 */
@ServerTest
class ExceptionMessageTest {

    private final SocketHttpClient socketClient;

    ExceptionMessageTest(SocketHttpClient socketClient) {
        this.socketClient = socketClient;
    }

    @SetUpServer
    static void setupServer(WebServerConfig.Builder builder) {
        builder.errorHandling(ErrorHandling.builder()
                                      .includeEntity(true)          // enable error message entities
                                      .build());
    }

    @Test
    void testNoUrlReflect() {
        String path = "/anyjavascript%3a/*%3c/script%3e%3cimg/onerror%3d'\\''"
                + "-/%22/-/%20onmouseover%d1/-/[%60*/[]/[(new(Image)).src%3d(/%3b/%2b/255t6qeelp23xlr08hn1uv"
                + "vnkeqae02stgk87yvnX%3b.oastifycom/).replace(/.%3b/g%2c[])]//'\\''src%3d%3e";
        String response = socketClient.sendAndReceive(Method.GET,
                                                      path,
                                                      "");
        Status status = SocketHttpClient.statusFromResponse(response);
        String entity = SocketHttpClient.entityFromResponse(response, false);
        assertThat(status, is(Status.BAD_REQUEST_400));
        assertThat(entity, containsString("see server log"));
        assertThat(entity, not(containsString("javascript")));
    }

    @Test
    void testNoHeaderReflect() {
        String response = socketClient.sendAndReceive(Method.GET,
                                                      "/",
                                                      "",
                                                      Collections.singletonList("<Content-Type>: <javascript/>"));
        Status status = SocketHttpClient.statusFromResponse(response);
        String entity = SocketHttpClient.entityFromResponse(response, false);
        assertThat(status, is(Status.BAD_REQUEST_400));
        assertThat(entity, not(containsString("javascript")));
    }
}
