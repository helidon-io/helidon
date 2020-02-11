/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.config.sources;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Logger;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.ConfigException;
import io.helidon.config.sources.FileSourceHelper.DataAndDigest;
import io.helidon.config.spi.ChangeWatcher;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource.ParsableSource;
import io.helidon.config.spi.ConfigSource.PollableSource;
import io.helidon.config.spi.ConfigSource.WatchableSource;
import io.helidon.config.spi.ConfigSourceBase;
import io.helidon.config.spi.ConfigSourceBuilderBase;
import io.helidon.config.spi.Content;
import io.helidon.config.spi.PollingStrategy;

public final class FileConfigSource extends ConfigSourceBase implements ParsableSource,
                                                                        WatchableSource<Path>,
                                                                        PollableSource<byte[]> {
    private static final Logger LOGGER = Logger.getLogger(FileConfigSource.class.getName());

    private final Path filePath;

    private FileConfigSource(Builder builder) {
        super(builder);
        this.filePath = builder.filePath;
    }

    public static FileConfigSource create(Path path) {
        return builder().filePath(path).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean exists() {
        return Files.exists(filePath) && !Files.isDirectory(filePath);
    }

    @Override
    public boolean isModified(byte[] stamp) {
        boolean theSame = FileSourceHelper.fileStamp(filePath)
                .map(newStamp -> Arrays.equals(stamp, newStamp))
                // if new stamp is not present, it means the file was deleted
                .orElse(false);

        return !theSame;
    }

    @Override
    public Class<Path> targetType() {
        return Path.class;
    }

    @Override
    public Content.ParsableContent load() throws ConfigException {
        LOGGER.fine(() -> String.format("Getting content from '%s'", filePath));

        // now we need to create all the necessary steps in one go, to make sure the digest matches the file
        Optional<DataAndDigest> maybeDad = FileSourceHelper.readDataAndDigest(filePath);

        if (maybeDad.isEmpty()) {
            return Content.parsableBuilder()
                    .exists(false)
                    .build();
        }

        DataAndDigest dad = maybeDad.get();
        InputStream dataStream = new ByteArrayInputStream(dad.data());

        /*
         * Build the content
         */
        var builder = Content.parsableBuilder()
                .pollingStamp(dad.digest())
                .pollingTarget(filePath)
                .data(dataStream);

        MediaTypes.detectType(filePath).ifPresent(builder::mediaType);

        return builder.build();
    }

    @Override
    public Optional<ChangeWatcher<?>> changeWatcher() {
        return super.changeWatcher();
    }

    @Override
    public Optional<PollingStrategy> pollingStrategy() {
        return super.pollingStrategy();
    }

    @Override
    public Optional<String> mediaType() {
        return super.mediaType();
    }

    @Override
    public Optional<ConfigParser> parser() {
        return super.parser();
    }

    public static class Builder
            extends ConfigSourceBuilderBase<Builder, Path>
            implements ParsableSource.Builder<Builder>,
                       WatchableSource.Builder<Builder, Path>,
                       PollableSource.Builder<Builder> {

        private Path filePath;

        @Override
        public FileConfigSource build() {
            return new FileConfigSource(this);
        }

        public Builder filePath(Path filePath) {
            this.filePath = filePath;
            return this;
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
    }
}
