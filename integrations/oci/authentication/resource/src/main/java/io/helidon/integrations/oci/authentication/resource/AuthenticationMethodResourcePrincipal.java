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

package io.helidon.integrations.oci.authentication.resource;

import java.lang.System.Logger.Level;
import java.util.Optional;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.integrations.oci.spi.OciAuthenticationMethod;
import io.helidon.service.registry.Service;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider.ResourcePrincipalAuthenticationDetailsProviderBuilder;

/**
 * Resource principal authentication method, uses the {@link com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider}.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 30)
@Service.Provider
class AuthenticationMethodResourcePrincipal implements OciAuthenticationMethod {
    private static final System.Logger LOGGER = System.getLogger(AuthenticationMethodResourcePrincipal.class.getName());
    private static final String RESOURCE_PRINCIPAL_VERSION_ENV_VAR = "OCI_RESOURCE_PRINCIPAL_VERSION";
    private static final String METHOD = "resource-principal";



    private final LazyValue<Optional<BasicAuthenticationDetailsProvider>> provider;

    AuthenticationMethodResourcePrincipal(ResourcePrincipalAuthenticationDetailsProviderBuilder builder) {
        provider = createProvider(builder);
    }

    @Override
    public String method() {
        return METHOD;
    }

    @Override
    public Optional<BasicAuthenticationDetailsProvider> provider() {
        return provider.get();
    }

    private static LazyValue<Optional<BasicAuthenticationDetailsProvider>>
    createProvider(ResourcePrincipalAuthenticationDetailsProviderBuilder builder) {

        return LazyValue.create(() -> {
            // https://github.com/oracle/oci-java-sdk/blob/v2.19.0/bmc-common/src/main/java/com/oracle/bmc/auth/ResourcePrincipalAuthenticationDetailsProvider.java#L246-L251
            if (System.getenv(RESOURCE_PRINCIPAL_VERSION_ENV_VAR) == null) {
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.TRACE, "Environment variable \"" + RESOURCE_PRINCIPAL_VERSION_ENV_VAR
                            + "\" is not set, resource principal cannot be used.");
                }
                return Optional.empty();
            }
            return Optional.of(builder.build());
        });
    }
}
