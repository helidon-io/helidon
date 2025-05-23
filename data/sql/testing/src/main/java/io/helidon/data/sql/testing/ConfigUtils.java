/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
package io.helidon.data.sql.testing;

import java.net.URI;

/**
 * Database configuration utilities.
 */
class ConfigUtils {

    private ConfigUtils() {
        throw new UnsupportedOperationException("No instances of ConfigUtils are allowed");
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
            throw new IllegalArgumentException("Missing URI authority initial sequence \"://\"");
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
        String dbPath = dbUri.getPath();
        if (dbPath.isEmpty()) {
            throw new IllegalArgumentException("Database name is empty");
        }
        String dbName = dbPath.charAt(0) == '/' ? dbPath.substring(1) : dbPath;
        if (dbName.isEmpty()) {
            throw new IllegalArgumentException("Database name is empty");
        }
        return dbName;
    }

    /**
     * Replace port value in provided URL.
     *
     * @param url  source URL
     * @param port new port value in returned URL
     * @return source url with port replaced by {@code port} value.
     */
    static String replacePortInUrl(String url, int port) {
        IndexAndLength hostSeparator = findHostSeparator(url);
        if (hostSeparator.index() >= 0) {
            int fromIndex = hostSeparator.index() + hostSeparator.length();

            int end = url.indexOf('/', fromIndex);
            int portBeg = url.indexOf(':', fromIndex);
            // Found port position in URL
            if (end > 0 && portBeg < end) {
                String frontPart = url.substring(0, portBeg + 1);
                String endPart = url.substring(end);
                return frontPart + port + endPart;
            } else {
                throw new IllegalStateException(
                        String.format("URL %s does not contain host and port part \"://host:port/\"", url));
            }
        } else {
            throw new IllegalStateException(
                    String.format("Could not find host separator \"://\" in URL %s", url));
        }
    }

    static int portFromDbUrl(String url) {
        // UCP     : jdbc:mysql://localhost:3306/testdb
        // mysql   : jdbc:oracle:thin:@localhost:1521/FREE

        IndexAndLength hostSeparator = findHostSeparator(url);
        if (hostSeparator.index() >= 0) {
            int fromIndex = hostSeparator.index() + hostSeparator.length();
            int end = url.indexOf('/', fromIndex);
            int portBeg = url.indexOf(':', fromIndex);
            // Found port position in URL
            if (end > 0 && portBeg < end) {
                return Integer.parseInt(url.substring(portBeg + 1, end));
            } else {
                throw new IllegalStateException(
                        String.format("URL %s does not contain host and port part \"://host:port/\"", url));
            }
        } else {
            throw new IllegalStateException(
                    String.format("Could not find host separator \"://\" in URL %s", url));
        }
    }

    // Find separator before host in database URL
    // Regular separator is "://", bur Oracle DB has an exception and uses ":@"
    // mysql : jdbc:mysql://localhost:3306/testdb
    // UCP   : jdbc:oracle:thin:@localhost:1521/FREE
    private static IndexAndLength findHostSeparator(String src) {
        // First check DB type
        int jdbcSep = src.indexOf(':');
        String scheme = src.substring(0, jdbcSep);
        if (!"jdbc".equals(scheme)) {
            throw new IllegalArgumentException(
                    String.format("Database JDBC url shall start with \"jdbc:\" prefix, but URC is %s", src));
        }
        if (src.length() > jdbcSep + 2) {
            int typeSep = src.indexOf(':', jdbcSep + 1);
            String dbType = src.substring(jdbcSep + 1, typeSep);
            // Keeping switch here to simplify future extension
            if ("oracle".equals(dbType)) {
                int begin = src.indexOf(":@");
                return new IndexAndLength(begin, 2);
            } else {
                int begin = src.indexOf("://");
                return new IndexAndLength(begin, 3);
            }
        } else {
            throw new IllegalArgumentException("Database JDBC url has nothing after \"jdbc:\" prefix");
        }
    }

    private record IndexAndLength(int index, int length) {
    }
}
