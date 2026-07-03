/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;
import java.util.Optional;

/**
 * Utility for URL sources.
 */
final class UrlHelper {
    static final int STATUS_NOT_FOUND = 404;
    private static final System.Logger LOGGER = System.getLogger(UrlHelper.class.getName());
    private static final String HEAD_METHOD = "HEAD";

    private UrlHelper() {
    }

    static boolean isModified(URL url, Instant stamp) {
        return dataStamp(url)
                .map(newStamp -> newStamp.isAfter(stamp) || newStamp.equals(Instant.MIN))
                .orElse(true);
    }

    static Optional<Instant> dataStamp(URL url) {
        // the URL may not be an HTTP URL
        try {
            URLConnection urlConnection = url.openConnection();
            if (urlConnection instanceof HttpURLConnection connection) {
                try {
                    connection.setRequestMethod(HEAD_METHOD);
                    if (STATUS_NOT_FOUND == connection.getResponseCode()) {
                        return Optional.empty();
                    }
                    if (connection.getLastModified() != 0) {
                        return Optional.of(Instant.ofEpochMilli(connection.getLastModified()));
                    }
                } finally {
                    connection.disconnect();
                }
            }
        } catch (IOException ex) {
            if (LOGGER.isLoggable(Level.TRACE)) {
                LOGGER.log(Level.TRACE,
                           "Configuration at url '" + configuredLocation(url) + "' HEAD is not accessible.",
                           ex);
            }
            return Optional.empty();
        }

        Instant timestamp = Instant.MIN;
        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Missing HEAD '" + configuredLocation(url)
                    + "' response header 'Last-Modified'. Used time '"
                                 + timestamp + "' as a content timestamp.");
        }
        return Optional.of(timestamp);
    }

    static String configuredLocation(URL url) {
        StringBuilder result = new StringBuilder();

        result.append(url.getProtocol()).append(':');

        String host = url.getHost();
        String path = "jar".equals(url.getProtocol()) ? url.getFile() : url.getPath();
        if (!host.isEmpty()) {
            result.append("//").append(host);

            int port = url.getPort();
            if (port != -1) {
                result.append(':').append(port);
            }
        } else {
            int nestedSeparator = path.indexOf("!/");
            if (nestedSeparator > 0) {
                String nestedLocation = path.substring(0, nestedSeparator);
                try {
                    nestedLocation = configuredLocation(new URL(nestedLocation));
                } catch (MalformedURLException ignored) {
                    int nestedScheme = nestedLocation.indexOf("://");
                    if (nestedScheme >= 0) {
                        int authorityStart = nestedScheme + 3;
                        int authorityEnd = nestedLocation.indexOf('/', authorityStart);
                        int at = nestedLocation.indexOf('@', authorityStart);
                        if (at >= 0 && (authorityEnd == -1 || at < authorityEnd)) {
                            nestedLocation = nestedLocation.substring(0, authorityStart)
                                    + nestedLocation.substring(at + 1);
                        }
                    }
                    int query = nestedLocation.indexOf('?');
                    if (query != -1) {
                        nestedLocation = nestedLocation.substring(0, query);
                    }
                    int fragment = nestedLocation.indexOf('#');
                    if (fragment != -1) {
                        nestedLocation = nestedLocation.substring(0, fragment);
                    }
                }
                String entry = path.substring(nestedSeparator);
                int query = entry.indexOf('?');
                if (query != -1) {
                    entry = entry.substring(0, query);
                }
                int fragment = entry.indexOf('#');
                if (fragment != -1) {
                    entry = entry.substring(0, fragment);
                }
                path = nestedLocation + entry;
            }
        }

        result.append(path);

        return result.toString();
    }
}
