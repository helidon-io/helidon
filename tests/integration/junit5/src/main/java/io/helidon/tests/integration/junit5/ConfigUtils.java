/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.junit5;

import java.net.URI;

/**
 * Configuration utilities.
 */
public class ConfigUtils {

    private ConfigUtils() {
        throw new IllegalStateException("No instances of ConfigUtils are allowed");
    }

    /**
     * Create {@link URI} from JDBC database URL.
     * URI (URL) syntax shall match following scheme:<p>
     * {@code URI = scheme ":" ["//" authority] path ["?" query] ["#" fragment]}<p>
     * Unfortunately {@code scheme} element of JDBC database URL may contain multiple fields
     * separated by {@code ':'} character. Only last of those fields can be passed to URI parser.
     * To simplify the parsing, {@code "//" authority} element is considered as mandatory.
     *
     * @param url JDBC database URL
     * @return {@link URI} from JDBC database URL
     */
    public static URI uriFromDbUrl(String url) {
        // Search for beginning of authority element which is considered as mandatory
        int authIndex = url.indexOf("://");
        if (authIndex == -1) {
            throw new IllegalArgumentException("Missing URI authorioty initial sequence \"://\"");
        }
        if (authIndex == 0) {
            throw new IllegalArgumentException("Missing URI segment part");
        }
        // Search for last sub-scheme separator ':' before "://", it may not exist
        int separator = url.lastIndexOf(':', authIndex - 1);
        if (separator >= 0) {
            return URI.create(url.substring(separator + 1));
        }
        return URI.create(url);
    }

    /**
     * Create database name from URI path element.
     * Path element starts with {@code '/'} character as a consequence of having {@code "//" authority} element
     * in the URL. Leading {@code '/'} character must be removed from the path if exists to get database name.
     *
     * @param dbUri source URI
     * @return database name from URI path element
     */
    public static String dbNameFromUri(URI dbUri) {
        String dbPath =  dbUri.getPath();
        if (dbPath.length() == 0) {
            throw new IllegalArgumentException("Database name is empty");
        }
        String dbName = dbPath.charAt(0) == '/' ? dbPath.substring(1) : dbPath;
        if (dbName.length() == 0) {
            throw new IllegalArgumentException("Database name is empty");
        }
        return dbName;
    }

}
