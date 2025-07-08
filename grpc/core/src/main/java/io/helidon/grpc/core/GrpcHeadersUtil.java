/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
                headers.add(HeaderNames.create(name, new String(encoder.encode(binary), US_ASCII)));
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
}
