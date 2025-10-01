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
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.GenericType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.jsonrpc.core.JsonRpcError;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http.spi.Sink;

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
import static io.helidon.jsonrpc.core.JsonUtil.JSON_BUILDER_FACTORY;

/**
 * JSON-RPC routing is an HTTP Service, as it is based on HTTP protocol.
 * <p>
 * Simply register it with HTTP routing under a desired path.
 */
public class JsonRpcRouting implements HttpService {

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
    public void routing(HttpRules httpRules) {
        for (JsonRpcRulesImpl.Rule rule : rules) {
            String pathPattern = rule.pathPattern();
            JsonRpcHandlers handlers = rule.handlers();
            Map<String, JsonRpcHandler> handlersMap = handlers.handlersMap();
            JsonRpcErrorHandler errorHandler = handlers.errorHandler().orElse(null);

            httpRules.post(pathPattern, (req, res) -> {
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
                        // Use error if returned by error handler
                        if (errorHandler != null) {
                            Optional<JsonRpcError> userError = errorHandler.handle(req, jsonObject);
                            if (userError.isPresent()) {
                                res.status(Status.OK_200).send(jsonRpcError(userError.get(), res, jsonObject.get("id")));
                            } else {
                                res.status(Status.OK_200).send();
                            }
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
                    JsonRpcResponse jsonRes = new JsonRpcSingleResponse(rpcId, res, sendCalled);

                    // invoke single handler
                    try {
                        handler.handle(jsonReq, jsonRes);
                        // if send() not called, return empty HTTP response
                        if (!sendCalled.get()) {
                            res.status(jsonRes.status()).send();
                        }
                    } catch (Throwable throwable1) {
                        try {
                            // see if there is an exception handler defined
                            Optional<JsonRpcError> mappedError = handleThrowable(handlers, jsonReq, jsonRes, throwable1);

                            // use error if returned, otherwise internal error
                            if (mappedError.isPresent()) {
                                JsonObject jsonRpcError = jsonRpcError(mappedError.get(), res, jsonObject.get("id"));
                                res.status(Status.OK_200).send(jsonRpcError);
                                return;
                            }
                        } catch (Throwable throwable2) {
                            // falls through
                        }
                        sendInternalError(res, jsonObject.get("id"));
                    }
                } else if (jsonRequest instanceof JsonArray jsonArray) {
                    // we must receive at least one request
                    int size = jsonArray.size();
                    if (size == 0) {
                        sendInvalidRequest(res);
                        return;
                    }

                    // process batch requests
                    JsonArrayBuilder arrayBuilder = JSON_BUILDER_FACTORY.createArrayBuilder();
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
                            // Use error if returned by error handler
                            if (errorHandler != null) {
                                Optional<JsonRpcError> userError = errorHandler.handle(req, jsonObject);
                                userError.ifPresent(
                                        e -> arrayBuilder.add(jsonRpcError(e, res, jsonObject.get("id"))));
                            } else {
                                JsonObject verifyError = jsonRpcError(error, res, jsonObject.get("id"));
                                arrayBuilder.add(verifyError);
                            }
                            continue;
                        }

                        // prepare and call method handler
                        JsonRpcHandler handler = handlersMap.get(jsonObject.getString("method"));
                        JsonRpcRequest jsonReq = new JsonRpcRequestImpl(req, jsonObject);
                        JsonValue rpcId = jsonReq.rpcId().orElse(null);
                        JsonRpcResponse jsonRes = new MyJsonRpcBatchResponse(rpcId, res, arrayBuilder);

                        // invoke handler
                        try {
                            handler.handle(jsonReq, jsonRes);
                        } catch (Throwable throwable1) {
                            try {
                                // see if there is an exception handler defined
                                Optional<JsonRpcError> mappedError = handleThrowable(handlers, jsonReq, jsonRes, throwable1);

                                // use error if returned, otherwise internal error
                                if (mappedError.isPresent()) {
                                    JsonObject jsonRpcError = jsonRpcError(mappedError.get(), res, jsonObject.get("id"));
                                    arrayBuilder.add(jsonRpcError);
                                    continue;
                                }
                            } catch (Throwable throwable2) {
                                // falls through
                            }
                            JsonObject internalError = jsonRpcError(INTERNAL_ERROR_ERROR, res, jsonObject.get("id"));
                            arrayBuilder.add(internalError);
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
            });
        }
    }

    private Optional<JsonRpcError> handleThrowable(JsonRpcHandlers handlers,
                                                   JsonRpcRequest jsonReq,
                                                   JsonRpcResponse jsonRes,
                                                   Throwable throwable) throws Throwable {
        // returned in registration order
        for (Map.Entry<Class<? extends Throwable>, JsonRpcExceptionHandler> entry : handlers.exceptionMap().entrySet()) {
            if (entry.getKey().isAssignableFrom(throwable.getClass())) {
                JsonRpcExceptionHandler handler = entry.getValue();
                return handler.handle(jsonReq, jsonRes, throwable);
            }
        }
        throw throwable;        // could not handle exception
    }

    private JsonRpcError verifyJsonRpc(JsonObject object, Map<String, JsonRpcHandler> handlersMap) {
        try {
            String version = object.getString("jsonrpc");
            if (!"2.0".equals(version)) {
                return INVALID_REQUEST_ERROR;
            }
            if (object.containsKey("method")) {
                String method = object.getString("method");
                if (handlersMap.get(method) != null) {
                    return null;                // method found
                }
            }
            return METHOD_NOT_FOUND_ERROR;
        } catch (ClassCastException e) {
            return INVALID_REQUEST_ERROR;       // malformed
        }
    }

    private JsonObject jsonRpcError(JsonRpcError error, ServerResponse res, JsonValue id) {
        JsonRpcResponse rpcRes = new JsonRpcResponseImpl(JsonValue.NULL, res);
        if (id != null) {
            rpcRes.rpcId(id);
        }
        if (error.data().isEmpty()) {
            rpcRes.error(error.code(), error.message());
        } else {
            rpcRes.error(error.code(), error.message(), error.data().get());
        }
        return rpcRes.asJsonObject();
    }

    private void sendInternalError(ServerResponse res, JsonValue id) {
        JsonObject internalError = jsonRpcError(INTERNAL_ERROR_ERROR, res, id);
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
            JsonRpcHandlers.Builder builder = JsonRpcHandlers.builder();
            builder.method(method, handler);
            register(pathPattern, builder.build());
            return this;
        }
    }

