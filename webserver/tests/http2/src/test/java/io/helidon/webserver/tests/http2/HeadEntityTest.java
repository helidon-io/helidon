/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.http2;

import java.nio.charset.StandardCharsets;

import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class HeadEntityTest {
    private final Http2Client client;

    HeadEntityTest(Http2Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.head("/send", (_, res) -> res.send("entity".getBytes(StandardCharsets.UTF_8)))
                .head("/stream", (_, res) -> res.outputStream().write('x'))
                .head("/empty", (_, res) -> {
                    res.headers().contentLength(6);
                    res.send();
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"/send", "/stream"})
    void entityWriteIsInternalServerError(String path) {
        try (var response = client.method(Method.HEAD).uri(path).request()) {
            assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
            assertThat(response.headers().contentLength().orElseThrow(), is(21L));
        }
    }

    @Test
    void emptyHeadResponsePreservesMetadata() {
        try (var response = client.method(Method.HEAD).uri("/empty").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers().contentLength().orElseThrow(), is(6L));
        }
    }
}
