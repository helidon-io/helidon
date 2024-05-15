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

import java.lang.System.Logger.Level;
import java.util.Optional;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.integrations.oci.spi.OciAtnStrategy;
import io.helidon.service.registry.Service;

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;

/**
 * Resource authentication strategy, uses the {@link com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider}.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 30)
@Service.Provider
class AtnStrategyResourcePrincipal implements OciAtnStrategy {
    static final String RESOURCE_PRINCIPAL_VERSION_ENV_VAR = "OCI_RESOURCE_PRINCIPAL_VERSION";
    static final String STRATEGY = "resource-principal";

    private static final System.Logger LOGGER = System.getLogger(AtnStrategyResourcePrincipal.class.getName());

    private final LazyValue<Optional<AbstractAuthenticationDetailsProvider>> provider;

    AtnStrategyResourcePrincipal(OciConfig config) {
        provider = createProvider(config);
    }

    @Override
    public String strategy() {
        return STRATEGY;
    }

    @Override
    public Optional<AbstractAuthenticationDetailsProvider> provider() {
        return provider.get();
    }

    private static LazyValue<Optional<AbstractAuthenticationDetailsProvider>> createProvider(OciConfig config) {
        return LazyValue.create(() -> {
            // https://github.com/oracle/oci-java-sdk/blob/v2.19.0/bmc-common/src/main/java/com/oracle/bmc/auth/ResourcePrincipalAuthenticationDetailsProvider.java#L246-L251
            if (System.getenv(RESOURCE_PRINCIPAL_VERSION_ENV_VAR) == null) {
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.TRACE, "Environment variable \"" + RESOURCE_PRINCIPAL_VERSION_ENV_VAR
                            + "\" is not set, resource principal cannot be used.");
                }
                return Optional.empty();
            }
            return Optional.of(ResourcePrincipalAuthenticationDetailsProvider.builder().build());
        });
    }
}
