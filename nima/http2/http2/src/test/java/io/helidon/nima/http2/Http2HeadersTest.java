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

package io.helidon.nima.http2;

import java.util.HexFormat;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderName;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.http2.Http2Headers.DynamicTable;
import io.helidon.nima.http2.Http2Headers.HeaderRecord;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class Http2HeadersTest {
    private static final HeaderName CUSTOM_HEADER_NAME = Header.create("custom-key");

    /*
    https://www.rfc-editor.org/rfc/rfc7541.html#appendix-C.2.1
     */
    @Test
    void testC_2_1() {
        String hexEncoded = "400a 6375 7374 6f6d 2d6b 6579 0d63 7573 746f 6d2d 6865 6164 6572";
        DynamicTable dynamicTable = DynamicTable.create(Http2Settings.create());
        Headers requestHeaders = headers(hexEncoded, dynamicTable).httpHeaders();

        assertThat(requestHeaders.get(Header.create("custom-key")).value(), is("custom-header"));
    }

    BufferData data(String hexEncoded) {
        byte[] bytes = HexFormat.of().parseHex(hexEncoded.replace(" ", ""));
        return BufferData.create(bytes);
    }

    /*
   https://www.rfc-editor.org/rfc/rfc7541.html#appendix-C.2.2
    */
    @Test
    void testC_2_2() {
        String hexEncoded = "040c 2f73 616d 706c 652f 7061 7468";
        DynamicTable dynamicTable = DynamicTable.create(Http2Settings.create());
        Http2Headers http2Headers = headers(hexEncoded, dynamicTable);

        assertThat(http2Headers.path(), is("/sample/path"));
        assertThat("Dynamic table should be empty", dynamicTable.currentTableSize(), is(0));
    }

    /*
   https://www.rfc-editor.org/rfc/rfc7541.html#appendix-C.2.3
    */
    @Test
    void testC_2_3() {
        String hexEncoded = "1008 7061 7373 776f 7264 0673 6563 7265 74";
        DynamicTable dynamicTable = DynamicTable.create(Http2Settings.create());
        Headers requestHeaders = headers(hexEncoded, dynamicTable).httpHeaders();

        assertThat(requestHeaders.get(Header.create("password")).value(), is("secret"));
        assertThat("Dynamic table should be empty", dynamicTable.currentTableSize(), is(0));
    }

    /*
    https://www.rfc-editor.org/rfc/rfc7541.html#appendix-C.2.4
    */
    @Test
    void testC_2_4() {
        String hexEncoded = "82";
        DynamicTable dynamicTable = DynamicTable.create(Http2Settings.create());
        Http2Headers http2Headers = headers(hexEncoded, dynamicTable);

        assertThat(http2Headers.method(), is(Http.Method.GET));
        assertThat("Dynamic table should be empty", dynamicTable.currentTableSize(), is(0));
    }

    /*
    https://www.rfc-editor.org/rfc/rfc7541.html#appendix-C.3
    */
    @Test
    void testC_3() {
        // 3.1
        String hexEncoded = "8286 8441 0f77 7777 2e65 7861 6d70 6c65 2e63 6f6d";
        DynamicTable dynamicTable = DynamicTable.create(Http2Settings.create());

        Http2Headers http2Headers = headers(hexEncoded, dynamicTable);

        assertThat(http2Headers.method(), is(Http.Method.GET));
        assertThat(http2Headers.scheme(), is("http"));
        assertThat(http2Headers.path(), is("/"));
        assertThat(http2Headers.authority(), is("www.example.com"));
        assertThat("Dynamic table should not be empty", dynamicTable.currentTableSize(), not(0));

        assertThat(dynamicTable.currentTableSize(), is(57));
        HeaderRecord headerRecord = dynamicTable.get(Http2Headers.StaticHeader.MAX_INDEX + 1);
        assertThat(headerRecord.headerName(), is(Http2Headers.AUTHORITY_NAME));
        assertThat(headerRecord.value(), is("www.example.com"));

        // 3.2
        hexEncoded = "8286 84be 5808 6e6f 2d63 6163 6865";
        http2Headers = headers(hexEncoded, dynamicTable);
        Headers requestHeaders = http2Headers.httpHeaders();

        assertThat(http2Headers.method(), is(Http.Method.GET));
        assertThat(http2Headers.scheme(), is("http"));
        assertThat(http2Headers.path(), is("/"));
        assertThat(http2Headers.authority(), is("www.example.com"));
        assertThat(requestHeaders.get(Header.create("cache-control")).value(), is("no-cache"));

        assertThat("Dynamic table should not be empty", dynamicTable.currentTableSize(), not(0));

        assertThat(dynamicTable.currentTableSize(), is(110));
        headerRecord = dynamicTable.get(Http2Headers.StaticHeader.MAX_INDEX + 1);
        assertThat(headerRecord.headerName(), is(Header.CACHE_CONTROL));
        assertThat(headerRecord.value(), is("no-cache"));
        headerRecord = dynamicTable.get(Http2Headers.StaticHeader.MAX_INDEX + 2);
        assertThat(headerRecord.headerName(), is(Http2Headers.AUTHORITY_NAME));
        assertThat(headerRecord.value(), is("www.example.com"));

        // 3.3
        hexEncoded = "8287 85bf 400a 6375 7374 6f6d 2d6b 6579 0c63 7573 746f 6d2d 7661 6c75 65";
        http2Headers = headers(hexEncoded, dynamicTable);
        requestHeaders = http2Headers.httpHeaders();

        assertThat(http2Headers.method(), is(Http.Method.GET));
        assertThat(http2Headers.scheme(), is("https"));
        assertThat(http2Headers.path(), is("/index.html"));
        assertThat(http2Headers.authority(), is("www.example.com"));
        assertThat(requestHeaders.get(CUSTOM_HEADER_NAME).value(), is("custom-value"));

        assertThat("Dynamic table should not be empty", dynamicTable.currentTableSize(), not(0));

        assertThat(dynamicTable.currentTableSize(), is(164));
        headerRecord = dynamicTable.get(Http2Headers.StaticHeader.MAX_INDEX + 1);
        assertThat(headerRecord.headerName(), is(CUSTOM_HEADER_NAME));
        assertThat(headerRecord.value(), is("custom-value"));
        headerRecord = dynamicTable.get(Http2Headers.StaticHeader.MAX_INDEX + 2);
        assertThat(headerRecord.headerName(), is(Header.CACHE_CONTROL));
        assertThat(headerRecord.value(), is("no-cache"));
        headerRecord = dynamicTable.get(Http2Headers.StaticHeader.MAX_INDEX + 3);
        assertThat(headerRecord.headerName(), is(Http2Headers.AUTHORITY_NAME));
        assertThat(headerRecord.value(), is("www.example.com"));
    }

    @Test
    void testC_4_toBytes() {
        DynamicTable dynamicTable = DynamicTable.create(Http2Settings.create());
        WritableHeaders<?> headers = WritableHeaders.create();
        Http2Headers http2Headers = Http2Headers.create(headers);
        http2Headers.method(Http.Method.GET);
        http2Headers.scheme("http");
        http2Headers.path("/");
        http2Headers.authority("www.example.com");

        Http2HuffmanEncoder huffman = new Http2HuffmanEncoder();

        BufferData buffer = BufferData.growing(32);
        http2Headers.write(dynamicTable, huffman, buffer);

        byte[] expected = HexFormat.of().parseHex("8286 8441 8cf1 e3c2 e5f2 3a6b a0ab 90f4 ff".replace(" ", ""));
        byte[] actual = new byte[buffer.available()];
        buffer.read(actual);

        assertThat(actual, is(expected));
    }

    /*
    https://www.rfc-editor.org/rfc/rfc7541.html#appendix-C.4
    */
    @Test
    void testC_4() {
        // 4.1
        String hexEncoded = "8286 8441 8cf1 e3c2 e5f2 3a6b a0ab 90f4 ff";
        DynamicTable dynamicTable = DynamicTable.create(Http2Settings.create());
        Http2Headers http2Headers = headers(hexEncoded, dynamicTable);

        assertThat(http2Headers.method(), is(Http.Method.GET));
        assertThat(http2Headers.scheme(), is("http"));
        assertThat(http2Headers.path(), is("/"));
        assertThat(http2Headers.authority(), is("www.example.com"));

        assertThat("Dynamic table should not be empty", dynamicTable.currentTableSize(), not(0));

        assertThat(dynamicTable.currentTableSize(), is(57));
        HeaderRecord headerRecord = dynamicTable.get(Http2Headers.StaticHeader.MAX_INDEX + 1);
        assertThat(headerRecord.headerName(), is(Http2Headers.AUTHORITY_NAME));
        assertThat(headerRecord.value(), is("www.example.com"));

        // 3.2
        hexEncoded = "8286 84be 5886 a8eb 1064 9cbf";
        http2Headers = headers(hexEncoded, dynamicTable);
        Headers requestHeaders = http2Headers.httpHeaders();

        assertThat(http2Headers.method(), is(Http.Method.GET));
        assertThat(http2Headers.scheme(), is("http"));
        assertThat(http2Headers.path(), is("/"));
        assertThat(http2Headers.authority(), is("www.example.com"));
        assertThat(requestHeaders.get(Header.create("cache-control")).value(), is("no-cache"));

        assertThat("Dynamic table should not be empty", dynamicTable.currentTableSize(), not(0));

        assertThat(dynamicTable.currentTableSize(), is(110));
        headerRecord = dynamicTable.get(Http2Headers.StaticHeader.MAX_INDEX + 1);
        assertThat(headerRecord.headerName(), is(Header.CACHE_CONTROL));
        assertThat(headerRecord.value(), is("no-cache"));
        headerRecord = dynamicTable.get(Http2Headers.StaticHeader.MAX_INDEX + 2);
        assertThat(headerRecord.headerName(), is(Http2Headers.AUTHORITY_NAME));
        assertThat(headerRecord.value(), is("www.example.com"));

        // 3.3
        hexEncoded = "8287 85bf 4088 25a8 49e9 5ba9 7d7f 8925 a849 e95b b8e8 b4bf";
        http2Headers = headers(hexEncoded, dynamicTable);
        requestHeaders = http2Headers.httpHeaders();

        assertThat(http2Headers.method(), is(Http.Method.GET));
        assertThat(http2Headers.scheme(), is("https"));
        assertThat(http2Headers.path(), is("/index.html"));
        assertThat(http2Headers.authority(), is("www.example.com"));
        assertThat(requestHeaders.get(CUSTOM_HEADER_NAME).value(), is("custom-value"));

        assertThat("Dynamic table should not be empty", dynamicTable.currentTableSize(), not(0));

        assertThat(dynamicTable.currentTableSize(), is(164));
        headerRecord = dynamicTable.get(Http2Headers.StaticHeader.MAX_INDEX + 1);
        assertThat(headerRecord.headerName(), is(CUSTOM_HEADER_NAME));
        assertThat(headerRecord.value(), is("custom-value"));
        headerRecord = dynamicTable.get(Http2Headers.StaticHeader.MAX_INDEX + 2);
        assertThat(headerRecord.headerName(), is(Header.CACHE_CONTROL));
        assertThat(headerRecord.value(), is("no-cache"));
        headerRecord = dynamicTable.get(Http2Headers.StaticHeader.MAX_INDEX + 3);
        assertThat(headerRecord.headerName(), is(Http2Headers.AUTHORITY_NAME));
        assertThat(headerRecord.value(), is("www.example.com"));
    }

    private Http2Headers headers(String hexEncoded, DynamicTable dynamicTable) {
        BufferData data = data(hexEncoded);
        Http2FrameHeader header = Http2FrameHeader.create(data.available(),
                                                          Http2FrameTypes.HEADERS,
                                                          Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                                          1);
        return Http2Headers.create(stream(),
                                   dynamicTable,
                                   new Http2HuffmanDecoder(),
                                   new Http2FrameData(header, data));
    }

    private Http2Stream stream() {
        return new Http2Stream() {
            private static final FlowControl.Inbound FC_IN_NOOP = FlowControl.builderInbound()
                    .noop()
                    .connectionWindowSize(WindowSize.createInboundNoop(http2WindowUpdate -> {}))
                    .windowUpdateStreamWriter(http2WindowUpdate -> {})
                    .build();

            @Override
            public void rstStream(Http2RstStream rstStream) {

            }

            @Override
            public void windowUpdate(Http2WindowUpdate windowUpdate) {

            }

            @Override
            public void headers(Http2Headers headers, boolean endOfStream) {

            }

            @Override
            public void data(Http2FrameHeader header, BufferData data) {

            }

            @Override
            public void priority(Http2Priority http2Priority) {

            }

            @Override
            public int streamId() {
                return 1;
            }

            @Override
            public Http2StreamState streamState() {
                return Http2StreamState.IDLE;
            }

            @Override
            public FlowControl.Outbound outboundFlowControl() {
                return FlowControl.Outbound.NOOP;
            }

            @Override
            public FlowControl.Inbound inboundFlowControl() {
                return FC_IN_NOOP;
            }
        };
    }

    private static class DevNullWriter implements Http2StreamWriter {

        @Override
        public void write(Http2FrameData frame, FlowControl.Outbound flowControl) {
        }

        @Override
        public int writeHeaders(Http2Headers headers, int streamId, Http2Flag.HeaderFlags flags, FlowControl.Outbound flowControl) {
            return 0;
        }

        @Override
        public int writeHeaders(Http2Headers headers,
                                int streamId,
                                Http2Flag.HeaderFlags flags,
                                Http2FrameData dataFrame,
                                FlowControl.Outbound flowControl) {
            return 0;
        }
    }
}