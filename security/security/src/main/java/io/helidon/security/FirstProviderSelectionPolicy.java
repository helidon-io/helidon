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

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import io.helidon.security.spi.OutboundSecurityProvider;
import io.helidon.security.spi.ProviderSelectionPolicy;
import io.helidon.security.spi.SecurityProvider;

/**
 * Selects the first provider by default, finds the named provider
 * for explicit name.
 */
class FirstProviderSelectionPolicy implements ProviderSelectionPolicy {
    private final Providers providers;
    private final List<OutboundSecurityProvider> outboundProviders = new LinkedList<>();

    @SuppressWarnings("unchecked")
    FirstProviderSelectionPolicy(ProviderSelectionPolicy.Providers providers) {
        this.providers = providers;

        providers.getProviders(OutboundSecurityProvider.class)
                .forEach(np -> outboundProviders.add(np.getProvider()));
    }

    @Override
    public <T extends SecurityProvider> Optional<T> selectProvider(Class<T> providerType) {
        List<NamedProvider<T>> providers = this.providers.getProviders(providerType);

        if (providers.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(providers.get(0).getProvider());
        }
    }

    @Override
    public List<OutboundSecurityProvider> selectOutboundProviders() {
        return outboundProviders;
    }

    @Override
    public <T extends SecurityProvider> Optional<T> selectProvider(Class<T> providerType,
                                                                   String requestedName) {
        return this.providers.getProviders(providerType)
                .stream()
                .filter(provider -> provider.getName().equals(requestedName))
                .findFirst()
                .map(NamedProvider::getProvider);
    }
}
