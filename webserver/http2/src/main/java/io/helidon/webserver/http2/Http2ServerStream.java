/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.http2;

import java.io.UncheckedIOException;
import java.net.SocketException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.common.socket.SocketWriterException;
import io.helidon.http.DirectHandler;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.http.RequestException;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.http2.ConnectionFlowControl;
import io.helidon.http.http2.Http2ConnectionWriter;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Exception;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2Priority;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2Stream;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.http.http2.StreamFlowControl;
import io.helidon.http.http2.WindowSize;
import io.helidon.webserver.CloseConnectionException;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ErrorHandling;
import io.helidon.webserver.Router;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.SniContext;
import io.helidon.webserver.SniRequestSupport;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;
import io.helidon.webserver.http2.spi.SubProtocolResult;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

/**
 * Server HTTP/2 stream implementation.
 */
class Http2ServerStream implements Runnable, Http2Stream {
    private static final DataFrame TERMINATING_FRAME =
            new DataFrame(Http2FrameHeader.create(0,
                                                  Http2FrameTypes.DATA,
                                                  Http2Flag.DataFlags.create(Http2Flag.DataFlags.END_OF_STREAM),
                                                  0), BufferData.empty());
    private static final Runnable NO_OP = () -> { };
    private static final long MAX_LOCALLY_RESET_STREAM_HEADER_BYTES = 64 * 1024;
    private static final int MAX_LOCALLY_RESET_STREAM_HEADER_BLOCKS = 64;
    private static final System.Logger LOGGER = System.getLogger(Http2Stream.class.getName());
    private static final Set<Http2StreamState> DATA_RECEIVABLE_STATES =
            Set.of(Http2StreamState.OPEN, Http2StreamState.HALF_CLOSED_LOCAL);

    private final ConnectionContext ctx;
    private final Http2Config http2Config;
    private final List<Http2SubProtocolSelector> subProviders;
    private final int streamId;
    private final Http2Settings serverSettings;
    private final Http2Settings clientSettings;
    private final Http2StreamWriter writer;
    private final Http2ConnectionWriter connectionWriter;
    private final Router router;
    private final Http2ConnectionChecks connectionAttackVectorMetrics;
    private final LocallyResetStreamTracker locallyResetStreamTracker;
    private final ConnectionFlowControl connectionFlowControl;
    private final InboundDataQueue inboundData;
    private final StreamFlowControl flowControl;
    private final Http2ConcurrentConnectionStreams streams;
    private final HttpRouting routing;
    private final AtomicReference<WriteState> writeState = new AtomicReference<>(WriteState.INIT);
    private final ReentrantLock resetCompletionLock = new ReentrantLock();
    private final ReentrantLock runnerLock = new ReentrantLock();
    private boolean wasLastDataFrame = false;
    private boolean hasEntity = true;
    private volatile Http2Headers headers;
    private volatile LocallyResetStreamState locallyResetStreamState;
    private volatile Http2Priority priority;
    private boolean remoteResetReceived;
    private volatile boolean resetStreamSent;
    private volatile boolean remoteCompleteAfterReset;
    private volatile boolean ignoreInboundDataAfterReset;
    private volatile boolean connectionAborted;
    // used from this instance and from connection
    private volatile Http2StreamState state = Http2StreamState.IDLE;
    private volatile Http2SubProtocolSelector.SubProtocolHandler subProtocolHandler;
    private Thread runnerThread;
    private boolean subProtocolTerminal;
    private Http2RstStream pendingSubProtocolReset;
    private long expectedLength = -1;
    private HttpPrologue prologue;
    // create a limit if accessed before we get the one from connection
    // must be volatile, as it is accessed both from connection thread and from stream thread
    private volatile Limit requestLimit = FixedLimit.create(new Semaphore(1));

    /**
     * A new HTTP/2 server stream.
     *
     * @param ctx                   connection context
     * @param streams               connection streams
     * @param routing               HTTP routing
     * @param http2Config           HTTP/2 configuration
     * @param subProviders          HTTP/2 sub protocol selectors
     * @param streamId              stream id
     * @param serverSettings        server settings
     * @param clientSettings        client settings
     * @param writer                writer
     * @param connectionFlowControl connection flow control
     * @param locallyResetStreamTracker stream reset lifecycle tracker
     * @param inboundDataBudget     connection-wide queued DATA budget
     */
    Http2ServerStream(ConnectionContext ctx,
                      Http2ConcurrentConnectionStreams streams,
                      LocallyResetStreamTracker locallyResetStreamTracker,
                      HttpRouting routing,
                      Http2Config http2Config,
                      List<Http2SubProtocolSelector> subProviders,
                      int streamId,
                      Http2Settings serverSettings,
                      Http2Settings clientSettings,
                      Http2StreamWriter writer,
                      ConnectionFlowControl connectionFlowControl,
                      InboundDataBudget inboundDataBudget,
                      Http2ConnectionChecks connectionAttackVectorMetrics) {
        this.ctx = ctx;
        this.streams = streams;
        this.routing = routing;
        this.http2Config = http2Config;
        this.subProviders = subProviders;
        this.streamId = streamId;
        this.serverSettings = serverSettings;
        this.clientSettings = clientSettings;
        this.writer = writer;
        this.connectionWriter = writer instanceof Http2ConnectionWriter http2ConnectionWriter
                ? http2ConnectionWriter
                : null;
        this.router = ctx.router();
        this.connectionAttackVectorMetrics = connectionAttackVectorMetrics;
        this.locallyResetStreamTracker = Objects.requireNonNull(locallyResetStreamTracker);
        this.connectionFlowControl = connectionFlowControl;
        this.inboundData = new InboundDataQueue(inboundDataBudget);
        this.flowControl = connectionFlowControl.createStreamFlowControl(
                streamId,
                http2Config.initialWindowSize(),
                http2Config.maxFrameSize()
        );
    }

    /**
     * Check if data can be received on this stream.
     * This method is called from connection thread.
     *
     * @throws Http2Exception in case data cannot be received
     */
    public void checkDataReceivable() throws Http2Exception {
        if (resetStreamSent && ignoreInboundDataAfterReset) {
            return;
        }
        if (!DATA_RECEIVABLE_STATES.contains(state)) {
            throw new Http2Exception(Http2ErrorCode.PROTOCOL, "Received data for stream "
                    + streamId + " in state " + state);
        }
    }

