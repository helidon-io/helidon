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

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.integrations.oci.spi.OciAtnMethod;
import io.helidon.service.registry.Service;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;

/**
 * Config file based authentication method, uses the {@link com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider}.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 20)
@Service.Provider
class AuthenticationMethodConfigFile implements OciAtnMethod {
    static final String DEFAULT_PROFILE_NAME = "DEFAULT";
    static final String METHOD = "config-file";

    private static final System.Logger LOGGER = System.getLogger(AuthenticationMethodConfigFile.class.getName());

    private final LazyValue<Optional<AbstractAuthenticationDetailsProvider>> provider;

    AuthenticationMethodConfigFile(OciConfig config) {
        provider = createProvider(config);
    }

    @Override
    public String method() {
        return METHOD;
    }

    @Override
    public Optional<AbstractAuthenticationDetailsProvider> provider() {
        return provider.get();
    }

    private static LazyValue<Optional<AbstractAuthenticationDetailsProvider>> createProvider(OciConfig config) {
        return LazyValue.create(() -> {
            // there are two options to override - the path to config file, and the profile
            var atnMethodConfig = config.configFileMethodConfig();
            String profile = atnMethodConfig.map(ConfigFileMethodConfigBlueprint::profile)
                    .orElse(DEFAULT_PROFILE_NAME);
            String configFilePath = atnMethodConfig.flatMap(ConfigFileMethodConfigBlueprint::path)
                    .orElse(null);

            try {
                ConfigFileReader.ConfigFile configFile;
                if (configFilePath == null) {
                    configFile = ConfigFileReader.parseDefault(profile);
                } else {
                    configFile = ConfigFileReader.parse(configFilePath, profile);
                }
                return Optional.of(new ConfigFileAuthenticationDetailsProvider(configFile));
            } catch (IOException e) {
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.TRACE, "Cannot parse config file. Location: " + configFilePath + ", profile: " + profile, e);
                }
                return Optional.empty();
            }
        });
    }
}
