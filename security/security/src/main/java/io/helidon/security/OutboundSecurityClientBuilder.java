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

import java.util.concurrent.CompletionStage;

import io.helidon.common.Builder;

/**
 * Builder constructing a security client - extends the {@link SecurityRequestBuilder} for convenience.
 */
public class OutboundSecurityClientBuilder extends SecurityRequestBuilder<OutboundSecurityClientBuilder>
        implements Builder<SecurityClient<OutboundSecurityResponse>> {

    private final SecurityContextImpl context;
    private final Security security;

    private SecurityEnvironment outboundEnvironment;
    private EndpointConfig outboundEndpointConfig;

    OutboundSecurityClientBuilder(Security security,
                                  SecurityContextImpl context) {
        super(context);
        this.security = security;
        this.context = context;
    }

    /**
     * Build an instance of a security client. The client is immutable.
     *
     * @return client instance
     */
    @Override
    public SecurityClient<OutboundSecurityResponse> build() {
        return new OutboundSecurityClientImpl(security,
                                              context,
                                              super.buildRequest(),
                                              super.getProviderName(),
                                              outboundEnvironment,
                                              outboundEndpointConfig);
    }

    /**
     * Configure outbound environment (path, headers, URI etc.) for this outbound call.
     *
     * @param outboundEnvironment environment to use for outbound call
     * @return updated builder instance
     */
    public OutboundSecurityClientBuilder outboundEnvironment(SecurityEnvironment outboundEnvironment) {
        this.outboundEnvironment = outboundEnvironment;
        return this;
    }

    /**
     * Configure outbound environment (path, headers, URI etc.) for this outbound call.
     *
     * @param outboundEnvironment environment builder to use for outbound call
     * @return updated builder instance
     */
    public OutboundSecurityClientBuilder outboundEnvironment(Builder<SecurityEnvironment> outboundEnvironment) {
        return outboundEnvironment(outboundEnvironment.build());
    }

    /**
     * Configure outbound endpoint config (annotations, config, attributes etc.) for this outbound call.
     *
     * @param outboundEndpointConfig endpoint config to use for outbound call
     * @return updated builder instance
     */
    public OutboundSecurityClientBuilder outboundEndpointConfig(EndpointConfig outboundEndpointConfig) {
        this.outboundEndpointConfig = outboundEndpointConfig;
        return this;
    }

    /**
     * Configure outbound endpoint config (annotations, config, attributes etc.) for this outbound call.
     *
     * @param outboundEndpointConfig endpoint config builder to use for outbound call
     * @return updated builder instance
     */
    public OutboundSecurityClientBuilder outboundEndpointConfig(Builder<EndpointConfig> outboundEndpointConfig) {
        return outboundEndpointConfig(outboundEndpointConfig.build());
    }

    /**
     * A shortcut method to build the client and invoke {@link SecurityClient#get()} on it.
     *
     * @return {@link SecurityResponse} of expected type
     */
    public OutboundSecurityResponse buildAndGet() {
        return build().get();
    }

    /**
     * A shortcut method to build the client and invoke {@link SecurityClient#submit()} on it.
     *
     * @return {@link CompletionStage} with {@link SecurityResponse} of expected type
     */
    public CompletionStage<OutboundSecurityResponse> submit() {
        return build().submit();
    }
}