    /**
     * Check if headers can be received on this stream.
     * This method is called from connection thread.
     *
     * @return true if headers are receivable as trailers
     * @throws Http2Exception in case headers cannot be received.
     */
    public boolean checkHeadersReceivable() throws Http2Exception {
        switch (state) {
        case IDLE:
            // headers
            return false;
        case OPEN:
            // trailers
            return true;
        case HALF_CLOSED_LOCAL:
        case HALF_CLOSED_REMOTE:
        case CLOSED:
            throw new Http2Exception(Http2ErrorCode.STREAM_CLOSED,
                                     "Stream " + streamId + " received headers when stream is " + state);
        default:
            throw new Http2Exception(Http2ErrorCode.INTERNAL,
                                     "Unknown stream state, streamId: " + streamId + ", state: " + state);
        }
    }

    @Override
    public boolean rstStream(Http2RstStream rstStream) {
        if (state == Http2StreamState.IDLE) {
            throw new Http2Exception(Http2ErrorCode.PROTOCOL,
                                     "Received RST_STREAM for stream "
                                             + streamId + " in IDLE state");
        }
        boolean rapidReset;
        resetCompletionLock.lock();
        try {
            remoteResetReceived = true;
            rapidReset = writeState.get() == WriteState.INIT;
        } finally {
            resetCompletionLock.unlock();
        }
        if (ignoreInboundDataAfterReset) {
            remoteCompleteAfterReset();
        }
        Http2SubProtocolSelector.SubProtocolHandler handler = null;
        runnerLock.lock();
        try {
            state = Http2StreamState.CLOSED;
            if (!subProtocolTerminal) {
                subProtocolTerminal = true;
                if (subProtocolHandler == null) {
                    pendingSubProtocolReset = rstStream;
                } else {
                    handler = subProtocolHandler;
                }
            }
        } finally {
            runnerLock.unlock();
        }
        resetSubProtocol(handler, rstStream);
        abortInboundData();
        return rapidReset;
    }

    void abortConnection() {
        Http2SubProtocolSelector.SubProtocolHandler handler = null;
        Thread runner;
        runnerLock.lock();
        try {
            connectionAborted = true;
            state = Http2StreamState.CLOSED;
            writeState.set(WriteState.END);
            runner = runnerThread;
            if (!subProtocolTerminal) {
                subProtocolTerminal = true;
                if (subProtocolHandler == null) {
                    pendingSubProtocolReset = new Http2RstStream(Http2ErrorCode.CANCEL);
                } else {
                    handler = subProtocolHandler;
                }
            }
        } finally {
            runnerLock.unlock();
        }
        if (runner != null) {
            runner.interrupt();
        }
        resetSubProtocol(handler, new Http2RstStream(Http2ErrorCode.CANCEL));
        inboundData.abortAndDrain();
    }

    private void resetSubProtocol(Http2SubProtocolSelector.SubProtocolHandler handler, Http2RstStream reset) {
        if (handler != null && reset != null) {
            try {
                handler.rstStream(reset);
            } catch (Throwable _) {
                ctx.log(LOGGER, DEBUG, "Failed to cancel subprotocol for closing connection on stream %d", streamId);
            }
        }
    }

    private void cancelSubProtocol(Http2RstStream reset) {
        Http2SubProtocolSelector.SubProtocolHandler handler = null;
        runnerLock.lock();
        try {
            if (!subProtocolTerminal) {
                subProtocolTerminal = true;
                if (subProtocolHandler == null) {
                    pendingSubProtocolReset = reset;
                } else {
                    handler = subProtocolHandler;
                    subProtocolHandler = null;
                }
            }
        } finally {
            runnerLock.unlock();
        }
        resetSubProtocol(handler, reset);
    }

    @Override
    public void windowUpdate(Http2WindowUpdate windowUpdate) {
        try {
            //5.1/3
            if (state == Http2StreamState.IDLE) {
                String msg = "Received WINDOW_UPDATE for stream " + streamId + " in state IDLE";
                connectionAttackVectorMetrics.closeConnection(Http2ErrorCode.PROTOCOL, msg);
            }
            //6.9/2
            if (windowUpdate.windowSizeIncrement() == 0) {
                Http2RstStream frame = new Http2RstStream(Http2ErrorCode.PROTOCOL);
                writer.write(frame.toFrameData(clientSettings, streamId, Http2Flag.NoFlags.create()));
                connectionAttackVectorMetrics.madeYouResetCheck();
            }
            //6.9.1/3
            long size = flowControl.outbound().incrementStreamWindowSize(windowUpdate.windowSizeIncrement());
            if (size > WindowSize.MAX_WIN_SIZE || size < 0L) {
                Http2RstStream frame = new Http2RstStream(Http2ErrorCode.FLOW_CONTROL);
                writer.write(frame.toFrameData(clientSettings, streamId, Http2Flag.NoFlags.create()));
                connectionAttackVectorMetrics.madeYouResetCheck();
            }
        } catch (UncheckedIOException e) {
            throw new ServerConnectionException("Failed to write window update", e);
        }
    }

    // this method is called from connection thread and start the
    // thread o this stream
    @Override
    public void headers(Http2Headers headers, boolean endOfStream) {
        this.headers = headers;
        OptionalLong contentLength = headers.httpHeaders().contentLength();
        if (contentLength.isPresent()) {
            this.expectedLength = contentLength.getAsLong();
            if (expectedLength == 0) {
                hasEntity = false;
            }
        }
        if (endOfStream) {
            hasEntity = false;
            closeFromRemote();
        } else {
            this.state = Http2StreamState.OPEN;
        }
    }

    @Override
    public void data(Http2FrameHeader header, BufferData data, boolean endOfStream) {
        int dataLength = data.available();
        if (ignoreInboundDataAfterReset) {
            discardDataAfterReset(header.length());
            if (endOfStream) {
                remoteCompleteAfterReset();
            }
            return;
        }
        if (!DATA_RECEIVABLE_STATES.contains(state)) {
            if (state == Http2StreamState.CLOSED) {
                restoreDiscardedConnectionCredit(header.length());
            } else {
                flowControl.inbound().incrementWindowSize(header.length());
            }
            return;
        }
        if (expectedLength != -1 && expectedLength < dataLength) {
            resetInvalidContentLength(header.length(), endOfStream);
            return;
        }
        if (expectedLength != -1) {
            expectedLength -= dataLength;
            if (endOfStream && expectedLength != 0) {
                resetInvalidContentLength(header.length(), true);
                return;
            }
        }
        if (dataLength == 0) {
            flowControl.inbound().incrementWindowSize(header.length());
            if (endOfStream) {
                closeFromRemote();
            }
            return;
        }
        enqueueDataAfterPrecheck(header, data, endOfStream);
    }

