/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.spi;

import java.util.concurrent.CompletionStage;

import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityEnvironment;

/**
 * Security provider for securing client requests (outbound).
 * <p>
 * The most common use cases:
 * <ul>
 * <li>Propagate current user's identity
 * <li>Call other service with correct service identity
 * </ul>
 *
 * @see #supportedCustomObjects()
 * @see #supportedConfigKeys()
 */
@FunctionalInterface
public interface OutboundSecurityProvider extends SecurityProvider {
    /**
     * Check if the path to be executed is supported by this security provider.
     * Defaults to true.
     *
     * @param providerRequest context with environment, subject(s) etc. that was received
     * @param outboundEnv     environment for outbound call
     * @param outboundConfig  outbound endpoint configuration
     * @return true if this identity propagator can generate required headers for the path defined
     */
    default boolean isOutboundSupported(ProviderRequest providerRequest,
                                        SecurityEnvironment outboundEnv,
                                        EndpointConfig outboundConfig) {
        return true;
    }

    /**
     * Creates necessary updates to headers and entity needed for outbound
     * security (e.g. identity propagation, s2s security etc.).
     * This method will be invoked for outbound requests ONLY.
     *
     * @param providerRequest context with environment, subject(s) etc. that was received
     * @param outboundEnv     environment for outbound call
     * @param outboundConfig  outbound endpoint configuration
     * @return response with generated headers and other possible configuration
     * @see OutboundSecurityResponse#builder()
     */
    CompletionStage<OutboundSecurityResponse> outboundSecurity(ProviderRequest providerRequest,
                                                               SecurityEnvironment outboundEnv,
                                                               EndpointConfig outboundConfig);
}
