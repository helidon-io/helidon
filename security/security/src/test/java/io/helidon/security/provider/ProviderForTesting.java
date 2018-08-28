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

package io.helidon.security.provider;

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
import io.helidon.security.spi.SynchronousProvider;

/**
 * Just a simple testing provider.
 */
public class ProviderForTesting extends SynchronousProvider
        implements AuthenticationProvider, AuthorizationProvider, OutboundSecurityProvider {
    private final String denyResource;

    public ProviderForTesting(String denyResource) {
        this.denyResource = denyResource;
    }

    public static ProviderForTesting fromConfig(Config config) {
        return new ProviderForTesting(config.asString());
    }

    @Override
    protected AuthenticationResponse syncAuthenticate(ProviderRequest providerRequest) {
        return AuthenticationResponse
                .success(SecurityTest.SYSTEM);
    }

    @Override
    protected OutboundSecurityResponse syncOutbound(ProviderRequest providerRequest,
                                                    SecurityEnvironment outboundEnv,
                                                    EndpointConfig outboundEndpointConfig) {
        return OutboundSecurityResponse.empty();
    }

    @Override
    protected AuthorizationResponse syncAuthorize(ProviderRequest providerRequest) {
        String resource = providerRequest.getEnv().getAttribute("resourceType")
                .map(String::valueOf)
                .orElseThrow(() -> new IllegalArgumentException("Resource type is required"));

        if (denyResource.equals(resource)) {
            return AuthorizationResponse.deny();
        }

        return AuthorizationResponse.permit();
    }
}
