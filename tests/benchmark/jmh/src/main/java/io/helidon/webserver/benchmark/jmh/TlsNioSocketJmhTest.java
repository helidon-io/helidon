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

package io.helidon.webserver.benchmark.jmh;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.Principal;
import java.security.cert.Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.TlsNioSocket;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Benchmark)
public class TlsNioSocketJmhTest {
    private ServerSocketChannel server;
    private SocketChannel clientChannel;
    private SocketChannel serverChannel;
    private TlsNioSocket socket;

    @Setup
    public void setup() throws IOException {
        server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        clientChannel = SocketChannel.open(server.getLocalAddress());
        serverChannel = server.accept();
        socket = TlsNioSocket.client(clientChannel, new IdleSslEngine(), "client");
    }

    @TearDown
    public void tearDown() throws IOException {
        clientChannel.close();
        serverChannel.close();
        server.close();
    }

    @Benchmark
    public boolean tlsIdleIsConnected() {
        socket.idle();
        return socket.isConnected();
    }

    @Benchmark
    public byte[] tlsReplayFirstUnwrap(ReplayState state) {
        return state.socket.get();
    }

    @Benchmark
    public TlsNioSocket tlsInitialHandshake(InitialHandshakeState state) {
        TlsNioSocket socket = state.newSocket();
        socket.handshake();
        return socket;
    }

    @Benchmark
    public byte[] tlsInitialRead(InitialReadState state) {
        return state.newSocket().get();
    }

    @Benchmark
    public void tlsInitialWrite(InitialWriteState state) {
        state.newSocket().write(state.buffer);
    }

    @Benchmark
    public byte[] tlsPostHandshakeRead(PostHandshakeReadState state) {
        return state.socket.get();
    }

    @Benchmark
    public void tlsPostHandshakeWrite(PostHandshakeWriteState state) {
        state.socket.write(state.buffer);
    }

    @State(Scope.Thread)
    public static class InitialHandshakeState {
        private final SocketPair socketPair = new SocketPair();

        @Setup(Level.Trial)
        public void setup() throws IOException {
            socketPair.open();
        }

        @Setup(Level.Invocation)
        public void supplyHandshakeData() throws IOException {
            socketPair.supply(1);
        }

        @TearDown(Level.Invocation)
        public void drainHandshakeData() throws IOException {
            socketPair.drain(1);
        }

        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            socketPair.close();
        }

