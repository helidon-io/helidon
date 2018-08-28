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

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utilities for file-related source classes.
 *
 * @see ClasspathConfigSource
 * @see ClasspathOverrideSource
 */
class ClasspathSourceHelper {

    private static final Logger LOGGER = Logger.getLogger(ClasspathSourceHelper.class.getName());

    private ClasspathSourceHelper() {
        throw new AssertionError("Instantiation not allowed.");
    }

    static String uid(String resourceName) {
        try {
            Path resourcePath = ClasspathSourceHelper.resourcePath(resourceName);
            if (resourcePath != null) {
                return resourcePath.toString();
            }
        } catch (Exception ex) {
            //ignore it
            LOGGER.log(Level.FINE,
                       "Not possible to get filesystem path for resource '" + resourceName
                               + "'. Resource's name is used as ConfigSource URI.",
                       ex);
        }
        return resourceName;
    }

    static Path resourcePath(String resourceName) throws URISyntaxException {
        URL resourceUrl = Thread.currentThread().getContextClassLoader().getResource(resourceName);
        if (resourceUrl != null) {
            return Paths.get(resourceUrl.toURI());
        } else {
            return null;
        }
    }

    static Instant resourceTimestamp(String resourceName) {
        try {
            Path resourcePath = resourcePath(resourceName);
            if (resourcePath != null) {
                return Files.getLastModifiedTime(resourcePath).toInstant();
            }
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Error to get resource '" + resourceName + "' last modified time.", ex);
        }
        return Instant.EPOCH;
    }
}
