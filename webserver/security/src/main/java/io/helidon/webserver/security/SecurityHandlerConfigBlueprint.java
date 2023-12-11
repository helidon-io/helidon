/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.security;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;
import io.helidon.security.ClassToInstanceStore;

/**
 * Configuration of a {@link io.helidon.webserver.security.SecurityHandler}.
 */
@Prototype.Blueprint(decorator = SecurityConfigSupport.SecurityHandlerDecorator.class)
@Prototype.Configured
@Prototype.CustomMethods(SecurityConfigSupport.SecurityHandlerCustomMethods.class)
interface SecurityHandlerConfigBlueprint extends Prototype.Factory<SecurityHandler> {
    /**
     * An array of allowed roles for this path - must have a security provider supporting roles (either authentication
     * or authorization provider).
     * This method enables authentication and authorization (you can disable them again by calling
     * {@link SecurityHandler#skipAuthorization()}
     * and {@link #authenticationOptional()} if needed).
     *
     * @return if subject is any of these roles, allow access
     */
    @Option.Configured
    @Option.Singular("roleAllowed")
    Set<String> rolesAllowed();

    /**
     * Use a named authenticator (as supported by security - if not defined, default authenticator is used).
     * Will enable authentication.
     *
     * @return name of authenticator as configured in {@link io.helidon.security.Security}
     */
    @Option.Configured
    Optional<String> authenticator();
    /**
     * Use a named authorizer (as supported by security - if not defined, default authorizer is used, if none defined, all is
     * permitted).
     * Will enable authorization.
     *
     * @return name of authorizer as configured in {@link io.helidon.security.Security}
     */
    @Option.Configured
    Optional<String> authorizer();
    /**
     * If called, request will go through authentication process - defaults to false (even if authorize is true).
     *
     * @return whether to authenticate or not
     */
    @Option.Configured
    Optional<Boolean> authenticate();
    /**
     * If called, authentication failure will not abort request and will continue as anonymous (defaults to false).
     *
     * @return whether authn is optional
     */
    @Option.Configured
    Optional<Boolean> authenticationOptional();
    /**
     * Whether to audit this request - defaults to false, if enabled, request is audited with event type "request".
     *
     * @return whether to audit
     */
    @Option.Configured
    Optional<Boolean> audit();
    /**
     * Enable authorization for this route.
     *
     * @return whether to authorize
     */
    @Option.Configured
    Optional<Boolean> authorize();
    /**
     * Override for event-type, defaults to {@value SecurityHandler#DEFAULT_AUDIT_EVENT_TYPE}.
     *
     * @return audit event type to use
     */
    @Option.Configured
    Optional<String> auditEventType();

    /**
     * Override for audit message format, defaults to {@value SecurityHandler#DEFAULT_AUDIT_MESSAGE_FORMAT}.
     *
     * @return audit message format to use
     */
    @Option.Configured
    Optional<String> auditMessageFormat();

    /**
     * Query parameter handler(s).
     *
     * @return query parameters
     */
    @Option.Singular
    List<SecurityHandler.QueryParamHandler> queryParams();

    /**
     * A store of custom objects, that can be used to customize specific security providers.
     *
     * @return custom objects
     */
    Optional<ClassToInstanceStore<Object>> customObjects();
    /**
     * List of sockets this configuration should be applied to.
     * If empty, the configuration is applied to all configured sockets.
     *
     * @return list of sockets
     */
    @Option.Configured
    List<String> sockets();

    /**
     * Configuration associated with this security handler.
     *
     * @return the configuration (if provided)
     */
    Optional<Config> config();

    /**
     * Whether this is a combined handler. Internal use.
     *
     * @return if combined handler
     */
    boolean combined();
}
