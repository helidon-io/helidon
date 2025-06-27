/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.webclient.jsonrpc;

import io.helidon.webclient.api.WebClient;

import jakarta.json.Json;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

class JsonRpcClientTest {

    @Test
    void testCreate() {
        JsonRpcClient client = JsonRpcClient.create();
        assertThat(client, notNullValue());
    }

    @Test
    void testCreateFromWebClient() {
        WebClient webClient = WebClient.create();
        JsonRpcClient client = webClient.client(JsonRpcClient.PROTOCOL);
        assertThat(client, notNullValue());
    }

    @Test
    void testRequestCreation() {
        JsonRpcClient client = JsonRpcClient.builder()
                .baseUri("https://example.com")
                .build();
        assertThat(client, notNullValue());

        JsonRpcClientRequest req = client.rpcMethod("start")
                .path("/machine")
                .rpcId(1)
                .param("foo", Json.createValue("bar"));
        assertThat(req, notNullValue());
    }
}
