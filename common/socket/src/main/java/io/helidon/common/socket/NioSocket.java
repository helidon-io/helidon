/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import io.helidon.common.buffers.BufferData;

/**
 * Plain socket based on NIO {@link java.nio.channels.SocketChannel}.
 * This socket uses ByteBuffer for reading and writing.
 */
public sealed class NioSocket implements HelidonSocket permits TlsNioSocket {
    private static final int BUFFER_LENGTH = 8 * 1024;

    private final ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_LENGTH);
    private final ByteBuffer writeBuffer = ByteBuffer.allocate(BUFFER_LENGTH);

    private final SocketChannel delegate;
    private final String childSocketId;
    private final String socketId;

    /**
     * Plain socket.
     *
     * @param delegate      delegate socket
     * @param childSocketId channel id
     * @param socketId      server channel id
     */
    protected NioSocket(SocketChannel delegate, String childSocketId, String socketId) {
        this.delegate = delegate;
        this.childSocketId = childSocketId;
        this.socketId = socketId;
    }

    /**
     * Create a new server socket.
     *
     * @param delegate        underlying socket
     * @param channelId       channel id
     * @param serverChannelId server channel id
     * @return a new plain socket
     */
    public static NioSocket server(SocketChannel delegate, String channelId, String serverChannelId) {
        return new NioSocket(delegate, channelId, serverChannelId);
    }

    /**
     * Create a new client socket.
     *
     * @param delegate  underlying socket
     * @param channelId channel id
     * @return a new plain socket
     */
    public static NioSocket client(SocketChannel delegate, String channelId) {
        return new NioSocket(delegate, channelId, "client");
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
            // if close() is called without shutdownOutput(), the remote sometimes does not receive last bytes sent
            delegate.shutdownOutput();
            delegate.shutdownInput();
            delegate.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void idle() {
    }

    @Override
    public boolean isConnected() {
        return delegate.isOpen();
    }

    @SuppressWarnings("removal")
    @Override
    public int read(BufferData buffer) {
        try {
            if (readBuffer.remaining() == 0) {
                readBuffer.clear();
                delegate.read(readBuffer);
                readBuffer.flip();
            }
            return buffer.readFrom(readBuffer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void write(BufferData buffer) {
        try {
            while (!buffer.consumed()) {
                writeBuffer.clear();
                buffer.writeTo(writeBuffer, buffer.available());
                writeBuffer.flip();
                delegate.write(writeBuffer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public byte[] get() {
        try {
            int r = delegate.read(readBuffer);
            readBuffer.flip();
            if (r == -1) {
                return null; // end of data
            }
            if (r == 0) {
                throw new IllegalStateException("Read 0 bytes, this should never happen with blocking socket");
            }
            byte[] result = new byte[r];
            readBuffer.get(result);
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            readBuffer.clear();
        }
    }

    /**
     * Write to the underlying socket/channel from the provided buffer.
     * The buffer is not flipped.
     *
     * @param buffer buffer to write
     * @return number of bytes written
     */
    protected int write(ByteBuffer buffer) {
        try {
            return delegate.write(buffer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Read from the underlying socket/channel into the provided buffer.
     * The buffer is not flipped.
     *
     * @param buffer buffer to read to
     * @return number of bytes read
     */
    protected int read(ByteBuffer buffer) {
        try {
            return delegate.read(buffer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    SocketAddress localSocketAddress() {
        try {
            return delegate.getLocalAddress();
        } catch (IOException e) {
            return null;
        }
    }

    SocketAddress remoteSocketAddress() {
        try {
            return delegate.getRemoteAddress();
        } catch (IOException e) {
            return null;
        }
    }

    String localHost() {
        SocketAddress localSocketAddress = localSocketAddress();
        if (localSocketAddress instanceof InetSocketAddress inet) {
            return inet.getHostString();
        }
        if (localSocketAddress instanceof UnixDomainSocketAddress unix) {
            return unix.getPath().toString();
        }
        throw new IllegalStateException("Local host is not a supported socket address. Address: "
                                                + localSocketAddress.getClass().getName());
    }

    String remoteHost() {
        SocketAddress remoteSocketAddress = remoteSocketAddress();
        if (remoteSocketAddress instanceof InetSocketAddress inet) {
            return inet.getHostString();
        }
        if (remoteSocketAddress instanceof UnixDomainSocketAddress unix) {
            return unix.getPath().toString();
        }
        throw new IllegalStateException("Remote host is not a supported socket address. Address: "
                                                + remoteSocketAddress.getClass().getName());
    }

    int localPort() {
        SocketAddress localSocketAddress = localSocketAddress();
        if (localSocketAddress instanceof InetSocketAddress inet) {
            return inet.getPort();
        }
        if (localSocketAddress instanceof UnixDomainSocketAddress) {
            return -1;
        }
        throw new IllegalStateException("Local host is not an a supported socket address. Address: "
                                                + localSocketAddress.getClass().getName());
    }

    int remotePort() {
        SocketAddress remoteSocketAddress = remoteSocketAddress();
        if (remoteSocketAddress instanceof InetSocketAddress inet) {
            return inet.getPort();
        }
        if (remoteSocketAddress instanceof UnixDomainSocketAddress) {
            return -1;
        }
        throw new IllegalStateException("Remote host is not an a supported socket address. Address: "
                                                + remoteSocketAddress.getClass().getName());
    }
}
