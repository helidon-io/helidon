/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.grpc;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;

class GrpcProtocolHandlerNotFound implements Http2SubProtocolSelector.SubProtocolHandler {
    private final Http2StreamWriter streamWriter;
    private final int streamId;
    private Http2StreamState currentStreamState;

    GrpcProtocolHandlerNotFound(Http2StreamWriter streamWriter,
                                int streamId,
                                Http2StreamState currentStreamState) {

        this.streamWriter = streamWriter;
        this.streamId = streamId;
        this.currentStreamState = currentStreamState;
    }

    @Override
    public void init() {
        WritableHeaders<?> writable = WritableHeaders.create();
        writable.set(Http2Headers.STATUS_NAME, Status.NOT_FOUND_404.code());
        writable.set(GrpcStatus.NOT_FOUND);
        Http2Headers http2Headers = Http2Headers.create(writable);
        streamWriter.writeHeaders(http2Headers,
                                  streamId,
                                  Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                                  FlowControl.Outbound.NOOP);
        currentStreamState = Http2StreamState.HALF_CLOSED_LOCAL;
    }

    @Override
    public Http2StreamState streamState() {
        return currentStreamState;
    }

    @Override
    public void rstStream(Http2RstStream rstStream) {
    }

    @Override
    public void windowUpdate(Http2WindowUpdate update) {
    }

    @Override
    public void data(Http2FrameHeader header, BufferData data) {
    }
}
