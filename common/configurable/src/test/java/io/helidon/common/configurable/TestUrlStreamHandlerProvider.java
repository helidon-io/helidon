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

package io.helidon.common.configurable;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.spi.URLStreamHandlerProvider;

/** Test URL protocol provider that records the opened connection. */
public final class TestUrlStreamHandlerProvider extends URLStreamHandlerProvider {
    static final String PROTOCOL = "helidon-test-timeout";

    private static RecordingUrlConnection connection;

    static RecordingUrlConnection connection() {
        return connection;
    }

    static void reset() {
        connection = null;
    }

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if (!PROTOCOL.equals(protocol)) {
            return null;
        }
        return new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                connection = new RecordingUrlConnection(url);
                return connection;
            }
        };
    }

    static final class RecordingUrlConnection extends URLConnection {
        private RecordingUrlConnection(URL url) {
            super(url);
        }

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public InputStream getInputStream() {
            connect();
            return InputStream.nullInputStream();
        }
    }
}
