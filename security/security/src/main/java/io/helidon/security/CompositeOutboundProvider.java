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

package io.helidon.security;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import io.helidon.security.spi.OutboundSecurityProvider;

/**
 * A security outbound provider building a result from one or more outbound providers.
 */
final class CompositeOutboundProvider implements OutboundSecurityProvider {
    private final List<OutboundSecurityProvider> providers = new LinkedList<>();

    @SuppressWarnings("unchecked")
    CompositeOutboundProvider(List<OutboundSecurityProvider> providers) {
        this.providers.addAll(providers);
    }

    @Override
    public boolean isOutboundSupported(ProviderRequest providerRequest,
                                       SecurityEnvironment outboundEnv,
                                       EndpointConfig outboundConfig) {
        return providers.stream()
                .anyMatch(provider -> provider.isOutboundSupported(providerRequest, outboundEnv, outboundConfig));
    }

    @Override
    public Collection<Class<? extends Annotation>> supportedAnnotations() {
        Set<Class<? extends Annotation>> result = new HashSet<>();
        providers.forEach(provider -> result.addAll(provider.supportedAnnotations()));
        return result;
    }

    @Override
    public OutboundSecurityResponse outboundSecurity(ProviderRequest providerRequest,
                                                     SecurityEnvironment outboundEnv,
                                                     EndpointConfig outboundConfig) {

        OutboundCall previousCall = new OutboundCall(OutboundSecurityResponse.abstain(),
                                                     providerRequest,
                                                     outboundEnv,
                                                     outboundConfig);

        for (OutboundSecurityProvider provider : providers) {
            if (previousCall.response.status() == SecurityResponse.SecurityStatus.ABSTAIN) {
                // previous call(s) did not care, I don't have to update request
                if (provider.isOutboundSupported(previousCall.inboundContext,
                                                 previousCall.outboundEnv,
                                                 previousCall.outboundConfig)) {
                    OutboundSecurityResponse outboundResponse = provider.outboundSecurity(previousCall.inboundContext,
                                                                                          previousCall.outboundEnv,
                                                                                          previousCall.outboundConfig);
                    SecurityEnvironment nextEnv = updateRequestHeaders(previousCall.outboundEnv, outboundResponse);
                    previousCall = new OutboundCall(outboundResponse,
                                                    previousCall.inboundContext,
                                                    nextEnv,
                                                    previousCall.outboundConfig);
                }
            }
            // construct a new request
            if (previousCall.response.status().isSuccess()) {
                // invoke current
                OutboundSecurityResponse outboundResponse = provider.outboundSecurity(previousCall.inboundContext,
                                                                                      previousCall.outboundEnv,
                                                                                      previousCall.outboundConfig);
                OutboundSecurityResponse prevResponse = previousCall.response;

                // combine
                OutboundSecurityResponse.Builder builder = OutboundSecurityResponse.builder();
                prevResponse.requestHeaders().forEach(builder::requestHeader);
                prevResponse.responseHeaders().forEach(builder::responseHeader);
                outboundResponse.requestHeaders().forEach(builder::requestHeader);
                outboundResponse.responseHeaders().forEach(builder::responseHeader);
                SecurityEnvironment nextEnv = updateRequestHeaders(previousCall.outboundEnv, outboundResponse);

                builder.status(outboundResponse.status());
                previousCall = new OutboundCall(builder.build(),
                                                previousCall.inboundContext,
                                                nextEnv,
                                                previousCall.outboundConfig);
            }
        }

        return previousCall.response;
    }

    private SecurityEnvironment updateRequestHeaders(SecurityEnvironment env, OutboundSecurityResponse response) {
        SecurityEnvironment.Builder builder = env.derive();

        response.requestHeaders().forEach(builder::header);

        return builder.build();
    }

    private static final class OutboundCall {
        private final ProviderRequest inboundContext;
        private final SecurityEnvironment outboundEnv;
        private final EndpointConfig outboundConfig;
        private final OutboundSecurityResponse response;

        private OutboundCall(OutboundSecurityResponse response,
                             ProviderRequest inboundContext,
                             SecurityEnvironment outboundEnv,
                             EndpointConfig outboundConfig) {
            this.response = response;

            this.inboundContext = inboundContext;
            this.outboundEnv = outboundEnv;
            this.outboundConfig = outboundConfig;
        }
    }
}
