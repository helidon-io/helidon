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
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParsingException;

import static io.helidon.webserver.jsonrpc.JsonRpcError.INTERNAL_ERROR;
import static io.helidon.webserver.jsonrpc.JsonRpcError.INVALID_REQUEST;
import static io.helidon.webserver.jsonrpc.JsonRpcError.METHOD_NOT_FOUND;
import static io.helidon.webserver.jsonrpc.JsonRpcError.PARSE_ERROR;
import static io.helidon.webserver.jsonrpc.JsonUtil.jsonbToJsonp;

/**
 * A routing class for JSON-RPC. This class provides a method to map
 * JSON-RPC routes to HTTP routes for registration in the Webserver.
 */
public class JsonRpcRouting implements Routing {

    static final JsonRpcError INVALID_REQUEST_ERROR = JsonRpcError.builder()
            .code(INVALID_REQUEST)
            .message("The JSON sent is not a valid Request object")
            .build();

    static final JsonRpcError METHOD_NOT_FOUND_ERROR = JsonRpcError.builder()
            .code(METHOD_NOT_FOUND)
            .message("The method does not exist or is not available")
            .build();

    static final JsonRpcError INTERNAL_ERROR_ERROR = JsonRpcError.builder()
            .code(INTERNAL_ERROR)
            .message("Internal JSON-RPC error")
            .build();

