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

package io.helidon.webserver.tests.gh2631;

import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class Gh8818Test {
    private final Http1Client client;

    Gh8818Test(WebServer server) {
        this.client = Http1Client.builder()
                               .baseUri("http://localhost:" + server.port())
                               .build();
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder builder) {
        builder.routing(Gh8818::routing);
    }

    @Test
    void checkForFullPath() {
        String requestedPath = get(Gh8818.ENDPOINT_PATH);
        assertThat("Requested path", requestedPath, is(Gh8818.ENDPOINT_PATH));
    }

    private String get(String path) {
        return client.get()
                .path(path)
                .requestEntity(String.class);
    }
}