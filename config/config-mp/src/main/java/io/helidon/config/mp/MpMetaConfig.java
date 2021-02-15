/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigSource;

class MpMetaConfig {
    private static final String META_CONFIG_ENV_VAR = "HELIDON_MP_META_CONFIG";
    static final String META_CONFIG_SYSTEM_PROPERTY = "io.helidon.config.mp.meta-config";

    private static final Logger LOGGER = Logger.getLogger(MpMetaConfig.class.getName());

    static Optional<Config> metaConfig() {
        return findMetaConfig()
                .map(it -> Config.builder()
                        .disableValueResolving()
                        .disableKeyResolving()
                        .disableFilterServices()
                        .disableEnvironmentVariablesSource()
                        .disableSystemPropertiesSource()
                        .disableMapperServices()
                        .addSource(it)
                        .build());
    }

    private static Optional<ConfigSource> findMetaConfig() {
        String fileName = System.getenv(META_CONFIG_ENV_VAR);
        if (fileName == null) {
            fileName = System.getProperty(META_CONFIG_SYSTEM_PROPERTY);
        }
        Optional<ConfigSource> found;
        if (fileName == null) {
            found = Optional.empty();
        } else {
            found = findMetaConfig(fileName);
        }

        return found.or(() -> findMetaConfig("mp-meta-config.yaml"))
                .or(() -> findMetaConfig("mp-meta-config.properties"));
    }

    private static Optional<ConfigSource> findMetaConfig(String fileName) {
        // file system, then classpath
        return findFile(fileName)
                .or(() -> findClasspath(MpMetaConfig.class.getClassLoader(), fileName));
    }

    private static Optional<ConfigSource> findFile(String name) {
        Path path = Paths.get(name);
        if (Files.exists(path) && Files.isReadable(path) && !Files.isDirectory(path)) {
            LOGGER.info("Found MP meta configuration file: " + path.toAbsolutePath());
            return Optional.of(ConfigSources.file(path).build());
        }
        return Optional.empty();
    }

    private static Optional<ConfigSource> findClasspath(ClassLoader cl, String name) {
        // so it is a classpath resource?
        URL resource = cl.getResource(name);
        if (null != resource) {
            LOGGER.fine(() -> "Found MP meta configuration resource: " + resource.getPath());
            return Optional.of(ConfigSources.classpath(name).build());
        }
        return Optional.empty();
    }

    static class MetaConfigSource implements org.eclipse.microprofile.config.spi.ConfigSource {
        private final org.eclipse.microprofile.config.spi.ConfigSource delegate;
        private final int ordinal;
        private final String name;

        MetaConfigSource(Builder builder) {
            this.delegate = builder.delegate;
            this.ordinal = builder.ordinal;
            this.name = builder.sourceName;
        }

        static Builder builder() {
            return new Builder();
        }

        @Override
        public Map<String, String> getProperties() {
            return delegate.getProperties();
        }

        @Override
        public String getValue(String propertyName) {
            return delegate.getValue(propertyName);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Set<String> getPropertyNames() {
            return delegate.getPropertyNames();
        }

        @Override
        public int getOrdinal() {
            return ordinal;
        }

        @Override
        public String toString() {
            return name + "(" + ordinal + ")" + ", wrapping: " + delegate.toString();
        }

        static class Builder implements io.helidon.common.Builder<MetaConfigSource> {
            private org.eclipse.microprofile.config.spi.ConfigSource delegate;
            private int ordinal;
            private boolean ordinalNotSet = true;
            private String sourceName;
            private boolean nameNotSet = true;

            private Builder() {
            }

            @Override
            public MetaConfigSource build() {
                return new MetaConfigSource(this);
            }

            Builder delegate(org.eclipse.microprofile.config.spi.ConfigSource delegate) {
                this.delegate = delegate;
                if (this.ordinalNotSet) {
                    this.ordinal = delegate.getOrdinal();
                }
                if (this.nameNotSet) {
                    this.sourceName = delegate.getName();
                }
                return this;
            }

            Builder ordinal(int ordinal) {
                this.ordinal = ordinal;
                this.ordinalNotSet = false;
                return this;
            }

            Builder name(String sourceName) {
                this.sourceName = sourceName;
                this.nameNotSet = false;
                return this;
            }
        }
    }
}
