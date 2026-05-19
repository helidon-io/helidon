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

import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.config.Config;
import io.helidon.security.ClassToInstanceStore;

/**
 * Configuration of gRPC method security.
 */
@Prototype.Blueprint
@Prototype.Configured
@Prototype.CustomMethods(GrpcSecurityConfigSupport.GrpcSecurityMethodCustomMethods.class)
interface GrpcSecurityMethodConfigBlueprint {
    /**
     * Bare gRPC method name such as {@code Upper}, not the full gRPC method path such as
     * {@code StringService/Upper}.
     *
     * @return gRPC method name
     */
    @Option.Configured
    String name();

    /**
     * An array of allowed roles for this gRPC method.
     *
     * @return if subject is any of these roles, allow access
     */
    @Option.Configured
    @Option.Singular("roleAllowed")
    Set<String> rolesAllowed();

    /**
     * Use a named authenticator.
     *
     * @return name of authenticator as configured in {@link io.helidon.security.Security}
     */
    @Option.Configured
    Optional<String> authenticator();

    /**
     * Use a named authorizer.
     *
     * @return name of authorizer as configured in {@link io.helidon.security.Security}
     */
    @Option.Configured
    Optional<String> authorizer();

    /**
     * Whether to authenticate this request.
     *
     * @return whether to authenticate
     */
    @Option.Configured
    Optional<Boolean> authenticate();

    /**
     * Whether authentication failure should continue as anonymous.
     *
     * @return whether authentication is optional
     */
    @Option.Configured
    Optional<Boolean> authenticationOptional();

    /**
     * Whether to audit this request.
     *
     * @return whether to audit
     */
    @Option.Configured
    Optional<Boolean> audit();

    /**
     * Whether to authorize this request.
     *
     * @return whether to authorize
     */
    @Option.Configured
    Optional<Boolean> authorize();

    /**
     * Override for audit event type.
     *
     * @return audit event type
     */
    @Option.Configured
    Optional<String> auditEventType();

    /**
     * Override for audit message format.
     *
     * @return audit message format
     */
    @Option.Configured
    Optional<String> auditMessageFormat();

    /**
     * A store of custom objects, that can be used to customize specific security providers.
     *
     * @return custom objects
     */
    Optional<ClassToInstanceStore<Object>> customObjects();

    /**
     * Configuration associated with this security handler.
     *
     * @return the configuration
     */
    Optional<Config> config();

}
