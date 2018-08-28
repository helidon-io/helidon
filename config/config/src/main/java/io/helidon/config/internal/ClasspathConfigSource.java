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

package io.helidon.config.internal;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.OptionalHelper;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigHelper;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.MissingValueException;
import io.helidon.config.spi.AbstractParsableConfigSource;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.PollingStrategy;

/**
 * {@link ConfigSource} implementation that loads configuration content from a resource on a classpath.
 *
 * @see AbstractParsableConfigSource.Builder
 */
public class ClasspathConfigSource extends AbstractParsableConfigSource<Instant> {

    private static final Logger LOGGER = Logger.getLogger(ClasspathConfigSource.class.getName());

    private static final String RESOURCE_KEY = "resource";

    private final String resource;

    ClasspathConfigSource(ClasspathBuilder builder, String resource) {
        super(builder);

        this.resource = resource.startsWith("/")
                ? resource.substring(1)
                : resource;
    }

    /**
     * Initializes config source instance from configuration properties.
     * <p>
     * Mandatory {@code properties}, see {@link io.helidon.config.ConfigSources#classpath(String)}:
     * <ul>
     * <li>{@code resource} - type {@code String}</li>
     * </ul>
     * Optional {@code properties}: see {@link AbstractParsableConfigSource.Builder#init(Config)}.
     *
     * @param metaConfig meta-configuration used to initialize returned config source instance from.
     * @return new instance of config source described by {@code metaConfig}
     * @throws MissingValueException  in case the configuration tree does not contain all expected sub-nodes
     *                                required by the mapper implementation to provide instance of Java type.
     * @throws ConfigMappingException in case the mapper fails to map the (existing) configuration tree represented by the
     *                                supplied configuration node to an instance of a given Java type.
     * @see io.helidon.config.ConfigSources#classpath(String)
     * @see AbstractParsableConfigSource.Builder#init(Config)
     */
    public static ClasspathConfigSource from(Config metaConfig) throws ConfigMappingException, MissingValueException {
        return (ClasspathConfigSource) new ClasspathBuilder(metaConfig.get(RESOURCE_KEY).asString())
                .init(metaConfig)
                .build();
    }

    @Override
    protected String uid() {
        return ClasspathSourceHelper.uid(resource);
    }

    @Override
    protected String getMediaType() {
        return OptionalHelper.from(Optional.ofNullable(super.getMediaType()))
                .or(this::probeContentType)
                .asOptional()
                .orElse(null);
    }

    private Optional<String> probeContentType() {
        return Optional.ofNullable(ConfigHelper.detectContentType(Paths.get(resource)));
    }

    @Override
    protected Optional<Instant> dataStamp() {
        return Optional.ofNullable(ClasspathSourceHelper.resourceTimestamp(resource));
    }

    @Override
    protected ConfigParser.Content<Instant> content() throws ConfigException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(resource);
        if (inputStream == null) {
            LOGGER.log(Level.FINE,
                       String.format("Error to get %s using %s CONTEXT ClassLoader.", description(), classLoader));
            throw new ConfigException(description() + " does not exist. Used ClassLoader: " + classLoader);
        }
        Optional<Instant> resourceTimestamp = Optional.ofNullable(ClasspathSourceHelper.resourceTimestamp(resource));
        try {
            LOGGER.log(Level.FINE,
                       String.format("Getting content from '%s'. Last modified at %s. Used ClassLoader: %s",
                                     ClasspathSourceHelper.resourcePath(resource), resourceTimestamp, classLoader));
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Error to get resource '" + resource + "' path. Used ClassLoader: " + classLoader, ex);
        }
        return ConfigParser.Content.from(new InputStreamReader(inputStream, StandardCharsets.UTF_8),
                                         getMediaType(),
                                         resourceTimestamp);
    }

    /**
     * Classpath ConfigSource Builder.
     * <p>
     * It allows to configure following properties:
     * <ul>
     * <li>{@code resource} - configuration resource name;</li>
     * <li>{@code mandatory} - is existence of configuration resource mandatory (by default) or is {@code optional}?</li>
     * <li>{@code media-type} - configuration content media type to be used to look for appropriate {@link ConfigParser};</li>
     * <li>{@code parser} - or directly set {@link ConfigParser} instance to be used to parse the source;</li>
     * </ul>
     * <p>
     * If the ConfigSource is {@code mandatory} and a {@code resource} does not exist
     * then {@link ConfigSource#load} throws {@link ConfigException}.
     * <p>
     * If {@code media-type} not set it tries to guess it from resource extension.
     */
    public static final class ClasspathBuilder extends Builder<ClasspathBuilder, Path> {

        private String resource;

        /**
         * Initialize builder.
         *
         * @param resource classpath resource name
         */
        public ClasspathBuilder(String resource) {
            super(Path.class);

            Objects.requireNonNull(resource, "resource name cannot be null");

            this.resource = resource;
        }

        @Override
        protected ClasspathBuilder init(Config metaConfig) {
            return super.init(metaConfig);
        }

        @Override
        protected Path getTarget() {
            try {
                Path resourcePath = ClasspathSourceHelper.resourcePath(resource);
                if (resourcePath != null) {
                    return resourcePath;
                } else {
                    throw new ConfigException("Could not find a filesystem path for resource '" + resource + "'.");
                }
            } catch (Exception ex) {
                throw new ConfigException("Could not find a filesystem path for resource '" + resource + "'.", ex);
            }
        }

        /**
         * Builds new instance of Classpath ConfigSource.
         * <p>
         * If {@code media-type} not set it tries to guess it from resource extension before parsing.
         *
         * @return new instance of Classpath ConfigSource.
         */
        @Override
        public ConfigSource build() {
            return new ClasspathConfigSource(this, resource);
        }

        PollingStrategy getPollingStrategyInternal() { //just for testing purposes
            return super.getPollingStrategy();
        }
    }
}
