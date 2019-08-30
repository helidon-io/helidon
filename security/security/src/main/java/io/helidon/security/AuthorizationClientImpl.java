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

package io.helidon.security;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.security.internal.SecurityAuditEvent;

/**
 * Authorizer.
 */
final class AuthorizationClientImpl implements SecurityClient<AuthorizationResponse> {
    private final Security security;
    private final SecurityContextImpl context;
    private final SecurityRequest request;
    private final String providerName;
    private final ProviderRequest providerRequest;

    AuthorizationClientImpl(Security security,
                            SecurityContextImpl context,
                            SecurityRequest request,
                            String providerName) {
        this.security = security;
        this.context = context;
        this.request = request;
        this.providerName = providerName;
        this.providerRequest = new ProviderRequest(context,
                                                   request.resources(),
                                                   request.requestEntity(),
                                                   request.responseEntity());
    }

    @Override
    public CompletionStage<AuthorizationResponse> submit() {
        // TODO ABAC - if annotated with Attribute meta annot, make sure that all are processed
        return security.resolveAtzProvider(providerName)
                .map(providerInstance -> providerInstance.authorize(providerRequest).thenApply(response -> {
                    if (response.status().isSuccess()) {
                        //Audit success
                        context.audit(SecurityAuditEvent.success(
                                AuditEvent.AUTHZ_TYPE_PREFIX + ".authorize",
                                "Provider %s. Request %s. Subject %s")
                                              .addParam(AuditEvent.AuditParam
                                                                .plain("provider", providerInstance.getClass().getName()))
                                              .addParam(AuditEvent.AuditParam.plain("request", this))
                                              .addParam(AuditEvent.AuditParam.plain("subject",
                                                                                    context.user()
                                                                                            .orElse(SecurityContext.ANONYMOUS))));
                    } else {
                        //Audit failure
                        context.audit(SecurityAuditEvent.failure(
                                AuditEvent.AUTHZ_TYPE_PREFIX + ".authorize",
                                "Provider %s, Description %s, Request %s. Subject %s")
                                              .addParam(AuditEvent.AuditParam
                                                                .plain("provider", providerInstance.getClass().getName()))
                                              .addParam(AuditEvent.AuditParam.plain("request", this))
                                              .addParam(AuditEvent.AuditParam.plain("subject",
                                                                                    context.user()
                                                                                            .orElse(SecurityContext.ANONYMOUS)))
                                              .addParam(AuditEvent.AuditParam
                                                                .plain("message", response.description().orElse(null)))
                                              .addParam(AuditEvent.AuditParam
                                                                .plain("exception", response.throwable().orElse(null))));
                    }

                    return response;
                }).exceptionally(throwable -> {
                    //Audit failure
                    context.audit(SecurityAuditEvent.error(
                            AuditEvent.AUTHZ_TYPE_PREFIX + ".authorize",
                            "Provider %s, Description %s, Request %s. Subject %s. %s: %s")
                                          .addParam(AuditEvent.AuditParam
                                                            .plain("provider", providerInstance.getClass().getName()))
                                          .addParam(AuditEvent.AuditParam.plain("description", "Audit failure"))
                                          .addParam(AuditEvent.AuditParam.plain("request", this))
                                          .addParam(AuditEvent.AuditParam.plain("subject",
                                                                                context.user()
                                                                                        .orElse(SecurityContext.ANONYMOUS)))
                                          .addParam(AuditEvent.AuditParam.plain("message", throwable.getMessage()))
                                          .addParam(AuditEvent.AuditParam.plain("exception", throwable)));
                    throw new SecurityException(throwable);
                }))
                .orElse(CompletableFuture.completedFuture(AuthorizationResponse.permit()));
    }
}
