/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.config.ConfigException;
import io.helidon.integrations.oci.spi.OciAtnStrategy;
import io.helidon.service.registry.Service;

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;

@Service.Provider
@Service.ExternalContracts(AbstractAuthenticationDetailsProvider.class)
class AtnDetailsProvider implements Supplier<AbstractAuthenticationDetailsProvider> {

    private final LazyValue<AbstractAuthenticationDetailsProvider> provider;

    AtnDetailsProvider(OciConfig ociConfig, List<OciAtnStrategy> atnDetailsProviders) {
        String chosenStrategy = ociConfig.atnStrategy();
        LazyValue<AbstractAuthenticationDetailsProvider> providerLazyValue = null;

        if (OciConfigBlueprint.STRATEGY_AUTO.equals(chosenStrategy)) {
            // auto, chose from existing
            providerLazyValue = LazyValue.create(() -> {
                for (OciAtnStrategy atnDetailsProvider : atnDetailsProviders) {
                    Optional<AbstractAuthenticationDetailsProvider> provider = atnDetailsProvider.provider();
                    if (provider.isPresent()) {
                        return provider.get();
                    }
                }
                return null;
            });
        } else {
            List<String> strategies = new ArrayList<>();

            for (OciAtnStrategy atnDetailsProvider : atnDetailsProviders) {
                if (chosenStrategy.equals(atnDetailsProvider.strategy())) {
                    providerLazyValue = LazyValue.create(() -> atnDetailsProvider.provider()
                            .orElseThrow(() -> new ConfigException("Strategy \""
                                                                           + chosenStrategy
                                                                           + "\" did not provide an authentication provider, "
                                                                           + "yet it is requested through configuration.")));
                    break;
                }
                strategies.add(atnDetailsProvider.strategy());
            }

            if (providerLazyValue == null) {
                throw new ConfigException("There is a strategy chosen for OCI authentication: \"" + chosenStrategy
                                                  + "\", yet there is not provider that can provide that strategy. Supported "
                                                  + "strategies: " + strategies);
            }
        }

        this.provider = providerLazyValue;
    }

    @Override
    public AbstractAuthenticationDetailsProvider get() {
        return provider.get();
    }
}
