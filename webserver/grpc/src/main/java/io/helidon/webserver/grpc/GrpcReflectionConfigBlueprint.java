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

package io.helidon.webserver.grpc;

import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * Configuration of gRPC Reflection feature.
 */
@Prototype.Blueprint
@Prototype.Configured(value = GrpcReflectionFeature.GRPC_REFLECTION, root = false)
@Prototype.Provides(ServerFeatureProvider.class)
interface GrpcReflectionConfigBlueprint extends Prototype.Factory<GrpcReflectionFeature> {

    /**
     * List of sockets to register this feature on. If empty, it would get registered on all sockets.
     *
     * @return socket names to register on, defaults to empty (all available sockets)
     */
    @Option.Configured
    @Option.Singular
    Set<String> sockets();

    /**
     * Name of this instance.
     *
     * @return instance name
     */
    @Option.Default(GrpcReflectionFeature.GRPC_REFLECTION)
    String name();

    /**
     * This feature can be enabled.
     *
     * @return whether the feature is enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean enabled();
}
