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

package io.helidon.webserver.http2.spi;

import java.util.Objects;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.HttpPrologue;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.http.http2.StreamFlowControl;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.Router;

/**
 * A selector of HTTP/2 sub-protocols.
 */
public interface Http2SubProtocolSelector {
    /**
     * Not supported sub-protocol result.
     */
    SubProtocolResult NOT_SUPPORTED = new SubProtocolResult(false, null);

    /**
     * Check if this is a sub-protocol request and return appropriate result.
     *
     * @param ctx                connection context
     * @param prologue           received prologue
     * @param headers            received headers
     * @param streamWriter       stream writer
     * @param streamId           stream id
     * @param serverSettings     server settings
     * @param clientSettings     client settings
     * @param streamFlowControl  stream flow control
     * @param currentStreamState current stream state
     * @param router             router
     * @return sub-protocol result
     */
    @SuppressWarnings("checkstyle:ParameterNumber") // all parameters required, no benefit using a record wrapper
    SubProtocolResult subProtocol(ConnectionContext ctx,
                                  HttpPrologue prologue,
                                  Http2Headers headers,
                                  Http2StreamWriter streamWriter,
                                  int streamId,
                                  Http2Settings serverSettings,
                                  Http2Settings clientSettings,
                                  StreamFlowControl streamFlowControl,
                                  Http2StreamState currentStreamState,
                                  Router router);

    /**
     * Handler of a sub-protocol.
     */
    interface SubProtocolHandler {
        /**
         * Called once the sub-protocol handler is available. Always dispatched on the
         * HTTP/2 stream thread.
         */
        void init();

        /**
         * Current stream state.
         *
         * @return stream state
         */
        Http2StreamState streamState();

        /**
         * Register a callback for when the handler reaches the {@link Http2StreamState#CLOSED closed} state.
         * Implementations that can close asynchronously must override this method and invoke the callback exactly once.
         * The callback may run on any thread. The default implementation validates the callback but does not register it.
         *
         * @param listener close listener
         */
        default void onStreamClosed(Runnable listener) {
            Objects.requireNonNull(listener);
        }

        /**
         * RST stream was received, or the local connection closed before the subprotocol started.
         * This method is normally dispatched on the HTTP/2 connection thread. If the stream closes while
         * subprotocol selection is in progress, it is dispatched on the stream thread before {@link #init()}.
         * Implementations must support both threads and concurrent invocation with {@code init()}.
         *
         * @param rstStream RST stream frame
         */
        void rstStream(Http2RstStream rstStream);

        /**
         * Window update was received. Note that this method will be dispatched on the
         * HTTP/2 connection thread, not the stream thread.
         *
         * @param update window update frame
         */
        void windowUpdate(Http2WindowUpdate update);

        /**
         * Data was received. Always dispatched on the HTTP/2 stream thread.
         * The data may be empty. Check the
         * {@link io.helidon.http.http2.Http2FrameHeader#flags(io.helidon.http.http2.Http2FrameTypes)}
         * if this is {@link io.helidon.http.http2.Http2Flag.DataFlags#END_OF_STREAM} to identify if
         * this is the last data incoming.
         *
         * @param header frame header
         * @param data   frame data
         */
        void data(Http2FrameHeader header, BufferData data);
    }
}
