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
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.jsonrpc.core.JsonRpcError;
import io.helidon.webserver.Routing;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParsingException;

import static io.helidon.jsonrpc.core.JsonRpcError.INTERNAL_ERROR;
import static io.helidon.jsonrpc.core.JsonRpcError.INVALID_REQUEST;
import static io.helidon.jsonrpc.core.JsonRpcError.METHOD_NOT_FOUND;
import static io.helidon.jsonrpc.core.JsonRpcError.PARSE_ERROR;

/**
 * A routing class for JSON-RPC. This class provides a method to map
 * JSON-RPC routes to HTTP routes for registration in the Webserver.
 */
public class JsonRpcRouting implements Routing {

    static final JsonRpcError INVALID_REQUEST_ERROR = JsonRpcError.create(
            INVALID_REQUEST,
            "The JSON sent is not a valid Request object");

    static final JsonRpcError METHOD_NOT_FOUND_ERROR = JsonRpcError.create(
            METHOD_NOT_FOUND,
            "The method does not exist or is not available");

    static final JsonRpcError INTERNAL_ERROR_ERROR = JsonRpcError.create(
            INTERNAL_ERROR,
            "Internal JSON-RPC error");

    static final JsonRpcError PARSE_ERROR_ERROR = JsonRpcError.create(
            PARSE_ERROR,
            "Invalid JSON was received by the server");

    private final JsonRpcRulesImpl rules;

    private JsonRpcRouting(Builder builder) {
        this.rules = builder.rules;
    }

    /**
     * Return a builder for this class.
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
     * Convert this instance to an {@link io.helidon.webserver.http.HttpRouting.Builder}
     * that can be registered in the Webserver.
     *
     * @return an instance of HttpRouting
     */
    public HttpRouting.Builder toHttpRouting() {
        HttpRouting.Builder builder = HttpRouting.builder();
        toHttpRouting(builder);
        return builder;
    }

