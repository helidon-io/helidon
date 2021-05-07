/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.math.BigInteger;
import java.security.spec.KeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;

import sun.security.util.DerInputStream;
import sun.security.util.DerValue;

final class DerUtils {
    private DerUtils() {
    }

    static void checkEnabled() {
        Module javaBase = String.class.getModule();
        Module myModule = DerUtils.class.getModule();

        if (!javaBase.isExported("sun.security.util", myModule)) {
            //--add-exports java.base/sun.security.util=io.helidon.common.pki
            throw new PkiException("Cannot read PKCS#1 key specification, as package sun.security.util "
                                           + "is not exported to this module. Please add --add-exports "
                                           + "java.base/sun.security.util=io.helidon.common.pki to java "
                                           + "command line options");
        }
    }

    static KeySpec pkcs1RsaKeySpec(byte[] bytes) {
        try {
            DerInputStream derReader = new DerInputStream(bytes);
            DerValue[] seq = derReader.getSequence(0);
            // skip version seq[0];
            BigInteger modulus = seq[1].getBigInteger();
            BigInteger publicExp = seq[2].getBigInteger();
            BigInteger privateExp = seq[3].getBigInteger();
            BigInteger prime1 = seq[4].getBigInteger();
            BigInteger prime2 = seq[5].getBigInteger();
            BigInteger exp1 = seq[6].getBigInteger();
            BigInteger exp2 = seq[7].getBigInteger();
            BigInteger crtCoef = seq[8].getBigInteger();

            return new RSAPrivateCrtKeySpec(modulus, publicExp, privateExp, prime1, prime2, exp1, exp2, crtCoef);
        } catch (IOException e) {
            throw new PkiException("Failed to get PKCS#1 RSA key spec", e);
        }
    }
}
