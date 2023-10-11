/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.net.URI;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.webserver.testing.junit5.ServerTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;

@ServerTest
class PathTest {

    private final SocketHttpClient client;
    private final URI uri;

    public PathTest(SocketHttpClient client, URI uri) {
        this.client = client;
        this.uri = uri;
    }

    /**
     * RFC 7230 5.3.1.  origin-form
     *
     * If the target URI's path component is
     * empty, the client MUST send "/" as the path within the origin-form of
     * request-target.
     */
    @Test
    void emptyPath() throws Exception {
        String received = client
                .manualRequest(
                        """
                                GET   HTTP/1.1
                                Host: localhost:%d
                                Accept: */*
                                Connection: keep-alive

                                """, uri.getPort())
                .receive();
        assertThat(received, startsWith("HTTP/1.1 400 Bad Request"));
    }

    @ParameterizedTest(name = "{index} GET {0} HTTP/1.1")
    @ValueSource(strings = {"\r", "\t", "\t\t\t", "<", "     "})
    void illegalPath(String param) throws IOException {
        String received = client
                .manualRequest(
                        """
                                GET %s HTTP/1.1
                                Host: localhost:%d
                                Accept: */*
                                Connection: keep-alive

                                """, param, uri.getPort())
                .receive();
        assertThat(received, startsWith("HTTP/1.1 400 Bad Request"));
    }
}