    static final JsonRpcError PARSE_ERROR_ERROR = JsonRpcError.builder()
            .code(PARSE_ERROR)
            .message("Invalid JSON was received by the server")
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
        for (Map.Entry<String, JsonRpcHandlers> entry : rulesMap.entrySet()) {
            String pathPattern = entry.getKey();
            Map<String, JsonRpcHandler> handlersMap = entry.getValue().handlersMap();
            builder.post(pathPattern, (req, res) -> {
                try {
                    // attempt to parse request as JSON
                    JsonStructure jsonRequest;
                    try {
                        jsonRequest = req.content().as(JsonStructure.class);
                    } catch (JsonParsingException e) {
                        JsonObject parseError = jsonRpcError(PARSE_ERROR_ERROR, null);
                        res.status(Status.OK_200).send(parseError);
                        return;
                    }

                    // is this a single request?
                    if (jsonRequest instanceof JsonObject jsonObject) {
                        // if request fails verification, return JSON-RPC error
                        JsonRpcError error = verifyJsonRpc(jsonObject, handlersMap);
                        if (error != null) {
                            JsonObject verifyError = jsonRpcError(error, jsonObject);
                            res.status(Status.OK_200).send(verifyError);
                            return;
                        }

                        // prepare and call method handler
                        JsonRpcHandler handler = handlersMap.get(jsonObject.getString("method"));
                        JsonRpcRequest jsonRpcRequest = new JsonRpcRequestImpl(req, jsonObject);
                        JsonRpcResponse jsonRpcResponse = new JsonRpcResponseImpl(res) {
                            @Override
                            public void send() {
                                try {
                                    JsonObjectBuilder builder = Json.createObjectBuilder()
                                            .add("jsonrpc", "2.0");
                                    jsonRpcRequest.requestId().map(id -> builder.add("id", id));
                                    Optional<JsonValue> result = result();
                                    if (result.isPresent()) {
                                        builder.add("result", result.get());
                                    } else {
                                        Optional<JsonRpcError> error = error();
                                        error.ifPresent(e -> builder.add("error", jsonbToJsonp(e)));
                                    }
                                    res.status(status().code()).send(builder.build());
                                } catch (Exception e) {
                                    sendInternalError(res);
                                }
                            }
                        };

                        // invoke single handler
                        try {
                            handler.handle(jsonRpcRequest, jsonRpcResponse);
                        } catch (Exception e) {
                            sendInternalError(res);
                        }
                    } else if (jsonRequest instanceof JsonArray jsonArray) {
                        int size = jsonArray.size();

                        // we must receive at least one request
                        if (size == 0) {
                            sendInvalidRequest(res);
                            return;
                        }

                        // process batch requests
                        JsonArrayBuilder jsonResult = Json.createArrayBuilder();
                        for (int i = 0; i < size; i++) {
                            JsonValue jsonValue = jsonArray.get(i);

                            // requests must be objects
                            if (!(jsonValue instanceof JsonObject jsonObject)) {
                                JsonObject invalidRequest = jsonRpcError(INVALID_REQUEST_ERROR, null);
                                jsonResult.add(invalidRequest);
                                continue;       // skip bad request
                            }

                            // check if request passes validation before proceeding
                            JsonRpcError error = verifyJsonRpc(jsonObject, handlersMap);
                            if (error != null) {
                                JsonObject verifyError = jsonRpcError(error, jsonObject);
                                jsonResult.add(verifyError);
                                continue;       // skip bad request
                            }

                            // prepare and call method handler
                            JsonRpcHandler handler = handlersMap.get(jsonObject.getString("method"));
                            JsonRpcRequest jsonRpcRequest = new JsonRpcRequestImpl(req, jsonObject);
                            JsonRpcResponse jsonRpcResponse = new JsonRpcResponseImpl(res) {
                                @Override
                                public void send() {
                                    try {
                                        JsonObjectBuilder builder = Json.createObjectBuilder()
                                                .add("jsonrpc", "2.0");
                                        jsonRpcRequest.requestId().map(id -> builder.add("id", id));
                                        Optional<JsonValue> result = result();
                                        if (result.isPresent()) {
                                            builder.add("result", result.get());
                                        } else {
                                            Optional<JsonRpcError> error = error();
                                            error.ifPresent(e -> builder.add("error", jsonbToJsonp(e)));
                                        }
                                        jsonResult.add(builder.build());
                                    } catch (Exception e) {
                                        sendInternalError(res);
                                    }
                                }
                            };

                            // invoke handler
                            try {
                                handler.handle(jsonRpcRequest, jsonRpcResponse);
                            } catch (Exception e) {
                                sendInternalError(res);
                                return;
                            }
                        }

                        // respond to batch request with batch response
                        res.status(Status.OK_200).send(jsonResult.build());
                    } else {
                        sendInvalidRequest(res);
                    }
                } catch (Exception e) {
                    sendInternalError(res);
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

    private JsonObject jsonRpcError(JsonRpcError error, JsonObject jsonObject) {
        JsonObjectBuilder errorBuilder = Json.createObjectBuilder()
                .add("jsonrpc", "2.0");
        if (jsonObject == null) {
            errorBuilder.add("id", JsonValue.NULL);
        } else if (jsonObject.containsKey("id")) {
            errorBuilder.add("id", jsonObject.getInt("id"));
        }
        errorBuilder.add("error", jsonbToJsonp(error));
        return errorBuilder.build();
    }

    private void sendInternalError(ServerResponse res) {
        JsonObject internalError = jsonRpcError(INTERNAL_ERROR_ERROR, null);
        res.status(Status.OK_200).send(internalError);
    }

    private void sendInvalidRequest(ServerResponse res) {
        JsonObject invalidRequest = jsonRpcError(INVALID_REQUEST_ERROR, null);
        res.status(Status.OK_200).send(invalidRequest);
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

        /**
         * Register JSON-RPC handlers directly without implementing a
         * {@link io.helidon.webserver.jsonrpc.JsonRpcService}.
         *
         * @param pathPattern the path pattern
         * @param handlers the handlers
         * @return this builder
         */
        public Builder register(String pathPattern, JsonRpcHandlers handlers) {
            rules.register(pathPattern, handlers);
            return this;
        }

        /**
         * Register a single JSON-RPC handler for a method and path pattern.
         *
         * @param pathPattern the path pattern
         * @param method the method name
         * @param handler the handler
         * @return this builder
         */
        public Builder register(String pathPattern, String method, JsonRpcHandler handler) {
            register(pathPattern, JsonRpcHandlers.create(method, handler));
            return this;
        }
    }
}
