/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.webserver.spi.ProtocolConfig;
import io.helidon.webserver.spi.ProtocolConfigProvider;

@Prototype.Blueprint
@Prototype.Configured(root = false, value = GrpcProtocolProvider.CONFIG_NAME)
@Prototype.Provides(ProtocolConfigProvider.class)
interface GrpcConfigBlueprint extends ProtocolConfig {

    /**
     * Protocol configuration name.
     *
     * @return name of this configuration
     */
    @Option.Default(GrpcProtocolProvider.CONFIG_NAME)
    String name();

    /**
     * Protocol configuration type.
     *
     * @return type of this configuration
     */
    @Option.Default(GrpcProtocolProvider.CONFIG_NAME)
    String type();

    /**
     * Whether to collect metrics for gRPC server calls.
     *
     * @return metrics flag
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean enableMetrics();

    /**
     * Whether to support compression if requested by a client. If explicitly
     * disabled, no compression will be ever be used by the server even if a
     * client-compatible compressor is found.
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enableCompression();
}
