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
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

public class JsonRpcRouting implements Routing {

    private final JsonRpcRulesImpl rules;

    private JsonRpcRouting(Builder builder) {
        this.rules = builder.rules;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Class<? extends Routing> routingType() {
        return JsonRpcRouting.class;
    }

    public HttpRouting.Builder toHttpRouting() {
        Map<String, JsonRpcHandlers> rulesMap = rules.rulesMap();
        HttpRouting.Builder builder = HttpRouting.builder();
        for (String pathPattern : rulesMap.keySet()) {
            Map<String, JsonRpcHandler> handlersMap = rulesMap.get(pathPattern).handlersMap();
            builder.post(pathPattern, (req, res) -> {
                JsonObject rpc = req.content().as(JsonObject.class);

                String version = rpc.getString("jsonrpc");
                if (version == null || !version.equals("2.0")) {
                    res.status(400).send("JSON-RPC version must be 2.0");
                    return;
                }

                String method = rpc.getString("method");
                if (!handlersMap.containsKey(method)) {
                    res.status(400).send("Unable to find JSON-RPC method");
                    return;
                }
                JsonRpcHandler handler = handlersMap.get(method);
                JsonRpcRequest jsonRpcRequest = new JsonRpcRequestImpl(rpc);
                JsonRpcResponse jsonRpcResponse = new JsonRpcResponseImpl() {
                    @Override
                    public void send() {
                        JsonObjectBuilder builder = Json.createObjectBuilder()
                                                        .add("jsonrpc", "2.0");
                        jsonRpcRequest.id().map(id -> builder.add("id", id));
                        if (result() != null) {
                            builder.add("result", result());
                        } else {
                            // TODO error
                        }
                        res.status(status().code()).send(builder.build());
                    }
                };
                handler.handle(jsonRpcRequest, jsonRpcResponse);
            });
        }
        return builder;
    }

    public static class Builder implements io.helidon.common.Builder<Builder, JsonRpcRouting> {

        private final JsonRpcRulesImpl rules = new JsonRpcRulesImpl();
        private final List<JsonRpcService> services = new ArrayList<>();

        private Builder() {
        }

        @Override
        public JsonRpcRouting build() {
            for (JsonRpcService service : services) {
                service.routing(rules);
            }
            return new JsonRpcRouting(this);
        }

        Builder service(JsonRpcService service) {
            services.add(service);
            return this;
        }
    }
}
