/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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
import io.helidon.security.spi.AuthenticationProvider;

/**
 * Authenticator.
 */
final class AuthenticationClientImpl implements SecurityClient<AuthenticationResponse> {
    private final Security security;
    private final SecurityContextImpl context;
    private final SecurityRequest request;
    private final String providerName;

    AuthenticationClientImpl(Security security,
                             SecurityContextImpl context,
                             SecurityRequest request,
                             String providerName) {
        this.security = security;
        this.context = context;
        this.request = request;
        this.providerName = providerName;
    }

    @Override
    public CompletionStage<AuthenticationResponse> submit() {
        return security.resolveAtnProvider(providerName)
                .map(this::authenticate)
                .orElseThrow(() -> new SecurityException("Could not find any authentication provider. Security is not "
                                                                 + "configured"))
                .thenCompose(authenticationResponse -> {
                    CompletionStage<AuthenticationResponse> response = mapSubject(
                            authenticationResponse);
                    return response;
                });

    }

    private CompletionStage<AuthenticationResponse> mapSubject(AuthenticationResponse prevResponse) {
        ProviderRequest providerRequest = new ProviderRequest(context,
                                                              request.resources());

        if (prevResponse.status() == SecurityResponse.SecurityStatus.SUCCESS) {
            return security.subjectMapper()
                    .map(mapper -> mapper.map(providerRequest, prevResponse))
                    .orElseGet(() -> CompletableFuture.completedFuture(prevResponse))
                    .thenApply(newResponse -> {
                        // intentionally checking for instance equality, as that means we are guaranteed no changes
                        if (newResponse == prevResponse) {
                            // no changes were done, response as is
                            return prevResponse;
                        } else {
                            newResponse.user().ifPresent(context::setUser);
                            newResponse.service().ifPresent(context::setService);
                            return newResponse;
                        }
                    });
        } else {
            return CompletableFuture.completedFuture(prevResponse);
        }
    }

    private CompletionStage<AuthenticationResponse> authenticate(AuthenticationProvider providerInstance) {
        // prepare request to provider
        ProviderRequest providerRequest = new ProviderRequest(context,
                                                              request.resources());

        return providerInstance.authenticate(providerRequest).thenApply(response -> {
            if (response.status().isSuccess()) {
                response.user()
                        .ifPresent(context::setUser);

                response.service()
                        .ifPresent(context::setService);

                //Audit success
                context.audit(SecurityAuditEvent
                                      .success(
                                              AuditEvent.AUTHN_TYPE_PREFIX + ".authenticate",
                                              "Path %s. Provider %s. Subject %s")
                                      .addParam(AuditEvent.AuditParam.plain("path", providerRequest.env().path()))
                                      .addParam(AuditEvent.AuditParam
                                                        .plain("provider", providerInstance.getClass().getName()))
                                      .addParam(AuditEvent.AuditParam.plain("subject", response.user())));
                return response;
            }

            //Audit failure
            SecurityAuditEvent event = SecurityAuditEvent
                    .failure(AuditEvent.AUTHN_TYPE_PREFIX + ".authenticate", "Provider %s. Message: %s")
                    .addParam(AuditEvent.AuditParam.plain("provider", providerInstance.getClass().getName()))
                    .addParam(AuditEvent.AuditParam.plain("message", response.description().orElse(null)));

            response.throwable()
                    .map(e -> event.addParam(AuditEvent.AuditParam.plain("exception", response.throwable())));
            context.audit(event);
            return response;
        }).exceptionally(throwable -> {
            //Audit failure
            context.audit(SecurityAuditEvent
                                  .error(AuditEvent.AUTHN_TYPE_PREFIX + ".authenticate", "Provider %s. Message: %s")
                                  .addParam(AuditEvent.AuditParam
                                                    .plain("provider", providerInstance.getClass().getName()))
                                  .addParam(AuditEvent.AuditParam.plain("message", throwable.getMessage()))
                                  .addParam(AuditEvent.AuditParam.plain("exception", throwable)));

            throw new SecurityException(throwable);
        });

    }
}
