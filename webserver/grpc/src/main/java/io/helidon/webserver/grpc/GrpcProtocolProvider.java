/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.grpc;

import io.helidon.webserver.ProtocolConfigs;
import io.helidon.webserver.http2.spi.Http2SubProtocolProvider;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;

/**
 * {@link java.util.ServiceLoader} provider implementation of grpc sub-protocol of HTTP/2.
 */
public class GrpcProtocolProvider implements Http2SubProtocolProvider<GrpcConfig> {
    static final String CONFIG_NAME = "grpc";

    /**
     * Default constructor required by Java {@link java.util.ServiceLoader}.
     *
     * @deprecated please do not use directly outside of testing, this is reserved for Java {@link java.util.ServiceLoader}
     */
    @Deprecated
    public GrpcProtocolProvider() {
    }

    @Override
    public String protocolType() {
        return CONFIG_NAME;
    }

    @Override
    public Class<GrpcConfig> protocolConfigType() {
        return GrpcConfig.class;
    }

    @Override
    public Http2SubProtocolSelector create(GrpcConfig config, ProtocolConfigs configs) {
        return GrpcProtocolSelector.create();
    }
}
