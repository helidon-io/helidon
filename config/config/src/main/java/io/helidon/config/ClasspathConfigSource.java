/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import io.helidon.common.media.type.MediaTypes;
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
     * Optional {@code properties}: see {@link AbstractParsableConfigSource.Builder#config(io.helidon.config.Config)}.
     *
     * @param metaConfig meta-configuration used to initialize returned config source instance from.
     * @return new instance of config source described by {@code metaConfig}
     * @throws MissingValueException  in case the configuration tree does not contain all expected sub-nodes
     *                                required by the mapper implementation to provide instance of Java type.
     * @throws ConfigMappingException in case the mapper fails to map the (existing) configuration tree represented by the
     *                                supplied configuration node to an instance of a given Java type.
     * @see io.helidon.config.ConfigSources#classpath(String)
     * @see AbstractParsableConfigSource.Builder#config(Config)
     */
    public static ClasspathConfigSource create(Config metaConfig) throws ConfigMappingException, MissingValueException {
        return builder()
                .config(metaConfig)
                .build();
    }

    /**
     * Create a new fluent API builder for classpath config source.
     *
     * @return a new builder instance
     */
    public static ClasspathBuilder builder() {
        return new ClasspathBuilder();
    }

    @Override
    protected String uid() {
        return ClasspathSourceHelper.uid(resource);
    }

    @Override
    protected String mediaType() {
        return Optional.ofNullable(super.mediaType())
                .or(this::probeContentType)
                .orElse(null);
    }

    private Optional<String> probeContentType() {
        return MediaTypes.detectType(resource);
    }

    @Override
    protected Optional<Instant> dataStamp() {
        return Optional.ofNullable(ClasspathSourceHelper.resourceTimestamp(resource));
    }

    @Override
    protected ConfigParser.Content<Instant> content() throws ConfigException {
        return ClasspathSourceHelper.content(resource,
                                             description(),
                                             (inputStreamReader, instant) -> ConfigParser.Content.create(inputStreamReader,
                                                                                                         mediaType(),
                                                                                                         instant));
    }

    @Override
    public String toString() {
        return "classpath: " + resource;
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
    public static final class ClasspathBuilder extends Builder<ClasspathBuilder, Path, ClasspathConfigSource> {

        private String resource;

        /**
         * Initialize builder.
         */
        private ClasspathBuilder() {
            super(Path.class);
        }

        /**
         * Configure the classpath resource to load the configuration from.
         *
         * @param resource resource on classpath
         * @return updated builder instance
         */
        public ClasspathBuilder resource(String resource) {
            this.resource = resource;
            return this;
        }

        /**
         * {@inheritDoc}
         * <ul>
         *     <li>{@code resource} - the classpath resource to load</li>
         * </ul>
         * @param metaConfig configuration properties used to configure a builder instance.
         * @return updated builder instance
         */
        @Override
        public ClasspathBuilder config(Config metaConfig) {
            metaConfig.get(RESOURCE_KEY).asString().ifPresent(this::resource);
            return super.config(metaConfig);
        }

        @Override
        protected Path target() {
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
        public ClasspathConfigSource build() {
            if (null == resource) {
                throw new IllegalArgumentException("resource must be defined");
            }
            return new ClasspathConfigSource(this, resource);
        }

        PollingStrategy pollingStrategyInternal() { //just for testing purposes
            return super.pollingStrategy();
        }
    }
}
