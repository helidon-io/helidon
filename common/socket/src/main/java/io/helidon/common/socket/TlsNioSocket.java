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
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;

/**
 * TLS NIO helidon socket.
 */
public final class TlsNioSocket extends NioSocket {

    private final Lock handshakeLock = new ReentrantLock();
    private final SSLEngine engine;
    private final ByteBuffer myAppData;

    private int unwrapRemaining;
    private ByteBuffer peerAppData;
    private ByteBuffer myNetData;
    private ByteBuffer peerNetData;
    private boolean closed;

    private volatile PeerInfo localPeer;
    private volatile PeerInfo remotePeer;
    private volatile byte[] lastSslSessionId;

    private TlsNioSocket(SocketChannel delegate, SSLEngine sslEngine, String channelId, String serverChannelId) {
        super(delegate, channelId, serverChannelId);

        this.engine = sslEngine;

        SSLSession dummySession = engine.getSession();
        this.peerNetData = ByteBuffer.allocate(dummySession.getPacketBufferSize());
        this.peerAppData = ByteBuffer.allocate(dummySession.getApplicationBufferSize());
        this.myNetData = ByteBuffer.allocate(dummySession.getPacketBufferSize());
        this.myAppData = ByteBuffer.allocate(dummySession.getApplicationBufferSize());
        dummySession.invalidate();
    }

    /**
     * Create a server TLS NIO socket.
     *
     * @param delegate        underlying socket
     * @param sslEngine       SSL engine
     * @param channelId       listener channel id
     * @param serverChannelId connection channel id
     * @return a new TLS socket
     */
    public static TlsNioSocket server(SocketChannel delegate,
                                      SSLEngine sslEngine,
                                      String channelId,
                                      String serverChannelId) {
        sslEngine.setUseClientMode(false);
        return new TlsNioSocket(delegate, sslEngine, channelId, serverChannelId);
    }

    /**
     * Create a client TLS NIO socket.
     *
     * @param delegate  underlying socket
     * @param sslEngine SSL engine
     * @param channelId channel id
     * @return a new TLS socket
     */
    public static TlsNioSocket client(SocketChannel delegate,
                                      SSLEngine sslEngine,
                                      String channelId) {
        sslEngine.setUseClientMode(true);
        return new TlsNioSocket(delegate, sslEngine, channelId, "client");
    }

    @Override
    public PeerInfo remotePeer() {
        if (renegotiated()) {
            remotePeer = null;
            localPeer = null;
        }

        if (remotePeer == null) {
            this.remotePeer = PeerInfoImpl.createRemote(this);
        }
        return this.remotePeer;
    }

    @Override
    public PeerInfo localPeer() {
        if (renegotiated()) {
            remotePeer = null;
            localPeer = null;
        }

        if (localPeer == null) {
            this.localPeer = PeerInfoImpl.createLocal(this);
        }
        return this.localPeer;
    }

    @Override
    public boolean isSecure() {
        return true;
    }

    @Override
    public boolean protocolNegotiated() {
        String protocol = engine.getApplicationProtocol();
        return protocol != null && !protocol.isEmpty();
    }

    @Override
    public String protocol() {
        String protocol = engine.getApplicationProtocol();
        if (protocol == null || protocol.isEmpty()) {
            throw new NoSuchElementException("No protocol negotiated, guard with #protocolNegotiated()");
        }
        return protocol;
    }

