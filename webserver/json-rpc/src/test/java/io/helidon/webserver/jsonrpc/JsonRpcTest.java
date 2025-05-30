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

import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.Router;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static io.helidon.common.media.type.MediaTypes.APPLICATION_JSON;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class JsonRpcTest {

    static final String JSON_RPC_START = """
            {"jsonrpc": "2.0", "method": "start", "params": { "when" : "NOW" }, "id": 1}
            """;

    static final String JSON_RPC_STOP = """
            {"jsonrpc": "2.0", "method": "stop", "params": { "when" : "NOW" }, "id": 2}
            """;

    private final Http1Client client;

    JsonRpcTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(Router.RouterBuilder<?> router) {
        router.addRouting(JsonRpcRouting.builder()
                                  .service(new JsonRpcService1())
                                  .build()
                                  .toHttpRouting());
    }

    @Test
    void testStart() {
        try (var res = client.post("/jsonrpc")
                .contentType(APPLICATION_JSON)
                .submit(JSON_RPC_START)) {
            assertThat(res.status().code(), is(200));
            JsonObject json = res.as(JsonObject.class);
            assertThat(json.getString("result"), is("RUNNING"));
        }
    }

    @Test
    void testStop() {
        try (var res = client.post("/jsonrpc")
                .contentType(APPLICATION_JSON)
                .submit(JSON_RPC_STOP)) {
            assertThat(res.status().code(), is(200));
            JsonObject json = res.as(JsonObject.class);
            assertThat(json.getString("result"), is("STOPPED"));
        }
    }

    static class JsonRpcService1 implements JsonRpcService {

        @Override
        public void routing(JsonRpcRules rules) {
            rules.register("/jsonrpc",
                           JsonRpcHandlers.builder()
                                   .method("start", this::start)
                                   .method("stop", this::stop)
                                   .build());
        }

        void start(JsonRpcRequest req, JsonRpcResponse res) {
            String when = req.params().getString("when");
            if (when.equals("NOW")) {
                res.id(req.id().orElseThrow());
                res.result(Json.createValue("RUNNING"));
                res.status(200).send();
            } else {
                res.error(JsonRpcError.builder().code(-32600).build());
                res.status(400).send();
            }
        }

        void stop(JsonRpcRequest req, JsonRpcResponse res) {
            String when = req.params().getString("when");
            if (when.equals("NOW")) {
                res.id(req.id().orElseThrow());
                res.result(Json.createValue("STOPPED"));
                res.status(200).send();
            } else {
                res.error(JsonRpcError.builder().code(-32600).build());
                res.status(400).send();
            }
        }
    }
}