        private TlsNioSocket newSocket() {
            return socketPair.newSocket();
        }
    }

    @State(Scope.Thread)
    public static class InitialReadState {
        private final SocketPair socketPair = new SocketPair();

        @Setup(Level.Trial)
        public void setup() throws IOException {
            socketPair.open();
        }

        @Setup(Level.Invocation)
        public void supplyHandshakeAndApplicationData() throws IOException {
            socketPair.supply(3);
        }

        @TearDown(Level.Invocation)
        public void drainHandshakeData() throws IOException {
            socketPair.drain(1);
        }

        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            socketPair.close();
        }

        private TlsNioSocket newSocket() {
            return socketPair.newSocket();
        }
    }

    @State(Scope.Thread)
    public static class InitialWriteState {
        private static final byte[] APPLICATION_DATA = {(byte) 'W'};

        private final SocketPair socketPair = new SocketPair();
        private final BufferData buffer = BufferData.create(APPLICATION_DATA);

        @Setup(Level.Trial)
        public void setup() throws IOException {
            socketPair.open();
        }

        @Setup(Level.Invocation)
        public void supplyHandshakeData() throws IOException {
            buffer.rewind();
            socketPair.supply(1);
        }

        @TearDown(Level.Invocation)
        public void drainHandshakeAndApplicationData() throws IOException {
            socketPair.drain(2);
        }

        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            socketPair.close();
        }

        private TlsNioSocket newSocket() {
            return socketPair.newSocket();
        }
    }

    @State(Scope.Thread)
    public static class PostHandshakeReadState {
        private final SocketPair socketPair = new SocketPair();
        private TlsNioSocket socket;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            socketPair.open();
            socketPair.supply(1);
            socket = socketPair.newSocket();
            socket.handshake();
            socketPair.drain(1);
        }

        @Setup(Level.Invocation)
        public void supplyNetworkData() throws IOException {
            socketPair.supply(1);
        }

        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            socketPair.close();
        }
    }

    @State(Scope.Thread)
    public static class PostHandshakeWriteState {
        private static final byte[] APPLICATION_DATA = {(byte) 'W'};

        private final SocketPair socketPair = new SocketPair();
        private final BufferData buffer = BufferData.create(APPLICATION_DATA);
        private TlsNioSocket socket;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            socketPair.open();
            socketPair.supply(1);
            socket = socketPair.newSocket();
            socket.handshake();
            socketPair.drain(1);
        }

        @Setup(Level.Invocation)
        public void prepareApplicationData() {
            buffer.rewind();
        }

        @TearDown(Level.Invocation)
        public void drainNetworkData() throws IOException {
            socketPair.drain(1);
        }

        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            socketPair.close();
        }
    }

    @State(Scope.Thread)
    public static class ReplayState {
        private static final byte[] REPLAY = new byte[32];

        private ServerSocketChannel server;
        private SocketChannel clientChannel;
        private SocketChannel serverChannel;
        private TlsNioSocket socket;

        @Setup(Level.Invocation)
        public void setup() throws IOException {
            server = ServerSocketChannel.open();
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            clientChannel = SocketChannel.open(server.getLocalAddress());
            serverChannel = server.accept();
            socket = TlsNioSocket.server(serverChannel,
                                         new ReplaySslEngine(REPLAY.length),
                                         "listener",
                                         "server",
                                         ByteBuffer.wrap(REPLAY));
        }

        @TearDown(Level.Invocation)
        public void tearDown() throws IOException {
            clientChannel.close();
            serverChannel.close();
            server.close();
        }
    }

    private static class IdleSslEngine extends SSLEngine {
        private static final SSLSession SESSION = new IdleSslSession();

        @Override
        public SSLEngineResult wrap(ByteBuffer[] srcs, int offset, int length, ByteBuffer dst) {
            return new SSLEngineResult(SSLEngineResult.Status.OK,
                                       SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING,
                                       0,
                                       0);
        }

        @Override
        public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts, int offset, int length) {
            return new SSLEngineResult(SSLEngineResult.Status.BUFFER_UNDERFLOW,
                                       SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING,
                                       0,
                                       0);
        }

        @Override
        public Runnable getDelegatedTask() {
            return null;
        }

        @Override
        public void closeInbound() {
        }

        @Override
        public boolean isInboundDone() {
            return false;
        }

        @Override
        public void closeOutbound() {
        }

        @Override
        public boolean isOutboundDone() {
            return false;
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return new String[0];
        }

        @Override
        public String[] getEnabledCipherSuites() {
            return new String[0];
        }

        @Override
        public void setEnabledCipherSuites(String[] suites) {
        }

        @Override
        public String[] getSupportedProtocols() {
            return new String[0];
        }

        @Override
        public String[] getEnabledProtocols() {
            return new String[0];
        }

        @Override
        public void setEnabledProtocols(String[] protocols) {
        }

        @Override
        public SSLSession getSession() {
            return SESSION;
        }

        @Override
        public void beginHandshake() {
        }

        @Override
        public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
            return SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
        }

        @Override
        public void setUseClientMode(boolean mode) {
        }

        @Override
        public boolean getUseClientMode() {
            return true;
        }

        @Override
        public void setNeedClientAuth(boolean need) {
        }

        @Override
        public boolean getNeedClientAuth() {
            return false;
        }

        @Override
        public void setWantClientAuth(boolean want) {
        }

        @Override
        public boolean getWantClientAuth() {
            return false;
        }

        @Override
        public void setEnableSessionCreation(boolean flag) {
        }

        @Override
        public boolean getEnableSessionCreation() {
            return false;
        }
    }

    private static final class ReplaySslEngine extends IdleSslEngine {
        private final int replayLength;
        private int consumed;

        private ReplaySslEngine(int replayLength) {
            this.replayLength = replayLength;
        }

        @Override
        public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts, int offset, int length) {
            int available = src.remaining();
            src.position(src.limit());
            consumed += available;
            if (consumed < replayLength) {
                return new SSLEngineResult(SSLEngineResult.Status.BUFFER_UNDERFLOW,
                                           SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING,
                                           available,
                                           0);
            }

            dsts[offset].put((byte) 'R');
            return new SSLEngineResult(SSLEngineResult.Status.OK,
                                       SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING,
                                       available,
                                       1);
        }
    }

    private static final class SocketPair {
        private static final long TRANSFER_TIMEOUT_NANOS = 5_000_000_000L;

        private final ByteBuffer networkData = ByteBuffer.allocate(3);

        private ServerSocketChannel server;
        private SocketChannel clientChannel;
        private SocketChannel serverChannel;

        private void open() throws IOException {
            server = ServerSocketChannel.open();
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            clientChannel = SocketChannel.open(server.getLocalAddress());
            serverChannel = server.accept();
            clientChannel.configureBlocking(false);
        }

        private TlsNioSocket newSocket() {
            return TlsNioSocket.server(serverChannel, new ScriptedSslEngine(), "listener", "server");
        }

        private void supply(int byteCount) throws IOException {
            networkData.clear();
            networkData.limit(byteCount);
            while (networkData.hasRemaining()) {
                networkData.put(networkData.remaining() == 1 ? (byte) 'R' : (byte) 'H');
            }
            networkData.flip();
            writeFully(clientChannel, networkData);
        }

        private void drain(int byteCount) throws IOException {
            networkData.clear();
            networkData.limit(byteCount);
            readFully(clientChannel, networkData);
        }

        private void close() throws IOException {
            clientChannel.close();
            serverChannel.close();
            server.close();
        }

        private static void writeFully(SocketChannel channel, ByteBuffer buffer) throws IOException {
            long deadline = System.nanoTime() + TRANSFER_TIMEOUT_NANOS;
            while (buffer.hasRemaining() && System.nanoTime() < deadline) {
                int transferred = channel.write(buffer);
                if (transferred < 0) {
                    throw new IOException("TLS benchmark peer closed while supplying data");
                }
                if (transferred == 0) {
                    Thread.onSpinWait();
                }
            }
            if (buffer.hasRemaining()) {
                throw new IOException("Timed out while supplying TLS benchmark peer data");
            }
        }

        private static void readFully(SocketChannel channel, ByteBuffer buffer) throws IOException {
            long deadline = System.nanoTime() + TRANSFER_TIMEOUT_NANOS;
            while (buffer.hasRemaining() && System.nanoTime() < deadline) {
                int transferred = channel.read(buffer);
                if (transferred < 0) {
                    throw new IOException("TLS benchmark peer closed while draining data");
                }
                if (transferred == 0) {
                    Thread.onSpinWait();
                }
            }
            if (buffer.hasRemaining()) {
                throw new IOException("Timed out while draining TLS benchmark peer data");
            }
        }
    }

    private static final class ScriptedSslEngine extends IdleSslEngine {
        private static final SSLEngineResult START_HANDSHAKE_RESULT =
                new SSLEngineResult(SSLEngineResult.Status.OK,
                                    SSLEngineResult.HandshakeStatus.NEED_TASK,
                                    1,
                                    0);
        private static final SSLEngineResult HANDSHAKE_WRAP_RESULT =
                new SSLEngineResult(SSLEngineResult.Status.OK,
                                    SSLEngineResult.HandshakeStatus.NEED_UNWRAP,
                                    0,
                                    1);
        private static final SSLEngineResult FINISHED_RESULT =
                new SSLEngineResult(SSLEngineResult.Status.OK,
                                    SSLEngineResult.HandshakeStatus.FINISHED,
                                    1,
                                    0);
        private static final SSLEngineResult APPLICATION_RESULT =
                new SSLEngineResult(SSLEngineResult.Status.OK,
                                    SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING,
                                    1,
                                    1);
        private static final SSLEngineResult EMPTY_RESULT =
                new SSLEngineResult(SSLEngineResult.Status.OK,
                                    SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING,
                                    0,
                                    0);

        private final Runnable delegatedTask = () -> state = EngineState.NEED_WRAP;

        private EngineState state = EngineState.NEW;
        private boolean delegatedTaskAvailable;
        private boolean clientMode;

        @Override
        public SSLEngineResult wrap(ByteBuffer[] srcs, int offset, int length, ByteBuffer dst) {
            if (state == EngineState.NEED_WRAP) {
                dst.put((byte) 'H');
                state = EngineState.NEED_UNWRAP;
                return HANDSHAKE_WRAP_RESULT;
            }
            ByteBuffer src = srcs[offset];
            if (!src.hasRemaining()) {
                return EMPTY_RESULT;
            }
            dst.put(src.get());
            return APPLICATION_RESULT;
        }

        @Override
        public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts, int offset, int length) {
            if (state == EngineState.NEW) {
                src.get();
                state = EngineState.NEED_TASK;
                delegatedTaskAvailable = true;
                return START_HANDSHAKE_RESULT;
            }
            if (state == EngineState.NEED_UNWRAP) {
                src.get();
                state = EngineState.READY;
                return FINISHED_RESULT;
            }
            dsts[offset].put(src.get());
            return APPLICATION_RESULT;
        }

        @Override
        public Runnable getDelegatedTask() {
            if (state == EngineState.NEED_TASK && delegatedTaskAvailable) {
                delegatedTaskAvailable = false;
                return delegatedTask;
            }
            return null;
        }

        @Override
        public void beginHandshake() {
            if (state != EngineState.NEW) {
                throw new IllegalStateException("Unexpected TLS benchmark handshake state: " + state);
            }
            state = EngineState.NEED_TASK;
            delegatedTaskAvailable = true;
        }

        @Override
        public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
            return switch (state) {
            case NEW, READY -> SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
            case NEED_TASK -> SSLEngineResult.HandshakeStatus.NEED_TASK;
            case NEED_WRAP -> SSLEngineResult.HandshakeStatus.NEED_WRAP;
            case NEED_UNWRAP -> SSLEngineResult.HandshakeStatus.NEED_UNWRAP;
            };
        }

        @Override
        public void setUseClientMode(boolean mode) {
            clientMode = mode;
        }

        @Override
        public boolean getUseClientMode() {
            return clientMode;
        }

        private enum EngineState {
            NEW,
            NEED_TASK,
            NEED_WRAP,
            NEED_UNWRAP,
            READY
        }
    }

    @SuppressWarnings("removal")
    private static final class IdleSslSession implements SSLSession {
        @Override
        public byte[] getId() {
            return new byte[0];
        }

        @Override
        public SSLSessionContext getSessionContext() {
            return null;
        }

        @Override
        public long getCreationTime() {
            return 0;
        }

        @Override
        public long getLastAccessedTime() {
            return 0;
        }

        @Override
        public void invalidate() {
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public void putValue(String name, Object value) {
        }

        @Override
        public Object getValue(String name) {
            return null;
        }

        @Override
        public void removeValue(String name) {
        }

        @Override
        public String[] getValueNames() {
            return new String[0];
        }

        @Override
        public Certificate[] getPeerCertificates() {
            return new Certificate[0];
        }

        @Override
        public Certificate[] getLocalCertificates() {
            return new Certificate[0];
        }

        @Override
        public javax.security.cert.X509Certificate[] getPeerCertificateChain() {
            return new javax.security.cert.X509Certificate[0];
        }

        @Override
        public Principal getPeerPrincipal() {
            return null;
        }

        @Override
        public Principal getLocalPrincipal() {
            return null;
        }

        @Override
        public String getCipherSuite() {
            return "";
        }

        @Override
        public String getProtocol() {
            return "";
        }

        @Override
        public String getPeerHost() {
            return "";
        }

        @Override
        public int getPeerPort() {
            return 0;
        }

        @Override
        public int getPacketBufferSize() {
            return 8;
        }

        @Override
        public int getApplicationBufferSize() {
            return 8;
        }
    }
}
