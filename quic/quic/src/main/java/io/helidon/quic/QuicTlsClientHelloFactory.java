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

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import io.helidon.common.buffers.BufferData;

final class QuicTlsClientHelloFactory {
    // Interim ClientHello construction used until SSLContext#createQUICEngine(..., QuicTLSCallbacks) can expose the
    // JDK-owned QUIC handshake. Keep this wire shape aligned with the future JSSE QUIC engine.
    // RFC 9001 section 8.4 prohibits TLS middlebox compatibility mode, so QUIC always sends an empty legacy_session_id.
    private static final byte[] EMPTY_LEGACY_SESSION_ID = BufferData.EMPTY_BYTES;
    // When SSLParameters do not pin named groups, prefer X25519 first and keep the common NIST fallback curves that
    // Helidon already implements so HelloRetryRequest can move to another widely deployed group.
    private static final List<QuicTlsNamedGroup> DEFAULT_SUPPORTED_GROUPS = List.of(
            QuicTlsNamedGroup.X25519,
            QuicTlsNamedGroup.SECP256_R1,
            QuicTlsNamedGroup.SECP384_R1,
            QuicTlsNamedGroup.SECP521_R1,
            QuicTlsNamedGroup.X448);
    private final SSLContext sslContext;
    private final String peerHost;
    private final int peerPort;

    QuicTlsClientHelloFactory(SSLContext sslContext) {
        this.sslContext = Objects.requireNonNull(sslContext, "sslContext");
        this.peerHost = null;
        this.peerPort = -1;
    }

    QuicTlsClientHelloFactory(SSLContext sslContext, String peerHost, int peerPort) {
        this.sslContext = Objects.requireNonNull(sslContext, "sslContext");
        this.peerHost = Objects.requireNonNull(peerHost, "peerHost");
        this.peerPort = peerPort;
    }

    QuicTls13ClientHandshake start(QuicVersion version,
                                   SSLParameters sslParameters,
                                   byte[] localTransportParameters,
                                   SecureRandom secureRandom) throws QuicTransportException {
        return start(version,
                     sslParameters,
                     localTransportParameters,
                     null,
                     secureRandom,
                     QuicAeadLimits.Confidentiality.defaults());
    }

    QuicTls13ClientHandshake start(QuicVersion version,
                                   SSLParameters sslParameters,
                                   byte[] localTransportParameters,
                                   QuicTlsResumptionTicket resumptionTicket,
                                   SecureRandom secureRandom) throws QuicTransportException {
        return start(version,
                     sslParameters,
                     localTransportParameters,
                     resumptionTicket,
                     secureRandom,
                     QuicAeadLimits.Confidentiality.defaults());
    }

    QuicTls13ClientHandshake start(QuicVersion version,
                                   SSLParameters sslParameters,
                                   byte[] localTransportParameters,
                                   QuicTlsResumptionTicket resumptionTicket,
                                   SecureRandom secureRandom,
                                   QuicAeadLimits.Confidentiality confidentialityLimits) throws QuicTransportException {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(sslParameters, "sslParameters");
        byte[] transportParameters = Objects.requireNonNull(localTransportParameters, "localTransportParameters").clone();
        Objects.requireNonNull(secureRandom, "secureRandom");

        try {
            List<QuicTls13CipherSuite> cipherSuites = cipherSuites(sslParameters);
            List<QuicTlsNamedGroup> supportedGroups = supportedGroups(sslParameters);
            List<QuicTlsExtension> additionalExtensions = additionalExtensions(sslParameters, transportParameters);
            QuicTlsResumptionTicket compatibleResumptionTicket =
                    compatibleResumptionTicket(resumptionTicket, version, sslParameters, cipherSuites)
                            ? resumptionTicket
                            : null;

            byte[] random = new byte[QuicTlsCodecSupport.RANDOM_LENGTH];
            secureRandom.nextBytes(random);

            return QuicTls13ClientHandshake.start(
                    version,
                    new QuicTls13ClientHandshake.ClientHelloParameters(random,
                                                                       EMPTY_LEGACY_SESSION_ID,
                                                                       cipherSuites,
                                                                       supportedGroups,
                                                                       List.of(supportedGroups.getFirst()),
                                                                       additionalExtensions,
                                                                       compatibleResumptionTicket),
                    secureRandom,
                    confidentialityLimits);
        } catch (ProviderException e) {
            throw new QuicTransportException("Failed to initialize the TLS client handshake",
                                             0,
                                             QuicTransportErrors.INTERNAL_ERROR.code(),
                                             e);
        }
    }

