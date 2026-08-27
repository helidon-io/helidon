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

package io.helidon.webserver.security;

import java.util.List;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.security.Security;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class SpecialRequestTargetValidationDisabledTest {
    private final SocketHttpClient client;

    SpecialRequestTargetValidationDisabledTest(SocketHttpClient client) {
        this.client = client;
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        var http1 = Http1ConnectionSelector.builder()
                .config(Http1Config.builder()
                                .validatePath(false)
                                .build())
                .build();

        server.protocolsDiscoverServices(false)
                .addConnectionSelector(http1)
                .featuresDiscoverServices(false)
                .addFeature(SecurityFeature.builder()
                                    .security(Security.builder().build())
                                    .build())
                .routing(routing -> routing.any((req, res) -> res.status(Status.NOT_IMPLEMENTED_501)
                        .send(req.requestedUri().toUri().toString())));
    }

    @Test
    void malformedConnectAuthorityDoesNotFailRequestedUriConstruction() {
        String response = client.sendAndReceive(Method.CONNECT, "example.com:2147483648", null, List.of());

        assertThat(SocketHttpClient.statusFromResponse(response), is(Status.NOT_IMPLEMENTED_501));
    }
}
