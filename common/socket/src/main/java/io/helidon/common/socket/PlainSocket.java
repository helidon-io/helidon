/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.common.socket;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Arrays;

import io.helidon.common.buffers.BufferData;

/**
 * Helidon socket that is based on plaintext.
 */
public sealed class PlainSocket implements HelidonSocket permits TlsSocket {
    private static final int BUFFER_LENGTH = 8 * 1024;

    private final byte[] readBuffer = new byte[BUFFER_LENGTH];

    private final Socket delegate;
    private final String childSocketId;
    private final String socketId;
    private final IdleInputStream inputStream;
    private final OutputStream outputStream;

    /**
     * Plain socket.
     *
     * @param delegate delegate socket
     * @param childSocketId channel id
     * @param socketId  server channel id
     */
    protected PlainSocket(Socket delegate, String childSocketId, String socketId) {
        this.delegate = delegate;
        this.childSocketId = childSocketId;
        this.socketId = socketId;
        try {
            this.inputStream = new IdleInputStream(delegate, delegate.getInputStream(), childSocketId, socketId);
            this.outputStream = delegate.getOutputStream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Create a new server socket.
     *
     * @param delegate underlying socket
     * @param channelId channel id
     * @param serverChannelId server channel id
     * @return a new plain socket
     */
    public static PlainSocket server(Socket delegate, String channelId, String serverChannelId) {
        return new PlainSocket(delegate, channelId, serverChannelId);
    }

    /**
     * Create a new client socket.
     *
     * @param delegate underlying socket
     * @param channelId channel id
     * @return a new plain socket
     */
    public static PlainSocket client(Socket delegate, String channelId) {
        return new PlainSocket(delegate, channelId, "client");
    }

    @Override
    public PeerInfo remotePeer() {
        return PeerInfoImpl.createRemote(this);
    }

    @Override
    public PeerInfo localPeer() {
        return PeerInfoImpl.createLocal(this);
    }

    @Override
    public boolean isSecure() {
        return false;
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
    public void close() {
        try {
            delegate.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void idle() {
        inputStream.idle();
    }

    @Override
    public boolean isConnected() {
        return !inputStream.isClosed();
    }

    @Override
    public int read(BufferData buffer) {
        return buffer.readFrom(inputStream);
    }

    @Override
    public void write(BufferData buffer) {
        buffer.writeTo(outputStream);
    }

    @Override
    public byte[] get() {
        try {
            int r = inputStream.read(readBuffer);
            if (r == -1) {
                return null; // end of data
            } else if (r == 0) {
                throw new IllegalStateException("Read 0 bytes, this should never happen with blocking socket");
            }
            return Arrays.copyOf(readBuffer, r);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    SocketAddress localSocketAddress() {
        return delegate.getLocalSocketAddress();
    }

    SocketAddress remoteSocketAddress() {
        return delegate.getRemoteSocketAddress();
    }

    String localHost() {
        SocketAddress localSocketAddress = localSocketAddress();
        if (localSocketAddress instanceof InetSocketAddress inet) {
            return inet.getHostString();
        }
        throw new IllegalStateException("Local host is not an instance of InetSocketAddress");
    }

    String remoteHost() {
        SocketAddress remoteSocketAddress = delegate.getRemoteSocketAddress();
        if (remoteSocketAddress instanceof InetSocketAddress inet) {
            return inet.getHostString();
        }
        throw new IllegalStateException("Remote host is not an instance of InetSocketAddress");
    }

    int localPort() {
        SocketAddress localSocketAddress = localSocketAddress();
        if (localSocketAddress instanceof InetSocketAddress inet) {
            return inet.getPort();
        }
        throw new IllegalStateException("Local host is not an instance of InetSocketAddress");
    }

    int remotePort() {
        SocketAddress remoteSocketAddress = delegate.getRemoteSocketAddress();
        if (remoteSocketAddress instanceof InetSocketAddress inet) {
            return inet.getPort();
        }
        throw new IllegalStateException("Remote host is not an instance of InetSocketAddress");
    }
}
