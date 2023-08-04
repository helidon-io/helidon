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

package io.helidon.nima.http2.webserver;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.http.DirectHandler;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.HttpPrologue;
import io.helidon.common.http.RequestException;
import io.helidon.common.http.ServerResponseHeaders;
import io.helidon.common.socket.SocketWriterException;
import io.helidon.nima.http.encoding.ContentDecoder;
import io.helidon.nima.http2.ConnectionFlowControl;
import io.helidon.nima.http2.Http2ErrorCode;
import io.helidon.nima.http2.Http2Exception;
import io.helidon.nima.http2.Http2Flag;
import io.helidon.nima.http2.Http2FrameData;
import io.helidon.nima.http2.Http2FrameHeader;
import io.helidon.nima.http2.Http2FrameTypes;
import io.helidon.nima.http2.Http2Headers;
import io.helidon.nima.http2.Http2Priority;
import io.helidon.nima.http2.Http2RstStream;
import io.helidon.nima.http2.Http2Settings;
import io.helidon.nima.http2.Http2StreamState;
import io.helidon.nima.http2.Http2StreamWriter;
import io.helidon.nima.http2.Http2WindowUpdate;
import io.helidon.nima.http2.StreamFlowControl;
import io.helidon.nima.http2.WindowSize;
import io.helidon.nima.http2.webserver.spi.Http2SubProtocolSelector;
import io.helidon.nima.http2.webserver.spi.SubProtocolResult;
import io.helidon.nima.webserver.CloseConnectionException;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.Router;
import io.helidon.nima.webserver.http.HttpRouting;

import static java.lang.System.Logger.Level.TRACE;

/**
 * Server HTTP/2 stream implementation.
 */
public class Http2Stream implements Runnable, io.helidon.nima.http2.Http2Stream {
    private static final DataFrame TERMINATING_FRAME =
            new DataFrame(Http2FrameHeader.create(0,
                                                  Http2FrameTypes.DATA,
                                                  Http2Flag.DataFlags.create(Http2Flag.DataFlags.END_OF_STREAM),
                                                  0), BufferData.empty());
    private static final System.Logger LOGGER = System.getLogger(Http2Stream.class.getName());

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

    private boolean wasLastDataFrame = false;
    private volatile Http2Headers headers;
    private volatile Http2Priority priority;
    // used from this instance and from connection
    private volatile Http2StreamState state = Http2StreamState.IDLE;
    private Http2SubProtocolSelector.SubProtocolHandler subProtocolHandler;
    private long expectedLength = -1;
    private HttpRouting routing;
    private HttpPrologue prologue;
    private Semaphore requestSemaphore;
    private boolean semaphoreAcquired;

    /**
     * A new HTTP/2 server stream.
     *
     * @param ctx                   connection context
     * @param routing               HTTP routing
     * @param http2Config           HTTP/2 configuration
     * @param subProviders          HTTP/2 sub protocol selectors
     * @param streamId              stream id
     * @param serverSettings        server settings
     * @param clientSettings        client settings
     * @param writer                writer
     * @param connectionFlowControl connection flow control
     */
    public Http2Stream(ConnectionContext ctx,
                       HttpRouting routing,
                       Http2Config http2Config,
                       List<Http2SubProtocolSelector> subProviders,
                       int streamId,
                       Http2Settings serverSettings,
                       Http2Settings clientSettings,
                       Http2StreamWriter writer,
                       ConnectionFlowControl connectionFlowControl) {
        this.ctx = ctx;
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
    }

    /**
     * Check this stream is not closed.
     * This method is called from connection thread.
     *
     * @throws Http2Exception in case this stream is closed
     */
    public void checkNotClosed() throws Http2Exception {
        if (state == Http2StreamState.HALF_CLOSED_REMOTE
                || state == Http2StreamState.CLOSED) {
            throw new Http2Exception(Http2ErrorCode.STREAM_CLOSED,
                                     "Stream " + streamId + " is closed. State: " + state);
        }
    }

    /**
     * Check if data can be received on this stream.
     * This method is called from connection thread.
     *
     * @throws Http2Exception in case data cannot be received
     */
    public void checkDataReceivable() throws Http2Exception {
        if (state != Http2StreamState.OPEN) {
            throw new Http2Exception(Http2ErrorCode.PROTOCOL, "Received data for stream "
                    + streamId + " in state " + state);
        }
    }

    /**
     * Check if headers can be received on this stream.
     * This method is called from connection thread.
     *
     * @throws Http2Exception in case headers cannot be received.
     */
    public void checkHeadersReceivable() throws Http2Exception {
        switch (state) {
        case IDLE:
            // this is OK
            break;
        case HALF_CLOSED_LOCAL:
        case HALF_CLOSED_REMOTE:
        case CLOSED:
            throw new Http2Exception(Http2ErrorCode.STREAM_CLOSED,
                                     "Stream " + streamId + " received headers when stream is " + state);
        case OPEN:
            throw new Http2Exception(Http2ErrorCode.PROTOCOL, "Received headers for open stream " + streamId);
        default:
            throw new Http2Exception(Http2ErrorCode.INTERNAL, "Unknown stream state: " + streamId + ", state: " + state);
        }
    }

    @Override
    public void rstStream(Http2RstStream rstStream) {
        if (state == Http2StreamState.IDLE) {
            throw new Http2Exception(Http2ErrorCode.PROTOCOL,
                                     "Received RST_STREAM for stream "
                                             + streamId + " in IDLE state");
        }
        // TODO interrupt
        this.state = Http2StreamState.CLOSED;
    }

