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
package io.helidon.webserver.tests.jsonrpc;

import java.time.Duration;

import io.helidon.http.Status;
import io.helidon.jsonrpc.core.JsonRpcError;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.jsonrpc.JsonRpcClient;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.jsonrpc.JsonRpcHandlers;
import io.helidon.webserver.jsonrpc.JsonRpcRequest;
import io.helidon.webserver.jsonrpc.JsonRpcResponse;
import io.helidon.webserver.jsonrpc.JsonRpcRouting;
import io.helidon.webserver.jsonrpc.JsonRpcRules;
import io.helidon.webserver.jsonrpc.JsonRpcService;
import io.helidon.webserver.testing.junit5.SetUpServer;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonStructure;

class JsonRpcBaseTest {

    static final String MACHINE_START = """
            {"jsonrpc": "2.0",
                "method": "start",
                "params": { "when" : "NOW", "duration" : "PT0S" },
                "id": 1}
            """;

    static final String MACHINE_STOP = """
            {"jsonrpc": "2.0",
                "method": "stop",
                "params": { "when" : "NOW" },
                "id": 2}
            """;

    private final Http1Client client;
    private final JsonRpcClient jsonRpcClient;

    JsonRpcBaseTest(Http1Client client, JsonRpcClient jsonRpcClient) {
        this.client = client;
        this.jsonRpcClient = jsonRpcClient;
    }

    Http1Client client() {
        return client;
    }

    JsonRpcClient jsonRpcClient() {
        return jsonRpcClient;
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder builder) {
        JsonRpcRouting routing = JsonRpcRouting.builder()
                // register a single method under "/calculator"
                .register("/calculator", "add", JsonRpcBaseTest::addNumbers)
                // register a service under "/machine"
                .service(new JsonRpcService1())
                .register("/notifier", "ping", JsonRpcBaseTest::ping)
                .build();
        builder.addRouting(routing.toHttpRouting());
    }

    // -- Calculator ----------------------------------------------------------

    static void addNumbers(JsonRpcRequest req, JsonRpcResponse res) {
        int left, right;

        // collect params either as array or object
        JsonStructure params = req.params().asJsonStructure();
        if (params instanceof JsonArray array) {
            left = array.getInt(0);
            right = array.getInt(1);
        } else if (params instanceof JsonObject object) {
            left = object.getInt("left");
            right = object.getInt("right");
        } else {
            throw new IllegalArgumentException("Should have failed JSON-RPC validation");
        }

        // send response
        res.result(Json.createValue(left + right)).send();
    }

    // -- Machine -------------------------------------------------------------

    public record StartStopParams(String when, Duration duration) {
    }

    public record StartStopResult(String status) {
    }

    static class JsonRpcService1 implements JsonRpcService {

        @Override
        public void routing(JsonRpcRules rules) {
            rules.register("/machine",
                           JsonRpcHandlers.builder()
                                   .method("start", this::start)
                                   .method("stop", this::stop)
                                   .errorHandler(this::error)
                                   .build());
        }

        void start(JsonRpcRequest req, JsonRpcResponse res) {
            StartStopParams params = req.params().as(StartStopParams.class);
            if (params.when().equals("NOW")) {
                res.result(new StartStopResult("RUNNING"))
                        .status(Status.OK_200)
                        .send();
            } else {
                res.error(JsonRpcError.INVALID_PARAMS, "Bad param")
                        .status(Status.OK_200)
                        .send();
            }
        }

        void stop(JsonRpcRequest req, JsonRpcResponse res) {
            StartStopParams params = req.params().as(StartStopParams.class);
            if (params.when().equals("NOW")) {
                res.result(new StartStopResult("STOPPED"))
                        .status(Status.OK_200)
                        .send();
            } else {
                res.error(JsonRpcError.INVALID_PARAMS, "Bad param")
                        .status(Status.OK_200)
                        .send();
            }
        }

        boolean error(ServerRequest req, JsonObject object) {
            try {
                String method = object.getString("method");
                return "expected".equalsIgnoreCase(method);
            } catch (Exception e) {
                return false;       // not handled
            }
        }
    }

    // -- Notifier ------------------------------------------------------------

    static void ping(JsonRpcRequest req, JsonRpcResponse res) {
        // don't call send(), just HTTP status response returned
    }
}
