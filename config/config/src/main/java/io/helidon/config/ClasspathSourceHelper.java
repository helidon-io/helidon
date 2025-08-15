/*
 * Copyright (c) 2017, 2025 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utilities for file-related source classes.
 *
 * @see io.helidon.config.ClasspathConfigSource
 */
class ClasspathSourceHelper {

    private static final System.Logger LOGGER = System.getLogger(ClasspathSourceHelper.class.getName());

    private ClasspathSourceHelper() {
        throw new AssertionError("Instantiation not allowed.");
    }

    static String uid(String resourceName) {
        try {
            Path resourcePath = ClasspathSourceHelper.resourcePath(resourceName);
            if (resourcePath != null) {
                //Backwards slash replacement is workaround used because of windows compatibility.
                return resourcePath.toString().replace('\\', '/');
            }
        } catch (Exception ex) {
            //ignore it
            LOGGER.log(Level.DEBUG,
                       "Not possible to get filesystem path for resource '" + resourceName
                               + "'. Resource's name is used as ConfigSource URI.",
                       ex);
        }
        return resourceName;
    }

    static Path resourcePath(String resourceName) throws URISyntaxException {
        URL resourceUrl = Thread.currentThread().getContextClassLoader().getResource(resourceName);
        if (resourceUrl != null) {
            // this can only work if we are using a file based classloader (which may not be always the case)
            // we may load classes from http, or from specific loaders, such as in Graal native image
            URI uri = resourceUrl.toURI();
            if ("file".equals(uri.getScheme())) {
                return Paths.get(uri);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

}
