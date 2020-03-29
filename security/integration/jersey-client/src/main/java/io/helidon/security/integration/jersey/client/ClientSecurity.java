/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.security.integration.jersey.client;

/**
 * Constants used to override behavior of the outbound security for Jersey.
 */
public final class ClientSecurity {
    /**
     * Property name for security context. Use this only in case you want to use a different security context
     * than the one in the current request context.
     * Set this with
     * {@link javax.ws.rs.client.Invocation.Builder#property(String, Object)}, obtained
     * through {@link javax.ws.rs.client.WebTarget#request()}
     */
    // do not change the value of this property, needed for backward compatibility
    public static final String PROPERTY_CONTEXT = "io.helidon.security.jersey.SecureClient.context";
    /**
     * Property name for outbound security provider name. Set this with
     * {@link javax.ws.rs.client.Invocation.Builder#property(String, Object)},
     * obtained
     * through {@link javax.ws.rs.client.WebTarget#request()}
     */
    // do not change the value of this property, needed for backward compatibility
    public static final String PROPERTY_PROVIDER = "io.helidon.security.jersey.SecureClient.explicitProvider";

    private ClientSecurity() {
    }
}
