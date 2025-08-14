/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
import io.helidon.webclient.jsonrpc.JsonRpcClient;
import io.helidon.webclient.jsonrpc.JsonRpcClientBatchRequest;


@SuppressWarnings("ALL")
class ServerSnippets {

    void snippet_1() {
        // tag::snippet_1[]
        JsonRpcRouting jsonRpcRouting = JsonRpcRouting.builder()
                .service(new MachineService())
                .build();

        WebServer.builder()
                .port(8080)
                .host("localhost")
                .routing(r -> r.register("/rpc", jsonRpcRouting))
                .build()
                .start();
        // end::snippet_1[]
    }

    // tag::snippet_2[]
    class MachineService implements JsonRpcService {

        @Override
        public void routing(JsonRpcRules rules) {
            rules.register("/machine",
                           JsonRpcHandlers.builder()
                                   .method("start", this::start)
                                   .method("stop", this::stop)
                                   .build());
        }

        void start(JsonRpcRequest req, JsonRpcResponse res) {
            StartStopParams params = req.params().as(StartStopParams.class);
            if (params.when().equals("NOW")) {
                res.result(new StartStopResult("RUNNING"));
            } else {
                res.error(JsonRpcError.INVALID_PARAMS, "Bad param");
            }
            res.send();
        }

        void stop(JsonRpcRequest req, JsonRpcResponse res) {
            StartStopParams params = req.params().as(StartStopParams.class);
            if (params.when().equals("NOW")) {
                res.result(new StartStopResult("STOPPED"));
            } else {
                res.error(JsonRpcError.INVALID_PARAMS, "Bad param");
            }
            res.send();
        }
    }
    // end::snippet_2[]

    // tag::snippet_3[]
    public record StartStopParams(String when, Duration duration) {
    }

    public record StartStopResult(String status) {
    }
    // end::snippet_3[]
}
