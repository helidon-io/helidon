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

package io.helidon.webserver.tests;

import java.util.List;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class FragmentValidationDisabledTest {
    private static final String FRAGMENT = "fragment{";

    private final SocketHttpClient client;

    FragmentValidationDisabledTest(SocketHttpClient client) {
        this.client = client;
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        var http1 = Http1ConnectionSelector.builder()
                .config(Http1Config.builder()
                                .validatePrologue(false)
                                .validatePath(true)
                                .build())
                .build();

        server.protocolsDiscoverServices(false)
                .addConnectionSelector(http1)
                .routing(routing -> routing.any((req, res) -> {
                    var prologue = req.prologue();
                    res.send(prologue.uriPath().rawPath() + '|' + prologue.fragment().rawValue());
                }));
    }

    @Test
    void fragmentWithIllegalCharacterReachesRouting() {
        assertFragmentPreserved(Method.GET, "/boards#" + FRAGMENT, "/boards");
    }

    @Test
    void optionsAsteriskPreservesFragment() {
        assertFragmentPreserved(Method.OPTIONS, "*#" + FRAGMENT, "*");
    }

    @Test
    void connectAuthorityPreservesFragment() {
        assertFragmentPreserved(Method.CONNECT, "example.com:443#" + FRAGMENT, "example.com:443");
    }

    @Test
    void relativeTargetIsRejected() {
        String response = client.sendAndReceive(Method.GET, "boards/", null, List.of());

        assertThat(SocketHttpClient.statusFromResponse(response), is(Status.BAD_REQUEST_400));
    }

    private void assertFragmentPreserved(Method method, String requestTarget, String expectedPath) {
        String response = client.sendAndReceive(method, requestTarget, null, List.of());

        assertThat(SocketHttpClient.statusFromResponse(response), is(Status.OK_200));
        assertThat(SocketHttpClient.entityFromResponse(response, true), is(expectedPath + '|' + FRAGMENT));
    }
}