    // Package-private overloads are deterministic rejection race test seams.
    void enqueueDataAfterPrecheck(Http2FrameHeader header, BufferData data, boolean endOfStream) {
        enqueueDataAfterPrecheck(header, data, endOfStream, NO_OP, NO_OP);
    }

    void enqueueDataAfterPrecheck(Http2FrameHeader header,
                                  BufferData data,
                                  boolean endOfStream,
                                  Runnable afterResetStateSnapshot) {
        enqueueDataAfterPrecheck(header, data, endOfStream, afterResetStateSnapshot, NO_OP);
    }

    void enqueueDataAfterPrecheck(Http2FrameHeader header,
                                  BufferData data,
                                  boolean endOfStream,
                                  Runnable afterResetStateSnapshot,
                                  Runnable afterDataOffer) {
        boolean ignoringInboundDataSnapshot = ignoreInboundDataAfterReset;
        afterResetStateSnapshot.run();
        if (!endOfStream) {
            if (ignoringInboundDataSnapshot || ignoreInboundDataAfterReset) {
                discardDataAfterReset(header.length());
                return;
            }
            InboundDataQueue.OfferResult offerResult = inboundData.offer(header, data);
            if (offerResult == InboundDataQueue.OfferResult.ACCEPTED) {
                afterDataOffer.run();
            }
            restoreDiscardedConnectionCredit(header.length());
            if (offerResult == InboundDataQueue.OfferResult.CLOSED) {
                if (ignoreInboundDataAfterReset && !locallyResetStreamState().discardData(header.length())) {
                    throw new Http2Exception(Http2ErrorCode.ENHANCE_YOUR_CALM,
                                             "Too much data after stream reset.");
                }
                return;
            }
            if (offerResult == InboundDataQueue.OfferResult.BUDGET_EXHAUSTED) {
                closeRejectedStream(Http2ErrorCode.ENHANCE_YOUR_CALM, true, false);
                return;
            }
            if (!DATA_RECEIVABLE_STATES.contains(state)) {
                abortInboundData();
            }
            return;
        }

        boolean budgetExhausted = false;
        boolean discardLimitExceeded = false;
        resetCompletionLock.lock();
        try {
            boolean ignoringInboundData = ignoringInboundDataSnapshot || ignoreInboundDataAfterReset;
            if (ignoringInboundData) {
                discardLimitExceeded = !locallyResetStreamState().discardData(header.length());
                if (endOfStream) {
                    remoteCompleteAfterReset();
                }
            } else {
                InboundDataQueue.OfferResult offerResult = inboundData.offer(header, data);
                if (offerResult == InboundDataQueue.OfferResult.BUDGET_EXHAUSTED) {
                    budgetExhausted = true;
                } else if (offerResult == InboundDataQueue.OfferResult.ACCEPTED) {
                    afterDataOffer.run();
                    if (!DATA_RECEIVABLE_STATES.contains(state)) {
                        abortInboundData();
                    } else {
                        if (state == Http2StreamState.HALF_CLOSED_LOCAL) {
                            state = Http2StreamState.CLOSED;
                        } else {
                            state = Http2StreamState.HALF_CLOSED_REMOTE;
                        }
                    }
                }
            }
        } finally {
            resetCompletionLock.unlock();
        }
        // The connection can accept more bytes as soon as the frame is queued, discarded, or rejected. Stream credit
        // remains withheld for an accepted frame until the application consumes it.
        restoreDiscardedConnectionCredit(header.length());
        if (discardLimitExceeded) {
            throw new Http2Exception(Http2ErrorCode.ENHANCE_YOUR_CALM,
                                     "Too much data after stream reset.");
        }
        if (budgetExhausted) {
            closeRejectedStream(Http2ErrorCode.ENHANCE_YOUR_CALM, true, endOfStream);
        }
    }

    @Override
    public void priority(Http2Priority http2Priority) {
        int i = http2Priority.streamId();
        if (i == this.streamId) {
            throw new Http2Exception(Http2ErrorCode.PROTOCOL, "Stream depends on itself");
        }
        this.priority = http2Priority;
    }

    @Override
    public int streamId() {
        return streamId;
    }

    @Override
    public Http2StreamState streamState() {
        return state;
    }

    @Override
    public StreamFlowControl flowControl() {
        return this.flowControl;
    }

    boolean discardsInboundAfterReset() {
        return ignoreInboundDataAfterReset;
    }

    void headersAfterReset(boolean endOfStream, long headerBlockLength) {
        if (!locallyResetStreamState().discardHeaders(headerBlockLength)) {
            throw new Http2Exception(Http2ErrorCode.ENHANCE_YOUR_CALM,
                                     "Too many headers after stream reset.");
        }
        if (endOfStream) {
            remoteCompleteAfterReset();
        }
    }

