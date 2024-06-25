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
import io.helidon.integrations.oci.spi.OciAuthenticationMethod;
import io.helidon.service.registry.Service;

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;

@Service.Provider
@Service.ExternalContracts(AbstractAuthenticationDetailsProvider.class)
class AdpProvider implements Supplier<Optional<AbstractAuthenticationDetailsProvider>> {

    private final LazyValue<Optional<AbstractAuthenticationDetailsProvider>> provider;

    AdpProvider(OciConfig ociConfig, List<OciAuthenticationMethod> atnDetailsProviders) {
        String chosenAtnMethod = ociConfig.authenticationMethod();
        LazyValue<Optional<AbstractAuthenticationDetailsProvider>> providerLazyValue = null;

        if (OciConfigBlueprint.AUTHENTICATION_METHOD_AUTO.equals(chosenAtnMethod)) {
            // auto, chose from existing
            providerLazyValue = LazyValue.create(() -> {
                for (OciAuthenticationMethod atnDetailsProvider : atnDetailsProviders) {
                    Optional<AbstractAuthenticationDetailsProvider> provider = atnDetailsProvider.provider();
                    if (provider.isPresent()) {
                        return provider;
                    }
                }
                return Optional.empty();
            });
        } else {
            List<String> strategies = new ArrayList<>();

            for (OciAuthenticationMethod atnDetailsProvider : atnDetailsProviders) {
                if (chosenAtnMethod.equals(atnDetailsProvider.method())) {
                    providerLazyValue = LazyValue.create(() -> toProvider(atnDetailsProvider, chosenAtnMethod));
                    break;
                }
                strategies.add(atnDetailsProvider.method());
            }

            if (providerLazyValue == null) {
                throw new ConfigException("There is an OCI authentication method chosen: \"" + chosenAtnMethod
                                                  + "\", yet there is not provider that can provide this method. Supported "
                                                  + "methods: " + strategies);
            }
        }

        this.provider = providerLazyValue;
    }

    @Override
    public Optional<AbstractAuthenticationDetailsProvider> get() {
        return provider.get();
    }

    private Optional<AbstractAuthenticationDetailsProvider> toProvider(OciAuthenticationMethod atnDetailsProvider,
                                                                       String chosenMethod) {
        return Optional.of(atnDetailsProvider.provider()
                                   .orElseThrow(() -> new ConfigException(
                                           "Authentication method \"" + chosenMethod + "\" did not provide an "
                                                   + "authentication provider, yet it is requested through configuration.")));
    }
}
