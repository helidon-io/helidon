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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.helidon.webserver.Routing;
import io.helidon.webserver.http.HttpRouting;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonStructure;

public class JsonRpcRouting implements Routing {

    private final JsonRpcRulesImpl rules;

    private JsonRpcRouting(Builder builder) {
        this.rules = builder.rules;
    }

    /**
     * Returns a builder for this class.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Class<? extends Routing> routingType() {
        return JsonRpcRouting.class;
    }

    /**
     * Converts this instance to an {@link io.helidon.webserver.http.HttpRouting.Builder}
     * that can be registered in the Webserver.
     *
     * @return an instance of HttpRouting
     */
    public HttpRouting.Builder toHttpRouting() {
        Map<String, JsonRpcHandlers> rulesMap = rules.rulesMap();
        HttpRouting.Builder builder = HttpRouting.builder();
        for (String pathPattern : rulesMap.keySet()) {
            Map<String, JsonRpcHandler> handlersMap = rulesMap.get(pathPattern).handlersMap();
            builder.post(pathPattern, (req, res) -> {
                JsonStructure structure = req.content().as(JsonStructure.class);

                // if single request, create an array
                JsonArray array;
                if (structure instanceof JsonObject object) {
                    array = Json.createArrayBuilder().add(object).build();
                } else {
                    array = (JsonArray) structure;
                }

                // verify that requests are valid
                if (!verifyJsonRpc(array, handlersMap)) {
                    res.status(400).send("Invalid JSON-RPC request");
                    return;
                }

                // execute each request in order
                for (int i = 0; i < array.size(); i++) {
                    JsonObject rpc = array.getJsonObject(i);
                    JsonRpcHandler handler = handlersMap.get(rpc.getString("method"));
                    JsonRpcRequest jsonRpcRequest = new JsonRpcRequestImpl(rpc);
                    JsonRpcResponse jsonRpcResponse = new JsonRpcResponseImpl() {
                        @Override
                        public void send() {
                            try {
                                JsonObjectBuilder builder = Json.createObjectBuilder()
                                        .add("jsonrpc", "2.0");
                                jsonRpcRequest.id().map(id -> builder.add("id", id));
                                if (result() != null) {
                                    builder.add("result", result());
                                } else {
                                    builder.add("error", JsonUtils.jsonbToJsonp(error()));
                                }
                                res.status(status().code()).send(builder.build());
                            } catch (Exception e) {
                                res.status(500).send();
                            }
                        }
                    };
                    handler.handle(jsonRpcRequest, jsonRpcResponse);
                }
            });
        }
        return builder;
    }

    /**
     * Verifies that JSON-RPC requests are valid checking for version and method.
     *
     * @param array array of requests
     * @param handlersMap register handlers
     * @return outcome of verification
     */
    private boolean verifyJsonRpc(JsonArray array, Map<String, JsonRpcHandler> handlersMap) {
        try {
            for (int i = 0; i < array.size(); i++) {
                JsonObject rpc = array.getJsonObject(i);
                String version = rpc.getString("jsonrpc");
                if (!"2.0".equals(version)) {
                    return false;
                }
                String method = rpc.getString("method");
                JsonRpcHandler handler = handlersMap.get(method);
                if (handler == null) {
                    return false;
                }
            }
            return true;
        } catch (ClassCastException e) {
            return false;       // malformed
        }
    }

    /**
     * Builder for {@link io.helidon.webserver.jsonrpc.JsonRpcRouting}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, JsonRpcRouting> {

        private final JsonRpcRulesImpl rules = new JsonRpcRulesImpl();
        private final List<JsonRpcService> services = new ArrayList<>();

        private Builder() {
        }

        /**
         * Builds a {@link io.helidon.webserver.jsonrpc.JsonRpcRouting} with the
         * registered services.
         *
         * @return a routing instance
         */
        @Override
        public JsonRpcRouting build() {
            for (JsonRpcService service : services) {
                service.routing(rules);
            }
            return new JsonRpcRouting(this);
        }

        /**
         * Adds a JSON-RPC service to this routing.
         *
         * @param service the server to add
         * @return this builder
         */
        Builder service(JsonRpcService service) {
            services.add(service);
            return this;
        }
    }
}
