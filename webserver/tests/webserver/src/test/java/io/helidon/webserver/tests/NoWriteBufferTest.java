/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test that a simple response can be sent using the {@link ServerResponse#outputStream()} using no write buffer
 * (i.e. the write buffer size was set to {@code 0}).
 */
@ServerTest
class NoWriteBufferTest {
    private static final String RESPONSE = "Hello World!";

    private final Http1Client client;

    NoWriteBufferTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder builder) {
        builder.writeBufferSize(0);
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/", (req, res) -> {
            try(OutputStream out = res.outputStream()) {
                out.write(RESPONSE.getBytes(StandardCharsets.UTF_8));
            }
        });
    }

    @Test
    void noWriteBufferTest() throws Exception {
        try (HttpClientResponse response = client.method(Method.GET).request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(String.class), is(RESPONSE));
        }
    }
}
