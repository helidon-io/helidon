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

package io.helidon.webserver.http3;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.context.Context;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.tls.Tls;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3FrameListener;
import io.helidon.http.http3.Http3GoAway;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.http.http3.Http3Settings;
import io.helidon.http.media.MediaContext;
import io.helidon.quic.QuicCloseCommand;
import io.helidon.quic.QuicConfig;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicRemoteStreamRegistration;
import io.helidon.quic.QuicServerRuntime;
import io.helidon.quic.QuicTermination;
import io.helidon.quic.QuicVersion;
import io.helidon.quic.stream.QuicBidiStream;
import io.helidon.quic.stream.QuicBidiStreamReservation;
import io.helidon.quic.stream.QuicReceiverStream;
import io.helidon.quic.stream.QuicSenderStream;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.tests.http3.Http3TlsSupport;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.ListenerTlsContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.TransportBindingContext;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.spi.TransportBinding;

public final class Http3RawTestServer implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(Http3RawTestServer.class.getName());

    private final ExecutorService executor;
    private final Http3TlsSupport.Http3TlsMaterials tlsMaterials;
    private final String listenerName;
    private final QuicServerRuntime quicServer;
    private final Optional<RawRuntime> runtime;
    private final Consumer<QuicConnection> connectionObserver;
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();

    private Http3RawTestServer(ExecutorService executor,
                               Http3TlsSupport.Http3TlsMaterials tlsMaterials,
                               String listenerName,
                               QuicServerRuntime quicServer,
                               Optional<RawRuntime> runtime,
                               Consumer<QuicConnection> connectionObserver) {
        this.executor = executor;
        this.tlsMaterials = tlsMaterials;
        this.listenerName = Objects.requireNonNull(listenerName, "listenerName");
        this.quicServer = Objects.requireNonNull(quicServer, "quicServer");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.connectionObserver = connectionObserver == null ? _ -> {
        } : connectionObserver;
        acceptLoop();
    }

    public static Http3RawTestServer create(Handler handler) throws Exception {
        return createServer(handler, null, null, -1, 0, 0, -1, -1);
    }

    public static Http3RawTestServer create(Handler handler,
                                            AtomicReference<Http3RawTestServer> serverRef) throws Exception {
        return createServer(handler, serverRef, null, -1, 0, 0, -1, -1);
    }

    public static Http3RawTestServer create(Handler handler,
                                            Consumer<QuicConnection> connectionObserver) throws Exception {
        return createServer(handler, null, connectionObserver, -1, 0, 0, -1, -1);
    }

    public static Http3RawTestServer create(Handler handler,
                                            AtomicReference<Http3RawTestServer> serverRef,
                                            Consumer<QuicConnection> connectionObserver) throws Exception {
        return createServer(handler, serverRef, connectionObserver, -1, 0, 0, -1, -1);
    }

    public static Http3RawTestServer create(Handler handler,
                                            Consumer<QuicConnection> connectionObserver,
                                            long maxBidiStreams) throws Exception {
        return createServer(handler, null, connectionObserver, -1, 0, 0, maxBidiStreams, -1);
    }

    public static Http3RawTestServer create(Handler handler,
                                            Consumer<QuicConnection> connectionObserver,
                                            long maxBidiStreams,
                                            long maxUniStreams) throws Exception {
        return createServer(handler, null, connectionObserver, -1, 0, 0, maxBidiStreams, maxUniStreams);
    }

    public static Http3RawTestServer create(Handler handler,
                                            AtomicReference<Http3RawTestServer> serverRef,
                                            Consumer<QuicConnection> connectionObserver,
                                            long maxBidiStreams) throws Exception {
        return createServer(handler, serverRef, connectionObserver, -1, 0, 0, maxBidiStreams, -1);
    }

    public static Http3RawTestServer create(Handler handler,
                                            AtomicReference<Http3RawTestServer> serverRef,
                                            Consumer<QuicConnection> connectionObserver,
                                            long maxFieldSectionSize,
                                            long qpackMaxTableCapacity,
                                            int qpackBlockedStreams) throws Exception {
        return createServer(handler,
                            serverRef,
                            connectionObserver,
                            maxFieldSectionSize,
                            qpackMaxTableCapacity,
                            qpackBlockedStreams,
                            -1,
                            -1);
    }

    public static Http3RawTestServer createRawPeer(Consumer<QuicConnection> connectionHandler) throws Exception {
        Objects.requireNonNull(connectionHandler, "connectionHandler");
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        QuicServerRuntime quicServer = null;
        try {
            String listenerName = "raw-http3-peer";
            Http3TlsSupport.Http3TlsMaterials tlsMaterials = Http3TlsSupport.load();
            quicServer = createQuicServer(listenerName, executor, tlsMaterials);
            Http3RawTestServer server = new Http3RawTestServer(executor,
                                                               tlsMaterials,
                                                               listenerName,
                                                               quicServer,
                                                               Optional.empty(),
                                                               connectionHandler);
            server.localAddress();
            return server;
        } catch (Exception e) {
            if (quicServer != null) {
                quicServer.close();
            }
            executor.close();
            throw e;
        }
    }

    public static BufferedResponse response(int status, Headers headers, byte[] body) {
        return BufferedResponse.create(status, headers, body);
    }

    public static BufferedResponse text(int status, String body) {
        return BufferedResponse.text(status, body);
    }

    public String baseUri() {
        return "https://localhost:" + localAddress().getPort();
    }

    public Tls clientTlsHttp3() {
        return tlsMaterials.clientTlsHttp3();
    }

    public URI uri(String path) throws Exception {
        return new URI("https", null, "localhost", localAddress().getPort(), path, null, null);
    }

    public CompletableFuture<Void> sendGoAway(QuicConnection connection, long streamId) {
        return runtime.orElseThrow(() -> new IllegalStateException("Raw peer has no managed HTTP/3 runtime"))
                .sendGoAway(connection, Http3GoAway.requestStream(streamId));
    }

    @Override
    public void close() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        try {
            quicServer.stopAccepting();
        } finally {
            try {
                quicServer.close();
            } finally {
                try {
                    runtime.ifPresent(RawRuntime::close);
                } finally {
                    executor.close();
                }
            }
        }
    }

    private static Http3RawTestServer createServer(Handler handler,
                                                   AtomicReference<Http3RawTestServer> serverRef,
                                                   Consumer<QuicConnection> connectionObserver,
                                                   long maxFieldSectionSize,
                                                   long qpackMaxTableCapacity,
                                                   int qpackBlockedStreams,
                                                   long maxBidiStreams,
                                                   long maxUniStreams) throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        QuicServerRuntime quicServer = null;
        RawRuntime runtime = null;
        try {
            String listenerName = "raw-http3";
            Http3TlsSupport.Http3TlsMaterials tlsMaterials = Http3TlsSupport.load();
            runtime = new RawRuntime(executor,
                                     maxFieldSectionSize,
                                     handler,
                                     qpackMaxTableCapacity,
                                     qpackBlockedStreams);
            quicServer = createQuicServer(listenerName,
                                          executor,
                                          tlsMaterials,
                                          maxBidiStreams,
                                          maxUniStreams);

            Http3RawTestServer server = new Http3RawTestServer(executor,
                                                               tlsMaterials,
                                                               listenerName,
                                                               quicServer,
                                                               Optional.of(runtime),
                                                               connectionObserver);
            server.localAddress();
            if (serverRef != null) {
                serverRef.set(server);
            }
            return server;
        } catch (Exception e) {
            if (quicServer != null) {
                quicServer.close();
            }
            if (runtime != null) {
                runtime.close();
            }
            executor.close();
            throw e;
        }
    }

    private static QuicServerRuntime createQuicServer(String listenerName,
                                                      ExecutorService executor,
                                                      Http3TlsSupport.Http3TlsMaterials tlsMaterials) {
        return createQuicServer(listenerName, executor, tlsMaterials, -1, -1);
    }

    private static QuicServerRuntime createQuicServer(String listenerName,
                                                      ExecutorService executor,
                                                      Http3TlsSupport.Http3TlsMaterials tlsMaterials,
                                                      long maxBidiStreams,
                                                      long maxUniStreams) {
        QuicConfig.Builder quicConfig = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1));
        if (maxBidiStreams >= 0) {
            quicConfig.maxBidiStreams(maxBidiStreams);
        }
        if (maxUniStreams >= 0) {
            quicConfig.maxUniStreams(maxUniStreams);
        }
        return QuicServerRuntime.builder()
                .serverId(listenerName)
                .executor(executor)
                .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .tls(tlsMaterials.serverTls())
                .applicationProtocols(List.of(Http3Client.PROTOCOL_ID))
                .applicationErrors(Http3RuntimeSupport.applicationErrors())
                .quicConfig(quicConfig.buildPrototype())
                .build();
    }

    private static Http3Handler asHttp3Handler(Handler handler,
                                               QuicConnection connection,
                                               Map<Long, QuicBidiStream> requestStreams) {
        return new Http3Handler() {
            @Override
            public Optional<Http3Handler.BufferedResponse> handle(Http3ServerStream stream) {
                QuicBidiStream requestStream = Objects.requireNonNull(requestStreams.remove(stream.streamId()),
                                                                      "QUIC request stream");
                Http3RawTestServer.BufferedResponse response = handler.handle(stream.request(),
                                                                             connection,
                                                                             stream.streamId(),
                                                                             new StreamControlAdapter(stream,
                                                                                                      requestStream));
                if (response == null) {
                    return Optional.empty();
                }
                return Optional.of(Http3Handler.BufferedResponse.create(response.status(),
                                                                       response.headers(),
                                                                       response.body()));
            }
        };
    }

    private InetSocketAddress localAddress() {
        return quicServer.localAddress();
    }

    private void acceptLoop() {
        quicServer.accept().whenComplete((connection, throwable) -> {
            if (throwable == null) {
                dispatch(connection);
            } else if (!shutdownStarted.get() && LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                Throwable cause = throwable instanceof CompletionException completionException
                        && completionException.getCause() != null
                        ? completionException.getCause()
                        : throwable;
                LOGGER.log(System.Logger.Level.DEBUG,
                           "Failed to accept raw HTTP/3 connection on listener " + listenerName + ": " + cause,
                           cause);
            }
            if (!shutdownStarted.get() && quicServer.accepting()) {
                acceptLoop();
            }
        });
    }

    private void dispatch(QuicConnection connection) {
        if (runtime.isEmpty()) {
            executor.execute(() -> {
                try {
                    connectionObserver.accept(connection);
                } catch (RuntimeException | Error e) {
                    connection.terminate(QuicCloseCommand.application(
                            Http3ErrorCode.INTERNAL_ERROR.code(),
                            e,
                            "Failed to dispatch raw HTTP/3 peer on listener " + listenerName));
                }
            });
            return;
        }
        try {
            connectionObserver.accept(connection);
            runtime.orElseThrow().accept(connection);
        } catch (RuntimeException | Error e) {
            connection.terminate(QuicCloseCommand.application(
                    Http3ErrorCode.INTERNAL_ERROR.code(),
                    e,
                    "Failed to dispatch raw HTTP/3 connection on listener " + listenerName));
        }
    }

    @FunctionalInterface
    public interface Handler {
        BufferedResponse handle(Http3Protocol.DecodedRequestHead request,
                                QuicConnection connection,
                                long streamId,
                                StreamControl stream);
    }

    public interface StreamControl {
        int writeResponseHeaders(int status, Headers headers, boolean endStream);

        void writeData(byte[] data, boolean endStream);

        void reset(long errorCode);

        boolean stopSendingReceived();

        long stopSendingErrorCode();

        InputStream requestBodyInputStream();
    }

    public record BufferedResponse(int status, Headers headers, byte[] body) {
        public BufferedResponse {
            headers = WritableHeaders.create(Objects.requireNonNull(headers, "headers"));
            body = body == null ? new byte[0] : body.clone();
        }

        public static BufferedResponse create(int status, Headers headers, byte[] body) {
            return new BufferedResponse(status, headers, body);
        }

        public static BufferedResponse text(int status, String body) {
            return create(status,
                          WritableHeaders.create()
                                  .add(HeaderValues.create(HeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8")),
                          body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class RawRuntime implements Http3ServerConnection.StreamLifecycle, AutoCloseable {
        private static final Http3FrameListener NO_OP_FRAME_LISTENER = Http3FrameListener.create(List.of());

        private final TestTransportBindingContext bindingContext;
        private final Handler handler;
        private final Http3Settings localSettings;
        private final ExecutorService executor;
        private final Http3Config config;
        private final Map<QuicConnection, Http3ServerConnection> connections = new ConcurrentHashMap<>();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final ReentrantLock stateLock = new ReentrantLock();

        private RawRuntime(ExecutorService executor,
                           long maxFieldSectionSize,
                           Handler handler,
                           long qpackMaxTableCapacity,
                           int qpackBlockedStreams) {
            this.executor = Objects.requireNonNull(executor, "executor");
            this.handler = Objects.requireNonNull(handler, "handler");
            this.localSettings = maxFieldSectionSize < 0
                    ? Http3Settings.create(qpackMaxTableCapacity, qpackBlockedStreams)
                    : Http3Settings.create(maxFieldSectionSize, qpackMaxTableCapacity, qpackBlockedStreams);
            Http3Config.Builder configBuilder = Http3Config.builder()
                    .qpackMaxTableCapacity(qpackMaxTableCapacity)
                    .qpackBlockedStreams(qpackBlockedStreams);
            if (maxFieldSectionSize >= 0) {
                configBuilder.maxFieldSectionSize(maxFieldSectionSize);
            }
            this.config = configBuilder.buildPrototype();
            this.bindingContext = new TestTransportBindingContext(executor);
        }

        @Override
        public boolean requestStarted(Http3ServerConnection connection, Http3ServerStream stream) {
            return !closed.get() && !connection.rejectsStream(stream.streamId());
        }

        @Override
        public void requestCompleted(Http3ServerConnection connection, Http3ServerStream stream) {
        }

        @Override
        public void close() {
            List<Http3ServerConnection> currentConnections;
            stateLock.lock();
            try {
                if (!closed.compareAndSet(false, true)) {
                    return;
                }
                currentConnections = List.copyOf(connections.values());
                connections.clear();
            } finally {
                stateLock.unlock();
            }
            currentConnections.forEach(Http3ServerConnection::close);
        }

        private void accept(QuicConnection connection) {
            ObservedQuicConnection observedConnection = new ObservedQuicConnection(connection);
            Http3ServerConnection serverConnection = new Http3ServerConnection(
                    bindingContext,
                    observedConnection,
                    ConnectionObservation.noop(),
                    Optional.empty(),
                    localSettings,
                    asHttp3Handler(handler, connection, observedConnection.requestStreams()),
                    NO_OP_FRAME_LISTENER,
                    NO_OP_FRAME_LISTENER,
                    Http3RuntimeSupport.DEFAULT_STREAM_OPEN_TIMEOUT,
                    bindingContext.config().connectionOptions().readTimeout(),
                    config,
                    this,
                    executor);
            boolean accepted = false;
            stateLock.lock();
            try {
                if (!closed.get()) {
                    accepted = connections.putIfAbsent(connection, serverConnection) == null;
                }
            } finally {
                stateLock.unlock();
            }
            if (!accepted) {
                serverConnection.close();
                return;
            }
            connection.whenTerminated().whenComplete((termination, throwable) -> {
                boolean normalTermination = termination != null
                        && termination.cause().isEmpty()
                        && ((termination.kind() == QuicTermination.Kind.CONNECTION_CLOSE
                        && termination.layer() == QuicTermination.Layer.APPLICATION
                        && termination.errorCode().orElse(-1) == Http3ErrorCode.NO_ERROR.code())
                        || termination.kind() == QuicTermination.Kind.SILENT
                        || termination.errorCode().orElse(-1) == 0);
                StreamOutcome outcome = termination != null
                        && (normalTermination
                        || termination.kind() == QuicTermination.Kind.STATELESS_RESET)
                        ? StreamOutcome.CANCELLED
                        : StreamOutcome.ERROR;
                serverConnection.transportTerminated(termination == null ? throwable : termination.closeCause(),
                                                     throwable,
                                                     outcome);
                serverConnection.whenTerminated()
                        .whenComplete((_, _) -> connections.remove(connection, serverConnection));
            });
            serverConnection.start();
        }

        private CompletableFuture<Void> sendGoAway(QuicConnection connection, Http3GoAway goAway) {
            Http3ServerConnection serverConnection = connections.get(connection);
            if (serverConnection == null) {
                throw new IllegalStateException("HTTP/3 connection is not managed by this server");
            }
            return serverConnection.sendGoAway(goAway, "manual");
        }
    }

    private static final class TestTransportBindingContext
            implements TransportBindingContext, ListenerContext {
        private final ExecutorService executor;
        private final Limit requestLimit = FixedLimit.create();
        private final Limit connectionLimit = FixedLimit.create();
        private final Context context = Context.create();
        private final MediaContext mediaContext = MediaContext.create();
        private final ContentEncodingContext contentEncodingContext = ContentEncodingContext.create();
        private final DirectHandlers directHandlers = DirectHandlers.create();
        private final ListenerConfig listenerConfig = ListenerConfig.create();

        private TestTransportBindingContext(ExecutorService executor) {
            this.executor = Objects.requireNonNull(executor, "executor");
        }

        @Override
        public SocketAddress configuredAddress() {
            return new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        }

        @Override
        public ListenerContext listenerContext() {
            return this;
        }

        @Override
        public Router router() {
            return Router.empty();
        }

        @Override
        public Limit requestLimit() {
            return requestLimit;
        }

        @Override
        public Limit connectionLimit() {
            return connectionLimit;
        }

        @Override
        public ListenerTlsContext listenerTls() {
            throw new UnsupportedOperationException("Raw HTTP/3 server owns TLS at the QUIC transport");
        }

        @Override
        public void fatalBindingFailure(TransportBinding binding, Throwable cause) {
            throw new IllegalStateException("Raw HTTP/3 transport binding failed", cause);
        }

        @Override
        public Context context() {
            return context;
        }

        @Override
        public MediaContext mediaContext() {
            return mediaContext;
        }

        @Override
        public ContentEncodingContext contentEncodingContext() {
            return contentEncodingContext;
        }

        @Override
        public DirectHandlers directHandlers() {
            return directHandlers;
        }

        @Override
        public ListenerConfig config() {
            return listenerConfig;
        }

        @Override
        public ExecutorService executor() {
            return executor;
        }
    }

    private static final class ObservedQuicConnection implements QuicConnection {
        private final QuicConnection delegate;
        private final Map<Long, QuicBidiStream> requestStreams = new ConcurrentHashMap<>();

        private ObservedQuicConnection(QuicConnection delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public Optional<String> applicationProtocol() {
            return delegate.applicationProtocol();
        }

        @Override
        public QuicVersion quicVersion() {
            return delegate.quicVersion();
        }

        @Override
        public CompletableFuture<QuicBidiStream> openNewLocalBidiStream(Duration limitIncreaseDuration) {
            return delegate.openNewLocalBidiStream(limitIncreaseDuration);
        }

        @Override
        public CompletableFuture<QuicBidiStreamReservation> reserveNewLocalBidiStream() {
            return delegate.reserveNewLocalBidiStream();
        }

        @Override
        public CompletableFuture<QuicSenderStream> openNewLocalUniStream(Duration limitIncreaseDuration) {
            return delegate.openNewLocalUniStream(limitIncreaseDuration);
        }

        @Override
        public QuicRemoteStreamRegistration addRemoteStreamListener(
                Predicate<? super QuicReceiverStream> listener) {
            return delegate.addRemoteStreamListener(stream -> {
                if (!(stream instanceof QuicBidiStream bidiStream)) {
                    return listener.test(stream);
                }
                requestStreams.put(bidiStream.streamId(), bidiStream);
                try {
                    boolean claimed = listener.test(stream);
                    if (!claimed) {
                        requestStreams.remove(bidiStream.streamId(), bidiStream);
                    }
                    return claimed;
                } catch (RuntimeException | Error e) {
                    requestStreams.remove(bidiStream.streamId(), bidiStream);
                    throw e;
                }
            });
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }

        @Override
        public Optional<QuicTermination> termination() {
            return delegate.termination();
        }

        @Override
        public CompletionStage<QuicTermination> whenTerminated() {
            return delegate.whenTerminated();
        }

        @Override
        public void terminate(QuicCloseCommand command) {
            delegate.terminate(command);
        }

        @Override
        public PeerInfo remotePeer() {
            return delegate.remotePeer();
        }

        @Override
        public PeerInfo localPeer() {
            return delegate.localPeer();
        }

        @Override
        public boolean isSecure() {
            return delegate.isSecure();
        }

        @Override
        public String socketId() {
            return delegate.socketId();
        }

        @Override
        public String childSocketId() {
            return delegate.childSocketId();
        }

        private Map<Long, QuicBidiStream> requestStreams() {
            return requestStreams;
        }
    }

    private static final class StreamControlAdapter implements StreamControl {
        private final Http3ServerStream serverStream;
        private final QuicBidiStream quicStream;

        private StreamControlAdapter(Http3ServerStream serverStream, QuicBidiStream quicStream) {
            this.serverStream = Objects.requireNonNull(serverStream, "serverStream");
            this.quicStream = Objects.requireNonNull(quicStream, "quicStream");
        }

        @Override
        public int writeResponseHeaders(int status, Headers headers, boolean endStream) {
            return serverStream.writeResponseHeaders(status, headers, endStream);
        }

        @Override
        public void writeData(byte[] data, boolean endStream) {
            serverStream.writeData(data, 0, data.length, endStream);
        }

        @Override
        public void reset(long errorCode) {
            serverStream.reset(errorCode);
        }

        @Override
        public boolean stopSendingReceived() {
            return quicStream.stopSendingReceived();
        }

        @Override
        public long stopSendingErrorCode() {
            return quicStream.sndErrorCode();
        }

        @Override
        public InputStream requestBodyInputStream() {
            return new RequestBodyInputStream(serverStream.requestBodyReader(-1));
        }
    }

    private static final class RequestBodyInputStream extends InputStream {
        private static final int DEFAULT_READ_SIZE = 8 * 1024;

        private final Function<Integer, BufferData> reader;
        private BufferData current = BufferData.empty();
        private boolean endOfStream;
        private boolean closed;

        private RequestBodyInputStream(Function<Integer, BufferData> reader) {
            this.reader = Objects.requireNonNull(reader, "reader");
        }

        @Override
        public int read() throws IOException {
            byte[] oneByte = new byte[1];
            return read(oneByte, 0, 1) == -1 ? -1 : Byte.toUnsignedInt(oneByte[0]);
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (closed) {
                throw new IOException("HTTP/3 request body input stream is closed");
            }
            if (length == 0) {
                return 0;
            }
            while (current.available() == 0) {
                if (endOfStream) {
                    return -1;
                }
                current = reader.apply(Math.max(length, DEFAULT_READ_SIZE));
                if (current.available() == 0) {
                    endOfStream = true;
                    return -1;
                }
            }
            return current.read(bytes, offset, Math.min(length, current.available()));
        }

        @Override
        public int available() {
            return closed ? 0 : current.available();
        }

        @Override
        public void close() {
            closed = true;
            current = BufferData.empty();
        }
    }
}
