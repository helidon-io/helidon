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

package io.helidon.webserver.grpc;

import io.helidon.http.Status;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

class GrpcProtocolHandlerNotFoundTest {

    private boolean validateHeaders;

    @Test
    void testNotFoundHeaders() {
        Http2StreamWriter writer = new Http2StreamWriter() {
            @Override
            public void write(Http2FrameData frame) {
                throw new UnsupportedOperationException("Unsupported");
            }

            @Override
            public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
                throw new UnsupportedOperationException("Unsupported");

            }

            @Override
            public int writeHeaders(Http2Headers headers, int streamId, Http2Flag.HeaderFlags flags, FlowControl.Outbound flowControl) {
                validateHeaders = (headers.status() == Status.NOT_FOUND_404);
                try {
                    headers.validateResponse();
                } catch (Exception e) {
                    validateHeaders = false;
                }
                return 0;
            }

            @Override
            public int writeHeaders(Http2Headers headers, int streamId, Http2Flag.HeaderFlags flags, Http2FrameData dataFrame, FlowControl.Outbound flowControl) {
                throw new UnsupportedOperationException("Unsupported");
            }
        };
        GrpcProtocolHandlerNotFound handler = new GrpcProtocolHandlerNotFound(writer, 1, Http2StreamState.OPEN);
        assertThat(validateHeaders, is(false));
        handler.init();
        assertThat(validateHeaders, is(true));
    }
}
