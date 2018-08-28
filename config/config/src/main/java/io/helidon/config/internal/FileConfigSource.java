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

import java.io.StringReader;
import java.nio.file.Path;
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
import io.helidon.config.spi.ConfigParser.Content;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.PollingStrategy;

/**
 * {@link ConfigSource} implementation that loads configuration content from a file on a filesystem.
 *
 * @see FileBuilder
 */
public class FileConfigSource extends AbstractParsableConfigSource<byte[]> {

    private static final Logger LOGGER = Logger.getLogger(FileConfigSource.class.getName());
    private static final String PATH_KEY = "path";

    private final Path filePath;

    FileConfigSource(FileBuilder builder, Path filePath) {
        super(builder);

        this.filePath = filePath;
    }

    /**
     * Initializes config source instance from configuration properties.
     * <p>
     * Mandatory {@code properties}, see {@link io.helidon.config.ConfigSources#file(String)}:
     * <ul>
     * <li>{@code path} - type {@link Path}</li>
     * </ul>
     * Optional {@code properties}: see {@link AbstractParsableConfigSource.Builder#init(Config)}.
     *
     * @param metaConfig meta-configuration used to initialize returned config source instance from.
     * @return new instance of config source described by {@code metaConfig}
     * @throws MissingValueException  in case the configuration tree does not contain all expected sub-nodes
     *                                required by the mapper implementation to provide instance of Java type.
     * @throws ConfigMappingException in case the mapper fails to map the (existing) configuration tree represented by the
     *                                supplied configuration node to an instance of a given Java type.
     * @see io.helidon.config.ConfigSources#file(String)
     * @see AbstractParsableConfigSource.Builder#init(Config)
     */
    public static FileConfigSource from(Config metaConfig) throws ConfigMappingException, MissingValueException {
        return (FileConfigSource) new FileBuilder(metaConfig.get(PATH_KEY).as(Path.class))
                .init(metaConfig)
                .build();
    }

    @Override
    protected String uid() {
        return filePath.toString();
    }

    @Override
    protected String getMediaType() {
        return OptionalHelper.from(Optional.ofNullable(super.getMediaType()))
                .or(this::probeContentType)
                .asOptional()
                .orElse(null);
    }

    private Optional<String> probeContentType() {
        return Optional.ofNullable(ConfigHelper.detectContentType(filePath));
    }

    @Override
    protected Optional<byte[]> dataStamp() {
        return Optional.ofNullable(FileSourceHelper.digest(filePath));
    }

    @Override
    protected Content<byte[]> content() throws ConfigException {
        Optional<byte[]> stamp = dataStamp();
        LOGGER.log(Level.FINE, String.format("Getting content from '%s'", filePath));

        return Content.from(new StringReader(FileSourceHelper.safeReadContent(filePath)),
                            getMediaType(),
                            stamp);
    }

    /**
     * File ConfigSource Builder.
     * <p>
     * It allows to configure following properties:
     * <ul>
     * <li>{@code path} - configuration file path;</li>
     * <li>{@code mandatory} - is existence of configuration resource mandatory (by default) or is {@code optional}?</li>
     * <li>{@code media-type} - configuration content media type to be used to look for appropriate {@link ConfigParser};</li>
     * <li>{@code parser} - or directly set {@link ConfigParser} instance to be used to parse the source;</li>
     * </ul>
     * <p>
     * If the File ConfigSource is {@code mandatory} and a {@code file} does not exist
     * then {@link ConfigSource#load} throws {@link ConfigException}.
     * <p>
     * If {@code media-type} not set it tries to guess it from file extension.
     */
    public static final class FileBuilder extends Builder<FileBuilder, Path> {
        private Path path;

        /**
         * Initialize builder.
         *
         * @param path configuration file path
         */
        public FileBuilder(Path path) {
            super(Path.class);

            Objects.requireNonNull(path, "file path cannot be null");

            this.path = path;
        }

        @Override
        protected FileBuilder init(Config metaConfig) {
            return super.init(metaConfig);
        }

        @Override
        protected Path getTarget() {
            return path;
        }

        /**
         * Builds new instance of File ConfigSource.
         * <p>
         * If {@code media-type} not set it tries to guess it from file extension.
         *
         * @return new instance of File ConfigSource.
         */
        public ConfigSource build() {
            return new FileConfigSource(this, path);
        }

        PollingStrategy getPollingStrategyInternal() { //just for testing purposes
            return super.getPollingStrategy();
        }
    }
}
