/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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

package io.helidon.security;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.security.util.AbacSupport;

/**
 * A request sent to security providers.
 * Contains all information that may be needed to authenticate or authorize a request:
 * <ul>
 * <li>User's subject: {@link #subject()} - if user is authenticated</li>
 * <li>Service subject: {@link #service()} - if service is authenticated</li>
 * <li>Environment information: {@link #env()} - path, method etc.</li>
 * <li>Object: {@link #getObject()} - target resource, if provided by user</li>
 * <li>Security context: {@link #securityContext()} - current subjects and information about security context of this
 * request</li>
 * <li>Endpoint configuration: {@link #endpointConfig()} - annotations, endpoint specific configuration, custom objects,
 * custom attributes</li>
 * </ul>
 */
public interface ProviderRequest extends AbacSupport {
    /**
     * Create a new provider request.
     *
     * The following attributes will be bound by this method (even if already specified):
     * <ul>
     *     <li>{@code env}</li>
     *     <li>{code subject}</li>
     *     <li>{code service}</li>
     * </ul>
     *
     * @param context current security context
     * @param boundAttributes suppliers of bound attributes
     * @return a new provider request
     */
    static ProviderRequest create(SecurityContext context,
                                  Map<String, Supplier<Object>> boundAttributes) {
        return new ProviderRequestImpl(context, boundAttributes);
    }

    /**
     * Get a value of a property from an object.
     * If object implements {@link AbacSupport} the value is obtained through {@link AbacSupport#abacAttribute(String)}, if not,
     * the value is obtained by reflection from a public field or a public getter method.
     * The method name may be (for attribute called for example "audit"):
     * <ul>
     * <li>audit</li>
     * <li>getAudit</li>
     * <li>isAudit</li>
     * <li>shouldAudit</li>
     * <li>hasAudit</li>
     * </ul>
     *
     * @param object object to get attribute from
     * @param key    key of the attribute
     * @return value of the attribute if found
     */
    static Optional<Object> getValue(Object object, String key) {
        return ProviderRequestImpl.getValue(object, key);
    }

    /**
     * Configuration of the invoked endpoint, such as annotations declared.
     * @return endpoint config
     */
    EndpointConfig endpointConfig();

    /**
     * Security context associated with current request.
     * @return security context
     */
    SecurityContext securityContext();

    /**
     * Current user subject, if already authenticated.
     * @return user subject or empty
     */
    Optional<Subject> subject();

    /**
     * Current service subject, if already authenticated.
     * @return service subject or empty.
     */
    Optional<Subject> service();

    /**
     * Environment of current request, such as the URI invoked, time to use for security decisions etc.
     * @return security environment
     */
    SecurityEnvironment env();

    /**
     * The object of this request. Security request may be configured for a specific entity (e.g. if this is an entity
     * modification request, the entity itself may be provided to help in a security task.
     *
     * @return the object or empty if not known
     */
    Optional<Object> getObject();
}
