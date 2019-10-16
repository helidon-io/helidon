/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.spi.AbstractOverrideSource;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.OverrideSource;
import io.helidon.config.spi.PollingStrategy;

/**
 * {@link OverrideSource} implementation that loads override definitions from a file on a filesystem.
 *
 * @see FileBuilder
 */
public class FileOverrideSource extends AbstractOverrideSource<byte[]> {

    private static final Logger LOGGER = Logger.getLogger(FileOverrideSource.class.getName());

    private final Path filePath;

    FileOverrideSource(FileBuilder builder) {
        super(builder);

        this.filePath = builder.path;
    }

    @Override
    protected String uid() {
        return filePath.toString();
    }

    @Override
    protected Optional<byte[]> dataStamp() {
        return Optional.ofNullable(FileSourceHelper.digest(filePath));
    }

    @Override
    protected Data<OverrideData, byte[]> loadData() throws ConfigException {
        Optional<byte[]> digest = dataStamp();
        LOGGER.log(Level.FINE, String.format("Getting content from '%s'.", filePath));

        OverrideData overrideData = OverrideSource.OverrideData
                .create(new StringReader(FileSourceHelper.safeReadContent(filePath)));
        return new Data<>(Optional.of(overrideData), digest);

    }

    /**
     * Create a new file override source from meta configuration.
     *
     * @param metaConfig meta configuration containing the {@code path} and other configuration options
     * @return a new file override source
     */
    public static FileOverrideSource create(Config metaConfig) {
        return builder().config(metaConfig).build();
    }

    /**
     * Create a new fluent API builder.
     *
     * @return builder to create new instances of file override source
     */
    public static FileBuilder builder() {
        return new FileBuilder();
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
         */
        private FileBuilder() {
            super(Path.class);
        }

        /**
         * Configure path to look for the source.
         *
         * @param path file path
         * @return updated builder
         */
        public FileBuilder path(Path path) {
            this.path = path;
            return this;
        }

        @Override
        public FileBuilder config(Config metaConfig) {
            metaConfig.get("path").as(Path.class).ifPresent(this::path);

            return super.config(metaConfig);
        }

        @Override
        protected Path target() {
            return path;
        }

        /**
         * Builds new instance of File ConfigSource.
         * <p>
         * If {@code media-type} not set it tries to guess it from file extension.
         *
         * @return new instance of File ConfigSource.
         */
        @Override
        public FileOverrideSource build() {
            Objects.requireNonNull(path, "file path cannot be null");
            return new FileOverrideSource(this);
        }

        PollingStrategy pollingStrategyInternal() { //just for testing purposes
            return super.pollingStrategy();
        }
    }
}
