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

package io.helidon.quic;

import java.nio.ByteBuffer;
import java.util.Objects;

final class QuicTlsCookie {
    private static final String MESSAGE_NAME = "HelloRetryRequest";

    private QuicTlsCookie() {
    }

    static ByteBuffer encode(byte[] cookie) {
        byte[] copy = requireCookie(cookie);
        ByteBuffer encoded = ByteBuffer.allocate(
                QuicTlsCodecSupport.encodedVectorLength(QuicTlsCodecSupport.UINT16_LENGTH, copy.length, "cookie"));
        QuicTlsCodecSupport.putVector(encoded, QuicTlsCodecSupport.UINT16_LENGTH, copy, "cookie");
        return encoded.flip();
    }

    static byte[] decode(ByteBuffer extensionData) throws QuicTransportException {
        ByteBuffer buffer = Objects.requireNonNull(extensionData, "extensionData").slice();
        byte[] cookie = QuicTlsCodecSupport.copy(QuicTlsCodecSupport.readVector(buffer,
                                                                                QuicTlsCodecSupport.UINT16_LENGTH,
                                                                                "cookie",
                                                                                MESSAGE_NAME));
        if (cookie.length == 0) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed HelloRetryRequest message: cookie");
        }
        QuicTlsCodecSupport.ensureConsumed(buffer, MESSAGE_NAME);
        return cookie;
    }

    private static byte[] requireCookie(byte[] cookie) {
        byte[] copy = Objects.requireNonNull(cookie, "cookie").clone();
        if (copy.length == 0) {
            throw new IllegalArgumentException("TLS cookie must not be empty");
        }
        return copy;
    }
}
