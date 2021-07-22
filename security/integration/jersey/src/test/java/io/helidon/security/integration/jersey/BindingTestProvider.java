/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Map;

import io.helidon.security.AuthenticationResponse;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.AuthorizationProvider;
import io.helidon.security.spi.OutboundSecurityProvider;
import io.helidon.security.spi.SynchronousProvider;

/**
 * Simple authorization provider, denying access to "deny" path.
 */
public class BindingTestProvider extends SynchronousProvider
        implements AuthorizationProvider, AuthenticationProvider, OutboundSecurityProvider {
    @Override
    protected AuthorizationResponse syncAuthorize(ProviderRequest providerRequest) {
        String path = providerRequest
                .env().path().orElseThrow(() -> new IllegalArgumentException("Path is a required parameter"));
        if ("/deny".equals(path)) {
            return AuthorizationResponse.deny();
        }
        return AuthorizationResponse.permit();
    }

    @Override
    protected AuthenticationResponse syncAuthenticate(ProviderRequest providerRequest) {
        List<String> strings = providerRequest.env().headers().get("x-user");

        if (null == strings) {
            return AuthenticationResponse.abstain();
        }
        return AuthenticationResponse.success(Principal.create(strings.get(0)));
    }

    @Override
    protected OutboundSecurityResponse syncOutbound(ProviderRequest providerRequest,
                                                    SecurityEnvironment outboundEnv,
                                                    EndpointConfig outboundEndpointConfig) {
        return providerRequest.securityContext()
                .user()
                .map(user -> OutboundSecurityResponse
                        .withHeaders(Map.of("x-user", List.of(user.principal().id()))))
                .orElse(OutboundSecurityResponse.abstain());
    }
}
