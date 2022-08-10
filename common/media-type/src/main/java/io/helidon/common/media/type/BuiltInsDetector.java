/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import io.helidon.common.media.type.spi.MediaTypeDetector;

/**
 * Detector for built-in media type mappings.
 */
class BuiltInsDetector implements MediaTypeDetector {
    private static final System.Logger LOGGER = System.getLogger(BuiltInsDetector.class.getName());
    private static final Map<String, MediaType> MAPPINGS = new HashMap<>();

    static {
        try (InputStream builtIns = MediaTypes.class.getResourceAsStream("default-media-types.properties")) {
            if (null != builtIns) {
                Properties properties = new Properties();
                properties.load(builtIns);
                for (String name : properties.stringPropertyNames()) {
                    MAPPINGS.put(name, MediaTypes.create(properties.getProperty(name)));
                }
            } else {
                LOGGER.log(Level.ERROR, "Failed to find default media type mapping resource");
            }
        } catch (IOException e) {
            LOGGER.log(Level.ERROR, "Failed to load default media types mapping", e);
        }
    }

    @Override
    public Optional<MediaType> detectExtensionType(String fileSuffix) {
        return Optional.ofNullable(MAPPINGS.get(fileSuffix));
    }
}
