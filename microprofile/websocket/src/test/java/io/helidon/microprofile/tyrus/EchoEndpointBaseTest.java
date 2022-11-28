/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tyrus;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;

class EchoEndpointBaseTest {
    protected static final int WAIT_MILLIS = 5000;
    protected static final String HELLO_WORLD = "Hello World";

    @Inject
    private WebTarget webTarget;

    private final HttpClient client;

    EchoEndpointBaseTest() {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(WAIT_MILLIS))
                .build();
    }

    protected HttpClient httpClient() {
        return client;
    }

    protected String serverPort() {
        String uri = webTarget.getUri().toString();
        return uri.substring(uri.lastIndexOf(':') + 1);
    }

    protected void await(CompletableFuture<?> future) throws Exception {
        future.get(WAIT_MILLIS, TimeUnit.MILLISECONDS);
    }
}
