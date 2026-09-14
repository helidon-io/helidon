/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.grpc.security;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration of gRPC service security.
 */
@Prototype.Blueprint
@Prototype.Configured
interface GrpcSecurityServiceConfigBlueprint {
    /**
     * Name of the gRPC service, either the full name such as {@code package.StringService} or the final
     * service name segment such as {@code StringService}.
     *
     * @return gRPC service name
     */
    @Option.Configured
    String name();

    /**
     * Default security handler for this gRPC service.
     *
     * @return service default security handler
     */
    @Option.Configured
    @Option.DefaultCode("GrpcSecurityHandler.create()")
    GrpcSecurityHandler defaults();

    /**
     * Method-specific security configuration.
     *
     * @return method security configuration
     */
    @Option.Configured
    @Option.Singular
    List<GrpcSecurityMethodConfig> methods();
}
