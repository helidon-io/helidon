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

import java.util.List;
import java.util.Optional;

import io.helidon.security.NamedProvider;

/**
 * A policy that selects provider to use.
 * To support loading from class name, this class must have a public constructor
 * that accepts {@link Providers} as a single parameter, or a public constructor that
 * accepts {@link Providers} and {@link io.helidon.config.Config} if you want your instance to be configurable.
 * To support configuring through a builder pattern, you must provide to security
 * builder a function that accepts {@link Providers} and returns an instance of this interface,
 * which could be the constructor itself.
 */
public interface ProviderSelectionPolicy {
    /**
     * Select a provider instance of the type defined that this policy has configured as the default.
     *
     * @param providerType type of provider (one of {@link AuthenticationProvider}, {@link AuthorizationProvider})
     * @param <T>          type of provider
     * @return security provider instance
     */
    <T extends SecurityProvider> Optional<T> selectProvider(Class<T> providerType);

    /**
     * Specific method for outbound providers, as we have an option to choose the first outbound provider that matches
     * the current request.
     *
     * @return list of outbound provider to choose from (may be empty)
     */
    List<OutboundSecurityProvider> selectOutboundProviders();

    /**
     * Select a provider instance of the type defined that this policy finds for the requested name.
     *
     * @param providerType  type of provider (one of {@link AuthenticationProvider}, {@link AuthorizationProvider} or {@link
     *                      OutboundSecurityProvider})
     * @param requestedName explicit provider name to find
     * @param <T>           type of provider
     * @return security provider instance
     */
    <T extends SecurityProvider> Optional<T> selectProvider(Class<T> providerType, String requestedName);

    /**
     * Interface that is passed to a constructor of a {@link ProviderSelectionPolicy} implementation to supply all configured
     * providers from security.
     */
    interface Providers {
        /**
         * Get a list of named providers based on provider type.
         *
         * @param providerType Type of provider (one of {@link AuthenticationProvider}, {@link AuthorizationProvider} or {@link
         *                     OutboundSecurityProvider})
         * @param <T>          type of provider
         * @return List of providers that match the type, or empty list if none found
         */
        <T extends SecurityProvider> List<NamedProvider<T>> getProviders(Class<T> providerType);
    }
}
