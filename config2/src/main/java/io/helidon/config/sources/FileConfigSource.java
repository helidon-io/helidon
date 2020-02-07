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
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;

public class FileConfigSource implements ConfigSource.EagerSource,
                                         ConfigSource.TargetPollingSource,
                                         ConfigSource.StampPollingSource<byte[]> {
    private static final Logger LOGGER = Logger.getLogger(FileConfigSource.class.getName());

    private final Optional<String> configuredMediaType;
    private final Optional<ConfigParser> configuredParser;
    private final Path filePath;

    private FileConfigSource(Path filePath) {
        this.filePath = filePath;
        this.configuredParser = Optional.empty();
        this.configuredMediaType = Optional.empty();
    }

    public static FileConfigSource create(Path path) {
        return new FileConfigSource(path);
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
    public ConfigParser.Content load() throws ConfigException {
        LOGGER.fine(() -> String.format("Getting content from '%s'", filePath));

        // now we need to create all the necessary steps in one go, to make sure the digest matches the file
        Optional<DataAndDigest> maybeDad = FileSourceHelper.readDataAndDigest(filePath);

        if (maybeDad.isEmpty()) {
            return ConfigParser.Content.builder()
                    .exists(false)
                    .build();
        }

        DataAndDigest dad = maybeDad.get();
        InputStream dataStream = new ByteArrayInputStream(dad.data());

        /*
         * Build the content
         */
        ConfigParser.Content.Builder builder = ConfigParser.Content.builder()
                .pollingStamp(dad.digest())
                .pollingTarget(filePath);

        ConfigParser.ParsableContentBuilder parsable = ConfigParser.ParsableContentBuilder
                .create(dataStream, "file: " + filePath.toString());

        configuredParser.ifPresent(parsable::parser);
        configuredMediaType.or(() -> MediaTypes.detectType(filePath))
                .ifPresent(parsable::mediaType);

        builder.parsable(parsable);

        return builder.build();
    }
}
