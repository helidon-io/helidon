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

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.HexFormat;

final class QuicTlsRfc8448Vectors {
    // RFC 8448 publishes these hello messages as complete wire images. Keeping them in one shared helper ensures
    // transcript, cipher-suite, and hello-codec tests all exercise the same published examples.
    static final String SIMPLE_CLIENT_HELLO = """
            01 00 00 c0 03 03 cb 34 ec b1 e7 81 63 ba 1c 38
            c6 da cb 19 6a 6d ff a2 1a 8d 99 12 ec 18 a2 ef
            62 83 02 4d ec e7 00 00 06 13 01 13 03 13 02 01
            00 00 91 00 00 00 0b 00 09 00 00 06 73 65 72 76
            65 72 ff 01 00 01 00 00 0a 00 14 00 12 00 1d 00
            17 00 18 00 19 01 00 01 01 01 02 01 03 01 04 00
            23 00 00 00 33 00 26 00 24 00 1d 00 20 99 38 1d
            e5 60 e4 bd 43 d2 3d 8e 43 5a 7d ba fe b3 c0 6e
            51 c1 3c ae 4d 54 13 69 1e 52 9a af 2c 00 2b 00
            03 02 03 04 00 0d 00 20 00 1e 04 03 05 03 06 03
            02 03 08 04 08 05 08 06 04 01 05 01 06 01 02 01
            04 02 05 02 06 02 02 02 00 2d 00 02 01 01 00 1c
            00 02 40 01
            """;
    static final String SIMPLE_SERVER_HELLO = """
            02 00 00 56 03 03 a6 af 06 a4 12 18 60 dc 5e 6e
            60 24 9c d3 4c 95 93 0c 8a c5 cb 14 34 da c1 55
            77 2e d3 e2 69 28 00 13 01 00 00 2e 00 33 00 24
            00 1d 00 20 c9 82 88 76 11 20 95 fe 66 76 2b db
            f7 c6 72 e1 56 d6 cc 25 3b 83 3d f1 dd 69 b1 b0
            4e 75 1f 0f 00 2b 00 02 03 04
            """;
    static final String HRR_CLIENT_HELLO_1 = """
            01 00 00 b0 03 03 b0 b1 c5 a5 aa 37 c5 91 9f 2e
            d1 d5 c6 ff f7 fc b7 84 97 16 94 5a 2b 8c ee 92
            58 a3 46 67 7b 6f 00 00 06 13 01 13 03 13 02 01
            00 00 81 00 00 00 0b 00 09 00 00 06 73 65 72 76
            65 72 ff 01 00 01 00 00 0a 00 08 00 06 00 1d 00
            17 00 18 00 33 00 26 00 24 00 1d 00 20 e8 e8 e3
            f3 b9 3a 25 ed 97 a1 4a 7d ca cb 8a 27 2c 62 88
            e5 85 c6 48 4d 05 26 2f ca d0 62 ad 1f 00 2b 00
            03 02 03 04 00 0d 00 20 00 1e 04 03 05 03 06 03
            02 03 08 04 08 05 08 06 04 01 05 01 06 01 02 01
            04 02 05 02 06 02 02 02 00 2d 00 02 01 01 00 1c
            00 02 40 01
            """;
    static final String HRR_SERVER_HELLO_RETRY_REQUEST = """
            02 00 00 ac 03 03 cf 21 ad 74 e5 9a 61 11 be 1d
            8c 02 1e 65 b8 91 c2 a2 11 16 7a bb 8c 5e 07 9e
            09 e2 c8 a8 33 9c 00 13 01 00 00 84 00 33 00 02
            00 17 00 2c 00 74 00 72 71 dc d0 4b b8 8b c3 18
            91 19 39 8a 00 00 00 00 ee fa fc 76 c1 46 b8 23
            b0 96 f8 aa ca d3 65 dd 00 30 95 3f 4e df 62 56
            36 e5 f2 1b b2 e2 3f cc 65 4b 1b 5b 40 31 8d 10
            d1 37 ab cb b8 75 74 e3 6e 8a 1f 02 5f 7d fa 5d
            6e 50 78 1b 5e da 4a a1 5b 0c 8b e7 78 25 7d 16
            aa 30 30 e9 e7 84 1d d9 e4 c0 34 22 67 e8 ca 0c
            af 57 1f b2 b7 cf f0 f9 34 b0 00 2b 00 02 03 04
            """;
    static final String HRR_SERVER_HELLO = """
            02 00 00 77 03 03 bb 34 1d 84 7f d7 89 c4 7c 38
            71 72 dc 0c 9b f1 47 fc ca cb 50 43 d8 6c a4 c5
            98 d3 ff 57 1b 98 00 13 01 00 00 4f 00 33 00 45
            00 17 00 41 04 58 3e 05 4b 7a 66 67 2a e0 20 ad
            9d 26 86 fc c8 5b 5a d4 1a 13 4a 0f 03 ee 72 b8
            93 05 2b d8 5b 4c 8d e6 77 6f 5b 04 ac 07 d8 35
            40 ea b3 e3 d9 c5 47 bc 65 28 c4 31 7d 29 46 86
            09 3a 6c ad 7d 00 2b 00 02 03 04
            """;
    // RFC 8448 publishes the complete server Certificate message for the simple 1-RTT handshake; keeping the exact
    // wire image here lets certificate-message tests and server-flight generation reuse the same known certificate.
    static final String SIMPLE_SERVER_CERTIFICATE = """
            0b 00 01 b9 00 00 01 b5 00 01 b0 30 82 01 ac 30
            82 01 15 a0 03 02 01 02 02 01 02 30 0d 06 09 2a
            86 48 86 f7 0d 01 01 0b 05 00 30 0e 31 0c 30 0a
            06 03 55 04 03 13 03 72 73 61 30 1e 17 0d 31 36
            30 37 33 30 30 31 32 33 35 39 5a 17 0d 32 36 30
            37 33 30 30 31 32 33 35 39 5a 30 0e 31 0c 30 0a
            06 03 55 04 03 13 03 72 73 61 30 81 9f 30 0d 06
            09 2a 86 48 86 f7 0d 01 01 01 05 00 03 81 8d 00
            30 81 89 02 81 81 00 b4 bb 49 8f 82 79 30 3d 98
            08 36 39 9b 36 c6 98 8c 0c 68 de 55 e1 bd b8 26
            d3 90 1a 24 61 ea fd 2d e4 9a 91 d0 15 ab bc 9a
            95 13 7a ce 6c 1a f1 9e aa 6a f9 8c 7c ed 43 12
            09 98 e1 87 a8 0e e0 cc b0 52 4b 1b 01 8c 3e 0b
            63 26 4d 44 9a 6d 38 e2 2a 5f da 43 08 46 74 80
            30 53 0e f0 46 1c 8c a9 d9 ef bf ae 8e a6 d1 d0
            3e 2b d1 93 ef f0 ab 9a 80 02 c4 74 28 a6 d3 5a
            8d 88 d7 9f 7f 1e 3f 02 03 01 00 01 a3 1a 30 18
            30 09 06 03 55 1d 13 04 02 30 00 30 0b 06 03 55
            1d 0f 04 04 03 02 05 a0 30 0d 06 09 2a 86 48 86
            f7 0d 01 01 0b 05 00 03 81 81 00 85 aa d2 a0 e5
            b9 27 6b 90 8c 65 f7 3a 72 67 17 06 18 a5 4c 5f
            8a 7b 33 7d 2d f7 a5 94 36 54 17 f2 ea e8 f8 a5
            8c 8f 81 72 f9 31 9c f3 6b 7f d6 c5 5b 80 f2 1a
            03 01 51 56 72 60 96 fd 33 5e 5e 67 f2 db f1 02
            70 2e 60 8c ca e6 be c1 fc 63 a4 2a 99 be 5c 3e
            b7 10 7c 3c 54 e9 b9 eb 2b d5 20 3b 1c 3b 84 e0
            a8 b2 f7 59 40 9b a3 ea c9 d9 1d 40 2d cc 0c c8
            f8 96 12 29 ac 91 87 b4 2b 4d e1 00 00
            """;
    // The matching RFC 8448 CertificateVerify message is kept verbatim so the codec tests can assert that Helidon's
    // parser and encoder preserve the exact TLS 1.3 RSA-PSS wire image.
    static final String SIMPLE_SERVER_CERTIFICATE_VERIFY = """
            0f 00 00 84 08 04 00 80 5a 74 7c 5d 88 fa 9b d2
            e5 5a b0 85 a6 10 15 b7 21 1f 82 4c d4 84 14 5a
            b3 ff 52 f1 fd a8 47 7b 0b 7a bc 90 db 78 e2 d3
            3a 5c 14 1a 07 86 53 fa 6b ef 78 0c 5e a2 48 ee
            aa a7 85 c4 f3 94 ca b6 d3 0b be 8d 48 59 ee 51
            1f 60 29 57 b1 54 11 ac 02 76 71 45 9e 46 44 5c
            9e a5 8c 18 1e 81 8e 95 b8 c3 fb 0b f3 27 84 09
            d3 be 15 2a 3d a5 04 3e 06 3d da 65 cd f5 ae a2
            0d 53 df ac d4 2f 74 f3
            """;
    // The RFC simple server Finished message provides a fixed verify_data sample that is useful both for message-codec
    // tests and for checking that the Helidon engine derives the same transcript-bound handshake MAC.
    static final String SIMPLE_SERVER_FINISHED = """
            14 00 00 20 9b 9b 14 1d 90 63 37 fb d2 cb dc e7
            1d f4 de da 4a b4 2c 30 95 72 cb 7f ff ee 54 54
            b7 8f 07 18
            """;
    // The RFC simple client Finished message is retained so the client-side Finished encoder can be checked against a
    // known post-authentication wire image instead of only synthetic test data.
    static final String SIMPLE_CLIENT_FINISHED = """
            14 00 00 20 a8 ec 43 6d 67 76 34 ae 52 5a c1 fc
            eb e1 1a 03 9e c1 76 94 fa c6 e9 85 27 b6 42 f2
            ed d5 ce 61
            """;
    private static final HexFormat HEX = HexFormat.of();
    // RFC 8448 section 2 publishes the RSA certificate private-key components so implementations can reproduce the
    // server CertificateVerify signature exactly during local interop and vector tests.
    private static final String RSA_MODULUS =
            "b4bb498f8279303d980836399b36c6988c0c68de55e1bdb826d3901a2461eafd"
                    + "2de49a91d015abbc9a95137ace6c1af19eaa6af98c7ced43120998e187a80ee0"
                    + "ccb0524b1b018c3e0b63264d449a6d38e22a5fda430846748030530ef0461c8c"
                    + "a9d9efbfae8ea6d1d03e2bd193eff0ab9a8002c47428a6d35a8d88d79f7f1e3f";
    private static final String RSA_PUBLIC_EXPONENT = "010001";
    private static final String RSA_PRIVATE_EXPONENT =
            "04dea705d43a6ea7209dd8072111a83c81e322a59278b33480641eaf7c0a6985"
                    + "b8e31c44f6de62e1b4c2309f6126e77b7c41e923314bbfa3881305dc1217f16c"
                    + "819ce538e922f369828d0e57195d8c8488460207b2faa726bcf708bbd7db7f67"
                    + "9f893492fc2a622e08970aac441ce4e0c3088df25ae679233df8a3bda2ff9941";
    private static final String RSA_PRIME_1 =
            "e435fb7cc83737756dacea96ab7f59a2cc1069db7deb190e17e33a532b273f30"
                    + "a327aa0aaabc58cd67466af9845fadc675fe094af92c4bd1f2c1bc33dd2e0515";
    private static final String RSA_PRIME_2 =
            "cabd3bc0e0438664c8d4cc9f99977a94d9bbfead8e43870abae3f7eb8b4e0eee"
                    + "8af1d9b4719ba6196cf2cbbae eebf8b3490afe9e9ffa74a88aa51fc645629303"
                    .replace(" ", "");
    private static final String RSA_EXPONENT_1 =
            "3f57345c27fe1b687e6e761627b78b1b826433dd760fa0bea6a6acf39490aa1b"
                    + "47cda4869d68f584dd5b5029bd32093b8258661fe715025e5d70a45a08d3d319";
    private static final String RSA_EXPONENT_2 =
            "183da01363bd2f2885cacbdc9964bf4764f1517636f86401286f71893c52ccfe"
                    + "40a6c23d0d086b47c6fb10d8fd1041e04def7e9a40ce957c417794e10412d139";
    private static final String RSA_COEFFICIENT =
            "839ca9a085e4286b2c90e466997a2c681f21339aa3477814e4dec11833050ed5"
                    + "0dd13cc038048a43c59b2acc416889c037665fe5afa605969f8c01dfa5ca969d";