    /**
     * Populate an {@link io.helidon.webserver.http.HttpRouting.Builder} with
     * all the routes for this JSON-RPC routing instance.
     *
     * @param builder an HTTP routing builder
     */
    public void toHttpRouting(HttpRouting.Builder builder) {
        for (JsonRpcRulesImpl.Rule rule : rules) {
            String pathPattern = rule.pathPattern();
            JsonRpcHandlers handlers = rule.handlers();
            Map<String, JsonRpcHandler> handlersMap = handlers.handlersMap();
            JsonRpcErrorHandler errorHandler = handlers.errorHandler();

            builder.post(pathPattern, (req, res) -> {
                try {
                    // attempt to parse request as JSON
                    JsonStructure jsonRequest;
                    try {
                        jsonRequest = req.content().as(JsonStructure.class);
                    } catch (JsonParsingException e) {
                        JsonObject parseError = jsonRpcError(PARSE_ERROR_ERROR, res, null);
                        res.status(Status.OK_200).send(parseError);
                        return;
                    }

                    // is this a single request?
                    if (jsonRequest instanceof JsonObject jsonObject) {
                        // if request fails verification, return JSON-RPC error
                        JsonRpcError error = verifyJsonRpc(jsonObject, handlersMap);
                        if (error != null) {
                            // if error handler succeeds don't report
                            if (errorHandler != null && errorHandler.handle(req, jsonObject)) {
                                res.status(Status.OK_200).send();
                            } else {
                                // otherwise return error
                                JsonObject verifyError = jsonRpcError(error, res, jsonObject);
                                res.status(Status.OK_200).send(verifyError);
                            }
                            return;
                        }

                        // prepare and call method handler
                        AtomicBoolean sendCalled = new AtomicBoolean();
                        JsonRpcHandler handler = handlersMap.get(jsonObject.getString("method"));
                        JsonRpcRequest jsonReq = new JsonRpcRequestImpl(req, jsonObject);
                        JsonValue rpcId = jsonReq.rpcId().orElse(null);
                        JsonRpcResponse jsonRes = new JsonRpcResponseImpl(rpcId, res) {
                            @Override
                            public void send() {
                                try {
                                    if (rpcId().isPresent()) {
                                        res.header(HeaderNames.CONTENT_TYPE, MediaTypes.APPLICATION_JSON_VALUE);
                                        res.status(status()).send(asJsonObject());
                                    } else {
                                        res.status(status()).send();        // notification
                                    }
                                    sendCalled.set(true);
                                } catch (Exception e) {
                                    sendInternalError(res);
                                }
                            }
                        };

                        // invoke single handler
                        try {
                            handler.handle(jsonReq, jsonRes);

                            // if send() not called, return empty HTTP response
                            if (!sendCalled.get()) {
                                res.status(jsonRes.status()).send();
                            }
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
                        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
                        for (int i = 0; i < size; i++) {
                            JsonValue jsonValue = jsonArray.get(i);

                            // requests must be objects
                            if (!(jsonValue instanceof JsonObject jsonObject)) {
                                JsonObject invalidRequest = jsonRpcError(INVALID_REQUEST_ERROR, res, null);
                                arrayBuilder.add(invalidRequest);
                                continue;       // skip bad request
                            }

                            // check if request passes validation before proceeding
                            JsonRpcError error = verifyJsonRpc(jsonObject, handlersMap);
                            if (error != null) {
                                // if error handler succeeds don't report
                                if (errorHandler != null && errorHandler.handle(req, jsonObject)) {
                                    continue;
                                }
                                // otherwise collect error
                                JsonObject verifyError = jsonRpcError(error, res, jsonObject);
                                arrayBuilder.add(verifyError);
                                continue;
                            }

                            // prepare and call method handler
                            JsonRpcHandler handler = handlersMap.get(jsonObject.getString("method"));
                            JsonRpcRequest jsonReq = new JsonRpcRequestImpl(req, jsonObject);
                            JsonValue rpcId = jsonReq.rpcId().orElse(null);
                            JsonRpcResponse jsonRes = new JsonRpcResponseImpl(rpcId, res) {
                                @Override
                                public void send() {
                                    try {
                                        if (rpcId().isPresent()) {
                                            res.header(HeaderNames.CONTENT_TYPE, MediaTypes.APPLICATION_JSON_VALUE);
                                            arrayBuilder.add(asJsonObject());
                                        }
                                    } catch (Exception e) {
                                        sendInternalError(res);
                                    }
                                }
                            };

                            // invoke handler
                            try {
                                handler.handle(jsonReq, jsonRes);
                            } catch (Exception e) {
                                sendInternalError(res);
                                return;
                            }
                        }

                        // respond to batch request always with 200
                        JsonArray result = arrayBuilder.build();
                        if (result.isEmpty()) {
                            res.status(Status.OK_200).send();
                        } else {
                            res.status(Status.OK_200).send(result);
                        }
                    } else {
                        sendInvalidRequest(res);
                    }
                } catch (Exception e) {
                    sendInternalError(res);
                }
            });
        }
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

    private JsonObject jsonRpcError(JsonRpcError error, ServerResponse res, JsonObject jsonObject) {
        JsonRpcResponse rpcRes = new JsonRpcResponseImpl(JsonValue.NULL, res);
        if (jsonObject != null && jsonObject.containsKey("id")) {
            rpcRes.rpcId(jsonObject.get("id"));
        }
        if (error.data().isEmpty()) {
            rpcRes.error(error.code(), error.message());
        } else {
            rpcRes.error(error.code(), error.message(), error.data().get());
        }
        return rpcRes.asJsonObject();
    }

    private void sendInternalError(ServerResponse res) {
        JsonObject internalError = jsonRpcError(INTERNAL_ERROR_ERROR, res, null);
        res.status(Status.OK_200).send(internalError);
    }

    private void sendInvalidRequest(ServerResponse res) {
        JsonObject invalidRequest = jsonRpcError(INVALID_REQUEST_ERROR, res, null);
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
         * @param handlers    the handlers
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
         * @param method      the method name
         * @param handler     the handler
         * @return this builder
         */
        public Builder register(String pathPattern, String method, JsonRpcHandler handler) {
            register(pathPattern, JsonRpcHandlers.create(method, handler));
            return this;
        }
    }
}
