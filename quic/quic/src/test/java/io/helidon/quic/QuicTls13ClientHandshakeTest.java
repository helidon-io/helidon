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
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicTls13ClientHandshakeTest {
    private static final HexFormat HEX = HexFormat.of();
    private static final QuicVersion VERSION = QuicVersion.QUIC_V1;
    private static final QuicTls13CipherSuite CIPHER_SUITE = QuicTls13CipherSuite.TLS_AES_128_GCM_SHA256;

    // Fixed hello random keeps the generated ClientHello stable enough for the HRR assertions without trying to pin
    // the ephemeral key share itself to a published vector.
    private static final byte[] CLIENT_HELLO_RANDOM = bytes(
            "00112233445566778899aabbccddeeff102132435465768798a9bacbdcedfe0f");

    // A short session id is enough to verify that HRR and ServerHello correctly echo the original ClientHello value.
    private static final byte[] SESSION_ID = bytes("01020304");

    // These bytes stand in for QUIC transport parameters so the hello-state tests also verify that unrelated
    // extensions survive the HRR rewrite untouched.
    private static final byte[] TRANSPORT_PARAMETERS = bytes("010203040506");

    // HelloRetryRequest cookies are opaque to the client; the test just needs a non-empty value to verify copy-through.
    private static final byte[] HRR_COOKIE = bytes("a0a1a2a3a4a5");
    private static final byte[] RESUMPTION_TICKET_NONCE = bytes("101112131415");
    private static final byte[] RESUMPTION_TICKET = bytes("202122232425262728292a2b2c2d2e2f");
    private static final byte[] RESUMPTION_PSK =
            bytes("303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f");

    // A fixed 16-byte sample is enough to confirm that both endpoints derive matching Handshake header-protection keys.
    private static final byte[] HEADER_PROTECTION_SAMPLE = bytes("0102030405060708090a0b0c0d0e0f10");

    @Test
    void shouldGenerateClientHelloAndCompleteSimpleServerHelloExchange() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        QuicTls13ClientHandshake handshake = startHandshake(secureRandom);

        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(handshake.clientHello());
        assertThat(clientHello.supportedVersions(), contains(QuicTlsSupportedVersions.TLS_1_3));
        assertThat(clientHello.supportedGroups(), contains(QuicTlsNamedGroup.X25519, QuicTlsNamedGroup.SECP256_R1));
        assertThat(clientHello.keyShares().stream().map(QuicTlsKeyShareEntry::namedGroup).toList(),
                   equalTo(List.of(QuicTlsNamedGroup.X25519)));
        assertThat(clientHello.extension(QuicTlsExtensions.QUIC_TRANSPORT_PARAMETERS).orElseThrow().data(),
                   equalTo(TRANSPORT_PARAMETERS));

        QuicTlsKeySharePossession serverKeyShare = QuicTlsKeySharePossession.create(QuicTlsNamedGroup.X25519, secureRandom);
        QuicTlsServerHelloMessage serverHello = serverHello(clientHello.legacySessionId(),
                                                            CIPHER_SUITE,
                                                            QuicTlsSupportedVersions.TLS_1_3,
                                                            serverKeyShare.keyShareEntry());

        QuicTls13ClientHandshake.CompleteResult result =
                (QuicTls13ClientHandshake.CompleteResult) handshake.consumeServerHello(serverHello.encode());
        QuicTls13ConnectionSecrets serverSecrets = QuicTls13ConnectionSecrets.create(VERSION,
                                                                                     CIPHER_SUITE,
                                                                                     serverKeyShare,
                                                                                     clientHello.keyShares().getFirst(),
                                                                                     false);

        assertThat(clientHello.extension(QuicTlsExtensions.EARLY_DATA).isPresent(), is(false));
        assertThat(result.preSharedKeySelected(), is(false));
        assertThat(hex(result.connectionSecrets().handshakeSecret()), is(hex(serverSecrets.handshakeSecret())));
        assertThat(result.serverHelloTranscriptHash().length, is(CIPHER_SUITE.hashLength()));
        assertThat(hex(result.handshakeTrafficKeys().computeHeaderProtectionMask(false,
                                                                                 ByteBuffer.wrap(
                                                                                         HEADER_PROTECTION_SAMPLE))),
                   is(hex(serverSecrets.deriveHandshakeTrafficKeys(result.serverHelloTranscriptHash())
                                  .computeHeaderProtectionMask(true,
                                                               ByteBuffer.wrap(HEADER_PROTECTION_SAMPLE)))));
    }

    @Test
    void shouldRejectInvalidServerKeyShareAsIllegalParameter() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        QuicTls13ClientHandshake handshake = startHandshake(secureRandom);
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(handshake.clientHello());
        QuicTlsServerHelloMessage serverHello = serverHello(
                clientHello.legacySessionId(),
                CIPHER_SUITE,
                QuicTlsSupportedVersions.TLS_1_3,
                QuicTlsKeyShareEntry.create(QuicTlsNamedGroup.X25519, new byte[32]));

        QuicTransportException failure =
                assertThrows(QuicTransportException.class, () -> handshake.consumeServerHello(serverHello.encode()));

        assertThat(failure.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 47));
    }

    @Test
    void shouldRegenerateClientHelloAfterHelloRetryRequest() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        QuicTls13ClientHandshake handshake = startHandshake(secureRandom);

        QuicTlsClientHelloMessage clientHello1 = QuicTlsClientHelloMessage.decode(handshake.clientHello());
        QuicTlsServerHelloMessage helloRetryRequest = helloRetryRequest(clientHello1.legacySessionId(),
                                                                        CIPHER_SUITE,
                                                                        QuicTlsNamedGroup.SECP256_R1,
                                                                        HRR_COOKIE);

        QuicTls13ClientHandshake.HelloRetryRequestResult result =
                (QuicTls13ClientHandshake.HelloRetryRequestResult) handshake.consumeServerHello(helloRetryRequest.encode());
        QuicTlsClientHelloMessage clientHello2 = result.clientHelloMessage();

        assertThat(hex(clientHello2.random()), is(hex(clientHello1.random())));
        assertThat(hex(clientHello2.legacySessionId()), is(hex(clientHello1.legacySessionId())));
        assertThat(clientHello2.cipherSuites(), equalTo(clientHello1.cipherSuites()));
        assertThat(clientHello2.supportedGroups(), equalTo(clientHello1.supportedGroups()));
        assertThat(clientHello2.keyShares().stream().map(QuicTlsKeyShareEntry::namedGroup).toList(),
                   equalTo(List.of(QuicTlsNamedGroup.SECP256_R1)));
        assertThat(QuicTlsCookie.decode(clientHello2.extension(QuicTlsExtensions.COOKIE).orElseThrow().dataBuffer()),
                   equalTo(HRR_COOKIE));
        assertThat(clientHello2.extension(QuicTlsExtensions.QUIC_TRANSPORT_PARAMETERS).orElseThrow().data(),
                   equalTo(TRANSPORT_PARAMETERS));
    }

    @Test
    void shouldOfferResumptionTicketInClientHello() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        QuicTlsResumptionTicket resumptionTicket = resumptionTicket();
        QuicTls13ClientHandshake handshake = startHandshake(List.of(CIPHER_SUITE), secureRandom, resumptionTicket);

        byte[] encodedClientHello = QuicTlsCodecSupport.copy(handshake.clientHello());
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(ByteBuffer.wrap(encodedClientHello));
        QuicTlsPreSharedKeys.OfferedPsks offeredPsks = clientHello.offeredPreSharedKeys().orElseThrow();

        assertThat(clientHello.extension(QuicTlsExtensions.EARLY_DATA).isPresent(), is(false));
        assertThat(clientHello.extensions().getLast().type(), is(QuicTlsExtensions.PRE_SHARED_KEY));
        assertThat(offeredPsks.identities().getFirst().identity(), equalTo(RESUMPTION_TICKET));
        assertThat(offeredPsks.binders().size(), is(1));
        assertResumptionBinder(encodedClientHello, resumptionTicket, null, null);
    }

    @Test
    void shouldRecomputeBinderAfterHelloRetryRequest() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        QuicTlsResumptionTicket resumptionTicket = resumptionTicket();
        QuicTls13ClientHandshake handshake = startHandshake(List.of(CIPHER_SUITE), secureRandom, resumptionTicket);

        byte[] clientHello1 = QuicTlsCodecSupport.copy(handshake.clientHello());
        QuicTlsClientHelloMessage clientHelloMessage1 = QuicTlsClientHelloMessage.decode(ByteBuffer.wrap(clientHello1));
        byte[] encodedHelloRetryRequest = QuicTlsCodecSupport.copy(helloRetryRequest(clientHelloMessage1.legacySessionId(),
                                                                                     CIPHER_SUITE,
                                                                                     QuicTlsNamedGroup.SECP256_R1,
                                                                                     HRR_COOKIE).encode());

        QuicTls13ClientHandshake.HelloRetryRequestResult result = (QuicTls13ClientHandshake.HelloRetryRequestResult)
                handshake.consumeServerHello(ByteBuffer.wrap(encodedHelloRetryRequest));
        byte[] clientHello2 = QuicTlsCodecSupport.copy(result.clientHello());
        QuicTlsClientHelloMessage clientHelloMessage2 = QuicTlsClientHelloMessage.decode(ByteBuffer.wrap(clientHello2));

        assertThat(clientHelloMessage2.extension(QuicTlsExtensions.EARLY_DATA).isPresent(), is(false));
        assertThat(clientHelloMessage2.extensions().getLast().type(), is(QuicTlsExtensions.PRE_SHARED_KEY));
        assertThat(clientHelloMessage2.keyShares().stream().map(QuicTlsKeyShareEntry::namedGroup).toList(),
                   equalTo(List.of(QuicTlsNamedGroup.SECP256_R1)));
        assertThat(QuicTlsCookie.decode(clientHelloMessage2.extension(QuicTlsExtensions.COOKIE).orElseThrow().dataBuffer()),
                   equalTo(HRR_COOKIE));
        assertResumptionBinder(clientHello2, resumptionTicket, clientHello1, encodedHelloRetryRequest);
    }

    @Test
    void shouldDropPreSharedKeyAfterIncompatibleHelloRetryRequest() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        QuicTls13ClientHandshake handshake = startHandshake(List.of(CIPHER_SUITE,
                                                                    QuicTls13CipherSuite.TLS_AES_256_GCM_SHA384),
                                                            secureRandom,
                                                            resumptionTicket());
        QuicTlsClientHelloMessage clientHello1 = QuicTlsClientHelloMessage.decode(handshake.clientHello());
        byte[] encodedHelloRetryRequest = QuicTlsCodecSupport.copy(helloRetryRequest(clientHello1.legacySessionId(),
                                                                                     QuicTls13CipherSuite.TLS_AES_256_GCM_SHA384,
                                                                                     QuicTlsNamedGroup.SECP256_R1,
                                                                                     HRR_COOKIE).encode());

        QuicTls13ClientHandshake.HelloRetryRequestResult result = (QuicTls13ClientHandshake.HelloRetryRequestResult)
                handshake.consumeServerHello(ByteBuffer.wrap(encodedHelloRetryRequest));
        QuicTlsClientHelloMessage clientHello2 = QuicTlsClientHelloMessage.decode(result.clientHello());

        assertThat(clientHello2.extension(QuicTlsExtensions.EARLY_DATA).isPresent(), is(false));
        assertThat(clientHello2.extension(QuicTlsExtensions.PRE_SHARED_KEY).isPresent(), is(false));
    }

    @Test
    void shouldDeriveResumptionSecretsWhenServerSelectsOfferedIdentity() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        QuicTlsResumptionTicket resumptionTicket = resumptionTicket();
        QuicTls13ClientHandshake handshake = startHandshake(List.of(CIPHER_SUITE), secureRandom, resumptionTicket);

        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(handshake.clientHello());
        QuicTlsKeySharePossession serverKeyShare = QuicTlsKeySharePossession.create(QuicTlsNamedGroup.X25519, secureRandom);
        QuicTlsServerHelloMessage serverHello = serverHello(clientHello.legacySessionId(),
                                                            CIPHER_SUITE,
                                                            QuicTlsSupportedVersions.TLS_1_3,
                                                            serverKeyShare.keyShareEntry(),
                                                            0);

        QuicTls13ClientHandshake.CompleteResult result =
                (QuicTls13ClientHandshake.CompleteResult) handshake.consumeServerHello(serverHello.encode());
        QuicTls13ConnectionSecrets serverSecrets = QuicTls13ConnectionSecrets.create(VERSION,
                                                                                     CIPHER_SUITE,
                                                                                     resumptionTicket.resumptionPsk(),
                                                                                     serverKeyShare,
                                                                                     clientHello.keyShares().getFirst(),
                                                                                     false);

        assertThat(result.preSharedKeySelected(), is(true));
        assertThat(hex(result.connectionSecrets().handshakeSecret()), is(hex(serverSecrets.handshakeSecret())));
    }

    @Test
    void shouldRejectUnexpectedSelectedIdentity() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        QuicTls13ClientHandshake handshake = startHandshake(List.of(CIPHER_SUITE), secureRandom, resumptionTicket());

        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(handshake.clientHello());
        QuicTlsKeySharePossession serverKeyShare = QuicTlsKeySharePossession.create(QuicTlsNamedGroup.X25519, secureRandom);
        QuicTlsServerHelloMessage serverHello = serverHello(clientHello.legacySessionId(),
                                                            CIPHER_SUITE,
                                                            QuicTlsSupportedVersions.TLS_1_3,
                                                            serverKeyShare.keyShareEntry(),
                                                            1);

        QuicTransportException thrown = assertThrows(QuicTransportException.class,
                                                     () -> handshake.consumeServerHello(serverHello.encode()));

        assertThat(thrown.reason(), is("ServerHello selected an out-of-range pre_shared_key identity"));
    }

    @Test
    void shouldRejectServerHelloThatUsesIncompatiblePskHash() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        QuicTls13ClientHandshake handshake = startHandshake(List.of(CIPHER_SUITE,
                                                                    QuicTls13CipherSuite.TLS_AES_256_GCM_SHA384),
                                                            secureRandom,
                                                            resumptionTicket());

        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(handshake.clientHello());
        QuicTlsKeySharePossession serverKeyShare = QuicTlsKeySharePossession.create(QuicTlsNamedGroup.X25519, secureRandom);
        QuicTlsServerHelloMessage serverHello = serverHello(clientHello.legacySessionId(),
                                                            QuicTls13CipherSuite.TLS_AES_256_GCM_SHA384,
                                                            QuicTlsSupportedVersions.TLS_1_3,
                                                            serverKeyShare.keyShareEntry(),
                                                            0);

        QuicTransportException thrown = assertThrows(QuicTransportException.class,
                                                     () -> handshake.consumeServerHello(serverHello.encode()));

        assertThat(thrown.reason(),
                   is("ServerHello selected a cipher suite incompatible with the offered pre_shared_key"));
    }

    @Test
    void shouldRejectSecondHelloRetryRequest() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        QuicTls13ClientHandshake handshake = startHandshake(secureRandom);
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(handshake.clientHello());
        QuicTlsServerHelloMessage helloRetryRequest = helloRetryRequest(clientHello.legacySessionId(),
                                                                        CIPHER_SUITE,
                                                                        QuicTlsNamedGroup.SECP256_R1,
                                                                        HRR_COOKIE);

        handshake.consumeServerHello(helloRetryRequest.encode());
        QuicTransportException thrown = assertThrows(QuicTransportException.class,
                                                     () -> handshake.consumeServerHello(helloRetryRequest.encode()));

        assertThat(thrown.reason(), is("Received second HelloRetryRequest"));
    }

    @Test
    void shouldRejectHelloRetryRequestForAlreadySharedGroup() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        QuicTls13ClientHandshake handshake = startHandshake(secureRandom);
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(handshake.clientHello());
        QuicTlsServerHelloMessage helloRetryRequest = helloRetryRequest(clientHello.legacySessionId(),
                                                                        CIPHER_SUITE,
                                                                        QuicTlsNamedGroup.X25519,
                                                                        HRR_COOKIE);

        QuicTransportException thrown = assertThrows(QuicTransportException.class,
                                                     () -> handshake.consumeServerHello(helloRetryRequest.encode()));

        assertThat(thrown.reason(),
                   is("HelloRetryRequest selected a group already present in the ClientHello key_share extension"));
    }

    @Test
    void shouldRejectHelloRetryRequestForUnsupportedGroup() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        QuicTls13ClientHandshake handshake = startHandshake(secureRandom);
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(handshake.clientHello());
        QuicTlsServerHelloMessage helloRetryRequest = helloRetryRequest(clientHello.legacySessionId(),
                                                                        CIPHER_SUITE,
                                                                        QuicTlsNamedGroup.SECP384_R1,
                                                                        HRR_COOKIE);

        QuicTransportException thrown = assertThrows(QuicTransportException.class,
                                                     () -> handshake.consumeServerHello(helloRetryRequest.encode()));

        assertThat(thrown.reason(),
                   is("HelloRetryRequest selected a group outside the ClientHello supported_groups extension"));
    }

    @Test
    void shouldRejectServerHelloThatChangesCipherSuiteAfterHelloRetryRequest() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        QuicTls13ClientHandshake handshake = startHandshake(secureRandom);
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(handshake.clientHello());
        handshake.consumeServerHello(helloRetryRequest(clientHello.legacySessionId(),
                                                       CIPHER_SUITE,
                                                       QuicTlsNamedGroup.SECP256_R1,
                                                       HRR_COOKIE).encode());

        QuicTlsKeySharePossession serverKeyShare = QuicTlsKeySharePossession.create(QuicTlsNamedGroup.SECP256_R1, secureRandom);
        QuicTlsServerHelloMessage serverHello = serverHello(clientHello.legacySessionId(),
                                                            QuicTls13CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
                                                            QuicTlsSupportedVersions.TLS_1_3,
                                                            serverKeyShare.keyShareEntry());

        QuicTransportException thrown = assertThrows(QuicTransportException.class,
                                                     () -> handshake.consumeServerHello(serverHello.encode()));

        assertThat(thrown.reason(), is("ServerHello cipher suite does not match the HelloRetryRequest"));
    }

    @Test
    void shouldRejectServerHelloThatChangesVersionAfterHelloRetryRequest() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        QuicTls13ClientHandshake handshake = startHandshake(secureRandom);
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(handshake.clientHello());
        handshake.consumeServerHello(helloRetryRequest(clientHello.legacySessionId(),
                                                       CIPHER_SUITE,
                                                       QuicTlsNamedGroup.SECP256_R1,
                                                       HRR_COOKIE).encode());

        QuicTlsKeySharePossession serverKeyShare = QuicTlsKeySharePossession.create(QuicTlsNamedGroup.SECP256_R1, secureRandom);
        QuicTlsServerHelloMessage serverHello = serverHello(clientHello.legacySessionId(),
                                                            CIPHER_SUITE,
                                                            0x0303,
                                                            serverKeyShare.keyShareEntry());

        QuicTransportException thrown = assertThrows(QuicTransportException.class,
                                                     () -> handshake.consumeServerHello(serverHello.encode()));

        assertThat(thrown.reason(), is("ServerHello supported version does not match the HelloRetryRequest"));
    }

    @Test
    void shouldRejectServerHelloWithWrongKeyShareAfterHelloRetryRequest() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        QuicTls13ClientHandshake handshake = startHandshake(secureRandom);
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(handshake.clientHello());
        handshake.consumeServerHello(helloRetryRequest(clientHello.legacySessionId(),
                                                       CIPHER_SUITE,
                                                       QuicTlsNamedGroup.SECP256_R1,
                                                       HRR_COOKIE).encode());

        QuicTlsKeySharePossession serverKeyShare = QuicTlsKeySharePossession.create(QuicTlsNamedGroup.X25519, secureRandom);
        QuicTlsServerHelloMessage serverHello = serverHello(clientHello.legacySessionId(),
                                                            CIPHER_SUITE,
                                                            QuicTlsSupportedVersions.TLS_1_3,
                                                            serverKeyShare.keyShareEntry());

        QuicTransportException thrown = assertThrows(QuicTransportException.class,
                                                     () -> handshake.consumeServerHello(serverHello.encode()));

        assertThat(thrown.reason(),
                   is("ServerHello key_share group does not match the HelloRetryRequest selected group"));
    }

    private static QuicTls13ClientHandshake startHandshake(SecureRandom secureRandom) throws Exception {
        return startHandshake(List.of(CIPHER_SUITE), secureRandom, null);
    }

    private static QuicTls13ClientHandshake startHandshake(List<QuicTls13CipherSuite> cipherSuites,
                                                           SecureRandom secureRandom,
                                                           QuicTlsResumptionTicket resumptionTicket) throws Exception {
        return QuicTls13ClientHandshake.start(
                VERSION,
                new QuicTls13ClientHandshake.ClientHelloParameters(
                        CLIENT_HELLO_RANDOM,
                        SESSION_ID,
                        cipherSuites,
                        List.of(QuicTlsNamedGroup.X25519, QuicTlsNamedGroup.SECP256_R1),
                        List.of(QuicTlsNamedGroup.X25519),
                        resumptionTicket == null
                                ? List.of(QuicTlsExtension.create(QuicTlsExtensions.QUIC_TRANSPORT_PARAMETERS,
                                                                  TRANSPORT_PARAMETERS))
                                : List.of(QuicTlsExtension.create(QuicTlsExtensions.QUIC_TRANSPORT_PARAMETERS,
                                                                  TRANSPORT_PARAMETERS),
                                          QuicTlsExtension.create(QuicTlsExtensions.PSK_KEY_EXCHANGE_MODES,
                                                                  pskKeyExchangeModes())),
                        resumptionTicket),
                secureRandom);
    }

    private static QuicTlsServerHelloMessage helloRetryRequest(byte[] legacySessionId,
                                                               QuicTls13CipherSuite cipherSuite,
                                                               QuicTlsNamedGroup selectedGroup,
                                                               byte[] cookie) {
        return QuicTlsServerHelloMessage.helloRetryRequest(
                legacySessionId,
                cipherSuite.codePoint(),
                List.of(
                        QuicTlsExtension.create(QuicTlsExtensions.KEY_SHARE,
                                                QuicTlsKeyShares.encodeHelloRetryRequest(selectedGroup)),
                        QuicTlsExtension.create(QuicTlsExtensions.COOKIE, QuicTlsCookie.encode(cookie)),
                        QuicTlsExtension.create(QuicTlsExtensions.SUPPORTED_VERSIONS,
                                                QuicTlsSupportedVersions.encodeServerHello(
                                                        QuicTlsSupportedVersions.TLS_1_3))));
    }

    private static QuicTlsServerHelloMessage serverHello(byte[] legacySessionId,
                                                         QuicTls13CipherSuite cipherSuite,
                                                         int supportedVersion,
                                                         QuicTlsKeyShareEntry keyShare) {
        return serverHello(legacySessionId, cipherSuite, supportedVersion, keyShare, null);
    }

    private static QuicTlsServerHelloMessage serverHello(byte[] legacySessionId,
                                                         QuicTls13CipherSuite cipherSuite,
                                                         int supportedVersion,
                                                         QuicTlsKeyShareEntry keyShare,
                                                         Integer selectedIdentity) {
        return QuicTlsServerHelloMessage.create(
                0x0303,
                CLIENT_HELLO_RANDOM,
                legacySessionId,
                cipherSuite.codePoint(),
                0,
                selectedIdentity == null
                        ? List.of(
                        QuicTlsExtension.create(QuicTlsExtensions.KEY_SHARE,
                                                QuicTlsKeyShares.encodeServerHello(keyShare)),
                        QuicTlsExtension.create(QuicTlsExtensions.SUPPORTED_VERSIONS,
                                                QuicTlsSupportedVersions.encodeServerHello(supportedVersion)))
                        : List.of(
                                QuicTlsExtension.create(QuicTlsExtensions.KEY_SHARE,
                                                        QuicTlsKeyShares.encodeServerHello(keyShare)),
                                QuicTlsExtension.create(QuicTlsExtensions.SUPPORTED_VERSIONS,
                                                        QuicTlsSupportedVersions.encodeServerHello(supportedVersion)),
                                QuicTlsExtension.create(QuicTlsExtensions.PRE_SHARED_KEY,
                                                        QuicTlsPreSharedKeys.encodeServerHello(selectedIdentity))));
    }

    private static QuicTlsResumptionTicket resumptionTicket() {
        return new QuicTlsResumptionTicket(QuicVersion.QUIC_V1,
                                           CIPHER_SUITE,
                                           3600,
                                           0x10203040L,
                                           RESUMPTION_TICKET_NONCE,
                                           RESUMPTION_TICKET,
                                           RESUMPTION_PSK,
                                           "h3",
                                           TRANSPORT_PARAMETERS,
                                           System.currentTimeMillis() - 5000);
    }

    private static byte[] pskKeyExchangeModes() {
        return new byte[] {0x01, 0x01};
    }

    private static void assertResumptionBinder(byte[] encodedClientHello,
                                               QuicTlsResumptionTicket resumptionTicket,
                                               byte[] previousClientHello,
                                               byte[] helloRetryRequest) throws Exception {
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(ByteBuffer.wrap(encodedClientHello));
        QuicTlsPreSharedKeys.OfferedPsks offeredPsks = clientHello.offeredPreSharedKeys().orElseThrow();
        QuicTls13SecretSchedule secretSchedule = new QuicTls13SecretSchedule(resumptionTicket.cipherSuite());
        SecretKey earlySecret = secretSchedule.extractEarlySecret(resumptionTicket.resumptionPsk());
        SecretKey binderKey = secretSchedule.deriveResumptionBinderKey(earlySecret);
        SecretKey finishedKey = secretSchedule.deriveFinishedKey(binderKey);

        assertThat(offeredPsks.binders().getFirst(),
                   equalTo(secretSchedule.computeVerifyData(finishedKey,
                                                            binderTranscriptHash(resumptionTicket.cipherSuite(),
                                                                                 previousClientHello,
                                                                                 helloRetryRequest,
                                                                                 encodedClientHello))));
    }

    private static byte[] binderTranscriptHash(QuicTls13CipherSuite cipherSuite,
                                               byte[] previousClientHello,
                                               byte[] helloRetryRequest,
                                               byte[] encodedClientHello) throws Exception {
        byte[] truncatedClientHello = QuicTlsPreSharedKeys.truncateClientHello(encodedClientHello);
        if (previousClientHello == null || helloRetryRequest == null) {
            return cipherSuite.digest(truncatedClientHello);
        }

        MessageDigest digest = cipherSuite.newDigest();
        digest.update(syntheticMessageHash(cipherSuite.digest(previousClientHello)));
        digest.update(helloRetryRequest);
        digest.update(truncatedClientHello);
        return digest.digest();
    }

    private static byte[] syntheticMessageHash(byte[] messageHash) {
        byte[] result = new byte[QuicTlsHandshakeMessages.HEADER_LENGTH + messageHash.length];
        QuicTlsCodecSupport.putHandshakeHeader(ByteBuffer.wrap(result), 0xFE, messageHash.length);
        System.arraycopy(messageHash, 0, result, QuicTlsHandshakeMessages.HEADER_LENGTH, messageHash.length);
        return result;
    }

    private static byte[] bytes(String hex) {
        return HEX.parseHex(hex);
    }

    private static String hex(byte[] data) {
        return HEX.formatHex(data);
    }

    private static String hex(ByteBuffer buffer) {
        ByteBuffer copy = buffer.duplicate();
        byte[] data = new byte[copy.remaining()];
        copy.get(data);
        return HEX.formatHex(data);
    }

    private static String hex(SecretKey secretKey) {
        return HEX.formatHex(secretKey.getEncoded());
    }
}