    @Override
    public void run() {
        runnerLock.lock();
        try {
            if (connectionAborted || state == Http2StreamState.CLOSED) {
                return;
            }
            runnerThread = Thread.currentThread();
        } finally {
            runnerLock.unlock();
        }
        Thread.currentThread()
                .setName("[" + ctx.socketId() + " "
                                 + ctx.childSocketId() + " ] - " + streamId);
        boolean completed = false;
        boolean connectionFailed = false;
        boolean resetSent = false;
        try {
            handle();
            completed = true;
        } catch (SocketWriterException e) {
            connectionFailed = true;
            throw e;
        } catch (UncheckedIOException e) {
            connectionFailed = e.getCause() instanceof SocketException;
            throw e;
        } catch (CloseConnectionException e) {
            Http2ErrorCode errorCode = e.getCause() instanceof Http2Exception h2Exception
                    ? h2Exception.code()
                    : Http2ErrorCode.STREAM_CLOSED;
            Http2RstStream rst = new Http2RstStream(errorCode);
            try {
                writer.write(rst.toFrameData(serverSettings, streamId, Http2Flag.NoFlags.create()));
                resetSent = true;
            } catch (SocketWriterException | UncheckedIOException writeFailure) {
                connectionFailed = true;
                throw writeFailure;
            }
            // no sense in throwing an exception, as this is invoked from an executor service directly
        } catch (RequestException e) {
            if (!handleRequestException(e)) {
                return;
            }
            completed = true;
        } catch (Http2Exception e) {
            ctx.log(LOGGER, DEBUG, "Intentional HTTP/2 stream exception, code: %s, message: %s",
                    e.code(),
                    e.getMessage());
            closeRejectedStream(e.code(), true);
            completed = true;
        } finally {
            try {
                if (!completed && state != Http2StreamState.CLOSED) {
                    if (!connectionFailed && !resetSent) {
                        Http2RstStream rst = new Http2RstStream(Http2ErrorCode.INTERNAL);
                        try {
                            writer.write(rst.toFrameData(serverSettings, streamId, Http2Flag.NoFlags.create()));
                            resetSent = true;
                        } catch (SocketWriterException | UncheckedIOException writeFailure) {
                            ctx.log(LOGGER, DEBUG, "Failed to reset stream %d after handler failure", streamId);
                        }
                    }
                    if (resetSent) {
                        resetCompletionLock.lock();
                        try {
                            if (!remoteAlreadyComplete()) {
                                ignoreInboundDataAfterReset = true;
                                resetStreamSent = true;
                                locallyResetStreamTracker.add(this.streamId, locallyResetStreamState());
                                if (remoteCompleteAfterReset) {
                                    locallyResetStreamTracker.remoteComplete(this.streamId);
                                }
                            }
                        } finally {
                            resetCompletionLock.unlock();
                        }
                    }
                    state = Http2StreamState.CLOSED;
                    if (resetSent) {
                        locallyResetStreamTracker.localComplete(this.streamId);
                    }
                    streams.remove(this.streamId);
                }
                if (state == Http2StreamState.CLOSED) {
                    abortInboundData();
                }
                headers = null;
            } finally {
                runnerLock.lock();
                try {
                    runnerThread = null;
                    if (!ignoreInboundDataAfterReset || subProtocolTerminal) {
                        subProtocolHandler = null;
                    }
                } finally {
                    runnerLock.unlock();
                }
            }
        }
    }

