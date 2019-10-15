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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.spi.AbstractOverrideSource;
import io.helidon.config.spi.OverrideSource;
import io.helidon.config.spi.PollingStrategy;

/**
 * {@link OverrideSource} implementation that loads override definitions from a resource on a classpath.
 *
 * @see Builder
 */
public class ClasspathOverrideSource extends AbstractOverrideSource<Instant> {
    private static final Logger LOGGER = Logger.getLogger(ClasspathOverrideSource.class.getName());

    private final String resource;

    ClasspathOverrideSource(ClasspathBuilder builder) {
        super(builder);
        String builderResource = builder.resource;

        this.resource = builderResource.startsWith("/")
                ? builderResource.substring(1)
                : builderResource;
    }

    @Override
    protected String uid() {
        return ClasspathSourceHelper.uid(resource);
    }

    @Override
    protected Optional<Instant> dataStamp() {
        return Optional.of(ClasspathSourceHelper.resourceTimestamp(resource));
    }

    @Override
    public Data<OverrideData, Instant> loadData() throws ConfigException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(resource);
        if (inputStream == null) {
            LOGGER.log(Level.FINE,
                       String.format("Error to get %s using %s CONTEXT ClassLoader.", description(), classLoader));
            throw new ConfigException(description() + " does not exist. Used ClassLoader: " + classLoader);
        }
        Instant resourceTimestamp = ClasspathSourceHelper.resourceTimestamp(resource);
        try {
            LOGGER.log(Level.FINE,
                       String.format("Getting content from '%s'. Last modified at %s. Used ClassLoader: %s",
                                     ClasspathSourceHelper.resourcePath(resource), resourceTimestamp, classLoader));
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Error to get resource '" + resource + "' path. Used ClassLoader: " + classLoader, ex);
        }
        try {
            return new Data<>(
                    Optional.of(OverrideData.create(new InputStreamReader(inputStream, StandardCharsets.UTF_8))),
                    Optional.ofNullable(resourceTimestamp)
            );
        } catch (IOException e) {
            throw new ConfigException("Cannot load dta from resource.", e);
        }
    }

    /**
     * Create a new classpath override source from meta configuration, containing {@code resource} key and other options.
     * @param config meta configuration
     * @return a new classpath override source
     */
    public static ClasspathOverrideSource create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Create a new fluent API builder.
     *
     * @return a new builder
     */
    public static ClasspathBuilder builder() {
        return new ClasspathBuilder();
    }

    /**
     * Classpath OverrideSource Builder.
     * <p>
     * It allows to configure following properties:
     * <ul>
     * <li>{@code resource} - override resource name;</li>
     * <li>{@code mandatory} - is existence of override resource mandatory (by default) or is {@code optional}?</li>
     * </ul>
     * <p>
     * If the {@code OverrideSource} is {@code mandatory} and the {@code resource} does not exist
     * then {@link OverrideSource#load} throws {@link ConfigException}.
     */
    public static final class ClasspathBuilder extends Builder<ClasspathBuilder, Path> {

        private String resource;

        /**
         * Initialize builder.
         */
        private ClasspathBuilder() {
            super(Path.class);
        }

        /**
         * Configure the classpath resource to be used as a source.
         *
         * @param resource classpath resource path
         * @return updated builder instance
         */
        public ClasspathBuilder resource(String resource) {
            this.resource = resource;
            return this;
        }

        /**
         * Update builder from meta configuration.
         *
         * @param metaConfig meta configuration to load this override source from
         * @return updated builder instance
         */
        public ClasspathBuilder config(Config metaConfig) {
            metaConfig.get("resource").asString().ifPresent(this::resource);
            return super.config(metaConfig);
        }

        @Override
        protected Path target() {
            Objects.requireNonNull(resource, "resource name cannot be null");
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
         * Builds new instance of Classpath OverrideSource.
         *
         * @return new instance of Classpath OverrideSource.
         */
        @Override
        public ClasspathOverrideSource build() {
            return new ClasspathOverrideSource(this);
        }

        PollingStrategy pollingStrategyInternal() { //just for testing purposes
            return super.pollingStrategy();
        }
    }
}
