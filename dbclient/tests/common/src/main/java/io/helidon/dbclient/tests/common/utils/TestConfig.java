/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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
package io.helidon.dbclient.tests.common.utils;

import java.lang.System.Logger.Level;
import java.net.URI;

/**
 * Configuration utilities.
 */
public class TestConfig {

    /** Last used id in {@code Critter} table. */
    public static final int LAST_POKEMON_ID = 1000;

    private static final System.Logger LOGGER = System.getLogger(TestConfig.class.getName());
    private static final String CONFIG_PROPERTY_NAME = "io.helidon.tests.integration.dbclient.config";
    private static final String DEFAULT_CONFIG_FILE = "test.yaml";

    /**
     * Retrieve configuration file from {@code io.helidon.tests.integration.dbclient.config}
     * property if exists.
     * Default {@code test.yaml} value is used when no property is set.
     *
     * @return tests configuration file name
     */
    public static String configFile() {
        String configFile = System.getProperty(CONFIG_PROPERTY_NAME, DEFAULT_CONFIG_FILE);
        LOGGER.log(Level.DEBUG, () -> String.format("Configuration file: %s", configFile));
        return configFile;
    }

    /**
     * Create {@link URI} from database URL.
     * JDBC URL like {@code "jdbc:mysql://127.0.0.1:3306/database"} is not valid URI {@code String}.
     * {@code "jdbc:"} prefix must be removed before passing this URL {@code String} to URI factory method.
     * Configuration values processing utility.
     *
     * @param url JDBC database URL
     * @return {@link URI} from database URL
     */
    public static URI uriFromDbUrl(String url) {
        int separator = url.indexOf(':'); // 4
        if (separator == -1) {
            throw new IllegalArgumentException("Missing ':' character to separate leading jdbc prefix in database URL");
        }
        if (url.length() < separator + 2) {
            throw new IllegalArgumentException("Missing characters after \"jdbc:\"prefix");
        }
        return URI.create(url.substring(separator + 1));
    }

    /**
     * Retrieve database name from database {@link URI}.
     * {@link URI} path element contains leading {@code '/'} character which must be removed before returning
     * the database name.
     *
     * @param dbUri database {@link URI}
     * @return database name from database {@link URI}
     */
    public static String dbNameFromUri(URI dbUri) {
        String dbPath =  dbUri.getPath();
        if (dbPath.length() == 0) {
            throw new IllegalArgumentException("Database name is empty");
        }
        String dbName = dbPath.charAt(0) == '/' ? dbPath.substring(1, dbPath.length()) : dbPath;
        if (dbName.length() == 0) {
            throw new IllegalArgumentException("Database name is empty");
        }
        return dbName;
    }

}
