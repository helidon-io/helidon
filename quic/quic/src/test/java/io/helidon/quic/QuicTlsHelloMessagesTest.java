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

import java.security.SecureRandom;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class QuicTlsHelloMessagesTest {
    @Test
    void shouldParseAndReencodeRfc8448ClientHello() throws Exception {
        QuicTlsClientHelloMessage clientHello =
                QuicTlsClientHelloMessage.decode(QuicTlsRfc8448Vectors.message(QuicTlsRfc8448Vectors.SIMPLE_CLIENT_HELLO));

        assertThat(clientHello.legacyVersion(), is(0x0303));
        assertThat(clientHello.supportedVersions(), contains(QuicTlsSupportedVersions.TLS_1_3));
        assertThat(clientHello.supportedGroups(),
                   contains(QuicTlsNamedGroup.X25519,
                            QuicTlsNamedGroup.SECP256_R1,
                            QuicTlsNamedGroup.SECP384_R1,
                            QuicTlsNamedGroup.SECP521_R1));
        assertThat(clientHello.keyShares().getFirst().namedGroup(), is(QuicTlsNamedGroup.X25519));
        assertThat(QuicTlsCodecSupport.copy(clientHello.encode()),
                   equalTo(QuicTlsRfc8448Vectors.bytes(QuicTlsRfc8448Vectors.SIMPLE_CLIENT_HELLO)));
    }

    @Test
    void shouldParseAndReencodeRfc8448ServerHello() throws Exception {
        QuicTlsServerHelloMessage serverHello =
                QuicTlsServerHelloMessage.decode(QuicTlsRfc8448Vectors.message(QuicTlsRfc8448Vectors.SIMPLE_SERVER_HELLO));

        assertThat(serverHello.helloRetryRequest(), is(false));
        assertThat(serverHello.cipherSuite(), is(0x1301));
        assertThat(serverHello.supportedVersion().orElseThrow(), is(QuicTlsSupportedVersions.TLS_1_3));
        assertThat(serverHello.keyShare().orElseThrow().namedGroup(), is(QuicTlsNamedGroup.X25519));
        assertThat(QuicTlsCodecSupport.copy(serverHello.encode()),
                   equalTo(QuicTlsRfc8448Vectors.bytes(QuicTlsRfc8448Vectors.SIMPLE_SERVER_HELLO)));
    }

    @Test
    void shouldParseAndReencodeRfc8448HelloRetryRequest() throws Exception {
        QuicTlsServerHelloMessage helloRetryRequest = QuicTlsServerHelloMessage.decode(
                QuicTlsRfc8448Vectors.message(QuicTlsRfc8448Vectors.HRR_SERVER_HELLO_RETRY_REQUEST));

        assertThat(helloRetryRequest.helloRetryRequest(), is(true));
        assertThat(helloRetryRequest.cipherSuite(), is(0x1301));
        assertThat(helloRetryRequest.supportedVersion().orElseThrow(), is(QuicTlsSupportedVersions.TLS_1_3));
        assertThat(helloRetryRequest.helloRetryRequestSelectedGroup().orElseThrow(), is(QuicTlsNamedGroup.SECP256_R1));
        assertThat(QuicTlsCodecSupport.copy(helloRetryRequest.encode()),
                   equalTo(QuicTlsRfc8448Vectors.bytes(QuicTlsRfc8448Vectors.HRR_SERVER_HELLO_RETRY_REQUEST)));
    }

    @Test
    void shouldRoundTripSyntheticClientHello() throws Exception {
        QuicTlsKeySharePossession keyShare = QuicTlsKeySharePossession.create(QuicTlsNamedGroup.X25519, new SecureRandom());
        byte[] transportParameters = new byte[] {0x01, 0x02, 0x03};

        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.create(
                0x0303,
                new byte[QuicTlsCodecSupport.RANDOM_LENGTH],
                new byte[0],
                List.of(0x1301, 0x1303),
                new byte[] {0},
                List.of(
                        QuicTlsExtension.create(QuicTlsExtensions.SUPPORTED_VERSIONS,
                                                QuicTlsSupportedVersions.encodeClientHello(
                                                        List.of(QuicTlsSupportedVersions.TLS_1_3))),
                        QuicTlsExtension.create(QuicTlsExtensions.SUPPORTED_GROUPS,
                                                QuicTlsSupportedGroups.encode(
                                                        List.of(QuicTlsNamedGroup.X25519, QuicTlsNamedGroup.SECP256_R1))),
                        QuicTlsExtension.create(QuicTlsExtensions.KEY_SHARE,
                                                QuicTlsKeyShares.encodeClientHello(List.of(keyShare.keyShareEntry()))),
                        QuicTlsExtension.create(QuicTlsExtensions.QUIC_TRANSPORT_PARAMETERS, transportParameters)));

        QuicTlsClientHelloMessage decoded = QuicTlsClientHelloMessage.decode(clientHello.encode());

        assertThat(decoded.cipherSuites(), equalTo(List.of(0x1301, 0x1303)));
        assertThat(decoded.supportedVersions(), contains(QuicTlsSupportedVersions.TLS_1_3));
        assertThat(decoded.supportedGroups(), contains(QuicTlsNamedGroup.X25519, QuicTlsNamedGroup.SECP256_R1));
        assertThat(decoded.keyShares(), equalTo(List.of(keyShare.keyShareEntry())));
        assertThat(decoded.extension(QuicTlsExtensions.QUIC_TRANSPORT_PARAMETERS).orElseThrow().data(),
                   equalTo(transportParameters));
    }
}
