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

package io.helidon.http.http2;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

public class HttpCodecJmhBenchmark {
    private static final String SIMPLE_CONTENT_TYPE = "application/json";
    private static final String QUOTED_CONTENT_TYPE = "text/plain; profile=\"a\u0080\u00ff\"";
    private static final String ASCII_VALUE = "0123456789";
    private static final String LATIN1_VALUE = "\u008012345678\u00ff";
    private static final HeaderName CUSTOM_HEADER_NAME = HeaderNames.create("x-jmh-custom-header");
    private static final HeaderName MULTI_VALUE_HEADER_NAME = HeaderNames.create("x-jmh-multi-value");
    private static final Method CUSTOM_METHOD = Method.create("SEARCH");
    private static final HttpMediaType SIMPLE_MEDIA_TYPE = HttpMediaType.create(SIMPLE_CONTENT_TYPE);
    private static final HttpMediaType QUOTED_MEDIA_TYPE = HttpMediaType.create(QUOTED_CONTENT_TYPE);

    @Benchmark
    public HttpMediaType parseSimpleContentType() {
        return HttpMediaType.create(SIMPLE_CONTENT_TYPE);
    }

    @Benchmark
    public HttpMediaType parseQuotedContentType() {
        return HttpMediaType.create(QUOTED_CONTENT_TYPE);
    }

    @Benchmark
    public String serializeSimpleContentType() {
        return SIMPLE_MEDIA_TYPE.text();
    }

    @Benchmark
    public String serializeQuotedContentType() {
        return QUOTED_MEDIA_TYPE.text();
    }

    @Benchmark
    public int writeHttp1Ascii(EncodingState state) {
        state.http1Buffer.clear();
        state.asciiHeader.writeHttp1Header(state.http1Buffer);
        return state.http1Buffer.available();
    }

    @Benchmark
    public int writeHttp1Latin1(EncodingState state) {
        state.http1Buffer.clear();
        state.latin1Header.writeHttp1Header(state.http1Buffer);
        return state.http1Buffer.available();
    }

    @Benchmark
    public int encodeHpackAscii(EncodingState state) {
        state.hpackBuffer.clear();
        state.huffmanEncoder.encode(state.hpackBuffer, ASCII_VALUE);
        return state.hpackBuffer.available();
    }

    @Benchmark
    public int encodeHpackLatin1(EncodingState state) {
        state.hpackBuffer.clear();
        state.huffmanEncoder.encode(state.hpackBuffer, LATIN1_VALUE);
        return state.hpackBuffer.available();
    }

    @Benchmark
    public int writeHpackRequestAscii(EncodingState state) {
        return state.requestAscii.write();
    }

    @Benchmark
    public int writeHpackRequestLatin1(EncodingState state) {
        return state.requestLatin1.write();
    }

    @Benchmark
    public int writeHpackResponseAscii(EncodingState state) {
        return state.responseAscii.write();
    }

    @Benchmark
    public int writeHpackResponseLatin1(EncodingState state) {
        return state.responseLatin1.write();
    }

    private static Http2Headers requestHeaders(String value) {
        return Http2Headers.create(headers(value))
                .method(CUSTOM_METHOD)
                .scheme("https")
                .path("/search?name=benchmark")
                .authority("example.com");
    }

    private static Http2Headers responseHeaders(String value) {
        return Http2Headers.create(headers(value))
                .status(Status.OK_200);
    }

    private static WritableHeaders<?> headers(String value) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderValues.CONTENT_TYPE_JSON);
        headers.add(HeaderValues.CACHE_NO_CACHE);
        headers.add(HeaderValues.create(CUSTOM_HEADER_NAME, true, false, value));
        headers.add(HeaderValues.create(MULTI_VALUE_HEADER_NAME, true, false, value, value));
        return headers;
    }

    @State(Scope.Thread)
    public static class EncodingState {
        private final Header asciiHeader = HeaderValues.create("X-JMH", ASCII_VALUE);
        private final Header latin1Header = HeaderValues.create("X-JMH", LATIN1_VALUE);
        private final BufferData http1Buffer = BufferData.growing(32);
        private final BufferData hpackBuffer = BufferData.growing(32);
        private final Http2HuffmanEncoder huffmanEncoder = Http2HuffmanEncoder.create();
        private final HeaderBlockState requestAscii = new HeaderBlockState(requestHeaders(ASCII_VALUE));
        private final HeaderBlockState requestLatin1 = new HeaderBlockState(requestHeaders(LATIN1_VALUE));
        private final HeaderBlockState responseAscii = new HeaderBlockState(responseHeaders(ASCII_VALUE));
        private final HeaderBlockState responseLatin1 = new HeaderBlockState(responseHeaders(LATIN1_VALUE));
    }

    private static class HeaderBlockState {
        private final Http2Headers headers;
        private final Http2Headers.DynamicTable table = Http2Headers.DynamicTable.create(Http2Settings.create());
        private final Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
        private final BufferData buffer = BufferData.growing(256);

        private HeaderBlockState(Http2Headers headers) {
            this.headers = headers;
            write();
        }

        private int write() {
            buffer.clear();
            headers.write(table, huffman, buffer);
            return buffer.available();
        }
    }
}
