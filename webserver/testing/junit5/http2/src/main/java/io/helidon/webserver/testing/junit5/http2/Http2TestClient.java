/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.testing.junit5.http2;

import java.net.URI;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Http/2 low-level testing client.
 */
public class Http2TestClient implements AutoCloseable {

    private final URI uri;
    private final ConcurrentLinkedQueue<Http2TestConnection> testConnections = new ConcurrentLinkedQueue<>();

    Http2TestClient(URI uri) {
        this.uri = uri;
    }

    /**
     * Create new low-level http/2 connection.
     * @return new connection
     */
    public Http2TestConnection createConnection() {
        var testConnection = new Http2TestConnection(uri);
        testConnections.add(testConnection);
        return testConnection;
    }

    @Override
    public void close() {
        testConnections.forEach(Http2TestConnection::close);
    }
}

