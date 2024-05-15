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

import java.util.Base64;

import io.helidon.http.HeaderNames;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;

import io.grpc.Metadata;

import static java.nio.charset.StandardCharsets.US_ASCII;

class GrpcHeadersUtil {

    private GrpcHeadersUtil() {
    }

    static void updateHeaders(WritableHeaders<?> writable, Metadata headers) {
        Base64.Encoder encoder = Base64.getEncoder();
        headers.keys().forEach(name -> {
            if (name.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                byte[] binary = headers.get(Metadata.Key.of(name, Metadata.BINARY_BYTE_MARSHALLER));
                writable.add(HeaderNames.create(name, new String(encoder.encode(binary), US_ASCII)));
            } else {
                String ascii = headers.get(Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER));
                writable.add(HeaderNames.create(name, ascii));
            }
        });
    }

    static Metadata toMetadata(Http2Headers headers) {
        Base64.Decoder decoder = Base64.getDecoder();
        Metadata metadata = new Metadata();
        headers.httpHeaders().forEach(header -> {
            String name = header.name();
            if (name.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                metadata.put(Metadata.Key.of(name, Metadata.BINARY_BYTE_MARSHALLER),
                             decoder.decode(name.getBytes(US_ASCII)));
            } else {
                metadata.put(Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER), name);
            }
        });
        return metadata;
    }
}
