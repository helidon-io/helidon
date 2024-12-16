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

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.integrations.oci.spi.OciAuthenticationMethod;
import io.helidon.service.registry.Service;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SessionTokenAuthenticationDetailsProvider.SessionTokenAuthenticationDetailsProviderBuilder;

/**
 * Session token authentication method, uses the {@link com.oracle.bmc.auth.SessionTokenAuthenticationDetailsProvider}.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 15)
@Service.Provider
class AuthenticationMethodSessionToken implements OciAuthenticationMethod {
    static final String DEFAULT_PROFILE_NAME = "DEFAULT";
    static final String METHOD = "session-token";

    private static final System.Logger LOGGER = System.getLogger(AuthenticationMethodSessionToken.class.getName());

    private final LazyValue<Optional<BasicAuthenticationDetailsProvider>> provider;

    AuthenticationMethodSessionToken(OciConfig config,
                                     Supplier<Optional<ConfigFileReader.ConfigFile>> configFileSupplier,
                                     Supplier<SessionTokenAuthenticationDetailsProviderBuilder> builder) {
        provider = LazyValue.create(() -> createProvider(config, configFileSupplier, builder));
    }

    @Override
    public String method() {
        return METHOD;
    }

    @Override
    public Optional<BasicAuthenticationDetailsProvider> provider() {
        return provider.get();
    }

    private static Optional<BasicAuthenticationDetailsProvider>
    createProvider(OciConfig config,
                   Supplier<Optional<ConfigFileReader.ConfigFile>> configFileSupplier,
                   Supplier<SessionTokenAuthenticationDetailsProviderBuilder> builder) {

        /*
        Session tokens provide is available if either of the following is true:
        - there is authentication.session-token configuration
        - there is an OCI config file, and it contains security_token_file
         */

        Optional<ConfigFileReader.ConfigFile> maybeConfigFile = configFileSupplier.get();
        Optional<SessionTokenMethodConfig> maybeSessionTokenConfig = config.sessionTokenMethodConfig();

        if (hasSecurityToken(maybeConfigFile) || maybeSessionTokenConfig.isPresent()) {
            try {
                return Optional.of(builder.get().build());
            } catch (IOException e) {
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.TRACE, "Cannot create session token authentication provider", e);
                }
                return Optional.empty();
            }
        }

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Session token authentication provider is not configured");
        }
        return Optional.empty();
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static boolean hasSecurityToken(Optional<ConfigFileReader.ConfigFile> maybeConfigFile) {
        if (maybeConfigFile.isPresent()) {
            ConfigFileReader.ConfigFile configFile = maybeConfigFile.get();
            return configFile.get("security_token_file") != null;
        }
        return false;
    }
}