    String decodeSelectedApplicationProtocol(ByteBuffer extensionData) throws QuicTransportException {
        return QuicTlsApplicationProtocols.decodeServerSelection(extensionData);
    }

    private static boolean applicationProtocolMismatch(QuicTlsResumptionTicket resumptionTicket,
                                                       SSLParameters sslParameters) {
        Optional<String> applicationProtocol = resumptionTicket.applicationProtocol();
        String[] configuredProtocols = sslParameters.getApplicationProtocols();
        if (applicationProtocol.isEmpty()) {
            return configuredProtocols != null && configuredProtocols.length > 0;
        }
        if (configuredProtocols == null || configuredProtocols.length == 0) {
            return true;
        }
        String resumedProtocol = applicationProtocol.orElseThrow();
        for (String configuredProtocol : configuredProtocols) {
            if (resumedProtocol.equals(configuredProtocol)) {
                return false;
            }
        }
        return true;
    }

    private static ByteBuffer encodeServerNames(List<SNIServerName> serverNames) {
        int listLength = 0;
        Set<Integer> seenTypes = new LinkedHashSet<>();
        for (SNIServerName serverName : serverNames) {
            SNIServerName candidate = Objects.requireNonNull(serverName, "serverName");
            if (!seenTypes.add(candidate.getType())) {
                throw new IllegalArgumentException("Duplicate SNI name type: " + candidate.getType());
            }
            byte[] encoded = candidate.getEncoded();
            if (encoded.length == 0 || encoded.length > 0xFFFF) {
                throw new IllegalArgumentException("Invalid SNI server name length: " + encoded.length);
            }
            listLength += 1 + QuicTlsCodecSupport.UINT16_LENGTH + encoded.length;
        }
        if (listLength == 0 || listLength > 0xFFFF) {
            throw new IllegalArgumentException("Invalid SNI server name list length: " + listLength);
        }

        ByteBuffer encoded = ByteBuffer.allocate(QuicTlsCodecSupport.UINT16_LENGTH + listLength);
        encoded.putShort((short) listLength);
        for (SNIServerName serverName : serverNames) {
            byte[] serverNameBytes = serverName.getEncoded();
            encoded.put((byte) serverName.getType());
            encoded.putShort((short) serverNameBytes.length);
            encoded.put(serverNameBytes);
        }
        return encoded.flip();
    }

    private List<QuicTlsExtension> additionalExtensions(SSLParameters sslParameters, byte[] localTransportParameters) {
        List<QuicTlsExtension> extensions = new ArrayList<>(6);

        List<SNIServerName> serverNames = serverNames(sslParameters);
        if (!serverNames.isEmpty()) {
            extensions.add(QuicTlsExtension.create(QuicTlsExtensions.SERVER_NAME, encodeServerNames(serverNames)));
        }

        String[] applicationProtocols = sslParameters.getApplicationProtocols();
        if (applicationProtocols != null && applicationProtocols.length > 0) {
            extensions.add(QuicTlsExtension.create(QuicTlsExtensions.APPLICATION_LAYER_PROTOCOL_NEGOTIATION,
                                                   QuicTlsApplicationProtocols.encodeClientHello(applicationProtocols)));
        }

        String[] configuredSignatureSchemes = sslParameters.getSignatureSchemes();
        extensions.add(QuicTlsExtension.create(
                QuicTlsExtensions.SIGNATURE_ALGORITHMS,
                QuicTlsSignatureScheme.encodeCertificateVerifyVector(
                        QuicTlsSignatureScheme.certificateVerifySchemes(configuredSignatureSchemes))));
        extensions.add(QuicTlsExtension.create(
                QuicTlsExtensions.SIGNATURE_ALGORITHMS_CERT,
                QuicTlsSignatureScheme.encodeCertificateSignatureVector(
                        QuicTlsSignatureScheme.certificateSignatureSchemes(configuredSignatureSchemes))));
        // Advertising only psk_dhe_ke keeps the ClientHello honest about the fact that Helidon does not implement
        // plain-PSK key establishment.
        extensions.add(QuicTlsExtension.create(QuicTlsExtensions.PSK_KEY_EXCHANGE_MODES,
                                               QuicTlsPskKeyExchangeModes.encodeClientHello(
                                                       List.of(QuicTlsPskKeyExchangeModes.PSK_DHE_KE))));
        extensions.add(QuicTlsExtension.create(QuicTlsExtensions.QUIC_TRANSPORT_PARAMETERS, localTransportParameters));
        return List.copyOf(extensions);
    }

