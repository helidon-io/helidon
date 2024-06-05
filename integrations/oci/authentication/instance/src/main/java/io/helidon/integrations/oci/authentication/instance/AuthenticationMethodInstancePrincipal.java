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

package io.helidon.integrations.oci.authentication.instance;

import java.util.Optional;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.integrations.oci.HelidonOci;
import io.helidon.integrations.oci.OciConfig;
import io.helidon.integrations.oci.spi.OciAtnMethod;
import io.helidon.service.registry.Service;

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider.InstancePrincipalsAuthenticationDetailsProviderBuilder;

/**
 * Instance principal authentication method, uses the
 * {@link com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider}.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 40)
@Service.Provider
class AuthenticationMethodInstancePrincipal implements OciAtnMethod {
    private static final System.Logger LOGGER = System.getLogger(AuthenticationMethodInstancePrincipal.class.getName());
    private static final String METHOD = "instance-principal";

    private final LazyValue<Optional<AbstractAuthenticationDetailsProvider>> provider;

    AuthenticationMethodInstancePrincipal(OciConfig config,
                                          InstancePrincipalsAuthenticationDetailsProviderBuilder builder) {
        provider = createProvider(config, builder);
    }

    @Override
    public String method() {
        return METHOD;
    }

    @Override
    public Optional<AbstractAuthenticationDetailsProvider> provider() {
        return provider.get();
    }

    private static LazyValue<Optional<AbstractAuthenticationDetailsProvider>>
    createProvider(OciConfig config,
                   InstancePrincipalsAuthenticationDetailsProviderBuilder builder) {
        return LazyValue.create(() -> {
            if (HelidonOci.imdsAvailable(config)) {
                return Optional.of(builder.build());
            }
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                LOGGER.log(System.Logger.Level.TRACE, "OCI Metadata service is not available, "
                        + "instance principal cannot be used.");
            }
            return Optional.empty();
        });
    }

}
