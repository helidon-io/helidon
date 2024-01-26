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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.webclient.spi.ProtocolConfig;

/**
 * Configuration of an HTTP/1.1 client.
 */
@Prototype.Blueprint
@Prototype.Configured
interface GrpcClientProtocolConfigBlueprint extends ProtocolConfig {
    @Override
    default String type() {
        return GrpcProtocolProvider.CONFIG_KEY;
    }

    @Option.Configured
    @Option.Default(GrpcProtocolProvider.CONFIG_KEY)
    @Override
    String name();

}