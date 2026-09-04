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

import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicTls13SecretScheduleTest {
    private static final HexFormat HEX = HexFormat.of();
    // The RFC 8448 vectors in this test all use TLS_AES_128_GCM_SHA256, so a shared schedule constant keeps each
    // assertion tied to the same cipher-suite-specific hash and HKDF parameters.
    private static final QuicTls13SecretSchedule SCHEDULE =
            new QuicTls13SecretSchedule(QuicTls13CipherSuite.TLS_AES_128_GCM_SHA256);
    // RFC 8448 simple 1-RTT handshake vector values. They stay as named constants so each assertion can point at
    // the exact transcript hash, secret, or verify_data fragment it is validating.
    private static final String EARLY_SECRET =
            "33ad0a1c607ec03b09e6cd9893680ce210adf300aa1f2660e1b22e10f170f92a";
    private static final String SIMPLE_SHARED_SECRET =
            "8bd4054fb55b9d63fdfbacf9f04b9f0d35e6d63f537563efd46272900f89492d";
    private static final String SIMPLE_SERVER_HELLO_HASH =
            "860c06edc07858ee8e78f0e7428c58edd6b43f2ca3e6e95f02ed063cf0e1cad8";
    private static final String SIMPLE_HANDSHAKE_SECRET =
            "1dc826e93606aa6fdc0aadc12f741b01046aa6b99f691ed221a9f0ca043fbeac";
    private static final String SIMPLE_CLIENT_HANDSHAKE_TRAFFIC_SECRET =
            "b3eddb126e067f35a780b3abf45e2d8f3b1a950738f52e9600746a0e27a55a21";
    private static final String SIMPLE_SERVER_HANDSHAKE_TRAFFIC_SECRET =
            "b67b7d690cc16c4e75e54213cb2d37b4e9c912bcded9105d42befd59d391ad38";
    private static final String SIMPLE_SERVER_FINISHED_KEY =
            "008d3b66f816ea559f96b537e885c31fc068bf492c652f01f288a1d8cdc19fc8";
    private static final String SIMPLE_CLIENT_FINISHED_KEY =
            "b80ad01015fb2f0bd65ff7d4da5d6bf83f84821d1f87fdc7d3c75b5a7b42d9c4";
    private static final String SIMPLE_SERVER_FINISHED_HASH =
            "9608102a0f1ccc6db6250b7b7e417b1a000eaada3daae4777a7686c9ff83df13";
    private static final String SIMPLE_CLIENT_FINISHED_VERIFY_DATA =
            "a8ec436d677634ae525ac1fcebe11a039ec17694fac6e98527b642f2edd5ce61";
    private static final String RESUMED_PSK =
            "4ecd0eb6ec3b4d87f5d6028f922ca4c5851a277fd41311c9e62d2c9492e1c4f3";
    private static final String RESUMED_EARLY_SECRET =
            "9b2188e9b2fc6d64d71dc329900e20bb41915000f678aa839cbb797cb7d8332c";
    private static final String RESUMED_BINDER_HASH =
            "63224b2e4573f2d3454ca84b9d009a04f6be9e05711a8396473aefa01e924a14";
    private static final String RESUMED_BINDER_KEY =
            "69fe131a3bbad5d63c64eebcc30e395b9d8107726a13d074e389dbc8a4e47256";
    private static final String RESUMED_BINDER_FINISHED_KEY =
            "5588673e72cb59c87d220caffe94f2dea9a3b1609f7d50e90a48227db9ed7eaa";
    private static final String RESUMED_BINDER =
            "3add4fb2d8fdf822a0ca3cf7678ef5e88dae990141c5924d57bb6fa31b9e5f9d";
    private static final String RESUMED_CLIENT_HELLO_HASH =
            "08ad0fa05d7c7233b1775ba2ff9f4c5b8b59276b7f227f13a976245f5d960913";
    private static final String RESUMED_CLIENT_EARLY_TRAFFIC_SECRET =
            "3fbbe6a60deb66c30a32795aba0eff7eaa10105586e7be5c09678d63b6caab62";
    private static final String SIMPLE_MASTER_SECRET =
            "18df06843d13a08bf2a449844c5f8a478001bc4d4c627984d5a41da8d0402919";
    private static final String SIMPLE_CLIENT_APPLICATION_TRAFFIC_SECRET =
            "9e40646ce79a7f9dc05af8889bce6552875afa0b06df0087f792ebb7c17504a5";
    private static final String SIMPLE_SERVER_APPLICATION_TRAFFIC_SECRET =
            "a11af9f05531f856ad47116b45a950328204b4f44bfb6b3a4b4f1f3fcb631643";
    private static final String SIMPLE_EXPORTER_MASTER_SECRET =
            "fe22f881176eda18eb8f44529e6792c50c9a3f89452f68d8ae311b4309d3cf50";
    private static final String SIMPLE_CLIENT_FINISHED_HASH =
            "209145a96ee8e2a122ff810047cc952684658d6049e86429426db87c54ad143d";
    private static final String SIMPLE_RESUMPTION_MASTER_SECRET =
            "7df235f2031d2a051287d02b0241b0bfdaf86cc856231f2d5aba46c434ec196c";
    // RFC 8448 HelloRetryRequest vector values, separated from the simple-handshake block above because HRR changes
    // the transcript construction and is easy to regress independently.
    private static final String HRR_SHARED_SECRET =
            "c142ce13ca11b5c2233652e63ad3d97844f1621fbfb9de69d547dc8fedeabeb4";
    private static final String HRR_SERVER_HELLO_HASH =
            "8aa8e828ec2f8a884fec95a3139de01c15a3daa7ff5bfc3f4bfcc21b438d7bf8";
    private static final String HRR_HANDSHAKE_SECRET =
            "ce022e5e6e81e50736d773f2d3adfce8220d049bf510f0dbfac927ef4243b148";
    private static final String HRR_CLIENT_HANDSHAKE_TRAFFIC_SECRET =
            "158aa7ab8855073582b41d674b4055cabcc534728f659314861b4e08e2011566";
    private static final String HRR_SERVER_HANDSHAKE_TRAFFIC_SECRET =
            "3403e781e2af7b6508da28574f6e95a1abf162de83a97927c37672a4a0cef8a1";
    private static final String HRR_MASTER_SECRET =
            "1131545d0baf79ddce9b87f06945781a57dd18ef378dcd2060f8f9a569027ed8";

    @Test
    void shouldDeriveHandshakeSecretsFromKnownVector() {
        SecretKey earlySecret = SCHEDULE.extractEarlySecret(new byte[0]);
        SecretKey handshakeSecret = SCHEDULE.deriveHandshakeSecret(earlySecret, bytes(SIMPLE_SHARED_SECRET));
        SecretKey clientHandshakeTrafficSecret =
                SCHEDULE.deriveClientHandshakeTrafficSecret(handshakeSecret, bytes(SIMPLE_SERVER_HELLO_HASH));
        SecretKey serverHandshakeTrafficSecret =
                SCHEDULE.deriveServerHandshakeTrafficSecret(handshakeSecret, bytes(SIMPLE_SERVER_HELLO_HASH));

        assertSecret(earlySecret, EARLY_SECRET);
        assertSecret(handshakeSecret, SIMPLE_HANDSHAKE_SECRET);
        assertSecret(clientHandshakeTrafficSecret, SIMPLE_CLIENT_HANDSHAKE_TRAFFIC_SECRET);
        assertSecret(serverHandshakeTrafficSecret, SIMPLE_SERVER_HANDSHAKE_TRAFFIC_SECRET);
    }

    @Test
    void shouldDeriveFinishedKeysAndClientVerifyDataFromKnownVector() {
        SecretKey earlySecret = SCHEDULE.extractEarlySecret(new byte[0]);
        SecretKey handshakeSecret = SCHEDULE.deriveHandshakeSecret(earlySecret, bytes(SIMPLE_SHARED_SECRET));
        SecretKey clientHandshakeTrafficSecret =
                SCHEDULE.deriveClientHandshakeTrafficSecret(handshakeSecret, bytes(SIMPLE_SERVER_HELLO_HASH));
        SecretKey serverHandshakeTrafficSecret =
                SCHEDULE.deriveServerHandshakeTrafficSecret(handshakeSecret, bytes(SIMPLE_SERVER_HELLO_HASH));

        SecretKey clientFinishedKey = SCHEDULE.deriveFinishedKey(clientHandshakeTrafficSecret);
        SecretKey serverFinishedKey = SCHEDULE.deriveFinishedKey(serverHandshakeTrafficSecret);

        assertSecret(clientFinishedKey, SIMPLE_CLIENT_FINISHED_KEY);
        assertSecret(serverFinishedKey, SIMPLE_SERVER_FINISHED_KEY);
        assertThat(hex(SCHEDULE.computeVerifyData(clientFinishedKey, bytes(SIMPLE_SERVER_FINISHED_HASH))),
                   is(SIMPLE_CLIENT_FINISHED_VERIFY_DATA));
    }

    @Test
    void shouldDeriveResumptionBinderAndEarlyTrafficSecretsFromKnownVector() {
        SecretKey earlySecret = SCHEDULE.extractEarlySecret(bytes(RESUMED_PSK));
        SecretKey binderKey = SCHEDULE.deriveResumptionBinderKey(earlySecret);
        SecretKey binderFinishedKey = SCHEDULE.deriveFinishedKey(binderKey);

        assertSecret(earlySecret, RESUMED_EARLY_SECRET);
        assertSecret(binderKey, RESUMED_BINDER_KEY);
        assertSecret(binderFinishedKey, RESUMED_BINDER_FINISHED_KEY);
        assertThat(hex(SCHEDULE.computeVerifyData(binderFinishedKey, bytes(RESUMED_BINDER_HASH))),
                   is(RESUMED_BINDER));
        assertSecret(SCHEDULE.deriveClientEarlyTrafficSecret(earlySecret, bytes(RESUMED_CLIENT_HELLO_HASH)),
                     RESUMED_CLIENT_EARLY_TRAFFIC_SECRET);
    }

    @Test
    void shouldDeriveMasterAndApplicationSecretsFromKnownVector() {
        SecretKey earlySecret = SCHEDULE.extractEarlySecret(new byte[0]);
        SecretKey handshakeSecret = SCHEDULE.deriveHandshakeSecret(earlySecret, bytes(SIMPLE_SHARED_SECRET));
        SecretKey masterSecret = SCHEDULE.deriveMasterSecret(handshakeSecret);

        assertSecret(masterSecret, SIMPLE_MASTER_SECRET);
        assertSecret(SCHEDULE.deriveClientApplicationTrafficSecret(masterSecret, bytes(SIMPLE_SERVER_FINISHED_HASH)),
                     SIMPLE_CLIENT_APPLICATION_TRAFFIC_SECRET);
        assertSecret(SCHEDULE.deriveServerApplicationTrafficSecret(masterSecret, bytes(SIMPLE_SERVER_FINISHED_HASH)),
                     SIMPLE_SERVER_APPLICATION_TRAFFIC_SECRET);
        assertSecret(SCHEDULE.deriveExporterMasterSecret(masterSecret, bytes(SIMPLE_SERVER_FINISHED_HASH)),
                     SIMPLE_EXPORTER_MASTER_SECRET);
        assertSecret(SCHEDULE.deriveResumptionMasterSecret(masterSecret, bytes(SIMPLE_CLIENT_FINISHED_HASH)),
                     SIMPLE_RESUMPTION_MASTER_SECRET);
    }

    @Test
    void shouldDeriveHelloRetryRequestSecretsFromKnownVector() {
        SecretKey earlySecret = SCHEDULE.extractEarlySecret(new byte[0]);
        SecretKey handshakeSecret = SCHEDULE.deriveHandshakeSecret(earlySecret, bytes(HRR_SHARED_SECRET));
        SecretKey clientHandshakeTrafficSecret =
                SCHEDULE.deriveClientHandshakeTrafficSecret(handshakeSecret, bytes(HRR_SERVER_HELLO_HASH));
        SecretKey serverHandshakeTrafficSecret =
                SCHEDULE.deriveServerHandshakeTrafficSecret(handshakeSecret, bytes(HRR_SERVER_HELLO_HASH));
        SecretKey masterSecret = SCHEDULE.deriveMasterSecret(handshakeSecret);

        assertSecret(handshakeSecret, HRR_HANDSHAKE_SECRET);
        assertSecret(clientHandshakeTrafficSecret, HRR_CLIENT_HANDSHAKE_TRAFFIC_SECRET);
        assertSecret(serverHandshakeTrafficSecret, HRR_SERVER_HANDSHAKE_TRAFFIC_SECRET);
        assertSecret(masterSecret, HRR_MASTER_SECRET);
    }

    @Test
    void shouldTranslateMissingHkdfAlgorithmToTransportError() {
        QuicTransportException exception = assertThrows(
                QuicTransportException.class,
                () -> QuicTlsHkdf.extractSecret("Missing-HKDF", "TlsSecret", new byte[32], new byte[0]));

        assertThat(exception.errorCode(), is(QuicTransportErrors.INTERNAL_ERROR.code()));
        assertThat(exception.getCause(), instanceOf(NoSuchAlgorithmException.class));
    }

    private static byte[] bytes(String hex) {
        return HEX.parseHex(hex.replaceAll("\\s+", ""));
    }

    private static String hex(byte[] bytes) {
        return HEX.formatHex(bytes);
    }

    private static void assertSecret(SecretKey secret, String expectedHex) {
        assertThat(HEX.formatHex(secret.getEncoded()), is(expectedHex));
    }
}
