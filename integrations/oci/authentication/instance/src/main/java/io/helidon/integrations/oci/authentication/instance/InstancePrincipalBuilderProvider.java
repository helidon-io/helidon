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

package io.helidon.integrations.oci.authentication.instance;

import java.net.URI;
import java.util.function.Supplier;

import io.helidon.integrations.oci.OciConfig;
import io.helidon.service.registry.Service;

import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider.InstancePrincipalsAuthenticationDetailsProviderBuilder;

/**
 * Instance principal builder provider, uses the
 * {@link InstancePrincipalsAuthenticationDetailsProviderBuilder}.
 */
@Service.Provider
class InstancePrincipalBuilderProvider implements Supplier<InstancePrincipalsAuthenticationDetailsProviderBuilder> {
    private final OciConfig config;

    InstancePrincipalBuilderProvider(OciConfig config) {
        this.config = config;
    }

    @Override
    public InstancePrincipalsAuthenticationDetailsProviderBuilder get() {
        var builder = InstancePrincipalsAuthenticationDetailsProvider.builder()
                .timeoutForEachRetry((int) config.authenticationTimeout().toMillis());

        config.imdsDetectRetries()
                .ifPresent(builder::detectEndpointRetries);
        config.federationEndpoint()
                .map(URI::toString)
                .ifPresent(builder::federationEndpoint);
        config.imdsBaseUri()
                .map(URI::toString)
                .ifPresent(builder::metadataBaseUrl);

        return builder;
    }

}
