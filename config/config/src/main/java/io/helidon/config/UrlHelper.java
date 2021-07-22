/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility for URL sources.
 */
final class UrlHelper {
    private static final Logger LOGGER = Logger.getLogger(UrlHelper.class.getName());
    private static final String HEAD_METHOD = "HEAD";
    static final int STATUS_NOT_FOUND = 404;

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
            if (urlConnection instanceof HttpURLConnection) {
                HttpURLConnection connection = (HttpURLConnection) urlConnection;
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
            LOGGER.log(Level.FINE, ex, () -> "Configuration at url '" + url + "' HEAD is not accessible.");
            return Optional.empty();
        }

        Instant timestamp = Instant.MIN;
        LOGGER.finer("Missing HEAD '" + url + "' response header 'Last-Modified'. Used time '"
                             + timestamp + "' as a content timestamp.");
        return Optional.of(timestamp);
    }
}
