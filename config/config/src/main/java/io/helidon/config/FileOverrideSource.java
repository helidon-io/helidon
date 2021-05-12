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

package io.helidon.config;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.spi.ChangeWatcher;
import io.helidon.config.spi.ConfigContent;
import io.helidon.config.spi.OverrideSource;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.spi.WatchableSource;

/**
 * {@link OverrideSource} implementation that loads override definitions from a file on a filesystem.
 *
 * @see FileOverrideSource.Builder
 */
public final class FileOverrideSource extends AbstractSource
        implements OverrideSource, PollableSource<byte[]>, WatchableSource<Path> {

    private static final Logger LOGGER = Logger.getLogger(FileOverrideSource.class.getName());

    private final Path filePath;

    FileOverrideSource(Builder builder) {
        super(builder);

        this.filePath = builder.path;
    }

    @Override
    protected String uid() {
        return filePath.toString();
    }

    @Override
    public boolean exists() {
        return Files.exists(filePath);
    }

    @Override
    public Optional<ConfigContent.OverrideContent> load() throws ConfigException {
        LOGGER.log(Level.FINE, String.format("Getting content from '%s'.", filePath));

        return FileSourceHelper.readDataAndDigest(filePath)
                .map(dad -> ConfigContent.OverrideContent.builder()
                        .data(OverrideData.create(new InputStreamReader(new ByteArrayInputStream(dad.data()),
                                                                        StandardCharsets.UTF_8)))
                        .stamp(dad.digest())
                        .build());
    }

    @Override
    public boolean isModified(byte[] stamp) {
        return FileSourceHelper.isModified(filePath, stamp);
    }

    @Override
    public Optional<PollingStrategy> pollingStrategy() {
        return super.pollingStrategy();
    }

    @Override
    public Path target() {
        return filePath;
    }

    @Override
    public Optional<ChangeWatcher<Object>> changeWatcher() {
        return super.changeWatcher();
    }

    @Override
    public Class<Path> targetType() {
        return Path.class;
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
     * Create a new builder.
     *
     * @return builder to create new instances of file override source
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * File OverrideSource Builder.
     * <p>
     * It allows to configure following properties:
     * <ul>
     * <li>{@code path} - configuration file path;</li>
     * <li>{@code mandatory} - is existence of configuration resource mandatory (by default) or is {@code optional}?</li>
     * </ul>
     */
    public static final class Builder extends AbstractSourceBuilder<Builder, Path>
            implements PollableSource.Builder<Builder>,
                       WatchableSource.Builder<Builder, Path>,
                       io.helidon.common.Builder<FileOverrideSource> {

        private Path path;

        /**
         * Initialize builder.
         */
        private Builder() {
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

        @Override
        public Builder config(Config metaConfig) {
            metaConfig.get("path").as(Path.class).ifPresent(this::path);

            return super.config(metaConfig);
        }

        /**
         * Configure path to look for the source.
         *
         * @param path file path
         * @return updated builder
         */
        public Builder path(Path path) {
            this.path = path;
            return this;
        }

        @Override
        public Builder changeWatcher(ChangeWatcher<Path> changeWatcher) {
            return super.changeWatcher(changeWatcher);
        }

        @Override
        public Builder pollingStrategy(PollingStrategy pollingStrategy) {
            return super.pollingStrategy(pollingStrategy);
        }
    }
}