    private List<QuicTls13CipherSuite> cipherSuites(SSLParameters sslParameters) {
        SSLEngine sslEngine = createClientModeEngine(sslParameters);
        Set<QuicTls13CipherSuite> cipherSuites = new LinkedHashSet<>();
        for (String enabledCipherSuite : sslEngine.getEnabledCipherSuites()) {
            try {
                cipherSuites.add(QuicTls13CipherSuite.forName(enabledCipherSuite));
            } catch (IllegalArgumentException e) {
                // Ignore suites outside the QUIC-compatible TLS 1.3 set Helidon currently implements.
            }
        }
        if (cipherSuites.isEmpty()) {
            throw new IllegalArgumentException("No QUIC-compatible TLS 1.3 cipher suites are enabled");
        }
        return List.copyOf(cipherSuites);
    }

    private List<QuicTlsNamedGroup> supportedGroups(SSLParameters sslParameters) {
        String[] namedGroups = sslParameters.getNamedGroups();
        if (namedGroups == null || namedGroups.length == 0) {
            return DEFAULT_SUPPORTED_GROUPS;
        }

        Set<QuicTlsNamedGroup> result = new LinkedHashSet<>();
        for (String namedGroup : namedGroups) {
            for (QuicTlsNamedGroup candidate : QuicTlsNamedGroup.values()) {
                if (candidate.tlsName().equalsIgnoreCase(namedGroup)) {
                    result.add(candidate);
                    break;
                }
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("No supported TLS named groups are enabled");
        }
        return List.copyOf(result);
    }

    private List<SNIServerName> serverNames(SSLParameters sslParameters) {
        List<SNIServerName> configuredServerNames = sslParameters.getServerNames();
        if (configuredServerNames != null) {
            return List.copyOf(configuredServerNames);
        }
        if (peerHost == null || peerHost.isBlank()) {
            return List.of();
        }
        try {
            InetAddress.ofLiteral(peerHost);
            return List.of();
        } catch (IllegalArgumentException _) {
            // A peer host that is not an IP literal may be used as a DNS server name.
        }
        try {
            return List.of(new SNIHostName(peerHost));
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    private SSLEngine createClientModeEngine(SSLParameters sslParameters) {
        try {
            SSLEngine sslEngine = peerHost == null
                    ? sslContext.createSSLEngine()
                    : sslContext.createSSLEngine(peerHost, peerPort);
            sslEngine.setUseClientMode(true);
            sslEngine.setSSLParameters(sslParameters);
            return sslEngine;
        } catch (ProviderException | UnsupportedOperationException | IllegalStateException | IllegalArgumentException e) {
            throw QuicTlsHandshakeMessages.internalError("Failed to configure the client TLS engine", e);
        }
    }

    boolean compatibleResumptionTicket(QuicTlsResumptionTicket resumptionTicket,
                                       QuicVersion version,
                                       SSLParameters sslParameters) {
        return compatibleResumptionTicket(resumptionTicket, version, sslParameters, cipherSuites(sslParameters));
    }

    private boolean compatibleResumptionTicket(QuicTlsResumptionTicket resumptionTicket,
                                                QuicVersion version,
                                                SSLParameters sslParameters,
                                                List<QuicTls13CipherSuite> cipherSuites) {
        if (resumptionTicket == null || resumptionTicket.expired(System.currentTimeMillis())) {
            return false;
        }
        if (!resumptionTicket.transportCompatible(version)) {
            return false;
        }
        if (applicationProtocolMismatch(resumptionTicket, sslParameters)) {
            return false;
        }
        for (QuicTls13CipherSuite cipherSuite : cipherSuites) {
            if (resumptionTicket.cipherSuite().sameHash(cipherSuite)) {
                return true;
            }
        }
        return false;
    }
}
