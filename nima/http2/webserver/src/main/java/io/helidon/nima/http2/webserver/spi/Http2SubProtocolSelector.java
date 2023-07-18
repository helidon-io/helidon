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

package io.helidon.nima.http2.webserver.spi;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.http.HttpPrologue;
import io.helidon.nima.http2.Http2FrameHeader;
import io.helidon.nima.http2.Http2Headers;
import io.helidon.nima.http2.Http2RstStream;
import io.helidon.nima.http2.Http2Settings;
import io.helidon.nima.http2.Http2StreamState;
import io.helidon.nima.http2.Http2StreamWriter;
import io.helidon.nima.http2.Http2WindowUpdate;
import io.helidon.nima.http2.StreamFlowControl;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.Router;

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
         * Called once the sub-protocol handler is available.
         */
        void init();

        /**
         * Current stream state.
         *
         * @return stream state
         */
        Http2StreamState streamState();

        /**
         * RST stream was received.
         *
         * @param rstStream RST stream frame
         */
        void rstStream(Http2RstStream rstStream);

        /**
         * Window update was received.
         *
         * @param update window update frame
         */
        void windowUpdate(Http2WindowUpdate update);

        /**
         * Data was received.
         * The data may be empty. Check the
         * {@link io.helidon.nima.http2.Http2FrameHeader#flags(io.helidon.nima.http2.Http2FrameTypes)}
         * if this is {@link io.helidon.nima.http2.Http2Flag.DataFlags#END_OF_STREAM} to identify if this is the last data
         * incoming.
         *
         * @param header frame header
         * @param data   frame data
         */
        void data(Http2FrameHeader header, BufferData data);
    }
}
