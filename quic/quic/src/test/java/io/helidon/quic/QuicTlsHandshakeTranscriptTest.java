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

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class QuicTlsHandshakeTranscriptTest {
    // The transcript vectors in this test come from the RFC 8448 AES-128-GCM examples, so a shared cipher-suite
    // constant keeps the expected transcript hashes aligned with the right digest algorithm.
    private static final QuicTls13CipherSuite CIPHER_SUITE = QuicTls13CipherSuite.TLS_AES_128_GCM_SHA256;
    // RFC 8448 transcript-hash expectations for the simple handshake and HelloRetryRequest variants below.
    private static final String SIMPLE_SERVER_HELLO_HASH =
            "860c06edc07858ee8e78f0e7428c58edd6b43f2ca3e6e95f02ed063cf0e1cad8";
    private static final String HRR_SERVER_HELLO_HASH =
            "8aa8e828ec2f8a884fec95a3139de01c15a3daa7ff5bfc3f4bfcc21b438d7bf8";
    // The second ClientHello only appears in the HelloRetryRequest transcript path, so it stays local to this test.
    private static final String HRR_CLIENT_HELLO_2 = """
            01 00 01 fc 03 03 b0 b1 c5 a5 aa 37 c5 91 9f 2e
            d1 d5 c6 ff f7 fc b7 84 97 16 94 5a 2b 8c ee 92
            58 a3 46 67 7b 6f 00 00 06 13 01 13 03 13 02 01
            00 01 cd 00 00 00 0b 00 09 00 00 06 73 65 72 76
            65 72 ff 01 00 01 00 00 0a 00 08 00 06 00 1d 00
            17 00 18 00 33 00 47 00 45 00 17 00 41 04 a6 da
            73 92 ec 59 1e 17 ab fd 53 59 64 b9 98 94 d1 3b
            ef b2 21 b3 de f2 eb e3 83 0e ac 8f 01 51 81 26
            77 c4 d6 d2 23 7e 85 cf 01 d6 91 0c fb 83 95 4e
            76 ba 73 52 83 05 34 15 98 97 e8 06 57 80 00 2b
            00 03 02 03 04 00 0d 00 20 00 1e 04 03 05 03 06
            03 02 03 08 04 08 05 08 06 04 01 05 01 06 01 02
            01 04 02 05 02 06 02 02 02 00 2c 00 74 00 72 71
            dc d0 4b b8 8b c3 18 91 19 39 8a 00 00 00 00 ee
            fa fc 76 c1 46 b8 23 b0 96 f8 aa ca d3 65 dd 00
            30 95 3f 4e df 62 56 36 e5 f2 1b b2 e2 3f cc 65
            4b 1b 5b 40 31 8d 10 d1 37 ab cb b8 75 74 e3 6e
            8a 1f 02 5f 7d fa 5d 6e 50 78 1b 5e da 4a a1 5b
            0c 8b e7 78 25 7d 16 aa 30 30 e9 e7 84 1d d9 e4
            c0 34 22 67 e8 ca 0c af 57 1f b2 b7 cf f0 f9 34
            b0 00 2d 00 02 01 01 00 1c 00 02 40 01 00 15 00
            af 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
            00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
            00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
            00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
            00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
            00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
            00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
            00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
            00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
            00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
            00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
            """;

    @Test
    void shouldHashSimpleTranscriptFromClientHelloAndServerHello() {
        QuicTlsHandshakeTranscript transcript = new QuicTlsHandshakeTranscript();

        transcript.add(QuicTlsRfc8448Vectors.message(QuicTlsRfc8448Vectors.SIMPLE_CLIENT_HELLO));
        transcript.add(QuicTlsRfc8448Vectors.message(QuicTlsRfc8448Vectors.SIMPLE_SERVER_HELLO));

        assertThat(QuicTlsRfc8448Vectors.hex(transcript.hash(CIPHER_SUITE)), is(SIMPLE_SERVER_HELLO_HASH));
    }

    @Test
    void shouldHashHelloRetryRequestTranscriptUsingSyntheticMessageHash() {
        QuicTlsHandshakeTranscript transcript = new QuicTlsHandshakeTranscript();

        transcript.add(QuicTlsRfc8448Vectors.message(QuicTlsRfc8448Vectors.HRR_CLIENT_HELLO_1));
        transcript.add(QuicTlsRfc8448Vectors.message(QuicTlsRfc8448Vectors.HRR_SERVER_HELLO_RETRY_REQUEST));
        transcript.add(QuicTlsRfc8448Vectors.message(HRR_CLIENT_HELLO_2));
        transcript.add(QuicTlsRfc8448Vectors.message(QuicTlsRfc8448Vectors.HRR_SERVER_HELLO));

        assertThat(QuicTlsRfc8448Vectors.hex(transcript.hash(CIPHER_SUITE)), is(HRR_SERVER_HELLO_HASH));
    }

    @Test
    void shouldResolveCipherSuiteFromServerHelloMessages() throws Exception {
        assertThat(QuicTls13CipherSuite.fromServerHello(
                           QuicTlsRfc8448Vectors.message(QuicTlsRfc8448Vectors.SIMPLE_SERVER_HELLO)),
                   is(CIPHER_SUITE));
        assertThat(QuicTls13CipherSuite.fromServerHello(
                           QuicTlsRfc8448Vectors.message(QuicTlsRfc8448Vectors.HRR_SERVER_HELLO_RETRY_REQUEST)),
                   is(CIPHER_SUITE));
        assertThat(QuicTls13CipherSuite.fromServerHello(
                           QuicTlsRfc8448Vectors.message(QuicTlsRfc8448Vectors.HRR_SERVER_HELLO)),
                   is(CIPHER_SUITE));
    }
}
