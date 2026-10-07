/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
package io.helidon.grpc.core;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.http.HeaderNames;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;

import io.grpc.Metadata;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrpcHeadersUtilTest {

    @Test
    void testEncodeTimeout() {
        Map<Long, String> timeouts = Map.of(
                0L, "0n",
                1L, "1n",
                99_999_999L, "99999999n",
                100_000_000L, "100000u",
                100_000_001L, "100001u",
                100_000_000_000L, "100000m",
                100_000_000_000_000L, "100000S",
                100_000_000_000_000_000L, "1666667M",
                Long.MAX_VALUE, "2562048H");
        timeouts.forEach((nanos, encoded) -> assertThat("timeout " + nanos,
                                                      GrpcHeadersUtil.encodeTimeout(nanos), is(encoded)));
    }

    @Test
    void testDecodeTimeout() {
        Map<String, Long> timeouts = Map.of(
                "0n", 0L,
                "1n", 1L,
                "2u", 2000L,
                "3m", 3_000_000L,
                "4S", 4_000_000_000L,
                "5M", 300_000_000_000L,
                "6H", 21_600_000_000_000L,
                "00000001n", 1L,
                "99999999H", Long.MAX_VALUE,
                "99999999M", 5_999_999_940_000_000_000L);
        timeouts.forEach((encoded, nanos) -> assertThat("timeout " + encoded,
                                                      GrpcHeadersUtil.decodeTimeout(encoded), is(nanos)));
    }

    @Test
    void testTimeoutRoundingDoesNotShortenDeadline() {
        for (long timeout : List.of(99_999_999L, 100_000_001L, 99_999_999_999L, 100_000_000_001L,
                                   99_999_999_999_999L, 100_000_000_000_001L,
                                   99_999_999_999_999_999L, 100_000_000_000_000_001L, Long.MAX_VALUE)) {
            String encoded = GrpcHeadersUtil.encodeTimeout(timeout);
            assertThat("encoded timeout " + encoded + " must not shorten " + timeout,
                       GrpcHeadersUtil.decodeTimeout(encoded), greaterThanOrEqualTo(timeout));
        }
    }

    @Test
    void testMalformedTimeout() {
        for (String timeout : List.of("", "n", "123", "1s", "1h", "1U", "-1S", "+1S", "1.0S",
                                      " 1S", "1S ", "100000000n", "\u0661S")) {
            assertThrows(IllegalArgumentException.class, () -> GrpcHeadersUtil.decodeTimeout(timeout), timeout);
        }
        assertThrows(IllegalArgumentException.class, () -> GrpcHeadersUtil.encodeTimeout(-1));
    }

    @Test
    void testUpdateHeaders() {
        Metadata metadata = new Metadata();
        Metadata.Key<String> key = Metadata.Key.of("cookie", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(key, "sugar");
        metadata.put(key, "almond");
        WritableHeaders<?> headers = WritableHeaders.create();
        GrpcHeadersUtil.updateHeaders(headers, metadata);
        // there is exactly one header name: `Cookie`
        assertThat(headers.size(), is(1));
        List<String> values = headers.get(HeaderNames.COOKIE).allValues();
        assertThat(values, hasItem("sugar"));
        assertThat(values, hasItem("almond"));
    }

    @Test
    void testUpdateBinaryHeaders() {
        Metadata metadata = new Metadata();
        Metadata.Key<byte[]> key = Metadata.Key.of("secret-bin", Metadata.BINARY_BYTE_MARSHALLER);
        byte[] mySecret = "my-secret".getBytes(StandardCharsets.UTF_8);
        metadata.put(key, mySecret);
        WritableHeaders<?> headers = WritableHeaders.create();
        GrpcHeadersUtil.updateHeaders(headers, metadata);
        assertThat(headers.size(), is(1));
        List<String> values = headers.get(HeaderNames.create("secret-bin")).allValues();
        byte[] value = Base64.getDecoder().decode(values.getFirst().getBytes(StandardCharsets.UTF_8));
        assertThat(new String(value, StandardCharsets.UTF_8), is("my-secret"));
    }

    @Test
    void testToMetadata() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.COOKIE, "sugar", "almond");
        Http2Headers http2Headers = mock(Http2Headers.class);
        when(http2Headers.httpHeaders()).thenReturn(headers);
        Metadata metadata = GrpcHeadersUtil.toMetadata(http2Headers);
        Metadata.Key<String> key = Metadata.Key.of("cookie", Metadata.ASCII_STRING_MARSHALLER);
        assertThat(metadata.containsKey(key), is(true));
        Set<String> values = new HashSet<>();
        metadata.getAll(key).forEach(values::add);
        assertThat(values, hasItem("sugar"));
        assertThat(values, hasItem("almond"));
    }
}
