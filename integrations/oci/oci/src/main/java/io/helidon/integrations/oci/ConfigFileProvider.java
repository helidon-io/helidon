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
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.service.registry.Service;

import com.oracle.bmc.ConfigFileReader;

@Service.Provider
@Service.ExternalContracts(ConfigFileReader.ConfigFile.class)
class ConfigFileProvider implements Supplier<Optional<ConfigFileReader.ConfigFile>> {
    static final String DEFAULT_PROFILE_NAME = "DEFAULT";

    private static final System.Logger LOGGER = System.getLogger(ConfigFileProvider.class.getName());

    private final LazyValue<Optional<ConfigFileReader.ConfigFile>> value;

    ConfigFileProvider(OciConfig config) {
        value = LazyValue.create(() -> findConfigFile(config));
    }

    @Override
    public Optional<ConfigFileReader.ConfigFile> get() {
        return value.get();
    }

    private Optional<ConfigFileReader.ConfigFile> findConfigFile(OciConfig config) {
        var atnMethodConfig = config.configFileMethodConfig();
        // there are two options to override - the path to config file, and the profile
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
            return Optional.of(configFile);
        } catch (IOException e) {
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                LOGGER.log(System.Logger.Level.TRACE,
                           "Cannot parse config file. Location: " + configFilePath + ", profile: " + profile,
                           e);
            }
            return Optional.empty();
        }
    }
}
