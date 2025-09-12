/*
 * Copyright (c) 2019, 2025 Oracle and/or its affiliates.
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
package io.helidon.common.media.type;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import io.helidon.common.media.type.spi.MediaTypeDetector;
import io.helidon.metadata.MetadataConstants;
import io.helidon.metadata.MetadataDiscovery;
import io.helidon.metadata.MetadataFile;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.TRACE;

/**
 * Detector for custom media type mappings.
 */
class CustomDetector implements MediaTypeDetector {
    private static final System.Logger LOGGER = System.getLogger(CustomDetector.class.getName());
    private static final Map<String, MediaType> MAPPINGS = new HashMap<>();

    static {
        // look for configured mapping by a user
        // to override existing mappings from default
        var resources = MetadataDiscovery.instance()
                .list(MetadataConstants.MEDIA_TYPES_FILE);

        try {
            for (MetadataFile resource : resources) {
                if (LOGGER.isLoggable(TRACE)) {
                    LOGGER.log(TRACE, "Loading custom media type mapping from: " + resource.fileName());
                }
                try (InputStream is = resource.inputStream()) {
                    Properties properties = new Properties();
                    properties.load(is);
                    for (String name : properties.stringPropertyNames()) {
                        MAPPINGS.put(name, MediaTypes.create(properties.getProperty(name)));
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(ERROR, "Failed to load custom media types mapping", e);
        }
    }

    @Override
    public Optional<MediaType> detectExtensionType(String fileSuffix) {
        return Optional.ofNullable(MAPPINGS.get(fileSuffix));
    }
}
