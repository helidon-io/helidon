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

package io.helidon.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import io.helidon.config.internal.FileSourceHelper;
import io.helidon.config.spi.AbstractConfigSource;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigSource;

import static java.nio.file.FileVisitOption.FOLLOW_LINKS;

/**
 * {@link ConfigSource} implementation that loads configuration content from a directory on a filesystem.
 *
 * @see io.helidon.config.spi.AbstractSource.Builder
 */
public class DirectoryConfigSource extends AbstractConfigSource<Instant> {

    private static final String PATH_KEY = "path";

    private final Path directoryPath;

    DirectoryConfigSource(DirectoryBuilder builder, Path directoryPath) {
        super(builder);

        this.directoryPath = directoryPath;
    }

    /**
     * Initializes config source instance from configuration properties.
     * <p>
     * Mandatory {@code properties}, see {@link io.helidon.config.ConfigSources#directory(String)}:
     * <ul>
     * <li>{@code path} - type {@link Path}</li>
     * </ul>
     * Optional {@code properties}: see
     * {@link io.helidon.config.spi.AbstractParsableConfigSource.Builder#config(Config)}.
     *
     * @param metaConfig meta-configuration used to initialize returned config source instance from.
     * @return new instance of config source described by {@code metaConfig}
     * @throws MissingValueException  in case the configuration tree does not contain all expected sub-nodes
     *                                required by the mapper implementation to provide instance of Java type.
     * @throws ConfigMappingException in case the mapper fails to map the (existing) configuration tree represented by the
     *                                supplied configuration node to an instance of a given Java type.
     * @see io.helidon.config.ConfigSources#directory(String)
     * @see io.helidon.config.spi.AbstractParsableConfigSource.Builder#config(Config)
     */
    public static DirectoryConfigSource create(Config metaConfig) throws ConfigMappingException, MissingValueException {
        return builder().config(metaConfig).build();
    }

    /**
     * Create a fluent API builder to construct a directory config source.
     *
     * @return a new builder instance
     */
    public static DirectoryBuilder builder() {
        return new DirectoryBuilder();
    }

    @Override
    protected String uid() {
        return directoryPath.toString();
    }

    @Override
    protected Optional<Instant> dataStamp() {
        return Optional.ofNullable(FileSourceHelper.lastModifiedTime(directoryPath));
    }

    @Override
    protected Data<ConfigNode.ObjectNode, Instant> loadData() throws ConfigException {
        try {
            ConfigNode.ObjectNode.Builder objectNodeRoot = ConfigNode.ObjectNode.builder();

            Files.walk(directoryPath, 1, FOLLOW_LINKS)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        String content = FileSourceHelper.safeReadContent(path);
                        objectNodeRoot.addValue(path.getFileName().toString(), content);
                    });

            return new Data<>(Optional.of(objectNodeRoot.build()), dataStamp());
        } catch (ConfigException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ConfigException("Configuration at directory '" + directoryPath + "' is not accessible.", ex);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * If the Directory ConfigSource is {@code mandatory} and a {@code directory} does not exist
     * then {@link ConfigSource#load} throws {@link ConfigException}.
     */
    public static final class DirectoryBuilder extends Builder<DirectoryBuilder, Path, DirectoryConfigSource> {
        private Path path;

        /**
         * Initialize builder.
         */
        private DirectoryBuilder() {
            super(Path.class);
        }

        /**
         * Configuration directory path.
         *
         * @param path directory
         * @return updated builder instance
         */
        public DirectoryBuilder path(Path path) {
            this.path = path;
            return this;
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
        public DirectoryBuilder config(Config metaConfig) {
            metaConfig.get(PATH_KEY).as(Path.class).ifPresent(this::path);
            return super.config(metaConfig);
        }

        @Override
        protected Path target() {
            return path;
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
            return new DirectoryConfigSource(this, path);
        }
    }
}
