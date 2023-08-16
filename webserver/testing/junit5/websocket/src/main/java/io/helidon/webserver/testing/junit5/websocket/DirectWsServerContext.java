/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.testing.junit5.websocket;

import java.util.concurrent.ExecutorService;

import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.context.Context;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.MediaContext;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.http.DirectHandlers;

class DirectWsServerContext implements ConnectionContext, ListenerContext {
    private final ExecutorService executor;
    private final Router router;
    private final HelidonSocket socket;
    private final DataWriter dataWriter;
    private final DataReader dataReader;
    private final ListenerConfig listenerConfiguration;

    DirectWsServerContext(ExecutorService executor,
                          Router router,
                          HelidonSocket socket,
                          DataWriter dataWriter,
                          DataReader dataReader) {
        this.executor = executor;
        this.router = router;
        this.socket = socket;
        this.dataWriter = dataWriter;
        this.dataReader = dataReader;

        PeerInfo peerInfo = socket.localPeer();
        this.listenerConfiguration = ListenerConfig.builder()
                .name("@default")
                .host(peerInfo.host())
                .port(peerInfo.port())
                .listenerContext(Context.builder().id("test-ws-direct-listener").build())
                .mediaContext(MediaContext.create())
                .contentEncoding(ContentEncodingContext.create())
                .directHandlers(DirectHandlers.create())
                .build();
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
        return socket.socketId();
    }

    @Override
    public String childSocketId() {
        return socket.childSocketId();
    }

    @Override
    public ListenerContext listenerContext() {
        return this;
    }

    @Override
    public ExecutorService executor() {
        return executor;
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
    public Context context() {
        return listenerConfiguration.listenerContext().get();
    }

    @Override
    public MediaContext mediaContext() {
        return listenerConfiguration.mediaContext().get();
    }

    @Override
    public ContentEncodingContext contentEncodingContext() {
        return listenerConfiguration.contentEncoding().get();
    }

    @Override
    public DirectHandlers directHandlers() {
        return listenerConfiguration.directHandlers().get();
    }

    @Override
    public ListenerConfig config() {
        return listenerConfiguration;
    }
}
