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
package io.helidon.docs.se.jsonrpc;

import java.time.Duration;
import java.util.Optional;

import io.helidon.jsonrpc.core.JsonRpcError;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.jsonrpc.JsonRpcHandlers;
import io.helidon.webserver.jsonrpc.JsonRpcRequest;
import io.helidon.webserver.jsonrpc.JsonRpcResponse;
import io.helidon.webserver.jsonrpc.JsonRpcRouting;
import io.helidon.webserver.jsonrpc.JsonRpcRules;
import io.helidon.webserver.jsonrpc.JsonRpcService;
import io.helidon.http.Status;
import io.helidon.jsonrpc.core.JsonRpcResult;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.jsonrpc.JsonRpcClient;
import io.helidon.webclient.jsonrpc.JsonRpcClientResponse;
import io.helidon.webclient.jsonrpc.JsonRpcClientBatchRequest;
import io.helidon.webclient.jsonrpc.JsonRpcClientBatchResponse;


@SuppressWarnings("ALL")
class ClientSnippets {

    void snippets() {
        // tag::snippet_1[]
        WebClient webClient = WebClient.builder()
                .baseUri("http://localhost:8080/rpc")
                .build();

        JsonRpcClient client = webClient.client(JsonRpcClient.PROTOCOL);
        // end::snippet_1[]

        // tag::snippet_2[]
        JsonRpcClientResponse res = client.rpcMethod("start")
                .rpcId(1)
                .param("when", "NOW")
                .param("duration", "PT0S")
                .path("/machine")
                .submit();
        // end::snippet_2[]

        // tag::snippet_3[]
        if (res.status() == Status.OK_200 && res.result().isPresent()) {
            StartStopResult result = res.result().get().as(StartStopResult.class);
            if (result.status().equals("RUNNING")) {
                // success start!
            }
        }
        // end::snippet_3[]

        // tag::snippet_4[]
        JsonRpcClientBatchRequest batch = client.batch("/machine");

        batch.rpcMethod("start")
                .rpcId(1)
                .param("when", "NOW")
                .param("duration", "PT0S")
                .addToBatch()
                .rpcMethod("stop")
                .rpcId(2)
                .param("when", "NOW")
                .addToBatch();

        JsonRpcClientBatchResponse batchRes = batch.submit();
        // end::snippet_4[]

        // tag::snippet_5[]
        if (batchRes.status() == Status.OK_200 && batchRes.size() == 2) {
            Optional<JsonRpcResult> result0 = batchRes.get(0).result();
            if (result0.get().as(StartStopResult.class).status().equals("RUNNING")) {
                // successful start!
            }
            Optional<JsonRpcResult> result1 = batchRes.get(1).result();
            if (result0.get().as(StartStopResult.class).status().equals("STOPPED")) {
                // successful stop!
            }
        }
        // end::snippet_5[]
    }

    // tag::snippet_6[]
    public record StartStopParams(String when, Duration duration) {
    }

    public record StartStopResult(String status) {
    }
    // end::snippet_6[]
}
