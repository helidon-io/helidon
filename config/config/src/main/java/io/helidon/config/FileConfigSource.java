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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.FileSourceHelper.DataAndDigest;
import io.helidon.config.spi.ChangeWatcher;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.ParsableSource;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.spi.WatchableSource;

/**
 * {@link ConfigSource} implementation that loads configuration content from a file on a filesystem.
 *
 * @see io.helidon.config.FileConfigSource.Builder
 */
public class FileConfigSource extends AbstractConfigSource
        implements WatchableSource<Path>, ParsableSource, PollableSource<byte[]> {

    private static final Logger LOGGER = Logger.getLogger(FileConfigSource.class.getName());
    private static final String PATH_KEY = "path";

    private final Path filePath;

    /**
     * Create a new file config source.
     *
     * @param builder builder with configured path and other options of this source
     */
    FileConfigSource(Builder builder) {
        super(builder);

        this.filePath = builder.path;
    }

    /**
     * Initializes config source instance from configuration properties.
     * <p>
     * Mandatory {@code properties}, see {@link io.helidon.config.ConfigSources#file(String)}:
     * <ul>
     * <li>{@code path} - type {@link Path}</li>
     * </ul>
     * Optional {@code properties}: see {@link AbstractConfigSourceBuilder#config(Config)}.
     *
     * @param metaConfig meta-configuration used to initialize returned config source instance from.
     * @return new instance of config source described by {@code metaConfig}
     * @throws MissingValueException  in case the configuration tree does not contain all expected sub-nodes
     *                                required by the mapper implementation to provide instance of Java type.
     * @throws ConfigMappingException in case the mapper fails to map the (existing) configuration tree represented by the
     *                                supplied configuration node to an instance of a given Java type.
     * @see io.helidon.config.ConfigSources#file(String)
     * @see AbstractConfigSourceBuilder#config(Config)
     */
    public static FileConfigSource create(Config metaConfig) throws ConfigMappingException, MissingValueException {
        return FileConfigSource.builder()
                .config(metaConfig)
                .build();
    }

    /**
     * Get a builder instance to create a new config source.
     * @return a fluent API builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected String uid() {
        return filePath.toString();
    }

    @Override
    public boolean isModified(byte[] stamp) {
        return FileSourceHelper.isModified(filePath, stamp);
    }

    @Override
    public Path target() {
        return filePath;
    }

    @Override
    public Optional<ConfigParser.Content> load() throws ConfigException {
        LOGGER.fine(() -> String.format("Getting content from '%s'", filePath));

        // now we need to create all the necessary steps in one go, to make sure the digest matches the file
        Optional<DataAndDigest> dataAndDigest = FileSourceHelper.readDataAndDigest(filePath);

        if (dataAndDigest.isEmpty()) {
            return Optional.empty();
        }

        DataAndDigest dad = dataAndDigest.get();
        InputStream dataStream = new ByteArrayInputStream(dad.data());

        /*
         * Build the content
         */
        var builder = ConfigParser.Content.builder()
                .stamp(dad.digest())
                .data(dataStream);

        MediaTypes.detectType(filePath).ifPresent(builder::mediaType);

        return Optional.of(builder.build());
    }

    @Override
    public Optional<ConfigParser> parser() {
        return super.parser();
    }

    @Override
    public Optional<String> mediaType() {
        return super.mediaType();
    }

    @Override
    public Optional<PollingStrategy> pollingStrategy() {
        return super.pollingStrategy();
    }

    @Override
    public Optional<ChangeWatcher<Object>> changeWatcher() {
        return super.changeWatcher();
    }

    @Override
    public boolean exists() {
        return Files.exists(filePath);
    }

    /**
     * File ConfigSource Builder.
     * <p>
     * It allows to configure following properties:
     * <ul>
     * <li>{@code path} - configuration file path;</li>
     * <li>{@code optional} - is existence of configuration resource optional, or mandatory (by default)?</li>
     * <li>{@code media-type} - configuration content media type to be used to look for appropriate {@link ConfigParser};</li>
     * <li>{@code parser} - or directly set {@link ConfigParser} instance to be used to parse the source;</li>
     * </ul>
     * <p>
     * If {@code media-type} not set it tries to guess it from file extension.
     */
    public static final class Builder extends AbstractConfigSourceBuilder<Builder, Path>
            implements PollableSource.Builder<Builder>,
                       WatchableSource.Builder<Builder, Path>,
                       ParsableSource.Builder<Builder>,
                       io.helidon.common.Builder<FileConfigSource> {

        private Path path;

        private Builder() {
        }

        /**
         * Configure the path to read configuration from (mandatory).
         *
         * @param path path of a file to use
         * @return updated builder instance
         */
        public Builder path(Path path) {
            this.path = path;
            return this;
        }

        /**
         * {@inheritDoc}
         * <ul>
         * <li>{@code path} - path to the file containing the configuration</li>
         * </ul>
         *
         * @param metaConfig configuration properties used to configure a builder instance.
         * @return modified builder instance
         */
        @Override
        public Builder config(Config metaConfig) {
            metaConfig.get(PATH_KEY).as(Path.class).ifPresent(this::path);
            return super.config(metaConfig);
        }

        @Override
        public Builder parser(ConfigParser parser) {
            return super.parser(parser);
        }

        @Override
        public Builder mediaType(String mediaType) {
            return super.mediaType(mediaType);
        }

        @Override
        public Builder changeWatcher(ChangeWatcher<Path> changeWatcher) {
            return super.changeWatcher(changeWatcher);
        }

        @Override
        public Builder pollingStrategy(PollingStrategy pollingStrategy) {
            return super.pollingStrategy(pollingStrategy);
        }

        /**
         * Builds new instance of File ConfigSource.
         * <p>
         * If {@code media-type} not set it tries to guess it from file extension.
         *
         * @return new instance of File ConfigSource.
         */
        @Override
        public FileConfigSource build() {
            if (null == path) {
                throw new IllegalArgumentException("File path cannot be null");
            }
            return new FileConfigSource(this);
        }
    }
}
