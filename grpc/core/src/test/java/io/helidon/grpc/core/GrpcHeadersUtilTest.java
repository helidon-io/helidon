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
import java.util.Set;

import io.helidon.http.HeaderNames;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;

import io.grpc.Metadata;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrpcHeadersUtilTest {

    @Test
    void updateHeaders() {
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
    void updateBinaryHeaders() {
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
    void toMetadata() {
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

    @Test
    void encodeTimeoutHours() {
        // 2 hours in nanos
        assertThat(GrpcHeadersUtil.encodeTimeout(7_200_000_000_000L), is("2H"));
    }

    @Test
    void encodeTimeoutMinutes() {
        // 2 minutes in nanos = 120_000_000_000L
        assertThat(GrpcHeadersUtil.encodeTimeout(120_000_000_000L), is("2M"));
    }

    @Test
    void encodeTimeoutSeconds() {
        // 5 seconds in nanos = 5_000_000_000L
        assertThat(GrpcHeadersUtil.encodeTimeout(5_000_000_000L), is("5S"));
    }

    @Test
    void encodeTimeoutMilliseconds() {
        // 500ms in nanos = 500_000_000L
        assertThat(GrpcHeadersUtil.encodeTimeout(500_000_000L), is("500m"));
    }

    @Test
    void encodeTimeoutMicroseconds() {
        // 500us in nanos = 500_000L
        assertThat(GrpcHeadersUtil.encodeTimeout(500_000L), is("500u"));
    }

    @Test
    void encodeTimeoutNanoseconds() {
        // 500ns -- only nanoseconds fit
        assertThat(GrpcHeadersUtil.encodeTimeout(500L), is("500n"));
    }

    @Test
    void encodeTimeoutNanosecondsMinimum() {
        assertThat(GrpcHeadersUtil.encodeTimeout(1L), is("1n"));
    }

    @Test
    void encodeTimeoutPicksLargestExactUnit() {
        // 1_000_000ns = 1ms exactly, should encode as "1m" not "1000u" or "1000000n"
        assertThat(GrpcHeadersUtil.encodeTimeout(1_000_000L), is("1m"));
    }

    @Test
    void encodeTimeoutDoesNotTruncate() {
        // 1_500_000_000ns = 1.5s -- not an exact number of seconds.
        // Must encode as "1500m" (milliseconds), not "1S" (which would lose 500ms)
        assertThat(GrpcHeadersUtil.encodeTimeout(1_500_000_000L), is("1500m"));
    }

    @Test
    void encodeTimeoutEightDigitBoundary() {
        // 99_999_999ms in nanos. Should encode as "99999999m" (exactly 8 digits).
        assertThat(GrpcHeadersUtil.encodeTimeout(99_999_999_000_000L), is("99999999m"));
    }

    @Test
    void encodeTimeoutReturnsNullForZero() {
        assertThat(GrpcHeadersUtil.encodeTimeout(0L), is(nullValue()));
    }

    @Test
    void encodeTimeoutReturnsNullForNegative() {
        assertThat(GrpcHeadersUtil.encodeTimeout(-1L), is(nullValue()));
    }

    @Test
    void decodeTimeoutHours() {
        assertThat(GrpcHeadersUtil.decodeTimeout("1H"), is(3_600_000_000_000L));
    }

    @Test
    void decodeTimeoutMinutes() {
        assertThat(GrpcHeadersUtil.decodeTimeout("2M"), is(120_000_000_000L));
    }

    @Test
    void decodeTimeoutSeconds() {
        assertThat(GrpcHeadersUtil.decodeTimeout("5S"), is(5_000_000_000L));
    }

    @Test
    void decodeTimeoutMilliseconds() {
        assertThat(GrpcHeadersUtil.decodeTimeout("500m"), is(500_000_000L));
    }

    @Test
    void decodeTimeoutMicroseconds() {
        assertThat(GrpcHeadersUtil.decodeTimeout("500u"), is(500_000L));
    }

    @Test
    void decodeTimeoutNanoseconds() {
        assertThat(GrpcHeadersUtil.decodeTimeout("500n"), is(500L));
    }

    @Test
    void decodeTimeoutMaxEightDigits() {
        assertThat(GrpcHeadersUtil.decodeTimeout("99999999n"), is(99_999_999L));
    }

    @Test
    void decodeTimeoutRejectsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> GrpcHeadersUtil.decodeTimeout(null));
    }

    @Test
    void decodeTimeoutRejectsEmptyString() {
        assertThrows(IllegalArgumentException.class,
                () -> GrpcHeadersUtil.decodeTimeout(""));
    }

    @Test
    void decodeTimeoutRejectsUnknownUnit() {
        assertThrows(IllegalArgumentException.class,
                () -> GrpcHeadersUtil.decodeTimeout("100x"));
    }

    @Test
    void decodeTimeoutRejectsNoDigits() {
        assertThrows(IllegalArgumentException.class,
                () -> GrpcHeadersUtil.decodeTimeout("m"));
    }

    @Test
    void decodeTimeoutRejectsNegativeValue() {
        // gRPC spec says TimeoutValue is a positive integer
        assertThrows(IllegalArgumentException.class,
                () -> GrpcHeadersUtil.decodeTimeout("-5S"));
    }

    @Test
    void decodeTimeoutRejectsOverflow() {
        // 99_999_999 hours * 3_600_000_000_000 nanos/hour overflows long
        assertThrows(IllegalArgumentException.class,
                () -> GrpcHeadersUtil.decodeTimeout("99999999H"));
    }

    @Test
    void roundTripUnitAligned() {
        // Values that align exactly to a unit boundary
        long[] testValues = {
                3_600_000_000_000L,  // 1 hour
                60_000_000_000L,     // 1 minute
                1_000_000_000L,      // 1 second
                1_000_000L,          // 1 millisecond
                1_000L,              // 1 microsecond
                1L,                  // 1 nanosecond
        };
        for (long nanos : testValues) {
            String encoded = GrpcHeadersUtil.encodeTimeout(nanos);
            long decoded = GrpcHeadersUtil.decodeTimeout(encoded);
            assertThat("round-trip for " + nanos + "ns (encoded: " + encoded + ")",
                    decoded, is(nanos));
        }
    }

    @Test
    void roundTripNonRoundMilliseconds() {
        // 1500ms — not an exact number of seconds, encodes as "1500m"
        long nanos = 1_500_000_000L;
        String encoded = GrpcHeadersUtil.encodeTimeout(nanos);
        assertThat(encoded, is("1500m"));
        assertThat(GrpcHeadersUtil.decodeTimeout(encoded), is(nanos));
    }
}
