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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.MissingValueException;
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
     * {@link io.helidon.config.spi.AbstractParsableConfigSource.Builder#init(Config)}.
     *
     * @param metaConfig meta-configuration used to initialize returned config source instance from.
     * @return new instance of config source described by {@code metaConfig}
     * @throws MissingValueException  in case the configuration tree does not contain all expected sub-nodes
     *                                required by the mapper implementation to provide instance of Java type.
     * @throws ConfigMappingException in case the mapper fails to map the (existing) configuration tree represented by the
     *                                supplied configuration node to an instance of a given Java type.
     * @see io.helidon.config.ConfigSources#directory(String)
     * @see io.helidon.config.spi.AbstractParsableConfigSource.Builder#init(Config)
     */
    public static DirectoryConfigSource from(Config metaConfig) throws ConfigMappingException, MissingValueException {
        return (DirectoryConfigSource) new DirectoryBuilder(metaConfig.get(PATH_KEY).as(Path.class))
                .init(metaConfig)
                .build();
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
    public static final class DirectoryBuilder extends Builder<DirectoryBuilder, Path> {
        private Path path;

        /**
         * Initialize builder.
         *
         * @param path configuration directory path
         */
        public DirectoryBuilder(Path path) {
            super(Path.class);

            Objects.requireNonNull(path, "directory path cannot be null");

            this.path = path;
        }

        @Override
        protected DirectoryBuilder init(Config metaConfig) {
            return super.init(metaConfig);
        }

        @Override
        protected Path getTarget() {
            return path;
        }

        /**
         * Builds new instance of Directory ConfigSource.
         *
         * @return new instance of File ConfigSource.
         */
        public ConfigSource build() {
            return new DirectoryConfigSource(this, path);
        }
    }
}
