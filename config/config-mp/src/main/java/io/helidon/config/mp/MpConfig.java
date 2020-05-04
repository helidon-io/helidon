/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.helidon.config.ConfigSources;

import org.eclipse.microprofile.config.Config;

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
    @SuppressWarnings("unchecked")
    public static io.helidon.config.Config toHelidonConfig(Config mpConfig) {
        if (mpConfig instanceof io.helidon.config.Config) {
            return (io.helidon.config.Config) mpConfig;
        }

        io.helidon.config.Config.Builder builder = io.helidon.config.Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableMapperServices()
                .disableCaching()
                .disableParserServices()
                .disableFilterServices();

        if (mpConfig instanceof MpConfigImpl) {
            ((MpConfigImpl) mpConfig).converters()
                    .forEach((clazz, converter) -> {
                        Class<Object> cl = (Class<Object>) clazz;
                        builder.addStringMapper(cl, converter::convert);
                    });
        }

        Map<String, String> allConfig = new HashMap<>();
        mpConfig.getPropertyNames()
                .forEach(it -> {
                    // covering the condition where a config key disappears between getting the property names and requesting
                    // the value
                    Optional<String> optionalValue = mpConfig.getOptionalValue(it, String.class);
                    optionalValue.ifPresent(value -> allConfig.put(it, value));
                });

        return builder.addSource(ConfigSources.create(allConfig))
                .build();
    }
}
