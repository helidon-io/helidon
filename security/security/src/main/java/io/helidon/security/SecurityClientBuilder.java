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

package io.helidon.security;

import java.util.concurrent.CompletionStage;

import io.helidon.common.Builder;

/**
 * Builder constructing a security client - extends the {@link SecurityRequestBuilder} for convenience.
 *
 * @param <T> Type of response the built client returns
 */
public class SecurityClientBuilder<T extends SecurityResponse>
        extends SecurityRequestBuilder<SecurityClientBuilder<T>>
        implements Builder<SecurityClient<T>> {

    private final SecurityContextImpl context;
    private final Security security;
    private final SecurityClientFactory<T> factory;

    SecurityClientBuilder(Security security,
                          SecurityContextImpl context,
                          SecurityClientFactory<T> factory) {
        super(context);
        this.security = security;
        this.context = context;
        this.factory = factory;
    }

    /**
     * Build an instance of a security client. The client is immutable.
     *
     * @return client instance
     */
    @Override
    public SecurityClient<T> build() {
        return factory.create(security, context, super.buildRequest(), super.providerName());
    }

    /**
     * A shortcut method to build the client and invoke {@link SecurityClient#get()} on it.
     *
     * @return {@link SecurityResponse} of expected type
     */
    public T buildAndGet() {
        return build().get();
    }

    /**
     * A shortcut method to build the client and invoke {@link SecurityClient#submit()} on it.
     *
     * @return {@link CompletionStage} with {@link SecurityResponse} of expected type
     */
    public CompletionStage<T> submit() {
        return build().submit();
    }

    @FunctionalInterface
    interface SecurityClientFactory<U extends SecurityResponse> {
        /**
         * Create a new instance of security client, internal use only.
         *
         * @param security     Security instance
         * @param context      Security context implementation
         * @param request      Security request
         * @param providerName explicit provider name
         * @return A new instance of a security client
         */
        SecurityClient<U> create(Security security,
                                 SecurityContextImpl context,
                                 SecurityRequest request,
                                 String providerName);
    }
}
