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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.webclient.spi.ProtocolConfig;

/**
 * Configuration of a JSON-RPC client.
 */
@Prototype.Blueprint
@Prototype.Configured
interface JsonRpcClientProtocolConfigBlueprint extends ProtocolConfig {

    /**
     * Type identifying this protocol.
     *
     * @return protocol type
     */
    @Override
    default String type() {
        return JsonRpcProtocolProvider.CONFIG_KEY;
    }

    /**
     * Name identifying this client protocol. Defaults to type.
     *
     * @return name of client protocol
     */
    @Option.Configured
    @Option.Default(JsonRpcProtocolProvider.CONFIG_KEY)
    @Override
    String name();
}
