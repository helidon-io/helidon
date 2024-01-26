/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webclient.grpc;

import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.spi.ClientProtocolProvider;

public class GrpcProtocolProvider implements ClientProtocolProvider<GrpcClient, GrpcClientProtocolConfig> {
    static final String CONFIG_KEY = "grpc";

    /**
     * Public constructor required by {@link java.util.ServiceLoader}.
     */
    public GrpcProtocolProvider() {
    }

    @Override
    public String protocolId() {
        return CONFIG_KEY;
    }

    @Override
    public Class<GrpcClientProtocolConfig> configType() {
        return GrpcClientProtocolConfig.class;
    }

    @Override
    public GrpcClientProtocolConfig defaultConfig() {
        return GrpcClientProtocolConfig.create();
    }

    @Override
    public GrpcClient protocol(WebClient client, GrpcClientProtocolConfig config) {
        return new GrpcClientImpl(client,
                                  GrpcClientConfig.builder().from(client.prototype())
                                          .protocolConfig(config)
                                          .buildPrototype());
    }
}
