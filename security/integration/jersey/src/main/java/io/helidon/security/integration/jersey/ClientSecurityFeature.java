/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.integration.jersey;

import javax.ws.rs.RuntimeType;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;

/**
 * Integration of Security module with Jersey clients.
 * If you want to use this class, please inject it as a context and then
 * register it on your {@link javax.ws.rs.client.Client}.
 *
 * @deprecated replaced with {@code io.helidon.security.integration.jersey.client.ClientSecurity} for constants
 *      the feature is no longer needed to configure security
 */
@Deprecated
public final class ClientSecurityFeature implements Feature {

    /**
     * Property name for security context. Set this with
     * {@link javax.ws.rs.client.Invocation.Builder#property(String, Object)}, obtained
     * through {@link javax.ws.rs.client.WebTarget#request()}
     *
     * @deprecated use {@code ClientSecurity} constants instead
     */
    @Deprecated
    public static final String PROPERTY_CONTEXT = "io.helidon.security.jersey.SecureClient.context";
    /**
     * Property name for outbound security provider name. Set this with
     * {@link javax.ws.rs.client.Invocation.Builder#property(String, Object)},
     * obtained
     * through {@link javax.ws.rs.client.WebTarget#request()}
     *
     * @deprecated use {@code ClientSecurity} constants instead
     */
    public static final String PROPERTY_PROVIDER = "io.helidon.security.jersey.SecureClient.explicitProvider";

    /**
     * Create a new security feature. Security context must be provided through for the security context.
     * This is a constructor to be used for clients that are not invoked within Jersey server
     * context.
     */
    public ClientSecurityFeature() {
    }

    @Override
    public boolean configure(FeatureContext context) {
        RuntimeType runtimeType = context.getConfiguration().getRuntimeType();

        //register client
        if (runtimeType == RuntimeType.CLIENT) {
            context.register(new ClientSecurityFilter());
        } else {
            throw new IllegalStateException(
                    "ClientSecurityFeature is only available for client side Jersey. For servers, please use SecurityFeature");
        }

        return true;
    }
}
