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

package io.helidon.webserver.http2;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2HuffmanDecoder;
import io.helidon.http.http2.Http2Stream;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.http.HttpSecurity;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

class Http2ServerRequestTest {
    @Test
    void testHeadersSynthesizeHostFromAuthority() {
        Http2Headers headers = Http2Headers.create(WritableHeaders.create())
                .method(Method.GET)
                .scheme("http")
                .path("/resource")
                .authority("signed.example:8443");
        HttpPrologue prologue = HttpPrologue.create("HTTP/2.0",
                                                    "HTTP",
                                                    "2.0",
                                                    Method.GET,
                                                    "/resource",
                                                    false);

        Http2ServerRequest request = Http2ServerRequest.create(mock(ConnectionContext.class),
                                                               HttpSecurity.create(),
                                                               prologue,
                                                               headers,
                                                               ContentDecoder.NO_OP,
                                                               1,
                                                               false,
                                                               () -> null,
                                                               null,
                                                               0,
                                                               0);

        assertThat(request.headers().first(HeaderNames.HOST).orElseThrow(), is("signed.example:8443"));
    }

    @Test
    void testAuthorityUsesValidatedHostWhenAuthorityIsMissing() {
        Http2Headers headers = http2Headers("signed.example");
        headers.validateRequest();
        HttpPrologue prologue = HttpPrologue.create("HTTP/2.0",
                                                    "HTTP",
                                                    "2.0",
                                                    Method.GET,
                                                    "/resource",
                                                    false);

        Http2ServerRequest request = Http2ServerRequest.create(mock(ConnectionContext.class),
                                                               HttpSecurity.create(),
                                                               prologue,
                                                               headers,
                                                               ContentDecoder.NO_OP,
                                                               1,
                                                               false,
                                                               () -> null,
                                                               null,
                                                               0,
                                                               0);

        assertThat(request.authority(), is("signed.example"));
        assertThat(request.headers().first(HeaderNames.HOST).orElseThrow(), is("signed.example"));
    }

    private static Http2Headers http2Headers(String host) {
        String hexEncoded = "82 86 84 " + literalWithNewName("host", host);
        BufferData data = BufferData.create(HexFormat.of().parseHex(hexEncoded.replace(" ", "")));
        Http2FrameHeader header = Http2FrameHeader.create(data.available(),
                                                          Http2FrameTypes.HEADERS,
                                                          Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                                          1);

        return Http2Headers.create(mock(Http2Stream.class),
                                   Http2Headers.DynamicTable.create(4096),
                                   Http2HuffmanDecoder.create(),
                                   new Http2FrameData(header, data));
    }

    private static String literalWithNewName(String name, String value) {
        return "40 " + lengthAndValue(name) + " " + lengthAndValue(value);
    }

    private static String lengthAndValue(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        return String.format("%02x %s", bytes.length, HexFormat.of().formatHex(bytes));
    }
}
