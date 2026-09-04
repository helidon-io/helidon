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
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.ProviderException;

import javax.crypto.KDF;
import javax.crypto.SecretKey;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import io.helidon.common.buffers.BufferData;

final class QuicTlsHkdf {
    // HKDF-Expand-Label uses an empty context for most TLS 1.3 derivations in this package, so one shared array
    // avoids repeating that allocation in every helper call.
    private static final byte[] EMPTY_CONTEXT = BufferData.EMPTY_BYTES;
    // RFC 8446 prepends "tls13 " to every HKDF label; centralizing the prefix keeps derived labels consistent.
    private static final String TLS13_LABEL_PREFIX = "tls13 ";

    private QuicTlsHkdf() {
    }

    static SecretKey extractSecret(String hkdfAlgorithm,
                                   String secretAlgorithm,
                                   byte[] salt,
                                   byte[] ikm) {
        try {
            byte[] secret = KDF.getInstance(hkdfAlgorithm)
                    .deriveData(HKDFParameterSpec.ofExtract()
                                        .addSalt(salt)
                                        .addIKM(ikm)
                                        .extractOnly());
            return new SecretKeySpec(secret, secretAlgorithm);
        } catch (GeneralSecurityException | ProviderException | UnsupportedOperationException e) {
            throw new QuicTransportException("Failed to extract a TLS secret",
                                             0,
                                             QuicTransportErrors.INTERNAL_ERROR.code(),
                                             e);
        }
    }

    static SecretKey expandSecret(String hkdfAlgorithm,
                                  SecretKey secret,
                                  String label,
                                  int length,
                                  String keyAlgorithm) {
        return expandSecret(hkdfAlgorithm, secret, label, EMPTY_CONTEXT, length, keyAlgorithm);
    }

    static SecretKey expandSecret(String hkdfAlgorithm,
                                  SecretKey secret,
                                  String label,
                                  byte[] context,
                                  int length,
                                  String keyAlgorithm) {
        return new SecretKeySpec(expandData(hkdfAlgorithm, secret, label, context, length), keyAlgorithm);
    }

    static byte[] expandData(String hkdfAlgorithm,
                             SecretKey secret,
                             String label,
                             int length) {
        return expandData(hkdfAlgorithm, secret, label, EMPTY_CONTEXT, length);
    }

    static byte[] expandData(String hkdfAlgorithm,
                             SecretKey secret,
                             String label,
                             byte[] context,
                             int length) {
        try {
            return KDF.getInstance(hkdfAlgorithm)
                    .deriveData(HKDFParameterSpec.expandOnly(secret, tls13LabelInfo(label, context, length), length));
        } catch (GeneralSecurityException | ProviderException | UnsupportedOperationException e) {
            throw new QuicTransportException("Failed to expand a TLS secret",
                                             0,
                                             QuicTransportErrors.INTERNAL_ERROR.code(),
                                             e);
        }
    }

    private static byte[] tls13LabelInfo(String label, byte[] context, int length) {
        byte[] labelBytes = (TLS13_LABEL_PREFIX + label).getBytes(StandardCharsets.UTF_8);
        ByteBuffer info = ByteBuffer.allocate(4 + labelBytes.length + context.length);
        info.putShort((short) length);
        info.put((byte) labelBytes.length);
        info.put(labelBytes);
        info.put((byte) context.length);
        info.put(context);
        return info.array();
    }
}
