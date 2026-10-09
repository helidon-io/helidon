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
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;

import io.grpc.Metadata;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * Utility class for gRPC metadata and timeout headers.
 */
public class GrpcHeadersUtil {

    private GrpcHeadersUtil() {
    }

    /**
     * Encodes a timeout as a gRPC {@code grpc-timeout} header value.
     * The value uses at most eight decimal digits and the finest unit that fits,
     * rounding up when conversion to a coarser unit is necessary.
     *
     * @param timeoutNanos nonnegative timeout in nanoseconds
     * @return encoded timeout
     * @throws java.lang.IllegalArgumentException if the timeout is negative
     */
    public static String encodeTimeout(long timeoutNanos) {
        if (timeoutNanos < 0) {
            throw new IllegalArgumentException("Timeout must not be negative");
        }
        long value = timeoutNanos;
        int unit = 0;
        while (value > 99_999_999) {
            value = Math.ceilDiv(value, unit < 3 ? 1000 : 60);
            unit++;
        }
        return Long.toString(value) + "numSMH".charAt(unit);
    }

    /**
     * Decodes a gRPC {@code grpc-timeout} header value.
     * The value must contain one to eight ASCII decimal digits followed by
     * {@code H}, {@code M}, {@code S}, {@code m}, {@code u}, or {@code n}.
     * Timeouts exceeding the nanosecond range are saturated at {@link java.lang.Long#MAX_VALUE}.
     *
     * @param timeout encoded timeout
     * @return nonnegative timeout in nanoseconds
     * @throws java.lang.IllegalArgumentException if the timeout is malformed
     */
    public static long decodeTimeout(String timeout) {
        Objects.requireNonNull(timeout);
        int length = timeout.length();
        if (length < 2 || length > 9) {
            throw new IllegalArgumentException("Invalid gRPC timeout length");
        }
        long value = 0;
        for (int i = 0; i < length - 1; i++) {
            char digit = timeout.charAt(i);
            if (digit < '0' || digit > '9') {
                throw new IllegalArgumentException("Invalid gRPC timeout value");
            }
            value = value * 10 + digit - '0';
        }
        TimeUnit unit = switch (timeout.charAt(length - 1)) {
            case 'H' -> TimeUnit.HOURS;
            case 'M' -> TimeUnit.MINUTES;
            case 'S' -> TimeUnit.SECONDS;
            case 'm' -> TimeUnit.MILLISECONDS;
            case 'u' -> TimeUnit.MICROSECONDS;
            case 'n' -> TimeUnit.NANOSECONDS;
            default -> throw new IllegalArgumentException("Invalid gRPC timeout unit");
        };
        return unit.toNanos(value);
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
                Iterable<byte[]> binary = metadata.getAll(key);
                if (binary != null) {
                    binary.forEach(value -> headers.add(HeaderNames.create(name),
                                                        new String(encoder.encode(value), US_ASCII)));
                }
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
        return toMetadata(headers.httpHeaders());
    }

    /**
     * Converts HTTP headers into a Metadata instance.
     *
     * @param headers the headers to convert
     * @return the new metadata
     */
    public static Metadata toMetadata(Headers headers) {
        Base64.Decoder decoder = Base64.getDecoder();
        Metadata metadata = new Metadata();
        headers.forEach(header -> {
            String name = header.name();
            if (name.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                Metadata.Key<byte[]> key = Metadata.Key.of(name, Metadata.BINARY_BYTE_MARSHALLER);
                header.allValues().forEach(value -> {
                    for (String encoded : value.split(",", -1)) {
                        metadata.put(key, decoder.decode(encoded.trim()));
                    }
                });
            } else {
                Metadata.Key<String> key = Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER);
                header.allValues().forEach(value -> metadata.put(key, value));
            }
        });
        return metadata;
    }
}
