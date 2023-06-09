/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.security.providers;

import io.helidon.config.Config;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityTest;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.AuthorizationProvider;
import io.helidon.security.spi.OutboundSecurityProvider;
import io.helidon.security.spi.SecurityProvider;

/**
 * Just a simple testing provider.
 */
public class ProviderForTesting implements AuthenticationProvider, AuthorizationProvider, OutboundSecurityProvider,
                                           SecurityProvider {
    private final String denyResource;

    public ProviderForTesting(String denyResource) {
        this.denyResource = denyResource;
    }

    public static ProviderForTesting create(Config config) {
        return new ProviderForTesting(config.asString().get());
    }

    @Override
    public AuthenticationResponse authenticate(ProviderRequest providerRequest) {
        return AuthenticationResponse
                .success(SecurityTest.SYSTEM);
    }

    @Override
    public OutboundSecurityResponse outboundSecurity(ProviderRequest providerRequest,
                                                     SecurityEnvironment outboundEnv,
                                                     EndpointConfig outboundEndpointConfig) {
        return OutboundSecurityResponse.empty();
    }

    @Override
    public AuthorizationResponse authorize(ProviderRequest providerRequest) {
        String resource = providerRequest.env().abacAttribute("resourceType")
                .map(String::valueOf)
                .orElseThrow(() -> new IllegalArgumentException("Resource type is required"));

        if (denyResource.equals(resource)) {
            return AuthorizationResponse.deny();
        }

        return AuthorizationResponse.permit();
    }
}
