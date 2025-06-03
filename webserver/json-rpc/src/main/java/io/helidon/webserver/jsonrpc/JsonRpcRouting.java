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
import java.util.Optional;

import io.helidon.http.Status;
import io.helidon.webserver.Routing;
import io.helidon.webserver.http.HttpRouting;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParsingException;

import static io.helidon.webserver.jsonrpc.JsonRpcError.INTERNAL_ERROR;
import static io.helidon.webserver.jsonrpc.JsonRpcError.INVALID_REQUEST;
import static io.helidon.webserver.jsonrpc.JsonRpcError.METHOD_NOT_FOUND;

/**
 * A routing class for JSON-RPC. This class provides a method to map
 * JSON-RPC routes to HTTP routes for registration in the Webserver.
 */
public class JsonRpcRouting implements Routing {

    static final JsonRpcError INVALID_REQUEST_ERROR = JsonRpcError.builder()
            .code(INVALID_REQUEST)
            .message("Invalid JSON was received by the server")
            .build();

    static final JsonRpcError METHOD_NOT_FOUND_ERROR = JsonRpcError.builder()
            .code(METHOD_NOT_FOUND)
            .message("Invalid JSON was received by the server")
            .build();

    static final JsonRpcError INTERNAL_ERROR_ERROR = JsonRpcError.builder()
            .code(INTERNAL_ERROR)
            .message("Internal JSON-RPC error")
            .build();

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
                JsonStructure jsonRequest;
                try {
                    jsonRequest = req.content().as(JsonStructure.class);
                } catch (JsonParsingException e) {
                    res.status(400).send("Invalid JSON-RPC request");
                    return;
                }

                // if single request, create an array
                JsonArray array;
                if (jsonRequest instanceof JsonObject object) {
                    array = Json.createArrayBuilder().add(object).build();
                } else {
                    array = (JsonArray) jsonRequest;
                }

                // execute each request in order
                try {
                    for (int i = 0; i < array.size(); i++) {
                        JsonObject jsonObject = array.getJsonObject(i);

                        // if request fails verification, return JSON-RPC error and status 200
                        JsonRpcError error = verifyJsonRpc(jsonObject, handlersMap);
                        if (error != null) {
                            res.status(Status.OK_200).send(jsonRpcError(error, jsonObject));
                            continue;       // process next request
                        }

                        // prepare and call method handler
                        JsonRpcHandler handler = handlersMap.get(jsonObject.getString("method"));
                        JsonRpcRequest jsonRpcRequest = new JsonRpcRequestImpl(jsonObject);
                        JsonRpcResponse jsonRpcResponse = new JsonRpcResponseImpl() {
                            @Override
                            public void send() {
                                try {
                                    JsonObjectBuilder builder = Json.createObjectBuilder()
                                            .add("jsonrpc", "2.0");
                                    jsonRpcRequest.id().map(id -> builder.add("id", id));
                                    Optional<JsonValue> result = result();
                                    if (result.isPresent()) {
                                        builder.add("result", result.get());
                                    } else {
                                        Optional<JsonRpcError> error = error();
                                        if (error.isPresent()) {
                                            builder.add("error", JsonUtils.jsonbToJsonp(error.get()));
                                        }
                                    }
                                    res.status(status().code()).send(builder.build());
                                } catch (Exception e) {
                                    res.status(Status.INTERNAL_SERVER_ERROR_500).send();
                                }
                            }
                        };

                        try {
                            handler.handle(jsonRpcRequest, jsonRpcResponse);
                        } catch (Exception e) {
                            res.status(Status.OK_200).send(jsonRpcError(INTERNAL_ERROR_ERROR, jsonObject));
                        }
                    }
                } catch (Exception e) {
                    res.status(Status.INTERNAL_SERVER_ERROR_500).send();
                }
            });
        }
        return builder;
    }

    private JsonRpcError verifyJsonRpc(JsonObject object, Map<String, JsonRpcHandler> handlersMap) {
        try {
            String version = object.getString("jsonrpc");
            if (!"2.0".equals(version)) {
                return INVALID_REQUEST_ERROR;
            }
            String method = object.getString("method");
            JsonRpcHandler handler = handlersMap.get(method);
            if (handler == null) {
                return METHOD_NOT_FOUND_ERROR;
            }
            return null;
        } catch (ClassCastException e) {
            return INVALID_REQUEST_ERROR;       // malformed
        }
    }

    private JsonObject jsonRpcError(JsonRpcError error, JsonObject jsonObject) throws Exception {
        JsonObjectBuilder errorBuilder = Json.createObjectBuilder()
                .add("jsonrpc", "2.0");
        if (jsonObject.containsKey("id")) {
            errorBuilder.add("id", jsonObject.getInt("id"));
        }
        errorBuilder.add("error", JsonUtils.jsonbToJsonp(error));
        return errorBuilder.build();
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
         * Build a {@link io.helidon.webserver.jsonrpc.JsonRpcRouting} with the
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
         * Add a JSON-RPC service to this routing.
         *
         * @param service the server to add
         * @return this builder
         */
        public Builder service(JsonRpcService service) {
            services.add(service);
            return this;
        }
    }
}
