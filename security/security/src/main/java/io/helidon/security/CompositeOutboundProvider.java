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

package io.helidon.security;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
    public CompletionStage<OutboundSecurityResponse> outboundSecurity(ProviderRequest providerRequest,
                                                                      SecurityEnvironment outboundEnv,
                                                                      EndpointConfig outboundConfig) {

        CompletionStage<OutboundCall> previous = CompletableFuture
                .completedFuture(new OutboundCall(OutboundSecurityResponse.abstain(),
                                                  providerRequest,
                                                  outboundEnv,
                                                  outboundConfig));

        for (OutboundSecurityProvider provider : providers) {
            previous = previous.thenCompose(call -> {
                if (call.response.getStatus() == SecurityResponse.SecurityStatus.ABSTAIN) {
                    // previous call(s) did not care, I don't have to update request
                    if (provider.isOutboundSupported(call.inboundContext, call.outboundEnv, call.outboundConfig)) {
                        return provider.outboundSecurity(call.inboundContext, call.outboundEnv, call.outboundConfig)
                                .thenApply(response -> {
                                    SecurityEnvironment nextEnv = updateRequestHeaders(call.outboundEnv, response);
                                    return new OutboundCall(response, call.inboundContext, nextEnv, call.outboundConfig);
                                });
                    } else {
                        // just continue with existing result
                        return CompletableFuture.completedFuture(call);
                    }
                }
                // construct a new request
                if (call.response.getStatus().isSuccess()) {
                    // invoke current
                    return provider.outboundSecurity(call.inboundContext, call.outboundEnv, call.outboundConfig)
                            .thenApply(thisResponse -> {
                                OutboundSecurityResponse prevResponse = call.response;

                                // combine
                                OutboundSecurityResponse.Builder builder = OutboundSecurityResponse.builder();
                                prevResponse.getRequestHeaders().forEach(builder::requestHeader);
                                prevResponse.getResponseHeaders().forEach(builder::responseHeader);
                                thisResponse.getRequestHeaders().forEach(builder::requestHeader);
                                thisResponse.getResponseHeaders().forEach(builder::responseHeader);
                                SecurityEnvironment nextEnv = updateRequestHeaders(call.outboundEnv, thisResponse);

                                builder.status(thisResponse.getStatus());
                                return new OutboundCall(builder.build(), call.inboundContext, nextEnv, call.outboundConfig);
                            });
                } else {
                    // just fail (as previous outbound all failed)
                    return CompletableFuture.completedFuture(call);
                }
            });
        }

        return previous.thenApply(outboundCall -> outboundCall.response);
    }

    private SecurityEnvironment updateRequestHeaders(SecurityEnvironment env, OutboundSecurityResponse response) {
        SecurityEnvironment.Builder builder = env.derive();

        response.getRequestHeaders().forEach(builder::header);

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
