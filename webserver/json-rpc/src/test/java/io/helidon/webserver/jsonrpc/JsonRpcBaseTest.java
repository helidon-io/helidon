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
package io.helidon.webserver.jsonrpc;

import java.time.Duration;

import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.Router;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonStructure;

class JsonRpcBaseTest {

    static final String CALCULATOR_ADD_ARRAY = """
            {"jsonrpc": "2.0",
                "method": "add",
                "params": [20, 25]},
                "id": 1}
            """;

    static final String CALCULATOR_ADD_OBJECT = """
            {"jsonrpc": "2.0",
                "method": "add",
                "params": { "left" : 20, "right": 25 },
                "id": 1}
            """;

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

    JsonRpcBaseTest(Http1Client client) {
        this.client = client;
    }

    Http1Client client() {
        return client;
    }

    @SetUpRoute
    static void routing(Router.RouterBuilder<?> router) {
        JsonRpcRouting routing = JsonRpcRouting.builder()
                // register a single method
                .register("/calculator", "add", JsonRpcBaseTest::addNumbers)
                // register a service under "/machine"
                .service(new JsonRpcService1())
                .build();
        router.addRouting(routing.toHttpRouting());
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
            throw new IllegalArgumentException("Should fail JSON-RPC validation");
        }

        // send response
        res.result(Json.createValue(left + right)).send();
    }

    // -- Machine -------------------------------------------------------------

    public record StartStopParams(String when, Duration duration) {
    }

    public record StartStopResult(String status) {
    }

    public record ErrorData(String reason) {
    }

    static class JsonRpcService1 implements JsonRpcService {

        @Override
        public void routing(JsonRpcRules rules) {
            rules.register("/machine",
                           JsonRpcHandlers.builder()
                                   .method("start", this::start)
                                   .method("stop", this::stop)
                                   .build());
        }

        void start(JsonRpcRequest req, JsonRpcResponse res) throws Exception {
            StartStopParams params = req.params().as(StartStopParams.class);
            if (params.when().equals("NOW")) {
                res.jsonId(req.jsonId().orElseThrow());
                res.result(new StartStopResult("RUNNING"));
                res.status(Status.OK_200).send();
            } else {
                res.error(JsonRpcError.builder()
                                  .code(JsonRpcError.INVALID_PARAMS)
                                  .data(new ErrorData("Bad param"))
                                  .build());
                res.status(Status.OK_200).send();
            }
        }

        void stop(JsonRpcRequest req, JsonRpcResponse res) throws Exception {
            StartStopParams params = req.params().as(StartStopParams.class);
            if (params.when().equals("NOW")) {
                res.jsonId(req.jsonId().orElseThrow());
                res.result(new StartStopResult("STOPPED"));
                res.status(Status.OK_200).send();
            } else {
                res.error(JsonRpcError.builder()
                                  .code(JsonRpcError.INVALID_PARAMS)
                                  .data(new ErrorData("Bad param"))
                                  .build());
                res.status(Status.OK_200).send();
            }
        }
    }
}
