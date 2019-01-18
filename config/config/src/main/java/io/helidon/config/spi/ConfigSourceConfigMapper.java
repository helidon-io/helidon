/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.spi;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

import io.helidon.common.OptionalHelper;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigMappers;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.ConfigSources;
import io.helidon.config.MissingValueException;
import io.helidon.config.internal.ClasspathConfigSource;
import io.helidon.config.internal.DirectoryConfigSource;
import io.helidon.config.internal.FileConfigSource;
import io.helidon.config.internal.UrlConfigSource;

/**
 * Mapper to convert meta-configuration to a {@link ConfigSource} instance.
 */
class ConfigSourceConfigMapper implements Function<Config, ConfigSource> {

    private static final String META_CONFIG_SOURCES_PROPERTIES = "META-INF/resources/meta-config-sources.properties";

    private static final String PROPERTIES_KEY = "properties";
    private static final String TYPE_KEY = "type";
    private static final String CLASS_KEY = "class";
    private static final String KEY_KEY = "key";

    private static final String SYSTEM_PROPERTIES_TYPE = "system-properties";
    private static final String ENVIRONMENT_VARIABLES_TYPE = "environment-variables";
    private static final String PREFIXED_TYPE = "prefixed";
    private static final String CLASSPATH_TYPE = "classpath";
    private static final String FILE_TYPE = "file";
    private static final String DIRECTORY_TYPE = "directory";
    private static final String URL_TYPE = "url";

    /**
     * Maps custom source `type` to custom `class`.
     */
    private final Map<String, String> customSources;

    ConfigSourceConfigMapper() {
        customSources = initCustomSources();
    }

    private static Map<String, String> initCustomSources() {
        try {
            Properties properties = new Properties();
            Enumeration<URL> e = Thread.currentThread().getContextClassLoader()
                    .getResources(META_CONFIG_SOURCES_PROPERTIES);

            while (e.hasMoreElements()) {
                URL resource = e.nextElement();
                try (InputStream is = resource.openStream()) {
                    properties.load(is);
                }
            }
            Map<String, String> providers = new HashMap<>();
            for (String type : properties.stringPropertyNames()) {
                providers.put(type, properties.getProperty(type));
            }
            return providers;
        } catch (IOException ex) {
            throw new ConfigException("Loading of " + META_CONFIG_SOURCES_PROPERTIES
                                              + " resources has failed with exception.", ex);
        }
    }

    static ConfigSourceConfigMapper instance() {
        return SingletonHolder.INSTANCE;
    }

    @Override
    public ConfigSource apply(Config config) throws ConfigMappingException, MissingValueException {
        Config properties = config.get(PROPERTIES_KEY) // use properties config node
                .asNode()
                .orElse(Config.empty(config)); // or empty config node

        return OptionalHelper.from(config.get(TYPE_KEY)
                                           .asString() // `type` is specified
                                           .flatMap(type -> OptionalHelper
                                                   .from(builtin(type, properties)) // return built-in source
                                                   .or(() -> providers(type, properties))
                                                   .asOptional())) // or use sources - custom type to class mapping
                .or(() -> config.get(CLASS_KEY)
                        .as(Class.class) // `class` is specified
                        .flatMap(clazz -> custom(clazz, properties))) // return custom source
                .asOptional()
                .orElseThrow(() -> new ConfigMappingException(config.key(), "Uncompleted source configuration."));
    }

    private Optional<ConfigSource> builtin(String type, Config properties) {
        final ConfigSource configSource;

        switch (type) {
        case SYSTEM_PROPERTIES_TYPE:
            configSource = ConfigSources.systemProperties();
            break;
        case ENVIRONMENT_VARIABLES_TYPE:
            configSource = ConfigSources.environmentVariables();
            break;
        case PREFIXED_TYPE:
            configSource = ConfigSources.prefixed(properties.get(KEY_KEY).asString().orElse(""),
                                                  properties.as(ConfigSource::create).get());
            break;
        case CLASSPATH_TYPE:
            configSource = properties.as(ClasspathConfigSource::create).get();
            break;
        case FILE_TYPE:
            configSource = properties.as(FileConfigSource::create).get();
            break;
        case DIRECTORY_TYPE:
            configSource = properties.as(DirectoryConfigSource::create).get();
            break;
        case URL_TYPE:
            configSource = properties.as(UrlConfigSource::create).get();
            break;
        default:
            configSource = null;
        }
        return Optional.ofNullable(configSource);
    }

    private Optional<ConfigSource> providers(String type, Config properties) {
        return Optional.ofNullable(customSources.get(type))
                .map(ConfigMappers::toClass)
                .flatMap(clazz -> custom(clazz, properties));
    }

    private Optional<ConfigSource> custom(Class<?> clazz, Config properties) {
        Object source = properties.as(clazz).get();

        if (source instanceof ConfigSource) {
            return Optional.of((ConfigSource) source);
        }

        throw new ConfigException("Failed to process configuration metadata, configured class " + clazz.getName() + " does "
                                          + "not implement ConfigSource");
    }

    /**
     * Singleton holder for {@link ConfigSourceConfigMapper}.
     */
    static final class SingletonHolder {
        private static final ConfigSourceConfigMapper INSTANCE = new ConfigSourceConfigMapper();

        private SingletonHolder() {
        }
    }

}