    private QuicTlsRfc8448Vectors() {
    }

    static ByteBuffer message(String hex) {
        return ByteBuffer.wrap(bytes(hex));
    }

    static byte[] bytes(String hex) {
        return HEX.parseHex(hex.replaceAll("\\s+", ""));
    }

    static String hex(byte[] bytes) {
        return HEX.formatHex(bytes);
    }

    static X509Certificate rsaCertificate() throws Exception {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(rsaCertificateDer()));
    }

    static byte[] rsaCertificateDer() throws Exception {
        return QuicTlsCertificateMessage.decode(message(SIMPLE_SERVER_CERTIFICATE))
                .certificateEntries()
                .getFirst()
                .encodedCertificate();
    }

    static PrivateKey rsaPrivateKey() throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(new RSAPrivateCrtKeySpec(
                bigInteger(RSA_MODULUS),
                bigInteger(RSA_PUBLIC_EXPONENT),
                bigInteger(RSA_PRIVATE_EXPONENT),
                bigInteger(RSA_PRIME_1),
                bigInteger(RSA_PRIME_2),
                bigInteger(RSA_EXPONENT_1),
                bigInteger(RSA_EXPONENT_2),
                bigInteger(RSA_COEFFICIENT)));
    }

    private static BigInteger bigInteger(String hex) {
        return new BigInteger(1, bytes(hex));
    }
}