    private boolean handleRequestException(RequestException exception) {
        if (state == Http2StreamState.CLOSED || writeState.get() == WriteState.END) {
            return false;
        }
        ErrorHandling errorHandling = ctx.listenerContext()
                .config()
                .errorHandling();
        if (LOGGER.isLoggable(DEBUG) && (exception.safeMessage() || errorHandling.logAllMessages())) {
            LOGGER.log(DEBUG, exception);
        }

        String message = null;
        if (errorHandling.includeEntity()) {
            message = exception.safeMessage()
                    ? exception.getMessage()
                    : "Bad request, see server log for more information";
        }

        DirectHandler handler = ctx.listenerContext()
                .directHandlers()
                .handler(exception.eventType());
        DirectHandler.TransportResponse response = handler.handle(exception.request(),
                                                                  exception.eventType(),
                                                                  exception.status(),
                                                                  exception.responseHeaders(),
                                                                  message);

        ServerResponseHeaders headers = response.headers();
        byte[] entity = response.entity().orElse(BufferData.EMPTY_BYTES);
        if (entity.length != 0) {
            headers.set(HeaderValues.create(HeaderNames.CONTENT_LENGTH, String.valueOf(entity.length)));
        }
        Http2Headers http2Headers = Http2Headers.create(headers)
                .status(exception.status());
        boolean resetRequestBody = prepareRejectedStream(false);
        AtomicBoolean rejectedStreamCompleted = new AtomicBoolean();
        Runnable completeRejectedStream = () -> {
            if (rejectedStreamCompleted.compareAndSet(false, true)) {
                completeRejectedStream(Http2ErrorCode.CANCEL, resetRequestBody, false, false);
            }
        };
        try {
            if (entity.length == 0) {
                Http2Flag.HeaderFlags flags =
                        Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM);
                if (connectionWriter == null) {
                    writer.writeHeaders(http2Headers, streamId, flags, flowControl.outbound());
                    completeRejectedStream.run();
                } else {
                    connectionWriter.writeHeaders(http2Headers,
                                                  streamId,
                                                  flags,
                                                  flowControl.outbound(),
                                                  completeRejectedStream);
                }
            } else {
                Http2FrameHeader dataHeader = Http2FrameHeader.create(entity.length,
                                                                      Http2FrameTypes.DATA,
                                                                      Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                                      streamId);
                if (connectionWriter == null) {
                    writer.writeHeaders(http2Headers,
                                        streamId,
                                        Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                        new Http2FrameData(dataHeader, BufferData.create(message)),
                                        flowControl.outbound());
                    completeRejectedStream.run();
                } else {
                    connectionWriter.writeHeaders(http2Headers,
                                                  streamId,
                                                  Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                                  new Http2FrameData(dataHeader, BufferData.create(message)),
                                                  flowControl.outbound(),
                                                  completeRejectedStream);
                }
            }
        } catch (RuntimeException writeFailure) {
            try {
                completeRejectedStream.run();
            } catch (RuntimeException cleanupFailure) {
                writeFailure.addSuppressed(cleanupFailure);
            }
            throw writeFailure;
        }
        return true;
    }

    void closeFromRemote() {
        if (expectedLength != -1 && expectedLength != 0) {
            resetInvalidContentLength(0, true);
            return;
        }
        resetCompletionLock.lock();
        try {
            if (ignoreInboundDataAfterReset) {
                remoteCompleteAfterReset();
                return;
            }
            if (state != Http2StreamState.CLOSED) {
                state = state == Http2StreamState.HALF_CLOSED_LOCAL
                        ? Http2StreamState.CLOSED
                        : Http2StreamState.HALF_CLOSED_REMOTE;
            }
            // we need to notify that there is no data coming
            inboundData.finish();
        } finally {
            resetCompletionLock.unlock();
        }
    }

    int writeHeaders(Http2Headers http2Headers, final boolean endOfStream) {
        writeState.updateAndGet(s -> {
            if (endOfStream) {
                return s.checkAndMove(WriteState.HEADERS_SENT)
                        .checkAndMove(WriteState.END);
            }
            return s.checkAndMove(WriteState.HEADERS_SENT);
        });

        Http2Flag.HeaderFlags flags;

        if (endOfStream) {
            flags = Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM);
        } else {
            flags = Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS);
        }

        try {
            if (endOfStream && connectionWriter != null) {
                return connectionWriter.writeHeaders(http2Headers, streamId, flags, flowControl.outbound(), this::closeFromLocal);
            }
            int written = writer.writeHeaders(http2Headers, streamId, flags, flowControl.outbound());
            if (endOfStream) {
                closeFromLocal();
            }
            return written;
        } catch (UncheckedIOException e) {
            throw new ServerConnectionException("Failed to write headers", e);
        }
    }

    int writeHeadersWithData(Http2Headers http2Headers, int contentLength, BufferData bufferData, boolean endOfStream) {
        writeState.updateAndGet(s -> {
            WriteState newState = s.checkAndMove(WriteState.HEADERS_SENT)
                    .checkAndMove(WriteState.DATA_SENT);
            return endOfStream ? newState.checkAndMove(WriteState.END) : newState;
        });

        Http2FrameData frameData =
                new Http2FrameData(Http2FrameHeader.create(contentLength,
                                                           Http2FrameTypes.DATA,
                                                           Http2Flag.DataFlags.create(endOfStream ? Http2Flag.END_OF_STREAM : 0),
                                                           streamId),
                                   bufferData);
        try {
            return writer.writeHeaders(http2Headers,
                                       streamId,
                                       Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                       flowControl.outbound())
                    + writeDataFrame(frameData, endOfStream);
        } catch (UncheckedIOException e) {
            if (endOfStream) {
                closeFromLocal();
            }
            throw new ServerConnectionException("Failed to write headers", e);
        } catch (RuntimeException e) {
            if (endOfStream) {
                closeFromLocal();
            }
            throw e;
        }
    }

    int writeData(BufferData bufferData, final boolean endOfStream) {
        writeState.updateAndGet(s -> {
            if (endOfStream) {
                return s.checkAndMove(WriteState.DATA_SENT)
                        .checkAndMove(WriteState.END);
            }
            return s.checkAndMove(WriteState.DATA_SENT);
        });

        Http2FrameData frameData =
                new Http2FrameData(Http2FrameHeader.create(bufferData.available(),
                                                           Http2FrameTypes.DATA,
                                                           Http2Flag.DataFlags.create(endOfStream ? Http2Flag.END_OF_STREAM : 0),
                                                           streamId),
                                   bufferData);

        try {
            return writeDataFrame(frameData, endOfStream);
        } catch (UncheckedIOException e) {
            if (endOfStream) {
                closeFromLocal();
            }
            throw new ServerConnectionException("Failed to write frame data", e);
        } catch (RuntimeException e) {
            if (endOfStream) {
                closeFromLocal();
            }
            throw e;
        }
    }

    int writeTrailers(Http2Headers http2trailers) {
        writeState.updateAndGet(s -> s.checkAndMove(WriteState.TRAILERS_SENT));

        try {
            Http2Flag.HeaderFlags flags =
                    Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM);
            if (connectionWriter != null) {
                return connectionWriter.writeHeaders(http2trailers,
                                                     streamId,
                                                     flags,
                                                     flowControl.outbound(),
                                                     this::closeFromLocal);
            }
            int written = writer.writeHeaders(http2trailers, streamId, flags, flowControl.outbound());
            closeFromLocal();
            return written;
        } catch (UncheckedIOException e) {
            throw new ServerConnectionException("Failed to write trailers", e);
        }
    }

    void write100Continue() {
        if (WriteState.EXPECTED_100 == writeState.getAndUpdate(s -> {
            if (WriteState.EXPECTED_100 == s) {
                return s.checkAndMove(WriteState.CONTINUE_100_SENT);
            }
            return s;
        })) {
            Header status = HeaderValues.createCached(Http2Headers.STATUS_NAME, 100);
            Http2Headers http2Headers = Http2Headers.create(WritableHeaders.create().add(status));
            try {
                writer.writeHeaders(http2Headers,
                                    streamId,
                                    Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                    flowControl.outbound());
            } catch (UncheckedIOException e) {
                throw new ServerConnectionException("Failed to write 100-Continue", e);
            }
        }
    }

    void requestLimit(Limit limit) {
        this.requestLimit = limit;
    }

    private void resetInvalidContentLength(int currentFrameLength, boolean endOfStream) {
        Http2RstStream rst = new Http2RstStream(Http2ErrorCode.PROTOCOL);
        boolean sendReset = false;
        resetCompletionLock.lock();
        try {
            ignoreInboundDataAfterReset = true;
            state = Http2StreamState.CLOSED;
            writeState.updateAndGet(s -> s.checkAndMove(WriteState.END));
            sendReset = !resetStreamSent;
            if (sendReset) {
                resetStreamSent = true;
                locallyResetStreamTracker.add(this.streamId, locallyResetStreamState());
            }
            if (endOfStream) {
                locallyResetStreamTracker.remoteComplete(this.streamId);
            }
        } finally {
            resetCompletionLock.unlock();
        }
        try {
            if (currentFrameLength > 0) {
                discardDataAfterReset(currentFrameLength);
            }
            if (sendReset) {
                writer.write(rst.toFrameData(clientSettings, streamId, Http2Flag.NoFlags.create()));
                connectionAttackVectorMetrics.madeYouResetCheck();
            }
        } finally {
            if (sendReset) {
                cancelSubProtocol(rst);
                locallyResetStreamTracker.localComplete(this.streamId);
            }
            streams.remove(this.streamId);
            abortInboundData();
        }
    }

    private void closeFromLocal() {
        if (state == Http2StreamState.HALF_CLOSED_REMOTE || state == Http2StreamState.CLOSED) {
            state = Http2StreamState.CLOSED;
            streams.deactivate(this.streamId);
            abortInboundData();
        } else {
            state = Http2StreamState.HALF_CLOSED_LOCAL;
        }
    }

    void prologue(HttpPrologue prologue) {
        this.prologue = prologue;
    }

    private int writeDataFrame(Http2FrameData frameData, boolean endOfStream) {
        if (connectionWriter == null) {
            writer.writeData(frameData, flowControl.outbound());
            if (endOfStream) {
                closeFromLocal();
            }
            return frameData.header().length() + Http2FrameHeader.LENGTH;
        }
        return connectionWriter.writeData(frameData,
                                          flowControl.outbound(),
                                          endOfStream ? this::closeFromLocal : NO_OP);
    }

    ConnectionContext connectionContext() {
        return this.ctx;
    }

    private BufferData readEntityFromPipeline() {
        write100Continue();
        if (wasLastDataFrame) {
            return BufferData.empty();
        }

        DataFrame frame;
        try {
            frame = inboundData.take();
            if (frame == null) {
                if (connectionAborted) {
                    throw RequestException.builder().message("Connection closed while waiting for request data").build();
                }
                return BufferData.empty();
            }
            inboundData.complete(frame, () -> flowControl.inbound().incrementStreamWindowSize(frame.flowControlLength()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServerConnectionException("Interrupted while waiting for request data", e);
        }

        if (frame.header().flags(Http2FrameTypes.DATA).endOfStream()) {
            wasLastDataFrame = true;
            if (state == Http2StreamState.CLOSED) {
                throw RequestException.builder().message("Stream is closed.").build();
            }
        }
        return frame.data();
    }

    private void closeRejectedStream(Http2ErrorCode resetCode, boolean forceReset) {
        boolean resetRequestBody = prepareRejectedStream(forceReset);
        completeRejectedStream(resetCode, resetRequestBody, forceReset, false);
    }

    private void closeRejectedStream(Http2ErrorCode resetCode, boolean forceReset, boolean remoteEndOfStream) {
        boolean resetRequestBody = prepareRejectedStream(forceReset);
        completeRejectedStream(resetCode, resetRequestBody, forceReset, remoteEndOfStream);
    }

    private void completeRejectedStream(Http2ErrorCode resetCode,
                                        boolean resetRequestBody,
                                        boolean forceReset,
                                        boolean remoteEndOfStream) {
        boolean sendReset = false;
        try {
            resetCompletionLock.lock();
            try {
                writeState.updateAndGet(s -> s.checkAndMove(WriteState.END));
                boolean remoteAlreadyComplete = remoteEndOfStream || remoteAlreadyComplete();
                if (resetRequestBody
                        && !remoteResetReceived
                        && !resetStreamSent
                        && (forceReset || !remoteAlreadyComplete)) {
                    sendReset = true;
                    resetStreamSent = true;
                    if (!remoteEndOfStream) {
                        locallyResetStreamTracker.add(this.streamId, locallyResetStreamState());
                        if (remoteCompleteAfterReset || remoteAlreadyComplete) {
                            locallyResetStreamTracker.remoteComplete(this.streamId);
                        }
                    }
                }
                streams.deactivate(this.streamId);
            } finally {
                resetCompletionLock.unlock();
            }
            if (sendReset) {
                writeResetStream(resetCode);
            }
        } finally {
            if (sendReset) {
                cancelSubProtocol(new Http2RstStream(resetCode));
                locallyResetStreamTracker.localComplete(this.streamId);
            }
            this.state = Http2StreamState.CLOSED;
            streams.remove(this.streamId);
        }
    }

    private boolean remoteAlreadyComplete() {
        return state == Http2StreamState.HALF_CLOSED_REMOTE || state == Http2StreamState.CLOSED;
    }

    private boolean prepareRejectedStream(boolean forceReset) {
        resetCompletionLock.lock();
        try {
            boolean resetRequestBody = forceReset || !remoteAlreadyComplete();
            if (resetRequestBody) {
                locallyResetStreamState();
            }
            ignoreInboundDataAfterReset = resetRequestBody;
            abortInboundData();
            return resetRequestBody;
        } finally {
            resetCompletionLock.unlock();
        }
    }

    private void discardDataAfterReset(int length) {
        connectionFlowControl.incrementInboundConnectionWindowSize(length);
        if (!locallyResetStreamState().discardData(length)) {
            throw new Http2Exception(Http2ErrorCode.ENHANCE_YOUR_CALM,
                                     "Too much data after stream reset.");
        }
    }

    private LocallyResetStreamState locallyResetStreamState() {
        LocallyResetStreamState resetState = locallyResetStreamState;
        if (resetState == null) {
            resetCompletionLock.lock();
            try {
                resetState = locallyResetStreamState;
                if (resetState == null) {
                    long maxHeaderBytes = Math.max(http2Config.maxFrameSize(), MAX_LOCALLY_RESET_STREAM_HEADER_BYTES);
                    resetState = new LocallyResetStreamState(http2Config.initialWindowSize(), maxHeaderBytes);
                    locallyResetStreamState = resetState;
                }
            } finally {
                resetCompletionLock.unlock();
            }
        }
        return resetState;
    }

    private void writeResetStream(Http2ErrorCode resetCode) {
        Http2RstStream rst = new Http2RstStream(resetCode);
        try {
            writer.write(rst.toFrameData(clientSettings, streamId, Http2Flag.NoFlags.create()));
        } catch (SocketWriterException | UncheckedIOException e) {
            throw new ServerConnectionException("Failed to write reset stream", e);
        }
        connectionAttackVectorMetrics.madeYouResetCheck();
    }

    private void remoteCompleteAfterReset() {
        resetCompletionLock.lock();
        try {
            closeIgnoredInboundDataFromRemote();
            remoteCompleteAfterReset = true;
            if (resetStreamSent) {
                locallyResetStreamTracker.remoteComplete(this.streamId);
            }
        } finally {
            resetCompletionLock.unlock();
        }
    }

    private void closeIgnoredInboundDataFromRemote() {
        if (state == Http2StreamState.HALF_CLOSED_LOCAL || state == Http2StreamState.CLOSED) {
            state = Http2StreamState.CLOSED;
        } else {
            state = Http2StreamState.HALF_CLOSED_REMOTE;
        }
    }

    private void restoreDiscardedConnectionCredit(int bytes) {
        if (bytes > 0) {
            connectionFlowControl.incrementInboundConnectionWindowSize(bytes);
        }
    }

    private void abortInboundData() {
        inboundData.abortAndDrain();
    }

    private void handle() {
        Headers httpHeaders = headers.httpHeaders();
        if (httpHeaders.containsToken(HeaderValues.EXPECT_100)) {
            writeState.updateAndGet(s -> s.checkAndMove(WriteState.EXPECTED_100));
        }
        ctx.sniContext().ifPresent(sniContext -> {
            String authority = headers.authority();
            if (authority == null) {
                throw SniRequestSupport.missingAuthority(prologue, httpHeaders);
            }
            SniContext.AuthorityCheck check;
            try {
                check = SniRequestSupport.checkAuthority(sniContext, authority);
            } catch (IllegalArgumentException e) {
                throw new Http2Exception(Http2ErrorCode.PROTOCOL, e.getMessage(), e);
            }
            SniRequestSupport.validateAuthorityCheck(check, prologue, httpHeaders);
        });
        Http2SubProtocolSelector.SubProtocolHandler selectedSubProtocol = null;
        for (Http2SubProtocolSelector provider : subProviders) {
            SubProtocolResult subProtocolResult = provider.subProtocol(ctx,
                                                                       prologue,
                                                                       headers,
                                                                       writer,
                                                                       streamId,
                                                                       serverSettings,
                                                                       clientSettings,
                                                                       flowControl,
                                                                       state, router);
            if (subProtocolResult.supported()) {
                selectedSubProtocol = subProtocolResult.subProtocol();
                break;
            }
        }
        if (selectedSubProtocol == null) {
            if (connectionAborted) {
                return;
            }
            ContentEncodingContext contentEncodingContext = ctx.listenerContext().contentEncodingContext();
            ContentDecoder decoder;
            if (contentEncodingContext.contentDecodingEnabled()) {
                if (httpHeaders.contains(HeaderNames.CONTENT_ENCODING)) {
                    String contentEncoding = httpHeaders.get(HeaderNames.CONTENT_ENCODING).get();
                    if (contentEncodingContext.contentDecodingSupported(contentEncoding)) {
                        decoder = contentEncodingContext.decoder(contentEncoding);
                    } else {
                        throw RequestException.builder()
                                .type(DirectHandler.EventType.OTHER)
                                .status(Status.UNSUPPORTED_MEDIA_TYPE_415)
                                .message("Unsupported content encoding")
                                .build();
                    }
                } else {
                    decoder = ContentDecoder.NO_OP;
                }
            } else {
                decoder = ContentDecoder.NO_OP;
            }
            LimitAlgorithm.Outcome outcome = requestLimit.tryAcquireOutcome(true);
            Http2ServerRequest request = Http2ServerRequest.create(ctx,
                                                                   routing.security(),
                                                                   prologue,
                                                                   headers,
                                                                   decoder,
                                                                   streamId,
                                                                   hasEntity,
                                                                   this::readEntityFromPipeline,
                                                                   outcome,
                                                                   ctx.listenerContext().config().maxPayloadSize(),
                                                                   http2Config.maxBufferedEntitySize().toBytes());
            Http2ServerResponse response = new Http2ServerResponse(this, request,
                                                                   http2Config.validateResponseHeaders());
            try {
                if (outcome.disposition() == LimitAlgorithm.Outcome.Disposition.ACCEPTED) {
                    LimitAlgorithm.Outcome.Accepted accepted = (LimitAlgorithm.Outcome.Accepted) outcome;
                    LimitAlgorithm.Token permit = accepted.token();
                    try {
                        routing.route(ctx, request, response);
                    } finally {
                        if (response.status() == Status.NOT_FOUND_404) {
                            permit.ignore();
                        } else {
                            switch (response.status().family()) {
                            case INFORMATIONAL:
                            case SUCCESSFUL:
                            case REDIRECTION:
                                permit.success();
                                break;
                            default:
                                permit.dropped();
                                break;
                            }
                        }
                    }
                } else {
                    ctx.log(LOGGER, TRACE, "Too many concurrent requests, rejecting request.");
                    response.status(Status.SERVICE_UNAVAILABLE_503)
                            .send("Too Many Concurrent Requests");
                    response.commit();
                }
            } finally {
                try {
                    if (this.state != Http2StreamState.CLOSED) {
                        request.content().consume();
                    }
                } catch (RequestException e) {
                    if (this.state != Http2StreamState.CLOSED) {
                        throw e;
                    }
                }
                if (this.state == Http2StreamState.CLOSED) {
                    // already closed
                } else if (this.state == Http2StreamState.HALF_CLOSED_REMOTE) {
                    this.state = Http2StreamState.CLOSED;
                } else {
                    this.state = Http2StreamState.HALF_CLOSED_LOCAL;
                }
            }
        } else {
            handleSubProtocol(selectedSubProtocol);
        }
    }

    private void handleSubProtocol(Http2SubProtocolSelector.SubProtocolHandler selectedSubProtocol) {
        boolean closed;
        Http2RstStream pendingReset;
        runnerLock.lock();
        try {
            subProtocolHandler = selectedSubProtocol;
            pendingReset = pendingSubProtocolReset;
            pendingSubProtocolReset = null;
            closed = connectionAborted || state == Http2StreamState.CLOSED || subProtocolTerminal;
        } finally {
            runnerLock.unlock();
        }
        resetSubProtocol(selectedSubProtocol, pendingReset);
        if (closed) {
            return;
        }
        selectedSubProtocol.onStreamClosed(() -> {
            runnerLock.lock();
            try {
                subProtocolTerminal = true;
                state = Http2StreamState.CLOSED;
            } finally {
                runnerLock.unlock();
            }
            streams.remove(streamId);
            abortInboundData();
        });
        runnerLock.lock();
        try {
            if (connectionAborted || state == Http2StreamState.CLOSED || subProtocolTerminal) {
                return;
            }
        } finally {
            runnerLock.unlock();
        }
        selectedSubProtocol.init();
        if (updateSubProtocolState(selectedSubProtocol)) {
            return;
        }
        while (this.state != Http2StreamState.CLOSED) {
            DataFrame frame;
            try {
                frame = inboundData.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ServerConnectionException("Interrupted while waiting for subprotocol data", e);
            }
            if (frame == null) {
                updateSubProtocolState(selectedSubProtocol);
                return;
            }
            if (this.state == Http2StreamState.CLOSED) {
                abortInboundData();
                return;
            }
            try {
                selectedSubProtocol.data(frame.header(), frame.data());
                updateSubProtocolState(selectedSubProtocol);
            } finally {
                if (this.state == Http2StreamState.CLOSED) {
                    abortInboundData();
                } else {
                    inboundData.complete(frame,
                                         () -> flowControl.inbound().incrementStreamWindowSize(frame.flowControlLength()));
                }
            }
        }
    }

    private boolean updateSubProtocolState(Http2SubProtocolSelector.SubProtocolHandler handler) {
        Http2StreamState handlerState = handler.streamState();
        runnerLock.lock();
        try {
            if (connectionAborted || state == Http2StreamState.CLOSED || subProtocolTerminal) {
                state = Http2StreamState.CLOSED;
                return true;
            }
            state = handlerState;
            return handlerState == Http2StreamState.CLOSED;
        } finally {
            runnerLock.unlock();
        }
    }

    interface LocallyResetStreamTracker {
        void add(int streamId, LocallyResetStreamState streamState);

        void localComplete(int streamId);

        void remoteComplete(int streamId);
    }

    static final class LocallyResetStreamState {
        private final long maxData;
        private final long maxHeaderBytes;
        private final AtomicLong discardedData = new AtomicLong();
        private final AtomicInteger discardedHeaderBlocks = new AtomicInteger();
        private final AtomicLong discardedHeaderBytes = new AtomicLong();

        private LocallyResetStreamState(long maxData, long maxHeaderBytes) {
            this.maxData = maxData;
            this.maxHeaderBytes = maxHeaderBytes;
        }

        boolean discardData(int length) {
            return discardedData.addAndGet(length) <= maxData;
        }

        boolean discardHeaders(long length) {
            return discardedHeaderBlocks.incrementAndGet() <= MAX_LOCALLY_RESET_STREAM_HEADER_BLOCKS
                    && discardedHeaderBytes.addAndGet(length) <= maxHeaderBytes;
        }

        long discardedData() {
            return discardedData.get();
        }
    }

    private enum WriteState {
        END,
        TRAILERS_SENT(END),
        DATA_SENT(TRAILERS_SENT, END),
        HEADERS_SENT(DATA_SENT, TRAILERS_SENT, END),
        CONTINUE_100_SENT(HEADERS_SENT, END),
        EXPECTED_100(CONTINUE_100_SENT, HEADERS_SENT, END),
        INIT(EXPECTED_100, HEADERS_SENT, END);

        private final Set<WriteState> allowedTransitions;

        WriteState(WriteState... allowedTransitions) {
            this.allowedTransitions = Set.of(allowedTransitions);
        }

        WriteState checkAndMove(WriteState newState) {
            if (this == newState || allowedTransitions.contains(newState)) {
                return newState;
            }

            IllegalStateException badTransitionException =
                    new IllegalStateException("Transition from " + this + " to " + newState + " is not allowed!");
            if (this == END) {
                throw new IllegalStateException("Stream is already closed.", badTransitionException);
            }
            throw badTransitionException;
        }
    }

    /**
     * Connection-wide budget for queued DATA. Bytes are the primary memory bound and frames independently guard
     * against excessive fragmentation.
     */
    static final class InboundDataBudget {
        private final ReentrantLock lock = new ReentrantLock();
        private final int maxFrames;
        private final long maxBytes;
        private int retainedFrames;
        private long retainedBytes;

        InboundDataBudget(int maxFrames, long maxBytes) {
            if (maxFrames < 1 || maxBytes < 1) {
                throw new IllegalArgumentException("Inbound DATA budget limits must be positive.");
            }
            this.maxFrames = maxFrames;
            this.maxBytes = maxBytes;
        }

        boolean tryAcquire(int bytes) {
            lock.lock();
            try {
                if (retainedFrames >= maxFrames || maxBytes - retainedBytes < bytes) {
                    return false;
                }
                retainedFrames++;
                retainedBytes += bytes;
                return true;
            } finally {
                lock.unlock();
            }
        }

        void release(int frames, long bytes) {
            lock.lock();
            try {
                retainedFrames -= frames;
                retainedBytes -= bytes;
                if (retainedFrames < 0 || retainedBytes < 0) {
                    throw new IllegalStateException("Released more queued HTTP/2 DATA than retained.");
                }
            } finally {
                lock.unlock();
            }
        }

        // Package-private budget accessors are test seams for deterministic accounting assertions.
        int availableFrames() {
            lock.lock();
            try {
                return maxFrames - retainedFrames;
            } finally {
                lock.unlock();
            }
        }

        long availableBytes() {
            lock.lock();
            try {
                return maxBytes - retainedBytes;
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Non-blocking connection-to-stream handoff that preserves the original DATA frames.
     */
    static final class InboundDataQueue {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition dataAvailable = lock.newCondition();
        private final ArrayDeque<DataFrame> queue = new ArrayDeque<>();
        private final InboundDataBudget budget;
        private DataFrame inFlight;
        private boolean finished;
        private boolean terminalDelivered;
        private boolean aborted;

        InboundDataQueue(InboundDataBudget budget) {
            this.budget = budget;
        }

        OfferResult offer(Http2FrameHeader header, BufferData data) {
            lock.lock();
            try {
                if (finished || aborted) {
                    return OfferResult.CLOSED;
                }
                if (!budget.tryAcquire(header.length())) {
                    return OfferResult.BUDGET_EXHAUSTED;
                }
                queue.add(new DataFrame(header, data));
                dataAvailable.signal();
                return OfferResult.ACCEPTED;
            } finally {
                lock.unlock();
            }
        }

        DataFrame take() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (queue.isEmpty()) {
                    if (aborted) {
                        return null;
                    }
                    if (finished && !terminalDelivered) {
                        terminalDelivered = true;
                        return TERMINATING_FRAME;
                    }
                    dataAvailable.await();
                }
                DataFrame frame = queue.remove();
                inFlight = frame;
                return frame;
            } finally {
                lock.unlock();
            }
        }

        void complete(DataFrame frame, Runnable consumed) {
            lock.lock();
            try {
                if (inFlight != frame) {
                    return;
                }
                // Clearing inFlight is the completion linearization point. Completion owns the stream-credit callback
                // once it claims the frame; an abort that claimed it first leaves inFlight clear and suppresses the
                // callback. Run the callback outside this lock because it may acquire the connection writer lock.
                inFlight = null;
                budget.release(1, frame.flowControlLength());
            } finally {
                lock.unlock();
            }
            consumed.run();
        }

        void finish() {
            lock.lock();
            try {
                finished = true;
                dataAvailable.signalAll();
            } finally {
                lock.unlock();
            }
        }

        long abortAndDrain() {
            lock.lock();
            try {
                if (aborted) {
                    return 0;
                }
                aborted = true;
                long discarded = 0;
                int permits = 0;
                if (inFlight != null) {
                    discarded = inFlight.flowControlLength();
                    inFlight = null;
                    permits++;
                }
                DataFrame frame;
                while ((frame = queue.poll()) != null) {
                    discarded += frame.flowControlLength();
                    permits++;
                }
                if (permits > 0) {
                    budget.release(permits, discarded);
                }
                dataAvailable.signalAll();
                return discarded;
            } finally {
                lock.unlock();
            }
        }

        enum OfferResult {
            ACCEPTED,
            CLOSED,
            BUDGET_EXHAUSTED
        }
    }

    record DataFrame(Http2FrameHeader header, BufferData data) {
        int flowControlLength() {
            return header.length();
        }
    }
}
