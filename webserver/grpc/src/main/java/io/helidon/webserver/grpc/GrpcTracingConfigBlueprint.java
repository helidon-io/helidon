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
package io.helidon.webserver.grpc;

import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.grpc.core.GrpcTracingName;

@Prototype.Blueprint
@Prototype.Configured
interface GrpcTracingConfigBlueprint {

    /**
     * A flag indicating if tracing is enabled.
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * A flag indicating verbose logging.
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean verbose();

    /**
     * A flag indicating streaming logging.
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean streaming();

    /**
     * Operation name constructor.
     *
     * @return the tracing name
     */
    Optional<GrpcTracingName> operationNameConstructor();

    /**
     * Set of attributes to trace.
     *
     * @return set of attributes to trace
     */
    Set<ServerRequestAttribute> tracedAttributes();
}
