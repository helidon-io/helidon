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

package io.helidon.webserver.testing.junit5;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;

/**
 * A socket not backed by any network, used for unit testing.
 */
public class DirectSocket implements HelidonSocket {
    private final PeerInfo localPeer;
    private final PeerInfo remotePeer;
    private final boolean isSecure;

    DirectSocket(PeerInfo localPeer, PeerInfo remotePeer, boolean isSecure) {
        this.localPeer = localPeer;
        this.remotePeer = remotePeer;
        this.isSecure = isSecure;
    }

    /**
     * Create a new socket with explicit peer information.
     *
     * @param localPeer local peer (local host and port)
     * @param remotePeer remote peer (remote party host and port)
     * @param isSecure whether the socket is secured (TLS)
     * @return a new direct socket
     */
    public static DirectSocket create(PeerInfo localPeer, PeerInfo remotePeer, boolean isSecure) {
        return new DirectSocket(localPeer, remotePeer, isSecure);
    }

    @Override
    public PeerInfo remotePeer() {
        return remotePeer;
    }

    @Override
    public PeerInfo localPeer() {
        return localPeer;
    }

    @Override
    public boolean isSecure() {
        return isSecure;
    }

    @Override
    public String socketId() {
        return "unit-client";
    }

    @Override
    public String childSocketId() {
        return "client";
    }

    @Override
    public void close() {
    }

    @Override
    public void idle() {

    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public int read(BufferData buffer) {
        return 0;
    }

    @Override
    public void write(BufferData buffer) {

    }

    @Override
    public byte[] get() {
        return new byte[0];
    }
}
