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

package io.helidon.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple class to provide version information.
 */
public class Version {

    private static final String VERSION_PROPERTIES_FILE = "/version.properties";
    private static final String UNKNOWN = "unknown";
    private static Properties versionProperties = new Properties();

    static {
        InputStream resourceStream = Version.class.getResourceAsStream(VERSION_PROPERTIES_FILE);
        try {
            versionProperties.load(resourceStream);
        } catch (IOException ex) {
            Logger logger = Logger.getLogger(Version.class.getName());
            logger.log(Level.WARNING, "Could not load resource " + VERSION_PROPERTIES_FILE + " " + ex.getMessage());
        }
    }

    /**
     * Name of product or project.
     */
    public static final String PRODUCT = "Helidon";

    /**
     * Overall version.
     */
    public static final String VERSION = versionProperties.getProperty("helidon.version", UNKNOWN);

    /**
     * Timestamp of this build.
     */
    public static final String BUILD_TIMESTAMP = versionProperties.getProperty("build.timestamp", UNKNOWN);

    /**
     * String representation of Version.
     *
     * @return String representation of Version.
     */
    public String toString() {
        return VERSION + " " + BUILD_TIMESTAMP;
    }
}
