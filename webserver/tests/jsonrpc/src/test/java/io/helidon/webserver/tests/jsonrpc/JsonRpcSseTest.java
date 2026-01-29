/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
package io.helidon.webserver.tests.jsonrpc;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.sse.SseEvent;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.jsonrpc.JsonRpcClient;
import io.helidon.webclient.sse.SseSource;
import io.helidon.webserver.testing.junit5.ServerTest;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class JsonRpcSseTest extends JsonRpcBaseTest {

    static final String SSE_METHOD = """
            {"jsonrpc": "2.0",
                "method": "sse",
                "params": {},
                "id": 1}
            """;

    JsonRpcSseTest(Http1Client client, JsonRpcClient jsonRpcClient) {
        super(client, jsonRpcClient);
    }

    @Test
    void testSse() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<List<String>> future = new CompletableFuture<>();

        try (var res = client().post("/rpc/sse")
                .contentType(MediaTypes.APPLICATION_JSON)
                .accept(MediaTypes.TEXT_EVENT_STREAM)
                .submit(SSE_METHOD)) {
            assertThat(res.status().code(), is(200));

            res.source(SseSource.TYPE, new SseSource() {
                private final List<String> methods = new ArrayList<>();

                @Override
                public void onEvent(SseEvent value) {
                    String jsonRpc = value.data(String.class);
                    JsonObject json = Json.createReader(new StringReader(jsonRpc)).readObject();
                    methods.add(json.getString("method"));
                }

                @Override
                public void onClose() {
                    future.complete(methods);
                }
            });

            List<String> methods = future.get(5, TimeUnit.SECONDS);
            assertThat(methods.size(), is(2));
            assertThat(methods.getFirst(), is("start"));
            assertThat(methods.getLast(), is("stop"));
        }
    }

    @Test
    void testJsonRpcSse() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<List<String>> future = new CompletableFuture<>();

        try (var res = jsonRpcClient().rpcMethod("status")
                .rpcId(3)
                .accept(MediaTypes.TEXT_EVENT_STREAM)
                .path("/rpc/machine")
                .submit()) {

            res.source(SseSource.TYPE, new SseSource() {
                private final List<String> results = new ArrayList<>();

                @Override
                public void onEvent(SseEvent value) {
                    String jsonRpc = value.data(String.class);
                    JsonObject json = Json.createReader(new StringReader(jsonRpc)).readObject();
                    results.add(json.getString("result"));
                }

                @Override
                public void onClose() {
                    future.complete(results);
                }
            });

            List<String> results = future.get(500, TimeUnit.SECONDS);
            assertThat(results.size(), is(2));
            assertThat(results.getFirst(), is("RUNNING"));
            assertThat(results.getLast(), is("RUNNING"));
        }
    }
}
