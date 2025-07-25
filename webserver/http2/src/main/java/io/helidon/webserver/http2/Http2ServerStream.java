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

package io.helidon.webserver.http2;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

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
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Exception;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2GoAway;
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
import io.helidon.service.registry.Services;
import io.helidon.webserver.CloseConnectionException;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ErrorHandling;
import io.helidon.webserver.Router;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.spi.HttpLimitListenerProvider;
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
    private final Router router;
    private final ArrayBlockingQueue<DataFrame> inboundData = new ArrayBlockingQueue<>(32);
    private final StreamFlowControl flowControl;
    private final Http2ConcurrentConnectionStreams streams;
    private final HttpRouting routing;
    private final AtomicReference<WriteState> writeState = new AtomicReference<>(WriteState.INIT);
    private final List<HttpLimitListenerProvider>  limitListenerProviders;
    private boolean wasLastDataFrame = false;
    private volatile Http2Headers headers;
    private volatile Http2Priority priority;
    // used from this instance and from connection
    private volatile Http2StreamState state = Http2StreamState.IDLE;
    private Http2SubProtocolSelector.SubProtocolHandler subProtocolHandler;
    private long expectedLength = -1;
    private HttpPrologue prologue;
    // create a limit if accessed before we get the one from connection
    // must be volatile, as it is accessed both from connection thread and from stream thread
    private volatile Limit requestLimit = FixedLimit.create(new Semaphore(1));

    /**
     * A new HTTP/2 server stream.
     *
     * @param ctx                   connection context
     * @param streams
     * @param routing               HTTP routing
     * @param http2Config           HTTP/2 configuration
     * @param subProviders          HTTP/2 sub protocol selectors
     * @param streamId              stream id
     * @param serverSettings        server settings
     * @param clientSettings        client settings
     * @param writer                writer
     * @param connectionFlowControl connection flow control
     */
    Http2ServerStream(ConnectionContext ctx,
                      Http2ConcurrentConnectionStreams streams,
                      HttpRouting routing,
                      Http2Config http2Config,
                      List<Http2SubProtocolSelector> subProviders,
                      int streamId,
                      Http2Settings serverSettings,
                      Http2Settings clientSettings,
                      Http2StreamWriter writer,
                      ConnectionFlowControl connectionFlowControl) {
        this.ctx = ctx;
        this.streams = streams;
        this.routing = routing;
        this.http2Config = http2Config;
        this.subProviders = subProviders;
        this.streamId = streamId;
        this.serverSettings = serverSettings;
        this.clientSettings = clientSettings;
        this.writer = writer;
        this.router = ctx.router();
        this.flowControl = connectionFlowControl.createStreamFlowControl(
                streamId,
                http2Config.initialWindowSize(),
                http2Config.maxFrameSize()
        );
        this.limitListenerProviders = Services.all(HttpLimitListenerProvider.class);
    }

    /**
     * Check if data can be received on this stream.
     * This method is called from connection thread.
     *
     * @throws Http2Exception in case data cannot be received
     */
    public void checkDataReceivable() throws Http2Exception {
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
        if (subProtocolHandler != null) {
            subProtocolHandler.rstStream(rstStream);
        }
        boolean rapidReset = writeState.get() == WriteState.INIT;
        this.state = Http2StreamState.CLOSED;
        return rapidReset;
    }

    @Override
    public void windowUpdate(Http2WindowUpdate windowUpdate) {
        try {
            //5.1/3
            if (state == Http2StreamState.IDLE) {
                String msg = "Received WINDOW_UPDATE for stream " + streamId + " in state IDLE";
                Http2GoAway frame = new Http2GoAway(0, Http2ErrorCode.PROTOCOL, msg);
                writer.write(frame.toFrameData(clientSettings, 0, Http2Flag.NoFlags.create()));
                throw new Http2Exception(Http2ErrorCode.PROTOCOL, msg);
            }
            //6.9/2
            if (windowUpdate.windowSizeIncrement() == 0) {
                Http2RstStream frame = new Http2RstStream(Http2ErrorCode.PROTOCOL);
                writer.write(frame.toFrameData(clientSettings, streamId, Http2Flag.NoFlags.create()));
            }
            //6.9.1/3
            long size = flowControl.outbound().incrementStreamWindowSize(windowUpdate.windowSizeIncrement());
            if (size > WindowSize.MAX_WIN_SIZE || size < 0L) {
                Http2RstStream frame = new Http2RstStream(Http2ErrorCode.FLOW_CONTROL);
                writer.write(frame.toFrameData(clientSettings, streamId, Http2Flag.NoFlags.create()));
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
        if (endOfStream) {
            closeFromRemote();
        } else {
            this.state = Http2StreamState.OPEN;
        }
        Headers httpHeaders = headers.httpHeaders();
        if (httpHeaders.contains(HeaderNames.CONTENT_LENGTH)) {
            this.expectedLength = httpHeaders.get(HeaderNames.CONTENT_LENGTH).get(long.class);
        }
    }

    @Override
    public void data(Http2FrameHeader header, BufferData data, boolean endOfStream) {
        if (expectedLength != -1 && expectedLength < header.length()) {
            state = Http2StreamState.CLOSED;
            writeState.updateAndGet(s -> s.checkAndMove(WriteState.END));
            streams.remove(this.streamId);
            Http2RstStream rst = new Http2RstStream(Http2ErrorCode.PROTOCOL);
            writer.write(rst.toFrameData(clientSettings, streamId, Http2Flag.NoFlags.create()));

            try {
                // we need to notify that there is no data coming
                inboundData.put(TERMINATING_FRAME);
            } catch (InterruptedException e) {
                throw new Http2Exception(Http2ErrorCode.INTERNAL, "Interrupted", e);
            }

            throw new Http2Exception(Http2ErrorCode.ENHANCE_YOUR_CALM,
                                     "Request data length doesn't correspond to the content-length header.");
        }
        if (expectedLength != -1) {
            expectedLength -= header.length();
        }
        try {
            inboundData.put(new DataFrame(header, data));
        } catch (InterruptedException e) {
            throw new Http2Exception(Http2ErrorCode.INTERNAL, "Interrupted", e);
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

    @Override
    public void run() {
        Thread.currentThread()
                .setName("[" + ctx.socketId() + " "
                                 + ctx.childSocketId() + " ] - " + streamId);
        try {
            handle();
        } catch (SocketWriterException | CloseConnectionException | UncheckedIOException e) {
            Http2RstStream rst = new Http2RstStream(Http2ErrorCode.STREAM_CLOSED);
            writer.write(rst.toFrameData(serverSettings, streamId, Http2Flag.NoFlags.create()));
            // no sense in throwing an exception, as this is invoked from an executor service directly
        } catch (RequestException e) {
            // gather error handling properties
            ErrorHandling errorHandling = ctx.listenerContext()
                    .config()
                    .errorHandling();

            // log message in DEBUG mode
            if (LOGGER.isLoggable(DEBUG) && (e.safeMessage() || errorHandling.logAllMessages())) {
                LOGGER.log(DEBUG, e);
            }

            // create message to return based on settings
            String message = null;
            if (errorHandling.includeEntity()) {
                message = e.safeMessage() ? e.getMessage() : "Bad request, see server log for more information";
            }

            DirectHandler handler = ctx.listenerContext()
                    .directHandlers()
                    .handler(e.eventType());
            DirectHandler.TransportResponse response = handler.handle(e.request(),
                                                                      e.eventType(),
                                                                      e.status(),
                                                                      e.responseHeaders(),
                                                                      message);

            ServerResponseHeaders headers = response.headers();
            byte[] entity = response.entity().orElse(BufferData.EMPTY_BYTES);
            if (entity.length != 0) {
                headers.set(HeaderValues.create(HeaderNames.CONTENT_LENGTH, String.valueOf(entity.length)));
            }
            Http2Headers http2Headers = Http2Headers.create(headers);
            if (entity.length == 0) {
                writer.writeHeaders(http2Headers,
                                    streamId,
                                    Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                                    flowControl.outbound());
            } else {
                Http2FrameHeader dataHeader = Http2FrameHeader.create(entity.length,
                                                                      Http2FrameTypes.DATA,
                                                                      Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                                      streamId);
                writer.writeHeaders(http2Headers,
                                    streamId,
                                    Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                    new Http2FrameData(dataHeader, BufferData.create(message)),
                                    flowControl.outbound());
            }
        } finally {
            headers = null;
            subProtocolHandler = null;
        }
    }

    void closeFromRemote() {
        this.state = Http2StreamState.HALF_CLOSED_REMOTE;
        try {
            // we need to notify that there is no data coming
            inboundData.put(TERMINATING_FRAME);
        } catch (InterruptedException e) {
            throw new Http2Exception(Http2ErrorCode.INTERNAL, "Interrupted", e);
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
            streams.remove(this.streamId);
            flags = Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM);
        } else {
            flags = Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS);
        }

        try {
            return writer.writeHeaders(http2Headers, streamId, flags, flowControl.outbound());
        } catch (UncheckedIOException e) {
            throw new ServerConnectionException("Failed to write headers", e);
        }
    }

    int writeHeadersWithData(Http2Headers http2Headers, int contentLength, BufferData bufferData, boolean endOfStream) {
        writeState.updateAndGet(s -> s
                .checkAndMove(WriteState.HEADERS_SENT)
                .checkAndMove(WriteState.DATA_SENT));

        Http2FrameData frameData =
                new Http2FrameData(Http2FrameHeader.create(contentLength,
                                                           Http2FrameTypes.DATA,
                                                           Http2Flag.DataFlags.create(endOfStream ? Http2Flag.END_OF_STREAM : 0),
                                                           streamId),
                                   bufferData);
        try {
            return writer.writeHeaders(http2Headers, streamId,
                                       Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                       frameData,
                                       flowControl.outbound());
        } catch (UncheckedIOException e) {
            throw new ServerConnectionException("Failed to write headers", e);
        } finally {
            if (endOfStream) {
                writeState.updateAndGet(s -> s.checkAndMove(WriteState.END));
                streams.remove(this.streamId);
            }
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

        if (endOfStream) {
            streams.remove(this.streamId);
        }

        Http2FrameData frameData =
                new Http2FrameData(Http2FrameHeader.create(bufferData.available(),
                                                           Http2FrameTypes.DATA,
                                                           Http2Flag.DataFlags.create(endOfStream ? Http2Flag.END_OF_STREAM : 0),
                                                           streamId),
                                   bufferData);

        try {
            writer.writeData(frameData, flowControl.outbound());
        } catch (UncheckedIOException e) {
            throw new ServerConnectionException("Failed to write frame data", e);
        }
        return frameData.header().length() + Http2FrameHeader.LENGTH;
    }

    int writeTrailers(Http2Headers http2trailers) {
        writeState.updateAndGet(s -> s.checkAndMove(WriteState.TRAILERS_SENT));
        streams.remove(this.streamId);

        try {
            return writer.writeHeaders(http2trailers,
                                       streamId,
                                       Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                                       flowControl.outbound());
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

    void prologue(HttpPrologue prologue) {
        this.prologue = prologue;
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
            flowControl.inbound().incrementWindowSize(frame.header().length());
        } catch (InterruptedException e) {
            // this stream was interrupted, does not make sense to do anything else
            return BufferData.empty();
        }

        if (frame.header().flags(Http2FrameTypes.DATA).endOfStream()) {
            wasLastDataFrame = true;
            if (state == Http2StreamState.CLOSED) {
                throw RequestException.builder().message("Stream is closed.").build();
            }
        }
        return frame.data();
    }

    private void handle() {
        Headers httpHeaders = headers.httpHeaders();
        if (headers.httpHeaders().contains(HeaderValues.EXPECT_100)) {
            writeState.updateAndGet(s -> s.checkAndMove(WriteState.EXPECTED_100));
        }

        subProtocolHandler = null;

        for (Http2SubProtocolSelector provider : subProviders) {
            SubProtocolResult subProtocolResult = provider.subProtocol(ctx,
                                                                       prologue,
                                                                       headers,
                                                                       writer,
                                                                       streamId,
                                                                       serverSettings,
                                                                       clientSettings,
                                                                       flowControl,
                                                                       state,
                                                                       router);
            if (subProtocolResult.supported()) {
                subProtocolHandler = subProtocolResult.subProtocol();
                break;
            }
        }

        if (subProtocolHandler == null) {
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

            Http2ServerRequest request = Http2ServerRequest.create(ctx,
                                                                   routing.security(),
                                                                   prologue,
                                                                   headers,
                                                                   decoder,
                                                                   streamId,
                                                                   this::readEntityFromPipeline);
            Http2ServerResponse response = new Http2ServerResponse(this, request);

            try {
                Optional<LimitAlgorithm.Token> token = requestLimit.tryAcquire(limitListenerProviders.stream()
                                                                                       .map(f -> f.create(prologue,
                                                                                                          headers.httpHeaders()))
                                                                                       .toList());

                if (token.isEmpty()) {
                    ctx.log(LOGGER, TRACE, "Too many concurrent requests, rejecting request.");
                    response.status(Status.SERVICE_UNAVAILABLE_503)
                            .send("Too Many Concurrent Requests");
                    response.commit();
                } else {
                    LimitAlgorithm.Token permit = token.get();
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
                }
            } finally {
                request.content().consume();
                if (this.state == Http2StreamState.HALF_CLOSED_REMOTE) {
                    this.state = Http2StreamState.CLOSED;
                } else {
                    this.state = Http2StreamState.HALF_CLOSED_LOCAL;
                }
            }
        } else {
            subProtocolHandler.init();
            while (subProtocolHandler.streamState() != Http2StreamState.CLOSED
                    && subProtocolHandler.streamState() != Http2StreamState.HALF_CLOSED_LOCAL) {
                DataFrame frame;
                try {
                    frame = inboundData.take();
                    flowControl.inbound().incrementWindowSize(frame.header().length());
                } catch (InterruptedException e) {
                    // this stream was interrupted, does not make sense to do anything else
                    String handlerName = subProtocolHandler.getClass().getSimpleName();
                    ctx.log(LOGGER, System.Logger.Level.DEBUG, "%s interrupted stream %d", handlerName, streamId);
                    return;
                }
                subProtocolHandler.data(frame.header, frame.data);
                this.state = subProtocolHandler.streamState();
            }
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

    private record DataFrame(Http2FrameHeader header, BufferData data) { }
}
