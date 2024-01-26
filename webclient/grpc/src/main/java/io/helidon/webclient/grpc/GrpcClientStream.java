/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webclient.grpc;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2Priority;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2Stream;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.http.http2.StreamFlowControl;
import io.helidon.webclient.api.ReleasableResource;

class GrpcClientStream implements Http2Stream, ReleasableResource {
    @Override
    public boolean rstStream(Http2RstStream rstStream) {
        return false;
    }

    @Override
    public void windowUpdate(Http2WindowUpdate windowUpdate) {

    }

    @Override
    public void headers(Http2Headers headers, boolean endOfStream) {

    }

    @Override
    public void data(Http2FrameHeader header, BufferData data, boolean endOfStream) {

    }

    @Override
    public void priority(Http2Priority http2Priority) {

    }

    @Override
    public int streamId() {
        return 0;
    }

    @Override
    public Http2StreamState streamState() {
        return null;
    }

    @Override
    public StreamFlowControl flowControl() {
        return null;
    }

    @Override
    public void closeResource() {

    }
}
