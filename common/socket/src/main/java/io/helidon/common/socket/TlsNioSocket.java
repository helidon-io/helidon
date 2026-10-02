/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
import java.lang.System.Logger.Level;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;

/**
 * TLS NIO helidon socket.
 */
@Api.Internal
public final class TlsNioSocket extends NioSocket {
    private static final System.Logger LOGGER = System.getLogger(TlsNioSocket.class.getName());
    private static final Runnable NO_OP = () -> { };
    private static final int HANDSHAKE_PENDING = 0;
    private static final int HANDSHAKE_COMPLETE = 1;
    private static final int HANDSHAKE_NOTIFIED = 2;
    private static final VarHandle INITIAL_HANDSHAKE_STATE;

    static {
        try {
            INITIAL_HANDSHAKE_STATE = MethodHandles.lookup()
                    .findVarHandle(TlsNioSocket.class, "initialHandshakeState", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final Lock handshakeLock = new ReentrantLock();
    private final SSLEngine engine;
    private final ByteBuffer myAppData;
    private final ByteBuffer emptyHandshakeData = ByteBuffer.allocate(0);
    private final Runnable initialHandshakeCompleted;

    private int unwrapRemaining;
    private ByteBuffer peerAppData;
    private ByteBuffer myNetData;
    private ByteBuffer peerNetData;
    private ByteBuffer replayNetData;
    private boolean closed;

    private volatile PeerInfo localPeer;
    private volatile PeerInfo remotePeer;
    private volatile byte[] lastSslSessionId;
    private volatile int initialHandshakeState;
    private volatile boolean idle;
    private boolean peerAppDataReady;

    private TlsNioSocket(SocketChannel delegate,
                         SSLEngine sslEngine,
                         String channelId,
                         String serverChannelId,
                         ByteBuffer replayNetData,
                         Runnable initialHandshakeCompleted) {
        super(delegate, channelId, serverChannelId);

        this.engine = sslEngine;
        this.replayNetData = replayNetData;
        this.initialHandshakeCompleted = Objects.requireNonNull(initialHandshakeCompleted,
                                                                "initial handshake completion callback");

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
     * @param channelId       connection channel id
     * @param serverChannelId listener channel id
     * @return a new TLS socket
     */
    public static TlsNioSocket server(SocketChannel delegate,
                                      SSLEngine sslEngine,
                                      String channelId,
                                      String serverChannelId) {
        sslEngine.setUseClientMode(false);
        return new TlsNioSocket(delegate,
                                sslEngine,
                                channelId,
                                serverChannelId,
                                null,
                                NO_OP);
    }

    /**
     * Create a server TLS NIO socket which reports completion of its initial handshake.
     *
     * <p>The callback runs at most once, after the initial handshake succeeds and outside the handshake lock.
     * Callback failures are logged and ignored.
     *
     * @param delegate                  underlying socket
     * @param sslEngine                 SSL engine
     * @param channelId                 connection channel id
     * @param serverChannelId           listener channel id
     * @param initialHandshakeCompleted non-blocking initial handshake completion callback
     * @return a new TLS socket
     */
    @Api.Internal
    public static TlsNioSocket server(SocketChannel delegate,
                                      SSLEngine sslEngine,
                                      String channelId,
                                      String serverChannelId,
                                      Runnable initialHandshakeCompleted) {
        sslEngine.setUseClientMode(false);
        return new TlsNioSocket(delegate,
                                sslEngine,
                                channelId,
                                serverChannelId,
                                null,
                                initialHandshakeCompleted);
    }

    /**
     * Create a server TLS NIO socket with already-read TLS network data to replay into the first unwrap.
     * This is intended for server implementations that need to inspect TLS records before creating the engine.
     *
     * @param delegate        underlying socket
     * @param sslEngine       SSL engine
     * @param channelId       connection channel id
     * @param serverChannelId listener channel id
     * @param replayNetData   already-read TLS network data
     * @return a new TLS socket
     */
    @Api.Internal
    public static TlsNioSocket server(SocketChannel delegate,
                                      SSLEngine sslEngine,
                                      String channelId,
                                      String serverChannelId,
                                      ByteBuffer replayNetData) {
        sslEngine.setUseClientMode(false);
        return new TlsNioSocket(delegate,
                                sslEngine,
                                channelId,
                                serverChannelId,
                                Objects.requireNonNull(replayNetData),
                                NO_OP);
    }

    /**
     * Create a server TLS NIO socket with already-read TLS network data which reports completion of its initial
     * handshake.
     *
     * <p>The callback runs at most once, after the initial handshake succeeds and outside the handshake lock.
     * Callback failures are logged and ignored.
     *
     * @param delegate                  underlying socket
     * @param sslEngine                 SSL engine
     * @param channelId                 connection channel id
     * @param serverChannelId           listener channel id
     * @param replayNetData             already-read TLS network data
     * @param initialHandshakeCompleted non-blocking initial handshake completion callback
     * @return a new TLS socket
     */
    @Api.Internal
    public static TlsNioSocket server(SocketChannel delegate,
                                      SSLEngine sslEngine,
                                      String channelId,
                                      String serverChannelId,
                                      ByteBuffer replayNetData,
                                      Runnable initialHandshakeCompleted) {
        sslEngine.setUseClientMode(false);
        return new TlsNioSocket(delegate,
                                sslEngine,
                                channelId,
                                serverChannelId,
                                Objects.requireNonNull(replayNetData, "replay network data"),
                                initialHandshakeCompleted);
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
        return new TlsNioSocket(delegate,
                                sslEngine,
                                channelId,
                                "client",
                                null,
                                NO_OP);
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
            idle = false;
            if (!peerAppDataReady) {
                peerAppData.clear();
            }

            while (peerAppData.position() == 0) {
                SSLEngineResult result = receiveAndUnwrap();
                SSLEngineResult.Status status = result.getStatus();

                if (status == SSLEngineResult.Status.CLOSED) {
                    doClosure();
                    return appBytes();
                }

                SSLEngineResult.HandshakeStatus handshakeStatus = result.getHandshakeStatus();
                if (handshakeFinished(handshakeStatus)) {
                    if (initialHandshakeState != HANDSHAKE_NOTIFIED) {
                        markInitialHandshakeComplete();
                        notifyInitialHandshakeCompletion();
                    }
                } else {
                    doHandshake(handshakeStatus);
                    notifyInitialHandshakeCompletion();
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
    public void write(BufferData buffer) {
        idle = false;
        if (buffer.consumed()) {
            return;
        }
        if (initialHandshakeState != HANDSHAKE_NOTIFIED) {
            handshake();
        }
        // Handshake/closure and normal writes reuse the same TLS staging buffers.
        handshakeLock.lock();
        try {
            doWrite(buffer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            handshakeLock.unlock();
        }
    }

    /**
     * Complete the initial TLS handshake.
     */
    public void handshake() {
        try {
            ensureHandshakeBeforeWrite();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            notifyInitialHandshakeCompletion();
        }
    }

    @Override
    public void close() {
        try {
            idle = false;
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

    @Override
    public void idle() {
        idle = true;
    }

    @Override
    public boolean isConnected() {
        if (closed || !super.isConnected()) {
            return false;
        }
        try {
            return !staleIdleDataAvailable();
        } catch (RuntimeException e) {
            closed = true;
            return false;
        }
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

    private boolean staleIdleDataAvailable() {
        if (!idle) {
            return false;
        }

        handshakeLock.lock();
        try {
            if (!idle) {
                return false;
            }
            if (closed) {
                return true;
            }

            peerAppData.clear();
            if (unwrapRemaining > 0) {
                peerNetData.compact();
                peerNetData.flip();
                unwrapRemaining = 0;
                return unwrapIdleDataAvailable();
            }

            peerNetData.clear();
            int read = readNonBlocking(peerNetData);
            if (read == 0) {
                return false;
            }
            if (read < 0) {
                closed = true;
                return true;
            }

            peerNetData.flip();
            return unwrapIdleDataAvailable();
        } catch (IOException e) {
            closed = true;
            return true;
        } finally {
            handshakeLock.unlock();
        }
    }

    private boolean unwrapIdleDataAvailable() throws SSLException {
        while (true) {
            SSLEngineResult result = engine.unwrap(peerNetData, peerAppData);
            SSLEngineResult.Status status = result.getStatus();
            if (status == SSLEngineResult.Status.CLOSED) {
                closed = true;
                return true;
            }
            if (status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                closed = true;
                return true;
            }
            if (status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                peerAppData = reallocate(peerAppData, engine.getSession().getApplicationBufferSize(), true);
                continue;
            }

            unwrapRemaining = peerNetData.remaining();
            if (peerAppData.position() > 0) {
                closed = true;
                return true;
            }
            if (!peerNetData.hasRemaining()) {
                return false;
            }
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
        } else if (replayNetData != null) {
            prepareReplayNetData(false);
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
                if (replayNetData != null) {
                    prepareReplayNetData(true);
                    needData = false;
                } else if (peerNetData.limit() == peerNetData.capacity()) {
                    peerNetData = reallocate(peerNetData, engine.getSession().getPacketBufferSize(), false);
                    needData = true;
                } else {
                    peerNetData.position(peerNetData.limit());
                    peerNetData.limit(peerNetData.capacity());
                    needData = true;
                }
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

    private void prepareReplayNetData(boolean append) {
        ByteBuffer replay = replayNetData;
        if (append) {
            if (peerNetData.remaining() == peerNetData.capacity()) {
                peerNetData = reallocate(peerNetData, engine.getSession().getPacketBufferSize(), false);
            } else {
                peerNetData.compact();
            }
        } else {
            peerNetData.clear();
        }
        int chunk = Math.min(peerNetData.remaining(), replay.remaining());
        int replayLimit = replay.limit();
        replay.limit(replay.position() + chunk);
        peerNetData.put(replay);
        replay.limit(replayLimit);
        peerNetData.flip();
        if (!replay.hasRemaining()) {
            replayNetData = null;
        }
    }

    private byte[] appBytes() {
        peerAppDataReady = false;
        peerAppData.flip();
        byte[] result = new byte[peerAppData.remaining()];
        peerAppData.get(result);
        return result;
    }

    private void doHandshake(SSLEngineResult.HandshakeStatus handshakeStatus) throws IOException {

        SSLEngineResult.HandshakeStatus status = handshakeStatus;
        try {
            handshakeLock.lock();

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
                    emptyHandshakeData.clear();
                    result = wrapAndSend(emptyHandshakeData, false);
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
            if (peerAppData.position() > 0) {
                peerAppDataReady = true;
            }
            if (!closed) {
                markInitialHandshakeComplete();
            }
        } finally {
            handshakeLock.unlock();
        }
    }

    private void doWrite(BufferData buffer) throws IOException {
        if (buffer.consumed()) {
            return;
        }
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

    private void ensureHandshakeBeforeWrite() throws IOException {
        if (initialHandshakeState != HANDSHAKE_PENDING) {
            return;
        }

        handshakeLock.lock();
        try {
            if (initialHandshakeState != HANDSHAKE_PENDING) {
                return;
            }
            SSLEngineResult.HandshakeStatus status = engine.getHandshakeStatus();
            if (status == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                engine.beginHandshake();
                status = engine.getHandshakeStatus();
            }
            doHandshake(status);
        } finally {
            handshakeLock.unlock();
        }
    }

    private void markInitialHandshakeComplete() {
        if (initialHandshakeState == HANDSHAKE_PENDING) {
            int completedState = initialHandshakeCompleted == NO_OP ? HANDSHAKE_NOTIFIED : HANDSHAKE_COMPLETE;
            INITIAL_HANDSHAKE_STATE.compareAndSet(this, HANDSHAKE_PENDING, completedState);
        }
    }

    private void notifyInitialHandshakeCompletion() {
        if (initialHandshakeState != HANDSHAKE_COMPLETE
                || !INITIAL_HANDSHAKE_STATE.compareAndSet(this, HANDSHAKE_COMPLETE, HANDSHAKE_NOTIFIED)) {
            return;
        }
        try {
            initialHandshakeCompleted.run();
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING, "Initial TLS handshake completion callback failed", failure);
        }
    }

    private static boolean handshakeFinished(SSLEngineResult.HandshakeStatus status) {
        return status == SSLEngineResult.HandshakeStatus.FINISHED
                || status == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
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
