/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.media.type.spi.MediaTypeDetector;

/**
 * Detector for custom media type mappings.
 */
class CustomDetector implements MediaTypeDetector {
    private static final String MEDIA_TYPE_RESOURCE = "META-INF/helidon/media-types.properties";
    private static final Logger LOGGER = Logger.getLogger(CustomDetector.class.getName());
    private static final Map<String, String> MAPPINGS = new HashMap<>();

    static {
        // look for configured mapping by a user
        // to override existing mappings from default
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try {
            Enumeration<URL> resources = classLoader.getResources(MEDIA_TYPE_RESOURCE);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                LOGGER.finest(() -> "Loading custom media type mapping from: " + url);
                try (InputStream is = url.openStream()) {
                    Properties properties = new Properties();
                    properties.load(is);
                    for (String name : properties.stringPropertyNames()) {
                        MAPPINGS.put(name, properties.getProperty(name));
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load custom media types mapping", e);
        }
    }

    @Override
    public Optional<String> detectExtensionType(String fileSuffix) {
        return Optional.ofNullable(MAPPINGS.get(fileSuffix));
    }
}
