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
import io.helidon.security.Security;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.context.ContextFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class SpecialRequestTargetTest {
    private final SocketHttpClient client;

    SpecialRequestTargetTest(SocketHttpClient client) {
        this.client = client;
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        server.featuresDiscoverServices(false)
                .addFeature(ContextFeature.create())
                .addFeature(SecurityFeature.builder()
                                    .security(Security.builder().build())
                                    .build())
                .routing(routing -> routing.any((req, res) -> {
                    var path = req.prologue().uriPath();
                    res.send(path.rawPath() + '|' + path.absolute().path());
                }));
    }

    @Test
    void optionsAsteriskReachesRouting() {
        String response = client.sendAndReceive(Method.OPTIONS, "*", null, List.of());

        assertThat(response, containsString("200 OK"));
        assertThat(response, containsString("*|/"));
    }

    @Test
    void connectAuthorityReachesRouting() {
        String response = client.sendAndReceive(Method.CONNECT, "example.com:443", null, List.of());

        assertThat(response, containsString("200 OK"));
        assertThat(response, containsString("example.com:443|/"));
    }
}
