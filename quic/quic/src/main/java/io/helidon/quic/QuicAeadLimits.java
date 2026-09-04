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

final class QuicAeadLimits {
    static final long NO_CONFIDENTIALITY_LIMIT = -1;
    static final long DEFAULT_AES_GCM_CONFIDENTIALITY_LIMIT = 1L << 23;
    static final long DEFAULT_CHACHA20_POLY1305_CONFIDENTIALITY_LIMIT = NO_CONFIDENTIALITY_LIMIT;

    private QuicAeadLimits() {
    }

    record Confidentiality(long aesGcm, long chacha20Poly1305) {
        Confidentiality {
            if (aesGcm < 1) {
                throw new IllegalArgumentException("aesGcm must be greater than 0: " + aesGcm);
            }
            if (chacha20Poly1305 == 0 || chacha20Poly1305 < NO_CONFIDENTIALITY_LIMIT) {
                throw new IllegalArgumentException(
                        "chacha20Poly1305 must be greater than 0 or -1 for no limit: " + chacha20Poly1305);
            }
        }

        static Confidentiality defaults() {
            return new Confidentiality(DEFAULT_AES_GCM_CONFIDENTIALITY_LIMIT,
                                       DEFAULT_CHACHA20_POLY1305_CONFIDENTIALITY_LIMIT);
        }
    }
}
