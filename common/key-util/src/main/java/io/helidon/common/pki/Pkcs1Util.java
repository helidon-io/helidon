/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

package io.helidon.common.pki;

import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.HexFormat;

final class Pkcs1Util {
    // this is a constant for RSA (and we only support RSA)
    private static final byte[] RSA_ALG = HexFormat.of().parseHex("020100300D06092A864886F70D0101010500");

    private Pkcs1Util() {
    }

    static KeySpec pkcs1RsaKeySpec(byte[] bytes) {
        return new PKCS8EncodedKeySpec(pkcs1ToPkcs8(bytes));
    }

    // Code provided by Weijun Wang
    private static byte[] pkcs1ToPkcs8(byte[] pkcs1Bytes) {

        // PKCS #8 key will look like
        // 30 len1
        // 02 01 00 30 0D 06 09 2A 86 48 86 F7 0D 01 01 01 05 00
        // 04 len2
        // p1

        byte[] len2 = encodeLen(pkcs1Bytes.length);
        int p8len = pkcs1Bytes.length + len2.length + 1 + RSA_ALG.length;
        byte[] len1 = encodeLen(p8len);
        byte[] pkcs8bytes = new byte[1 + len1.length + p8len];

        pkcs8bytes[0] = 0x30;
        System.arraycopy(len1, 0, pkcs8bytes, 1, len1.length);
        System.arraycopy(RSA_ALG, 0, pkcs8bytes, 1 + len1.length, RSA_ALG.length);
        pkcs8bytes[1 + len1.length + RSA_ALG.length] = 0x04;
        System.arraycopy(len2, 0, pkcs8bytes, 1 + len1.length + RSA_ALG.length + 1, len2.length);
        System.arraycopy(pkcs1Bytes, 0, pkcs8bytes, 1 + len1.length + RSA_ALG.length + 1 + len2.length, pkcs1Bytes.length);

        return pkcs8bytes;
    }

    private static byte[] encodeLen(int len) {
        if (len < 128) {
            return new byte[] {(byte) len};
        } else if (len < (1 << 8)) {
            return new byte[] {(byte) 0x081, (byte) len};
        } else if (len < (1 << 16)) {
            return new byte[] {(byte) 0x082, (byte) (len >> 8), (byte) len};
        } else {
            throw new PkiException("PKCS#1 key of unexpected size: " + len);
        }
    }
}
