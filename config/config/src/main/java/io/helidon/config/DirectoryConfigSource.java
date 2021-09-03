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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import io.helidon.config.spi.ChangeWatcher;
import io.helidon.config.spi.ConfigContent.NodeContent;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.NodeConfigSource;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.spi.WatchableSource;

import static io.helidon.config.FileSourceHelper.lastModifiedTime;
import static java.nio.file.FileVisitOption.FOLLOW_LINKS;

/**
 * {@link ConfigSource} implementation that loads configuration content from a directory on a filesystem.
 */
public class DirectoryConfigSource extends AbstractConfigSource
        implements PollableSource<Instant>,
                   WatchableSource<Path>,
                   NodeConfigSource {

    private static final String PATH_KEY = "path";

    private final Path directoryPath;

    DirectoryConfigSource(Builder builder) {
        super(builder);

        this.directoryPath = builder.path;
    }

    /**
     * Initializes config source instance from configuration properties.
     * <p>
     * Mandatory {@code properties}, see {@link io.helidon.config.ConfigSources#directory(String)}:
     * <ul>
     * <li>{@code path} - type {@link Path}</li>
     * </ul>
     * Optional {@code properties}: see
     * {@link AbstractConfigSourceBuilder#config(Config)}.
     *
     * @param metaConfig meta-configuration used to initialize returned config source instance from.
     * @return new instance of config source described by {@code metaConfig}
     * @throws MissingValueException  in case the configuration tree does not contain all expected sub-nodes
     *                                required by the mapper implementation to provide instance of Java type.
     * @throws ConfigMappingException in case the mapper fails to map the (existing) configuration tree represented by the
     *                                supplied configuration node to an instance of a given Java type.
     * @see io.helidon.config.ConfigSources#directory(String)
     * @see AbstractConfigSourceBuilder#config(Config)
     */
    public static DirectoryConfigSource create(Config metaConfig) throws ConfigMappingException, MissingValueException {
        return builder().config(metaConfig).build();
    }

    /**
     * Create a fluent API builder to construct a directory config source.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected String uid() {
        return directoryPath.toString();
    }

    @Override
    public boolean isModified(Instant stamp) {
        return FileSourceHelper.isModified(directoryPath, stamp);
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
    public Path target() {
        return directoryPath;
    }

    @Override
    public boolean exists() {
        return Files.exists(directoryPath);
    }

    @Override
    public Class<Path> targetType() {
        return Path.class;
    }

    @Override
    public Optional<NodeContent> load() throws ConfigException {
        if (!Files.exists(directoryPath)) {
            return Optional.empty();
        }
        try {
            ConfigNode.ObjectNode.Builder objectNodeRoot = ConfigNode.ObjectNode.builder();

            Files.walk(directoryPath, 1, FOLLOW_LINKS)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        String content = FileSourceHelper.safeReadContent(path);
                        objectNodeRoot.addValue(path.getFileName().toString(), content);
                    });


            NodeContent.Builder builder = NodeContent.builder()
                    .node(objectNodeRoot.build());

            lastModifiedTime(directoryPath).ifPresent(builder::stamp);

            return Optional.of(builder.build());
        } catch (ConfigException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ConfigException("Configuration at directory '" + directoryPath + "' is not accessible.", ex);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * A fluent API builder for {@link io.helidon.config.DirectoryConfigSource}.
     */
    public static final class Builder extends AbstractConfigSourceBuilder<Builder, Path>
                implements PollableSource.Builder<Builder>,
                           WatchableSource.Builder<Builder, Path>,
                           io.helidon.common.Builder<DirectoryConfigSource> {
        private Path path;

        /**
         * Initialize builder.
         */
        private Builder() {
        }

        /**
         * Builds new instance of Directory ConfigSource.
         *
         * @return new instance of File ConfigSource.
         */
        @Override
        public DirectoryConfigSource build() {
            if (null == path) {
                throw new IllegalArgumentException("path must be defined");
            }
            return new DirectoryConfigSource(this);
        }

        /**
         * {@inheritDoc}
         * <ul>
         *     <li>{@code path} - directory path</li>
         * </ul>
         * @param metaConfig configuration properties used to configure a builder instance.
         * @return updated builder instance
         */
        @Override
        public Builder config(Config metaConfig) {
            metaConfig.get(PATH_KEY).as(Path.class).ifPresent(this::path);
            return super.config(metaConfig);
        }

        /**
         * Configuration directory path.
         *
         * @param path directory
         * @return updated builder instance
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
