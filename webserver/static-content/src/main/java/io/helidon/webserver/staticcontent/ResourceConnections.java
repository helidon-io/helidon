/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.staticcontent;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;

final class ResourceConnections {
    private static final String JAR_PROTOCOL = "jar";

    private ResourceConnections() {
    }

    static URLConnection openConnection(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        if (JAR_PROTOCOL.equals(url.getProtocol())) {
            connection.setUseCaches(false);
        }
        return connection;
    }

    static JarURLConnection openJarConnection(URL url) throws IOException {
        JarURLConnection connection = (JarURLConnection) url.openConnection();
        connection.setUseCaches(false);
        return connection;
    }

    static InputStream openStream(URL url) throws IOException {
        return openConnection(url).getInputStream();
    }
}
