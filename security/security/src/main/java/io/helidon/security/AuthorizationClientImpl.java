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

import io.helidon.security.internal.SecurityAuditEvent;
import io.helidon.security.spi.AuthorizationProvider;

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
                                                   request.resources());
    }

    @Override
    public AuthorizationResponse submit() {
        // TODO ABAC - if annotated with Attribute meta annot, make sure that all are processed
        return security.resolveAtzProvider(providerName)
                .map(this::authorize)
                .orElse(AuthorizationResponse.permit());
    }

    private AuthorizationResponse authorize(AuthorizationProvider providerInstance) {
        AuthorizationResponse response = providerInstance.authorize(providerRequest);
        try {
            if (response.status().isSuccess()) {
                //Audit success
                context.audit(SecurityAuditEvent.success(
                                AuditEvent.AUTHZ_TYPE_PREFIX + ".authorize",
                                "Path %s. Provider %s. Subject %s")
                                      .addParam(AuditEvent.AuditParam.plain("path", providerRequest.env().path()))
                                      .addParam(AuditEvent.AuditParam
                                                        .plain("provider", providerInstance.getClass().getName()))
                                      .addParam(AuditEvent.AuditParam.plain("subject",
                                                                            context.user())));
            } else {
                //Audit failure
                context.audit(SecurityAuditEvent.failure(
                                AuditEvent.AUTHZ_TYPE_PREFIX + ".authorize",
                                "Path %s. Provider %s, Description %s, Request %s. Subject %s")
                                      .addParam(AuditEvent.AuditParam.plain("path", providerRequest.env().path()))
                                      .addParam(AuditEvent.AuditParam
                                                        .plain("provider", providerInstance.getClass().getName()))
                                      .addParam(AuditEvent.AuditParam.plain("request", this))
                                      .addParam(AuditEvent.AuditParam.plain("subject", context.user()))
                                      .addParam(AuditEvent.AuditParam
                                                        .plain("message", response.description().orElse(null)))
                                      .addParam(AuditEvent.AuditParam
                                                        .plain("exception", response.throwable().orElse(null))));
            }

            return response;
        } catch (Exception e) {
            //Audit failure
            context.audit(SecurityAuditEvent.error(
                            AuditEvent.AUTHZ_TYPE_PREFIX + ".authorize",
                            "Path %s. Provider %s, Description %s, Request %s. Subject %s. %s: %s")
                                  .addParam(AuditEvent.AuditParam.plain("path", providerRequest.env().path()))
                                  .addParam(AuditEvent.AuditParam
                                                    .plain("provider", providerInstance.getClass().getName()))
                                  .addParam(AuditEvent.AuditParam.plain("description", "Audit failure"))
                                  .addParam(AuditEvent.AuditParam.plain("request", this))
                                  .addParam(AuditEvent.AuditParam.plain("subject", context.user()))
                                  .addParam(AuditEvent.AuditParam.plain("message", e.getMessage()))
                                  .addParam(AuditEvent.AuditParam.plain("exception", e)));
            throw new SecurityException(e);
        }
    }
}
