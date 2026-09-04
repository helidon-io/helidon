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

import java.util.HexFormat;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

enum QuicTlsVersionData {
    V1(QuicVersion.QUIC_V1,
       "38762cf7f55934b34d179ae6a4c80cadccbb7f0a",
       "be0c690b9f66575a1d766b54e368c84e",
       "461599d35d632bf2239825bb",
       "quic key",
       "quic iv",
       "quic hp",
       "quic ku"),
    V2(QuicVersion.QUIC_V2,
       "0dede3def700a6db819381be6e269dcbf9bd2ed9",
       "8fb4b01b56ac48e260fbcbcead7ccc92",
       "d86969bc2d7c6d9990efb04a",
       "quicv2 key",
       "quicv2 iv",
       "quicv2 hp",
       "quicv2 ku");

    private final QuicVersion version;
    private final byte[] initialSalt;
    private final SecretKey retryKey;
    private final byte[] retryIv;
    private final String keyLabel;
    private final String ivLabel;
    private final String hpLabel;
    private final String keyUpdateLabel;

    QuicTlsVersionData(QuicVersion version,
                       String initialSalt,
                       String retryKey,
                       String retryIv,
                       String keyLabel,
                       String ivLabel,
                       String hpLabel,
                       String keyUpdateLabel) {
        this.version = version;
        this.initialSalt = HexFormat.of().parseHex(initialSalt);
        this.retryKey = new SecretKeySpec(HexFormat.of().parseHex(retryKey), "AES");
        this.retryIv = HexFormat.of().parseHex(retryIv);
        this.keyLabel = keyLabel;
        this.ivLabel = ivLabel;
        this.hpLabel = hpLabel;
        this.keyUpdateLabel = keyUpdateLabel;
    }

    static QuicTlsVersionData forVersion(QuicVersion version) {
        for (QuicTlsVersionData value : values()) {
            if (value.version == version) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unsupported QUIC version: " + version);
    }

    byte[] initialSalt() {
        return initialSalt.clone();
    }

    SecretKey retryKey() {
        return retryKey;
    }

    byte[] retryIv() {
        return retryIv.clone();
    }

    String keyLabel() {
        return keyLabel;
    }

    String ivLabel() {
        return ivLabel;
    }

    String hpLabel() {
        return hpLabel;
    }

    String keyUpdateLabel() {
        return keyUpdateLabel;
    }
}
