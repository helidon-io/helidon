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

import io.helidon.http.HeaderNames;
import io.helidon.http.WritableHeaders;

import io.grpc.InternalMetadata;
import io.grpc.Metadata;

import static java.nio.charset.StandardCharsets.US_ASCII;

class GrpcHeadersUtil {

    private static final byte[] BINARY_SUFFIX = Metadata.BINARY_HEADER_SUFFIX.getBytes(US_ASCII);

    private GrpcHeadersUtil() {
    }

    static void updateHeaders(WritableHeaders<?> writable, Metadata headers) {
        byte[][] byteHeaders = InternalMetadata.serialize(headers);
        for (int i = 0; i < byteHeaders.length; i += 2) {
            byte[] key = byteHeaders[i];
            byte[] value = byteHeaders[i + 1];
            if (endsWith(key, BINARY_SUFFIX)) {
                writable.add(HeaderNames.create(new String(key, US_ASCII)),
                             InternalMetadata.BASE64_ENCODING_OMIT_PADDING.encode(value));
            } else {
                writable.add(HeaderNames.create(new String(key, US_ASCII)),
                             new String(value, US_ASCII));
            }
        }
    }

    private static boolean endsWith(byte[] buffer, byte[] suffix) {
        int from = buffer.length - suffix.length;
        if (from >= 0) {
            for (int i = from; i < buffer.length; i++) {
                if (buffer[i] != suffix[i - from]) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
