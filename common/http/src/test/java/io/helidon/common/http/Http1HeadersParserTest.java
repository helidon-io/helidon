/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.common.http;

import java.nio.charset.StandardCharsets;

import io.helidon.common.buffers.DataReader;
import io.helidon.common.media.type.ParserMode;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class Http1HeadersParserTest {
    @Test
    void testHeadersAreCaseInsensitive() {
        DataReader reader = new DataReader(() -> (
                "Set-Cookie: c1=v1\r\nSet-Cookie: c2=v2\r\n"
                        + "Header: hv1\r\nheader: hv2\r\nheaDer: hv3\r\n"
                        + "\r\n").getBytes(StandardCharsets.US_ASCII));
        WritableHeaders<?> headers = Http1HeadersParser.readHeaders(reader, 1024, ParserMode.STRICT, true);

        testHeader(headers, "Set-Cookie", "c1=v1", "c2=v2");
        testHeader(headers, "set-cookie", "c1=v1", "c2=v2");
        testHeader(headers, "SET-CooKIE", "c1=v1", "c2=v2");

        testHeader(headers, "header", "hv1", "hv2", "hv3");
        testHeader(headers, "Header", "hv1", "hv2", "hv3");
        testHeader(headers, "HeADer", "hv1", "hv2", "hv3");
    }

    private void testHeader(Headers headers, String header, String... values) {
        Http.HeaderName headerName = Http.Header.create(header);
        assertThat("Headers should contain header: " + headerName.lowerCase(),
                   headers.contains(headerName),
                   is(true));
        assertThat("Header " + headerName.lowerCase(),
                   headers.get(headerName).allValues(),
                   hasItems(values));
    }
}