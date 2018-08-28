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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.security.internal.SecurityAuditEvent;
import io.helidon.security.spi.OutboundSecurityProvider;

/**
 * Outbound security builder and executor.
 *
 * See {@link #submit()}.
 */
final class OutboundSecurityClientImpl implements SecurityClient<OutboundSecurityResponse> {
    private final Security security;
    private final SecurityContextImpl context;
    private final String providerName;
    private final ProviderRequest providerRequest;
    private final SecurityEnvironment outboundEnv;
    private final EndpointConfig outboundEpConfig;

    OutboundSecurityClientImpl(Security security,
                               SecurityContextImpl context,
                               SecurityRequest request,
                               String providerName,
                               SecurityEnvironment outboundEnvironment,
                               EndpointConfig outboundEndpointConfig) {

        this.security = security;
        this.context = context;
        this.providerName = providerName;
        this.providerRequest = new ProviderRequest(context,
                                                   request.getResources(),
                                                   request.getRequestEntity(),
                                                   request.getResponseEntity());
        this.outboundEnv = outboundEnvironment;
        this.outboundEpConfig = outboundEndpointConfig;
    }

    @Override
    public CompletionStage<OutboundSecurityResponse> submit() {
        OutboundSecurityProvider providerInstance = findProvider();

        if (null == providerInstance) {
            return CompletableFuture.completedFuture(OutboundSecurityResponse.empty());
        }

        return providerInstance.outboundSecurity(providerRequest, outboundEnv, outboundEpConfig).thenApply(response -> {
            if (response.getStatus().isSuccess()) {
                //Audit success
                context.audit(SecurityAuditEvent.success(AuditEvent.OUTBOUND_TYPE_PREFIX + ".outbound",
                                                         "Provider %s. Request %s. Subject %s")
                                      .addParam(AuditEvent.AuditParam
                                                        .plain("provider", providerInstance.getClass().getName()))
                                      .addParam(AuditEvent.AuditParam.plain("request", this))
                                      .addParam(AuditEvent.AuditParam
                                                        .plain("subject", context.getUser().orElse(SecurityContext.ANONYMOUS))));
            } else {
                context.audit(SecurityAuditEvent.failure(AuditEvent.OUTBOUND_TYPE_PREFIX + ".outbound",
                                                         "Provider %s, Description %s, Request %s. Subject %s")
                                      .addParam(AuditEvent.AuditParam
                                                        .plain("provider", providerInstance.getClass().getName()))
                                      .addParam(AuditEvent.AuditParam.plain("request", this))
                                      .addParam(AuditEvent.AuditParam
                                                        .plain("message", response.getDescription().orElse(null)))
                                      .addParam(AuditEvent.AuditParam
                                                        .plain("exception", response.getThrowable().orElse(null)))
                                      .addParam(AuditEvent.AuditParam
                                                        .plain("subject", context.getUser().orElse(SecurityContext.ANONYMOUS))));
            }

            return response;
        }).exceptionally(e -> {
            context.audit(SecurityAuditEvent.error(AuditEvent.OUTBOUND_TYPE_PREFIX + ".outbound",
                                                   "Provider %s, Description %s, Request %s. Subject %s")
                                  .addParam(AuditEvent.AuditParam.plain("provider", providerInstance.getClass().getName()))
                                  .addParam(AuditEvent.AuditParam.plain("request", this))
                                  .addParam(AuditEvent.AuditParam.plain("message", e.getMessage()))
                                  .addParam(AuditEvent.AuditParam.plain("exception", e))
                                  .addParam(AuditEvent.AuditParam
                                                    .plain("subject", context.getUser().orElse(SecurityContext.ANONYMOUS))));
            throw new SecurityException("Failed to process security", e);
        });
    }

    private OutboundSecurityProvider findProvider() {
        return security.resolveOutboundProvider(providerName)
                .stream()
                .filter(p -> p.isOutboundSupported(providerRequest, outboundEnv, outboundEpConfig))
                .findFirst()
                .orElse(null);
    }
}
