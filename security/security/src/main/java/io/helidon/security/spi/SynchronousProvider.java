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

package io.helidon.security.spi;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.security.AuthenticationResponse;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityEnvironment;

/**
 * A provider base for synchronous providers.
 * This class doesn't (intentionally) implement any of the interfaces, as we leave it up to you, provider developer
 * to choose which of them suits your needs.
 * Just override the method for your provider and let the magic begin.
 * As java does not allow for multiple inheritance of classes, this is an easy way to implement methods
 * for all SPI interfaces without forcing each provider to handle all types of security.
 */
public abstract class SynchronousProvider implements SecurityProvider {
    /**
     * Authenticate a request.
     * This may be just resolving headers (tokens) or full authentication (basic auth).
     * Do not throw exception for normal processing (e.g. invalid credentials; you may throw an exception in case of
     * misconfiguration).
     *
     * This method will be invoked for inbound requests ONLY.
     *
     * @param providerRequest context of this security enforcement/validation
     * @return AuthenticationResponse, including the subject for successful authentications
     * @see AuthenticationResponse#success(io.helidon.security.Subject)
     */
    public final CompletionStage<AuthenticationResponse> authenticate(ProviderRequest providerRequest) {
        return CompletableFuture
                .supplyAsync(() -> syncAuthenticate(providerRequest),
                             providerRequest.getContext().getExecutorService());
    }

    /**
     * Authorize a request based on configuration.
     *
     * Authorization cannot be optional. If this method is called, it should always attempt to authorize the current request.
     * This method will be invoked for inbound requests ONLY.
     *
     * @param providerRequest context of this security enforcement/validation
     * @return response that either permits, denies or abstains from decision
     * @see AuthorizationResponse#permit()
     */
    public final CompletionStage<AuthorizationResponse> authorize(ProviderRequest providerRequest) {
        return CompletableFuture.supplyAsync(() -> syncAuthorize(providerRequest),
                                             providerRequest.getContext().getExecutorService());
    }

    /**
     * Creates necessary updates to headers and entity needed for outbound
     * security (e.g. identity propagation, s2s security etc.).
     * This method will be invoked for outbound requests ONLY.
     *
     * @param providerRequest context with environment, subject(s) etc. that was received
     * @param outboundEnv     environment for outbound call
     * @param outboundConfig  outbound endpoint configuration
     * @return response with generated headers and other possible configuration
     * @see OutboundSecurityResponse#builder()
     */
    public final CompletionStage<OutboundSecurityResponse> outboundSecurity(ProviderRequest providerRequest,
                                                                            SecurityEnvironment outboundEnv,
                                                                            EndpointConfig outboundConfig) {
        return CompletableFuture.supplyAsync(() -> syncOutbound(providerRequest, outboundEnv, outboundConfig),
                                             providerRequest.getContext().getExecutorService());
    }

    /**
     * Synchronous authentication.
     *
     * @param providerRequest context with environment, subject(s) etc.
     * @return authentication response
     * @see AuthenticationProvider#authenticate(ProviderRequest)
     */
    protected AuthenticationResponse syncAuthenticate(ProviderRequest providerRequest) {
        throw new UnsupportedOperationException("You must override syncAuthenticate method in your provider implementation "
                                                        + "to act as an AuthenticationProvider");
    }

    /**
     * Synchronous authorization.
     *
     * @param providerRequest context with environment, subject(s) etc.
     * @return authorization response
     * @see AuthorizationProvider#authorize(ProviderRequest)
     */
    protected AuthorizationResponse syncAuthorize(ProviderRequest providerRequest) {
        throw new UnsupportedOperationException("You must override syncAuthorize method in your provider implementation "
                                                        + "to act as an AuthorizationProvider");
    }

    /**
     * Synchronous outbound security.
     *
     * @param providerRequest        context with environment, subject(s) etc.
     * @param outboundEnv            environment of this outbound call
     * @param outboundEndpointConfig endpoint config for outbound call
     * @return outbound response
     * @see OutboundSecurityProvider#outboundSecurity(ProviderRequest, SecurityEnvironment, EndpointConfig)
     * @see OutboundSecurityProvider#isOutboundSupported(ProviderRequest, SecurityEnvironment, EndpointConfig)
     */
    protected OutboundSecurityResponse syncOutbound(ProviderRequest providerRequest,
                                                    SecurityEnvironment outboundEnv,
                                                    EndpointConfig outboundEndpointConfig) {
        throw new UnsupportedOperationException("You must override syncOutbound method in your provider implementation "
                                                        + "to act as an OutboundProvider");
    }

}
