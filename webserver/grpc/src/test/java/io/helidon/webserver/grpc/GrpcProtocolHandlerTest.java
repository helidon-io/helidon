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

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

class GrpcProtocolHandlerTest {

    private static final HeaderName GRPC_ACCEPT_ENCODING = HeaderNames.create("grpc-accept-encoding");

    @Test
    @SuppressWarnings("unchecked")
    void testIdentityCompressorFlag() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(GRPC_ACCEPT_ENCODING, "identity");
        GrpcProtocolHandler handler = new GrpcProtocolHandler(null,
                Http2Headers.create(headers),
                null,
                1,
                Http2Settings.builder().build(),
                Http2Settings.builder().build(),
                null,
                Http2StreamState.OPEN,
                null);
        handler.initCompression(null, headers);
        assertThat(handler.isIdentityCompressor(), is(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGzipCompressor() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(GRPC_ACCEPT_ENCODING, "gzip");
        GrpcProtocolHandler handler = new GrpcProtocolHandler(null,
                Http2Headers.create(headers),
                null,
                1,
                Http2Settings.builder().build(),
                Http2Settings.builder().build(),
                null,
                Http2StreamState.OPEN,
                null);
        handler.initCompression(null, headers);
        assertThat(handler.isIdentityCompressor(), is(false));
    }
}
