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
package io.helidon.webclient.jsonrpc;

import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.spi.ClientProtocolProvider;

/**
 * Provider for {@link JsonRpcClient}.
 */
public class JsonRpcProtocolProvider implements ClientProtocolProvider<JsonRpcClient, JsonRpcClientProtocolConfig> {

    static final String CONFIG_KEY = "jsonrpc";

    /**
     * Public constructor required by {@link java.util.ServiceLoader}.
     */
    public JsonRpcProtocolProvider() {
    }

    @Override
    public String protocolId() {
        return JsonRpcClient.PROTOCOL_ID;
    }

    @Override
    public Class<JsonRpcClientProtocolConfig> configType() {
        return JsonRpcClientProtocolConfig.class;
    }

    @Override
    public JsonRpcClientProtocolConfig defaultConfig() {
        return JsonRpcClientProtocolConfig.create();
    }

    @Override
    public JsonRpcClient protocol(WebClient client, JsonRpcClientProtocolConfig config) {
        return new JsonRpcClientImpl(JsonRpcClientConfig.builder()
                                             .from(client.prototype())
                                             .protocolConfig(config)
                                             .buildPrototype());
    }
}