    @Override
    public void windowUpdate(Http2WindowUpdate windowUpdate) {
        if (state == Http2StreamState.IDLE) {
            throw new Http2Exception(Http2ErrorCode.PROTOCOL, "Received WINDOW_UPDATE for stream "
                    + streamId + " in state IDLE");
        }
        //6.9/2
        if (windowUpdate.windowSizeIncrement() == 0) {
            Http2RstStream frame = new Http2RstStream(Http2ErrorCode.PROTOCOL);
            writer.write(frame.toFrameData(clientSettings, streamId, Http2Flag.NoFlags.create()));
        }
        //6.9.1/3
        if (flowControl.outbound().incrementStreamWindowSize(windowUpdate.windowSizeIncrement()) > WindowSize.MAX_WIN_SIZE) {
            Http2RstStream frame = new Http2RstStream(Http2ErrorCode.FLOW_CONTROL);
            writer.write(frame.toFrameData(clientSettings, streamId, Http2Flag.NoFlags.create()));
        }
    }

    // this method is called from connection thread and start the
    // thread o this stream
    @Override
    public void headers(Http2Headers headers, boolean endOfStream) {
        this.headers = headers;
        this.state = endOfStream ? Http2StreamState.HALF_CLOSED_REMOTE : Http2StreamState.OPEN;
        if (state == Http2StreamState.HALF_CLOSED_REMOTE) {
            try {
                // we need to notify that there is no data coming
                inboundData.put(TERMINATING_FRAME);
            } catch (InterruptedException e) {
                throw new Http2Exception(Http2ErrorCode.INTERNAL, "Interrupted", e);
            }
        }
    }

    @Override
    public void data(Http2FrameHeader header, BufferData data) {
        // todo check number of queued items and modify flow control if we seem to be collecting messages
        if (expectedLength != -1 && expectedLength < header.length()) {
            state = Http2StreamState.CLOSED;
            Http2RstStream rst = new Http2RstStream(Http2ErrorCode.PROTOCOL);
            writer.write(rst.toFrameData(clientSettings, streamId, Http2Flag.NoFlags.create()));
            return;
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
            DirectHandler handler = ctx.listenerContext()
                    .directHandlers()
                    .handler(e.eventType());
            DirectHandler.TransportResponse response = handler.handle(e.request(),
                                                                      e.eventType(),
                                                                      e.status(),
                                                                      e.responseHeaders(),
                                                                      e);

            ServerResponseHeaders headers = response.headers();
            byte[] message = response.entity().orElse(BufferData.EMPTY_BYTES);
            if (message.length != 0) {
                headers.set(Header.create(Header.CONTENT_LENGTH, String.valueOf(message.length)));
            }
            Http2Headers http2Headers = Http2Headers.create(headers);
            if (message.length == 0) {
                writer.writeHeaders(http2Headers,
                                    streamId,
                                    Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                                    flowControl.outbound());
            } else {
                Http2FrameHeader dataHeader = Http2FrameHeader.create(message.length,
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
            if (semaphoreAcquired) {
                requestSemaphore.release();
            }
        }
    }

    void requestSemaphore(Semaphore requestSemaphore) {
        this.requestSemaphore = requestSemaphore;
    }

    void prologue(HttpPrologue prologue) {
        this.prologue = prologue;
    }

    private BufferData readEntityFromPipeline() {
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
        }
        return frame.data();
    }

    private void handle() {
        Headers httpHeaders = headers.httpHeaders();
        if (httpHeaders.contains(Header.CONTENT_LENGTH)) {
            this.expectedLength = httpHeaders.get(Header.CONTENT_LENGTH).value(long.class);
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
            //            ContentDecoder decoder;
            //            if (contentEncodingContext.contentDecodingEnabled()) {
            //                if (httpHeaders.contains(Header.CONTENT_ENCODING)) {
            //                    String contentEncoding = httpHeaders.get(Header.CONTENT_ENCODING).value();
            //                    if (contentEncodingContext.contentDecodingSupported(contentEncoding)) {
            //                        decoder = contentEncodingContext.decoder(contentEncoding);
            //                    } else {
            //                        throw new Http2Exception(Http2ErrorCode.INTERNAL, "Unsupported content encoding " +
            //                        contentEncoding);
            //                    }
            //                } else {
            //                    decoder = ContentDecoder.NO_OP;
            //                }
            //            } else {
            //                decoder = ContentDecoder.NO_OP;
            //            }
            ContentDecoder decoder = ContentDecoder.NO_OP;
            Http2ServerRequest request = Http2ServerRequest.create(ctx,
                                                                   routing.security(),
                                                                   prologue,
                                                                   headers,
                                                                   decoder,
                                                                   streamId,
                                                                   this::readEntityFromPipeline);
            Http2ServerResponse response = new Http2ServerResponse(ctx, request, writer, streamId, flowControl.outbound());
            semaphoreAcquired = requestSemaphore.tryAcquire();
            try {
                if (semaphoreAcquired) {
                    routing.route(ctx, request, response);
                } else {
                    ctx.log(LOGGER, TRACE, "Too many concurrent requests, rejecting request.");
                    response.status(Http.Status.SERVICE_UNAVAILABLE_503)
                            .send("Too Many Concurrent Requests");
                    response.commit();
                }
            } finally {
                this.state = Http2StreamState.CLOSED;
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

    private record DataFrame(Http2FrameHeader header, BufferData data) { }
}