    private class JsonRpcSingleResponse extends JsonRpcResponseImpl {
        private final ServerResponse res;
        private final AtomicBoolean sendCalled;

        JsonRpcSingleResponse(JsonValue rpcId, ServerResponse res, AtomicBoolean sendCalled) {
            super(rpcId, res);
            this.res = res;
            this.sendCalled = sendCalled;
        }

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
                sendInternalError(res, rpcId().orElse(null));
            }
        }

        @Override
        public <T extends Sink<?>> T sink(GenericType<T> sinkType) {
            sendCalled.set(true);
            return super.sink(sinkType);
        }
    }

    private class MyJsonRpcBatchResponse extends JsonRpcResponseImpl {
        private final ServerResponse res;
        private final JsonArrayBuilder arrayBuilder;

        MyJsonRpcBatchResponse(JsonValue rpcId, ServerResponse res, JsonArrayBuilder arrayBuilder) {
            super(rpcId, res);
            this.res = res;
            this.arrayBuilder = arrayBuilder;
        }

        @Override
        public void send() {
            try {
                if (rpcId().isPresent()) {
                    res.header(HeaderNames.CONTENT_TYPE, MediaTypes.APPLICATION_JSON_VALUE);
                    arrayBuilder.add(asJsonObject());
                }
            } catch (Exception e) {
                sendInternalError(res, rpcId().orElse(null));
            }
        }
    }
}