    @Override
    public byte[] get() {
        try {
            peerAppData.clear();

            while (peerAppData.position() == 0) {
                SSLEngineResult result = receiveAndUnwrap();
                SSLEngineResult.Status status = result.getStatus();

                if (status == SSLEngineResult.Status.CLOSED) {
                    doClosure();
                    return appBytes();
                }

                SSLEngineResult.HandshakeStatus handshakeStatus = result.getHandshakeStatus();
                if (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED
                        && handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                    doHandshake(handshakeStatus);
                }
            }

            return appBytes();
        } catch (DataReader.InsufficientDataAvailableException e) {
            // connection closed
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public int read(BufferData buffer) {
        try {
            return doRead(buffer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void write(BufferData buffer) {
        try {
            doWrite(buffer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        try {
            engine.closeOutbound();
            doClosure();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            super.close();
        }
    }

    Optional<Principal> tlsPeerPrincipal() {
        try {
            return Optional.of(engine.getSession().getPeerPrincipal());
        } catch (SSLPeerUnverifiedException e) {
            return Optional.empty();
        }
    }

    Optional<Certificate[]> tlsPeerCertificates() {
        try {
            return Optional.of(engine.getSession().getPeerCertificates());
        } catch (SSLPeerUnverifiedException e) {
            return Optional.empty();
        }
    }

    Optional<Principal> tlsPrincipal() {
        return Optional.ofNullable(engine.getSession().getLocalPrincipal());
    }

    Optional<Certificate[]> tlsCertificates() {
        return Optional.ofNullable(engine.getSession().getLocalCertificates());
    }

    void doClosure() throws IOException {
        try {
            handshakeLock.lock();
            myAppData.clear();

            SSLEngineResult.Status st;
            SSLEngineResult.HandshakeStatus hs;
            do {
                myAppData.flip();
                SSLEngineResult r = wrapAndSend(myAppData, true);
                hs = r.getHandshakeStatus();
                st = r.getStatus();
            } while (st != SSLEngineResult.Status.CLOSED
                    && !(st == SSLEngineResult.Status.OK && hs == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING));
        } finally {
            handshakeLock.unlock();
        }
    }

    private SSLEngineResult receiveAndUnwrap() throws SSLException {
        SSLEngineResult result;
        SSLEngineResult.Status status;
        if (closed) {
            throw new DataReader.InsufficientDataAvailableException();
        }
        boolean needData;
        if (unwrapRemaining > 0) {
            peerNetData.compact();
            peerNetData.flip();
            needData = false;
        } else {
            peerNetData.clear();
            needData = true;
        }

        int x;
        do {
            if (needData) {
                do {
                    x = read(peerNetData);
                } while (x == 0);
                if (x == -1) {
                    throw new DataReader.InsufficientDataAvailableException();
                }
                peerNetData.flip();
            }
            result = engine.unwrap(peerNetData, peerAppData);
            status = result.getStatus();
            if (status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                if (peerNetData.limit() == peerNetData.capacity()) {
                    peerNetData = reallocate(peerNetData, engine.getSession().getPacketBufferSize(), false);
                } else {
                    peerNetData.position(peerNetData.limit());
                    peerNetData.limit(peerNetData.capacity());
                }
                needData = true;
            } else if (status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                peerAppData = reallocate(peerAppData, engine.getSession().getApplicationBufferSize(), true);
                needData = false;
            } else if (status == SSLEngineResult.Status.CLOSED) {
                closed = true;
                peerAppData.flip();
                return result;
            }
        } while (status != SSLEngineResult.Status.OK);

        unwrapRemaining = peerNetData.remaining();
        return result;
    }

    private byte[] appBytes() {
        peerAppData.flip();
        byte[] result = new byte[peerAppData.remaining()];
        peerAppData.get(result);
        return result;
    }

    private void doHandshake(SSLEngineResult.HandshakeStatus handshakeStatus) throws IOException {

        SSLEngineResult.HandshakeStatus status = handshakeStatus;
        try {
            handshakeLock.lock();
            myAppData.clear();

            while (status != SSLEngineResult.HandshakeStatus.FINISHED
                    && status != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {

                SSLEngineResult result = null;

                switch (status) {
                case NEED_TASK:
                    Runnable task;
                    while ((task = engine.getDelegatedTask()) != null) {
                        task.run();
                    }
                    // fall through to wrap
                case NEED_WRAP:
                    myAppData.clear();
                    myAppData.flip();
                    result = wrapAndSend(myAppData, false);
                    break;
                case NEED_UNWRAP:
                    peerAppData.clear();
                    result = receiveAndUnwrap();
                    break;
                default:
                    throw new IllegalStateException("Unexpected status: " + status);
                }

                status = result.getHandshakeStatus();
            }
        } finally {
            handshakeLock.unlock();
        }
    }

    private int doRead(BufferData buf) throws IOException {
        peerNetData.clear();
        var bytesRead = read(peerNetData);
        if (bytesRead > 0) {
            peerNetData.flip();
            while (peerNetData.hasRemaining()) {
                peerAppData.clear();
                var result = engine.unwrap(peerNetData, peerAppData);
                switch (result.getStatus()) {
                case OK:
                    peerAppData.flip();
                    break;
                case BUFFER_OVERFLOW:
                    peerAppData = bufferOverflow(peerAppData, engine.getSession().getApplicationBufferSize());
                    break;
                case BUFFER_UNDERFLOW:
                    peerNetData = bufferUnderflow(peerNetData, engine.getSession().getApplicationBufferSize());
                    break;
                case CLOSED:
                    close();
                    return -1;
                default:
                    throw new IllegalStateException("Invalid status: " + result.getStatus());
                }
            }

        } else if (bytesRead == -1) {
            engine.closeInbound();
        }
        return buf.readFrom(peerAppData);
    }

    private void doWrite(BufferData buffer) throws IOException {
        while (!buffer.consumed()) {
            myAppData.clear();
            buffer.writeTo(myAppData, buffer.available());
            myAppData.flip();

            while (myAppData.hasRemaining()) {
                SSLEngineResult result = wrapAndSend(myAppData, false);
                SSLEngineResult.Status status = result.getStatus();
                if (status == SSLEngineResult.Status.CLOSED) {
                    doClosure();
                    return;
                }
                SSLEngineResult.HandshakeStatus handshakeStatus = result.getHandshakeStatus();
                if (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED
                        && handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                    doHandshake(handshakeStatus);
                }
            }
        }
    }

    private SSLEngineResult wrapAndSend(ByteBuffer appData, boolean ignoreClose) throws SSLException {
        if (closed && !ignoreClose) {
            throw new SSLException("Engine is closed");
        }
        SSLEngineResult.Status status;
        SSLEngineResult result;

        myNetData.clear();
        do {
            result = engine.wrap(appData, myNetData);
            status = result.getStatus();
            if (status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                this.myNetData = reallocate(myNetData,
                                            engine.getSession().getPacketBufferSize(),
                                            true);
            }
        } while (status == SSLEngineResult.Status.BUFFER_OVERFLOW);

        if (status == SSLEngineResult.Status.CLOSED && !ignoreClose) {
            closed = true;
        }

        if (result.bytesProduced() > 0) {
            myNetData.flip();
            int len = myNetData.remaining();
            while (len > 0) {
                len -= write(myNetData);
            }
        }

        return result;
    }

    private ByteBuffer reallocate(ByteBuffer buffer, int size, boolean flip) {
        if (size <= buffer.capacity()) {
            size++;
        }
        ByteBuffer newBuffer = ByteBuffer.allocate(size);
        if (flip) {
            buffer.flip();
        }
        newBuffer.put(buffer);
        return newBuffer;
    }

    private ByteBuffer bufferOverflow(ByteBuffer buf, int proposedCapacity) {
        if (proposedCapacity > buf.capacity()) {
            return ByteBuffer.allocate(proposedCapacity);
        }
        return buf.compact();
    }

    private ByteBuffer bufferUnderflow(ByteBuffer buf, int proposedCapacity) {
        if (proposedCapacity <= buf.capacity()) {
            return buf;
        }
        return ByteBuffer.allocate(proposedCapacity).put(buf.flip());
    }

    /**
     * Check if TLS renegotiation happened,
     * if so ssl session id would have changed.
     *
     * @return true if tls was renegotiated
     */
    boolean renegotiated() {
        byte[] currentSessionId = engine.getSession().getId();

        // Intentionally avoiding locking and MessageDigest.isEqual
        if (Arrays.equals(currentSessionId, lastSslSessionId)) {
            return false;
        }

        lastSslSessionId = currentSessionId;
        return true;
    }
}
