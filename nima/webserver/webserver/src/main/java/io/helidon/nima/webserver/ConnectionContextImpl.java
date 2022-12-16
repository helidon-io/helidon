/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.nima.webserver.http.DirectHandlers;

final class ConnectionContextImpl implements ConnectionContext {
    private final ServerContext serverContext;
    private final ExecutorService sharedExecutor;
    private final DataWriter dataWriter;
    private final DataReader dataReader;
    private final Router router;
    private final String socketId;
    private final String childSocketId;
    private final DirectHandlers simpleHandlers;
    private final HelidonSocket socket;
    private final long maxPayloadSize;

    ConnectionContextImpl(ServerContext serverContext,
                          ExecutorService sharedExecutor,
                          DataWriter dataWriter,
                          DataReader dataReader,
                          Router router,
                          String socketId,
                          String childSocketId,
                          DirectHandlers simpleHandlers,
                          HelidonSocket socket,
                          long maxPayloadSize) {
        this.serverContext = serverContext;
        this.sharedExecutor = sharedExecutor;
        this.dataWriter = dataWriter;
        this.dataReader = dataReader;
        this.router = router;
        this.socketId = socketId;
        this.childSocketId = childSocketId;
        this.simpleHandlers = simpleHandlers;
        this.socket = socket;
        this.maxPayloadSize = maxPayloadSize;
    }

    @Override
    public ServerContext serverContext() {
        return serverContext;
    }

    @Override
    public ExecutorService sharedExecutor() {
        return sharedExecutor;
    }

    @Override
    public DataWriter dataWriter() {
        return dataWriter;
    }

    @Override
    public DataReader dataReader() {
        return dataReader;
    }

    @Override
    public Router router() {
        return router;
    }

    @Override
    public long maxPayloadSize() {
        return maxPayloadSize;
    }

    @Override
    public DirectHandlers directHandlers() {
        return simpleHandlers;
    }

    @Override
    public PeerInfo remotePeer() {
        return socket.remotePeer();
    }

    @Override
    public PeerInfo localPeer() {
        return socket.localPeer();
    }

    @Override
    public boolean isSecure() {
        return socket.isSecure();
    }

    @Override
    public String socketId() {
        return socketId;
    }

    @Override
    public String childSocketId() {
        return childSocketId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sharedExecutor,
                            dataWriter,
                            router,
                            socketId,
                            childSocketId);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (ConnectionContextImpl) obj;
        return Objects.equals(this.sharedExecutor, that.sharedExecutor)
                && Objects.equals(this.dataWriter, that.dataWriter)
                && Objects.equals(this.router, that.router)
                && Objects.equals(this.socketId, that.socketId)
                && Objects.equals(this.childSocketId, that.childSocketId);
    }

    @Override
    public String toString() {
        return "ConnectionContextImpl["
                + "serverContext=" + serverContext
                + ", sharedExecutor=" + sharedExecutor
                + ", dataWriter=" + dataWriter
                + ", dataReader=" + dataReader
                + ", router=" + router
                + ", socketId='" + socketId + '\''
                + ", childSocketId='" + childSocketId + '\''
                + ", simpleHandlers=" + simpleHandlers
                + ", socket=" + socket
                + ", maxPayloadSize=" + maxPayloadSize
                + ']';
    }
}
