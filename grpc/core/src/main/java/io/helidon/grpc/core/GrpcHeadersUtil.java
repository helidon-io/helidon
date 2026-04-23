/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

import java.util.Base64;
import java.util.Optional;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;

import io.grpc.Metadata;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * Utility class to map HTTP/2 headers to Metadata.
 */
public class GrpcHeadersUtil {

    private GrpcHeadersUtil() {
    }

    /**
     * Updates headers with metadata.
     *
     * @param headers the headers to update
     * @param metadata the metadata
     */
    public static void updateHeaders(WritableHeaders<?> headers, Metadata metadata) {
        Base64.Encoder encoder = Base64.getEncoder();
        metadata.keys().forEach(name -> {
            if (name.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                Metadata.Key<byte[]> key = Metadata.Key.of(name, Metadata.BINARY_BYTE_MARSHALLER);
                byte[] binary = metadata.get(key);
                headers.add(HeaderNames.create(name), new String(encoder.encode(binary), US_ASCII));
            } else {
                Metadata.Key<String> key = Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER);
                Iterable<String> ascii = metadata.getAll(key);
                if (ascii != null) {
                    ascii.forEach(v -> headers.add(HeaderNames.create(name), v));
                }
            }
        });
    }

    /**
     * Converts a set of HTTP/2 headers into a Metadata instance.
     *
     * @param headers the headers to convert
     * @return the new metadata
     */
    public static Metadata toMetadata(Http2Headers headers) {
        Base64.Decoder decoder = Base64.getDecoder();
        Metadata metadata = new Metadata();
        headers.httpHeaders().forEach(header -> {
            String name = header.name();
            if (name.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                Metadata.Key<byte[]> key = Metadata.Key.of(name, Metadata.BINARY_BYTE_MARSHALLER);
                metadata.put(key, decoder.decode(header.valueBytes()));
            } else {
                Metadata.Key<String> key = Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER);
                header.allValues().forEach(value -> metadata.put(key, value));
            }
        });
        return metadata;
    }

    /**
     * The {@code grpc-timeout} header name.
     *
     * <p>Defined in the
     * <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over HTTP/2 spec</a>:
     * <pre>
     * Timeout → "grpc-timeout" TimeoutValue TimeoutUnit
     * TimeoutValue → {positive integer as ASCII string of at most 8 digits}
     * TimeoutUnit → Hour / Minute / Second / Millisecond / Microsecond / Nanosecond
     *   Hour        → "H"
     *   Minute      → "M"
     *   Second      → "S"
     *   Millisecond → "m"
     *   Microsecond → "u"
     *   Nanosecond  → "n"
     * </pre>
     */
    public static final HeaderName GRPC_TIMEOUT = HeaderNames.create("grpc-timeout");

    // Per spec: TimeoutValue is a positive integer, at most 8 ASCII digits
    private static final long MAX_TIMEOUT_VALUE = 99_999_999L;
    private static final char[] TIMEOUT_UNITS = {'H', 'M', 'S', 'm', 'u', 'n'};
    private static final long[] NANOS_PER_UNIT = {3_600_000_000_000L, 60_000_000_000L, 1_000_000_000L, 1_000_000L, 1_000L, 1L};

    /**
     * Encodes a timeout as a {@code grpc-timeout} header value.
     *
     * <p>Picks the largest unit where the numeric value is both positive and
     * fits in 8 ASCII digits, and the conversion is exact (no loss of precision).
     * See <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">spec</a>.
     *
     * @param timeoutNanos timeout in nanoseconds
     * @return encoded header value such as {@code "500m"}, or {@code null} if {@code timeoutNanos <= 0}
     */
    public static Optional<String> encodeTimeout(long timeoutNanos) {
        if (timeoutNanos <= 0) {
            return Optional.empty();
        }
        // Walk from largest to smallest unit; pick the first where the value
        // is positive, fits in 8 digits, and the division is exact (no truncation).
        for (int i = 0; i < NANOS_PER_UNIT.length; i++) {
            long value = timeoutNanos / NANOS_PER_UNIT[i];
            if (value > 0 && value <= MAX_TIMEOUT_VALUE && timeoutNanos % NANOS_PER_UNIT[i] == 0) {
                return Optional.of(value + String.valueOf(TIMEOUT_UNITS[i]));
            }
        }
        // Nanoseconds with possible truncation to 8 digits (last resort for values
        // that do not align to any unit boundary within 8 digits)
        return Optional.of(Math.min(timeoutNanos, MAX_TIMEOUT_VALUE) + "n");
    }

    /**
     * Decodes a {@code grpc-timeout} header value to nanoseconds.
     *
     * <p>See <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">spec</a>.
     *
     * @param value header value such as {@code "500m"}
     * @return timeout in nanoseconds
     * @throws IllegalArgumentException if the value is null, empty, malformed, or overflows
     */
    public static long decodeTimeout(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("grpc-timeout value is null or empty");
        }
        char unit = value.charAt(value.length() - 1);
        String digits = value.substring(0, value.length() - 1);
        if (digits.isEmpty()) {
            throw new IllegalArgumentException("grpc-timeout has no numeric value: " + value);
        }
        long numericValue;
        try {
            numericValue = Long.parseLong(digits);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("grpc-timeout has invalid digits: " + value, e);
        }
        if (numericValue < 0) {
            throw new IllegalArgumentException("grpc-timeout must be a positive integer: " + value);
        }
        for (int i = 0; i < TIMEOUT_UNITS.length; i++) {
            if (unit == TIMEOUT_UNITS[i]) {
                try {
                    return Math.multiplyExact(numericValue, NANOS_PER_UNIT[i]);
                } catch (ArithmeticException e) {
                    throw new IllegalArgumentException(
                            "grpc-timeout value overflows long: " + value, e);
                }
            }
        }
        throw new IllegalArgumentException("grpc-timeout has unknown unit '" + unit + "': " + value);
    }
}
