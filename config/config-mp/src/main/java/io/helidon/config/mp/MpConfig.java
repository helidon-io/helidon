/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.config.mp;

import java.util.Iterator;

import io.helidon.config.ConfigSources;
import io.helidon.config.OverrideSources;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Utilities for Helidon MicroProfile Config implementation.
 */
public final class MpConfig {
    private MpConfig() {
    }

    /**
     * This method allows use to use Helidon Config on top of an MP config.
     * There is a limitation - the converters configured with MP config will not be available, unless
     * the implementation is coming from Helidon.
     * <p>
     * If you want to use the Helidon {@link io.helidon.config.Config} API instead of the MicroProfile
     * {@link org.eclipse.microprofile.config.Config} one, this method will create a Helidon config
     * instance that is based on the provided configuration instance.
     *
     * @param mpConfig MP Config instance
     * @return a new Helidon config using only the mpConfig as its config source
     */
    public static io.helidon.config.Config toHelidonConfig(Config mpConfig) {
        if (mpConfig instanceof io.helidon.config.Config) {
            return (io.helidon.config.Config) mpConfig;
        }

        // If the mpConfig is based on an SE config (such as when we use meta configuration)pom.xml
        // we must reuse that se config instance
        Iterator<ConfigSource> configSources = mpConfig.getConfigSources().iterator();
        ConfigSource first = configSources.hasNext() ? configSources.next() : null;
        if (!configSources.hasNext() && first instanceof MpHelidonConfigSource) {
            // we only have Helidon SE config as a source - let's just use it
            return ((MpHelidonConfigSource) first).unwrap();
        }

        // we use Helidon SE config to handle object mapping (and possible other mappers on classpath)
        io.helidon.config.Config mapper = io.helidon.config.Config.builder()
                .sources(ConfigSources.empty())
                .overrides(OverrideSources.empty())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableParserServices()
                .disableFilterServices()
                .disableCaching()
                .disableValueResolving()
                .changesExecutor(command -> {
                })
                .build();

        return new SeConfig(mapper, mpConfig);
    }
}
