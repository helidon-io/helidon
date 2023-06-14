/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.http2.webclient;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.media.type.ParserMode;
import io.helidon.common.socket.SocketOptions;

// a representation of a single remote endpoint
// this may use one or more connections (depending on parallel streams)
class Http2ClientConnectionHandler {
    // todo requires handling of timeouts and removal from this queue
    private final List<Http2ClientConnection> fullConnections = new LinkedList<>();

    private final ExecutorService executor;
    private final SocketOptions socketOptions;
    private String primaryPath;
    private final ConnectionKey connectionKey;
    private final AtomicReference<Http2ClientConnection> activeConnection = new AtomicReference<>();
    // simple solution for now
    private final Semaphore semaphore = new Semaphore(1);
    private final ParserMode mediaTypeParserMode;

    Http2ClientConnectionHandler(ExecutorService executor,
                                 SocketOptions socketOptions,
                                 String primaryPath,
                                 ConnectionKey connectionKey,
                                 ParserMode mediaTypeParserMode) {
        this.executor = executor;
        this.socketOptions = socketOptions;
        this.primaryPath = primaryPath;
        this.connectionKey = connectionKey;
        this.mediaTypeParserMode = mediaTypeParserMode;
    }

    public Http2ClientStream newStream(ConnectionContext ctx) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted", e);
        }
        try {
            // read/write lock to obtain a stream or create a new connection
            Http2ClientConnection conn = activeConnection.get();
            Http2ClientStream stream;
            if (conn == null) {
                conn = createConnection(connectionKey, ctx);
                stream = conn.createStream(ctx.priority());
            } else {
                stream = conn.tryStream(ctx.priority());
                if (stream == null) {
                    conn = createConnection(connectionKey, ctx);
                    stream = conn.createStream(ctx.priority());
                }
            }

            return stream;
        } finally {
            semaphore.release();
        }
    }

    private Http2ClientConnection createConnection(ConnectionKey connectionKey, ConnectionContext connectionContext) {
        Http2ClientConnection conn =
                new Http2ClientConnection(executor,
                                          socketOptions,
                                          connectionKey,
                                          primaryPath,
                                          connectionContext,
                                          mediaTypeParserMode);
        conn.connect();
        activeConnection.set(conn);
        fullConnections.add(conn);
        return conn;
    }
}
