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
import java.security.Principal;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.crypto.SecretKey;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.StandardConstants;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.common.buffers.BufferData;

final class QuicTls13ServerHandshake {
    // TLS 1.3 keeps legacy_version pinned to TLS 1.2 for wire compatibility, so the server-side helper validates the
    // ClientHello against the fixed compatibility value before negotiating the real version in supported_versions.
    private static final int LEGACY_TLS_VERSION = 0x0303;
    // RFC 8446 requires the ClientHello legacy_compression_methods vector to contain exactly the null-compression byte.
    private static final byte LEGACY_NULL_COMPRESSION_METHOD = 0x00;
    // TLS 1.3 handshake-time client authentication uses an empty request context; non-empty values are for
    // post-handshake authentication, which QUIC does not use in this releasable path.
    private static final byte[] EMPTY_CERTIFICATE_REQUEST_CONTEXT = BufferData.EMPTY_BYTES;
    // TLS 1.3 caps ticket lifetime to seven days, so the server-side resumption helper uses the same bound when it
    // validates the client's obfuscated_ticket_age before selecting a cached in-memory ticket.
    private static final long MAX_TICKET_LIFETIME_SECONDS = 604_800L;
    // When SSLParameters do not pin named groups, prefer X25519 first and keep the common NIST fallback curves aligned
    // with the interim ClientHello factory until the future JDK QUIC engine owns key-share negotiation.
    private static final List<QuicTlsNamedGroup> DEFAULT_SUPPORTED_GROUPS = List.of(
            QuicTlsNamedGroup.X25519,
            QuicTlsNamedGroup.SECP256_R1,
            QuicTlsNamedGroup.SECP384_R1,
            QuicTlsNamedGroup.SECP521_R1,
            QuicTlsNamedGroup.X448);
    private final QuicVersion version;
    private final SSLContext sslContext;
    private final SSLParameters sslParameters;
    private final X509KeyManager keyManager;
    private final X509TrustManager trustManager;
    private final byte[] localTransportParameters;
    private final SecureRandom secureRandom;
    private final String peerHost;
    private final int peerPort;
    private final QuicTlsServerSessionCache serverSessionCache;
    private final QuicAeadLimits.Confidentiality confidentialityLimits;
    private final QuicTlsHandshakeTranscript transcript = new QuicTlsHandshakeTranscript();
    private State state = State.EXPECT_CLIENT_HELLO;
    private QuicTls13CipherSuite cipherSuite;
    private QuicTls13ConnectionSecrets connectionSecrets;
    private byte[] serverHelloTranscriptHash;
    private QuicLongHeaderTrafficKeys handshakeTrafficKeys;
    private QuicOneRttTrafficKeys oneRttTrafficKeys;
    private String applicationProtocol;
    private String serverName;
    private byte[] remoteTransportParameters;
    private X509Certificate[] localCertificates;
    private X509Certificate[] peerCertificates;
    private List<QuicTlsSignatureScheme> clientCertificateSignatureSchemes = List.of();
    private List<QuicTlsSignatureScheme> requestedClientCertificateVerifySchemes = List.of();
    private List<QuicTlsSignatureScheme> requestedClientCertificateSignatureSchemes = List.of();
    private HelloRetryRequestState helloRetryRequest;

    private QuicTls13ServerHandshake(QuicVersion version, StartParameters parameters) {
        StartParameters params = Objects.requireNonNull(parameters, "parameters");
        this.version = Objects.requireNonNull(version, "version");
        this.sslContext = Objects.requireNonNull(params.sslContext(), "sslContext");
        this.sslParameters = QuicTlsParameters.copy(Objects.requireNonNull(params.sslParameters(), "sslParameters"));
        this.keyManager = Objects.requireNonNull(params.keyManager(), "keyManager");
        this.trustManager = params.trustManager();
        this.localTransportParameters =
                Objects.requireNonNull(params.localTransportParameters(), "localTransportParameters").clone();
        this.secureRandom = Objects.requireNonNull(params.secureRandom(), "secureRandom");
        this.peerHost = params.peerHost();
        this.peerPort = params.peerPort();
        this.serverSessionCache = Objects.requireNonNull(params.serverSessionCache(), "serverSessionCache");
        this.confidentialityLimits = Objects.requireNonNull(params.confidentialityLimits(), "confidentialityLimits");
        if ((this.sslParameters.getNeedClientAuth() || this.sslParameters.getWantClientAuth()) && this.trustManager == null) {
            throw new IllegalArgumentException(QuicTlsTrustManagers.SERVER_CLIENT_AUTH_TRUST_MANAGER_REQUIRED_MESSAGE);
        }
    }

    static QuicTls13ServerHandshake start(QuicVersion version, StartParameters parameters) {
        return new QuicTls13ServerHandshake(version, parameters);
    }

    static void rejectMiddleboxCompatibility(QuicTlsClientHelloMessage clientHello) throws QuicTransportException {
        if (clientHello.legacySessionId().length != 0) {
            throw new QuicTransportException("QUIC ClientHello legacy_session_id is not empty",
                                             0,
                                             QuicTransportErrors.PROTOCOL_VIOLATION);
        }
    }

    static List<SNIServerName> decodeRequestedServerNames(QuicTlsClientHelloMessage clientHello)
            throws QuicTransportException {
        QuicTlsExtension serverNameExtension = clientHello.extension(QuicTlsExtensions.SERVER_NAME).orElse(null);
        if (serverNameExtension == null) {
            return List.of();
        }

        ByteBuffer buffer = serverNameExtension.dataBuffer();
        ByteBuffer serverNameList = QuicTlsCodecSupport.readVector(buffer,
                                                                   QuicTlsCodecSupport.UINT16_LENGTH,
                                                                   "server_name_list",
                                                                   "server_name");
        QuicTlsCodecSupport.ensureConsumed(buffer, "server_name");

        List<SNIServerName> serverNames = new ArrayList<>();
        Set<Integer> seenTypes = new LinkedHashSet<>();
        while (serverNameList.hasRemaining()) {
            int nameType = QuicTlsCodecSupport.readUnsigned(serverNameList,
                                                            QuicTlsCodecSupport.UINT8_LENGTH,
                                                            "name_type",
                                                            "server_name");
            byte[] encodedName = QuicTlsCodecSupport.copy(QuicTlsCodecSupport.readVector(serverNameList,
                                                                                         QuicTlsCodecSupport.UINT16_LENGTH,
                                                                                         "host_name",
                                                                                         "server_name"));
            if (!seenTypes.add(nameType)) {
                throw QuicTlsHandshakeMessages.decodeError("Malformed server_name extension: duplicate name type");
            }
            if (nameType == StandardConstants.SNI_HOST_NAME) {
                serverNames.add(new SNIHostName(encodedName));
            }
        }
        return List.copyOf(serverNames);
    }

    Result consumeClientHello(ByteBuffer message) throws QuicTransportException {
        return switch (state) {
            case EXPECT_CLIENT_HELLO -> consumeInitialClientHello(message);
            case EXPECT_SECOND_CLIENT_HELLO -> consumeRetriedClientHello(message);
            default -> throw new IllegalStateException("Server handshake is not expecting ClientHello");
        };
    }

    void consumeClientCertificate(ByteBuffer message) throws QuicTransportException {
        if (state != State.EXPECT_CLIENT_CERTIFICATE) {
            throw new IllegalStateException("Server handshake is not expecting client Certificate");
        }

        QuicTlsCertificateMessage certificateMessage = QuicTlsCertificateMessage.decode(message);
        if (!Arrays.equals(certificateMessage.requestContext(), EMPTY_CERTIFICATE_REQUEST_CONTEXT)) {
            throw QuicTlsHandshakeMessages.decodeError(
                    "Malformed Certificate message: unexpected certificate_request_context");
        }

        X509Certificate[] certificates = certificateMessage.x509Certificates();

        transcript.add(message.asReadOnlyBuffer());
        if (certificates.length == 0) {
            peerCertificates = null;
            if (sslParameters.getNeedClientAuth()) {
                throw QuicTlsHandshakeMessages.certificateRequired("Empty client certificate chain");
            }
            state = State.EXPECT_CLIENT_FINISHED;
            return;
        }

        validateClientCertificates(certificates);
        peerCertificates = certificates.clone();
        state = State.EXPECT_CLIENT_CERTIFICATE_VERIFY;
    }

    void consumeClientCertificateVerify(ByteBuffer message) throws QuicTransportException {
        if (state != State.EXPECT_CLIENT_CERTIFICATE_VERIFY) {
            throw new IllegalStateException("Server handshake is not expecting client CertificateVerify");
        }

        QuicTlsCertificateVerifyMessage certificateVerify = QuicTlsCertificateVerifyMessage.decode(message);
        if (!requestedClientCertificateVerifySchemes.contains(certificateVerify.signatureScheme())) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "Client selected a CertificateVerify signature scheme that was not requested");
        }

        X509Certificate[] certificates = peerCertificates;
        if (certificates == null || certificates.length == 0) {
            throw QuicTlsHandshakeMessages.unexpectedMessage("Received CertificateVerify before client Certificate");
        }

        certificateVerify.verify(QuicTlsHandshakeMessages.peerCertificatePublicKey(certificates[0]),
                                  transcript.hash(cipherSuite),
                                  true);

        transcript.add(message.asReadOnlyBuffer());
        state = State.EXPECT_CLIENT_FINISHED;
    }

    void consumeClientFinished(ByteBuffer message) throws QuicTransportException {
        if (state != State.EXPECT_CLIENT_FINISHED) {
            throw new IllegalStateException("Server handshake is not expecting client Finished");
        }
        if (cipherSuite == null || connectionSecrets == null || serverHelloTranscriptHash == null) {
            throw new IllegalStateException("Server handshake state has not been initialized");
        }

        QuicTlsFinishedMessage finished = QuicTlsFinishedMessage.decode(message);
        QuicTls13SecretSchedule secretSchedule = new QuicTls13SecretSchedule(cipherSuite);
        SecretKey clientFinishedKey =
                secretSchedule.deriveFinishedKey(connectionSecrets.clientHandshakeTrafficSecret(serverHelloTranscriptHash));
        byte[] expectedVerifyData = secretSchedule.computeVerifyData(clientFinishedKey, transcript.hash(cipherSuite));

        if (!MessageDigest.isEqual(expectedVerifyData, finished.verifyData())) {
            throw QuicTlsHandshakeMessages.decryptError("The Finished message cannot be verified");
        }

        transcript.add(message.asReadOnlyBuffer());
        state = State.COMPLETE;
    }

    boolean complete() {
        return state == State.COMPLETE;
    }

    QuicLongHeaderTrafficKeys handshakeTrafficKeys() {
        QuicLongHeaderTrafficKeys keys = handshakeTrafficKeys;
        if (keys == null) {
            throw new IllegalStateException("Handshake traffic keys are not available yet");
        }
        return keys;
    }

    QuicTls13CipherSuite cipherSuite() {
        QuicTls13CipherSuite currentCipherSuite = cipherSuite;
        if (currentCipherSuite == null) {
            throw new IllegalStateException("Cipher suite is not available yet");
        }
        return currentCipherSuite;
    }

    QuicOneRttTrafficKeys oneRttTrafficKeys() {
        QuicOneRttTrafficKeys keys = oneRttTrafficKeys;
        if (keys == null) {
            throw new IllegalStateException("1-RTT traffic keys are not available yet");
        }
        return keys;
    }

    byte[] serverHelloTranscriptHash() {
        byte[] transcriptHash = serverHelloTranscriptHash;
        return transcriptHash == null ? null : transcriptHash.clone();
    }

    String applicationProtocol() {
        return applicationProtocol;
    }

    byte[] remoteTransportParameters() {
        return remoteTransportParameters == null ? null : remoteTransportParameters.clone();
    }

    X509Certificate[] localCertificates() {
        return localCertificates == null ? null : localCertificates.clone();
    }

    X509Certificate[] peerCertificates() {
        return peerCertificates == null ? null : peerCertificates.clone();
    }

    QuicTlsResumptionTicket newResumptionTicket(long ticketLifetimeSeconds,
                                                long ticketAgeAdd,
                                                byte[] ticketNonce,
                                                byte[] ticket)
            throws QuicTransportException {
        if (state != State.COMPLETE || cipherSuite == null || connectionSecrets == null) {
            throw new IllegalStateException("Server handshake must be complete before issuing a session ticket");
        }

        byte[] clientFinishedTranscriptHash = transcript.hash(cipherSuite);
        byte[] resumptionPsk = connectionSecrets.deriveResumptionPsk(clientFinishedTranscriptHash, ticketNonce);

        return new QuicTlsResumptionTicket(version,
                                           cipherSuite,
                                           ticketLifetimeSeconds,
                                           ticketAgeAdd,
                                           ticketNonce,
                                           ticket,
                                           resumptionPsk,
                                           applicationProtocol,
                                           serverName,
                                           localTransportParameters,
                                           System.currentTimeMillis());
    }

    private static List<QuicTls13CipherSuite> decodeCipherSuites(List<Integer> cipherSuites) {
        List<QuicTls13CipherSuite> decoded = new ArrayList<>(cipherSuites.size());
        for (int cipherSuite : cipherSuites) {
            try {
                decoded.add(QuicTls13CipherSuite.forCodePoint(cipherSuite));
            } catch (IllegalArgumentException e) {
                // Ignore suites outside the QUIC-compatible TLS 1.3 set Helidon currently implements.
            }
        }
        return List.copyOf(decoded);
    }

    private static boolean validTicketAge(long obfuscatedTicketAge, QuicTlsResumptionTicket resumptionTicket) {
        long deobfuscatedTicketAge = (obfuscatedTicketAge - resumptionTicket.ticketAgeAdd()) & 0xFFFF_FFFFL;
        long maxLifetimeSeconds = Math.min(resumptionTicket.ticketLifetimeSeconds(), MAX_TICKET_LIFETIME_SECONDS);
        if (maxLifetimeSeconds <= 0) {
            return false;
        }
        return deobfuscatedTicketAge <= maxLifetimeSeconds * 1000L;
    }

    private static void validateRetriedClientHello(QuicTlsClientHelloMessage clientHello,
                                                   HelloRetryRequestState retryState)
            throws QuicTransportException {
        QuicTlsClientHelloMessage initialClientHello = retryState.initialClientHello();
        if (clientHello.legacyVersion() != initialClientHello.legacyVersion()) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "ClientHello after HelloRetryRequest changed legacy_version");
        }
        if (!Arrays.equals(clientHello.random(), initialClientHello.random())) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "ClientHello after HelloRetryRequest changed random");
        }
        if (!Arrays.equals(clientHello.legacySessionId(), initialClientHello.legacySessionId())) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "ClientHello after HelloRetryRequest changed legacy_session_id");
        }
        if (!clientHello.cipherSuites().equals(initialClientHello.cipherSuites())) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "ClientHello after HelloRetryRequest changed cipher_suites");
        }
        if (!Arrays.equals(clientHello.legacyCompressionMethods(), initialClientHello.legacyCompressionMethods())) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "ClientHello after HelloRetryRequest changed legacy_compression_methods");
        }
        if (clientHello.extension(QuicTlsExtensions.EARLY_DATA).isPresent()) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "ClientHello after HelloRetryRequest retained early_data");
        }
        if (!retryComparableExtensions(clientHello).equals(retryComparableExtensions(initialClientHello))) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "ClientHello after HelloRetryRequest changed an extension that must remain identical");
        }

        Optional<QuicTlsPreSharedKeys.OfferedPsks> initialPsks = initialClientHello.offeredPreSharedKeys();
        Optional<QuicTlsPreSharedKeys.OfferedPsks> retriedPsks = clientHello.offeredPreSharedKeys();
        if (initialPsks.isEmpty() && retriedPsks.isPresent()) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "ClientHello after HelloRetryRequest added pre_shared_key");
        }
        if (retriedPsks.isPresent()) {
            List<QuicTlsPreSharedKeys.PskIdentity> initialIdentities =
                    initialPsks.orElseThrow().identities();
            int initialIndex = 0;
            for (QuicTlsPreSharedKeys.PskIdentity retriedIdentity : retriedPsks.orElseThrow().identities()) {
                boolean retainedIdentity = false;
                while (initialIndex < initialIdentities.size()) {
                    QuicTlsPreSharedKeys.PskIdentity initialIdentity = initialIdentities.get(initialIndex++);
                    if (MessageDigest.isEqual(initialIdentity.identity(), retriedIdentity.identity())) {
                        retainedIdentity = true;
                        break;
                    }
                }
                if (!retainedIdentity) {
                    throw QuicTlsHandshakeMessages.illegalParameter(
                            "ClientHello after HelloRetryRequest added or reordered a pre_shared_key identity");
                }
            }
        }
        validateRetriedCookie(clientHello, retryState.cookie());
    }

    private static void validateRetriedCookie(QuicTlsClientHelloMessage clientHello, byte[] expectedCookie)
            throws QuicTransportException {
        QuicTlsExtension cookieExtension = clientHello.extension(QuicTlsExtensions.COOKIE).orElse(null);
        if (expectedCookie == null) {
            if (cookieExtension != null) {
                throw QuicTlsHandshakeMessages.illegalParameter(
                        "ClientHello after HelloRetryRequest included an unexpected cookie");
            }
            return;
        }
        if (cookieExtension == null) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "ClientHello after HelloRetryRequest omitted the requested cookie");
        }
        byte[] actualCookie = QuicTlsCookie.decode(cookieExtension.dataBuffer());
        if (!MessageDigest.isEqual(expectedCookie, actualCookie)) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "ClientHello after HelloRetryRequest changed the requested cookie");
        }
    }

    private static List<QuicTlsExtension> retryComparableExtensions(QuicTlsClientHelloMessage clientHello) {
        List<QuicTlsExtension> comparable = new ArrayList<>(clientHello.extensions().size());
        for (QuicTlsExtension extension : clientHello.extensions()) {
            switch (extension.type()) {
            case QuicTlsExtensions.KEY_SHARE,
                 QuicTlsExtensions.COOKIE,
                 QuicTlsExtensions.PADDING,
                 QuicTlsExtensions.EARLY_DATA,
                 QuicTlsExtensions.PRE_SHARED_KEY -> {
            }
            default -> comparable.add(extension);
            }
        }
        return List.copyOf(comparable);
    }

    private static QuicTlsKeyShareEntry requireRetriedKeyShare(QuicTlsClientHelloMessage clientHello,
                                                               HelloRetryRequestState retryState)
            throws QuicTransportException {
        List<QuicTlsKeyShareEntry> keyShares = clientHello.keyShares();
        if (keyShares.size() != 1 || keyShares.getFirst().namedGroup() != retryState.selectedGroup()) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "ClientHello after HelloRetryRequest did not offer exactly one key_share for the requested group");
        }
        return keyShares.getFirst();
    }

    private static Optional<QuicTlsSignatureScheme> selectCertificateVerifyScheme(
            List<QuicTlsSignatureScheme> clientSignatureSchemes,
            List<QuicTlsSignatureScheme> localSignatureSchemes,
            PrivateKey privateKey,
            PublicKey publicKey) {
        for (QuicTlsSignatureScheme signatureScheme : clientSignatureSchemes) {
            if (!localSignatureSchemes.contains(signatureScheme)) {
                continue;
            }
            if (!supportsSignatureScheme(signatureScheme, privateKey, publicKey)) {
                continue;
            }
            return Optional.of(signatureScheme);
        }
        return Optional.empty();
    }

    private static boolean supportsSignatureScheme(QuicTlsSignatureScheme signatureScheme,
                                                   PrivateKey privateKey,
                                                   PublicKey publicKey) {
        if (!signatureScheme.certificateVerify()
                || !signatureScheme.keyType().equalsIgnoreCase(privateKey.getAlgorithm())
                || !signatureScheme.keyType().equalsIgnoreCase(publicKey.getAlgorithm())) {
            return false;
        }

        if (publicKey instanceof ECPublicKey ecPublicKey) {
            QuicTlsNamedGroup requiredGroup = signatureScheme.requiredCertificateGroup();
            if (requiredGroup != null) {
                ECParameterSpec parameterSpec = ecPublicKey.getParams();
                return QuicTlsNamedGroup.forEcParameterSpec(parameterSpec)
                        .map(requiredGroup::equals)
                        .orElse(false);
            }
        } else if (signatureScheme.requiredCertificateGroup() != null) {
            return false;
        }

        return signatureScheme.supports(privateKey, publicKey);
    }

    private static QuicTlsServerHelloMessage helloRetryRequest(byte[] legacySessionId,
                                                               QuicTls13CipherSuite cipherSuite,
                                                               QuicTlsNamedGroup selectedGroup,
                                                               byte[] cookie) {
        List<QuicTlsExtension> extensions = new ArrayList<>(cookie == null ? 2 : 3);
        extensions.add(QuicTlsExtension.create(QuicTlsExtensions.SUPPORTED_VERSIONS,
                                               QuicTlsSupportedVersions.encodeServerHello(
                                                       QuicTlsSupportedVersions.TLS_1_3)));
        extensions.add(QuicTlsExtension.create(QuicTlsExtensions.KEY_SHARE,
                                               QuicTlsKeyShares.encodeHelloRetryRequest(selectedGroup)));
        if (cookie != null) {
            extensions.add(QuicTlsExtension.create(QuicTlsExtensions.COOKIE, QuicTlsCookie.encode(cookie)));
        }
        return QuicTlsServerHelloMessage.helloRetryRequest(legacySessionId, cipherSuite.codePoint(), extensions);
    }

    private static byte[] encodeEncryptedExtensions(String applicationProtocol,
                                                    byte[] localTransportParameters,
                                                    boolean acknowledgeServerName) {
        List<QuicTlsExtension> extensions = new ArrayList<>(acknowledgeServerName ? 3 : 2);
        if (acknowledgeServerName) {
            extensions.add(QuicTlsExtension.create(QuicTlsExtensions.SERVER_NAME, BufferData.EMPTY_BYTES));
        }
        extensions.add(QuicTlsExtension.create(QuicTlsExtensions.APPLICATION_LAYER_PROTOCOL_NEGOTIATION,
                                               QuicTlsApplicationProtocols.encodeServerSelection(applicationProtocol)));
        extensions.add(QuicTlsExtension.create(QuicTlsExtensions.QUIC_TRANSPORT_PARAMETERS, localTransportParameters));
        ByteBuffer body = QuicTlsExtensions.encode(extensions);
        return encodeHandshakeMessage(QuicTlsHandshakeMessages.ENCRYPTED_EXTENSIONS, body);
    }

    private static byte[] encodeCertificateRequestMessage(List<QuicTlsSignatureScheme> certificateVerifySchemes,
                                                          List<QuicTlsSignatureScheme> certificateSignatureSchemes,
                                                          Principal[] certificateAuthorities) {
        List<QuicTlsExtension> extensions = new ArrayList<>();
        extensions.add(QuicTlsExtension.create(QuicTlsExtensions.SIGNATURE_ALGORITHMS,
                                               QuicTlsSignatureScheme.encodeCertificateVerifyVector(
                                                       certificateVerifySchemes)));
        extensions.add(QuicTlsExtension.create(QuicTlsExtensions.SIGNATURE_ALGORITHMS_CERT,
                                               QuicTlsSignatureScheme.encodeCertificateSignatureVector(
                                                       certificateSignatureSchemes)));
        if (certificateAuthorities.length != 0) {
            extensions.add(QuicTlsExtension.create(QuicTlsExtensions.CERTIFICATE_AUTHORITIES,
                                                   QuicTlsCertificateRequestMessage.encodeCertificateAuthorities(
                                                           certificateAuthorities)));
        }
        return copy(QuicTlsCertificateRequestMessage.create(EMPTY_CERTIFICATE_REQUEST_CONTEXT, extensions).encode());
    }

    private static byte[] encodeCertificateMessage(X509Certificate[] certificateChain) throws QuicTransportException {
        try {
            List<QuicTlsCertificateMessage.CertificateEntry> certificateEntries = new ArrayList<>(certificateChain.length);
            for (X509Certificate certificate : certificateChain) {
                certificateEntries.add(new QuicTlsCertificateMessage.CertificateEntry(certificate.getEncoded(), List.of()));
            }
            return copy(QuicTlsCertificateMessage.create(EMPTY_CERTIFICATE_REQUEST_CONTEXT, certificateEntries).encode());
        } catch (CertificateEncodingException | ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("Failed to encode the local server certificate chain", e);
        }
    }

    private static byte[] encodeHandshakeMessage(int messageType, ByteBuffer body) {
        ByteBuffer content = Objects.requireNonNull(body, "body").slice();
        ByteBuffer encoded = ByteBuffer.allocate(QuicTlsHandshakeMessages.HEADER_LENGTH + content.remaining());
        QuicTlsCodecSupport.putHandshakeHeader(encoded, messageType, content.remaining());
        encoded.put(content);
        return copy(encoded.flip());
    }

    private static byte[] copy(ByteBuffer buffer) {
        ByteBuffer duplicate = Objects.requireNonNull(buffer, "buffer").asReadOnlyBuffer();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }

    private Result consumeInitialClientHello(ByteBuffer message) throws QuicTransportException {
        byte[] clientHelloBytes = copy(message);
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(ByteBuffer.wrap(clientHelloBytes));
        validateClientHello(clientHello);

        QuicTls13CipherSuite selectedCipherSuite = selectCipherSuite(clientHello);
        ClientKeyShareSelection keyShareSelection = selectClientKeyShare(clientHello);
        if (keyShareSelection.helloRetryRequest()) {
            return helloRetryRequest(clientHelloBytes,
                                     clientHello,
                                     selectedCipherSuite,
                                     keyShareSelection.helloRetryRequestGroup(),
                                     null);
        }
        return createServerFlight(clientHelloBytes,
                                  clientHello,
                                  selectedCipherSuite,
                                  keyShareSelection.keyShareEntry());
    }

    private ServerFlight consumeRetriedClientHello(ByteBuffer message) throws QuicTransportException {
        byte[] clientHelloBytes = copy(message);
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(ByteBuffer.wrap(clientHelloBytes));
        validateClientHello(clientHello);

        HelloRetryRequestState retryState = requireHelloRetryRequestState();
        validateRetriedClientHello(clientHello, retryState);
        QuicTlsKeyShareEntry clientKeyShare = requireRetriedKeyShare(clientHello, retryState);
        return createServerFlight(clientHelloBytes, clientHello, retryState.cipherSuite(), clientKeyShare);
    }

    private void validateClientHello(QuicTlsClientHelloMessage clientHello) throws QuicTransportException {
        rejectMiddleboxCompatibility(clientHello);
        if (clientHello.legacyVersion() != LEGACY_TLS_VERSION) {
            throw QuicTlsHandshakeMessages.illegalParameter("ClientHello legacy_version is not TLS 1.2 compatibility mode");
        }
        byte[] legacyCompressionMethods = clientHello.legacyCompressionMethods();
        if (legacyCompressionMethods.length != 1 || legacyCompressionMethods[0] != LEGACY_NULL_COMPRESSION_METHOD) {
            throw QuicTlsHandshakeMessages.illegalParameter("ClientHello legacy_compression_methods is not null compression");
        }
        if (!clientHello.supportedVersions().contains(QuicTlsSupportedVersions.TLS_1_3)) {
            throw QuicTlsHandshakeMessages.illegalParameter("ClientHello did not offer TLS 1.3");
        }
        if (clientHello.keyShares().isEmpty()) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed ClientHello message: missing key_share extension");
        }
        validateResumptionOffer(clientHello);
    }

    private void validateResumptionOffer(QuicTlsClientHelloMessage clientHello) throws QuicTransportException {
        QuicTlsExtension earlyData = clientHello.extension(QuicTlsExtensions.EARLY_DATA).orElse(null);
        QuicTlsExtension preSharedKey = clientHello.extension(QuicTlsExtensions.PRE_SHARED_KEY).orElse(null);
        if (earlyData != null && earlyData.data().length != 0) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed ClientHello message: early_data");
        }
        if (earlyData != null && preSharedKey == null) {
            throw QuicTlsHandshakeMessages.illegalParameter("ClientHello early_data requires pre_shared_key");
        }
        QuicTlsExtension pskKeyExchangeModes =
                clientHello.extension(QuicTlsExtensions.PSK_KEY_EXCHANGE_MODES).orElse(null);
        if (pskKeyExchangeModes != null) {
            QuicTlsPskKeyExchangeModes.decodeClientHello(pskKeyExchangeModes.dataBuffer());
        }
        if (preSharedKey != null) {
            if (clientHello.extensions().getLast().type() != QuicTlsExtensions.PRE_SHARED_KEY) {
                throw QuicTlsHandshakeMessages.illegalParameter("ClientHello pre_shared_key must be the last extension");
            }
            clientHello.offeredPreSharedKeys()
                    .orElseThrow(() -> QuicTlsHandshakeMessages.decodeError(
                            "Malformed ClientHello message: missing pre_shared_key extension"));
            if (pskKeyExchangeModes == null) {
                throw QuicTlsHandshakeMessages.illegalParameter(
                        "ClientHello pre_shared_key requires psk_key_exchange_modes");
            }
        }
    }

    private QuicTls13CipherSuite selectCipherSuite(QuicTlsClientHelloMessage clientHello) throws QuicTransportException {
        List<QuicTls13CipherSuite> clientCipherSuites = decodeCipherSuites(clientHello.cipherSuites());
        List<QuicTls13CipherSuite> serverCipherSuites = enabledCipherSuites();
        List<QuicTls13CipherSuite> preferred = sslParameters.getUseCipherSuitesOrder()
                ? serverCipherSuites
                : clientCipherSuites;
        List<QuicTls13CipherSuite> proposed = sslParameters.getUseCipherSuitesOrder()
                ? clientCipherSuites
                : serverCipherSuites;

        for (QuicTls13CipherSuite candidate : preferred) {
            if (proposed.contains(candidate)) {
                return candidate;
            }
        }
        throw QuicTlsHandshakeMessages.handshakeFailure("No QUIC-compatible TLS 1.3 cipher suites are enabled in common");
    }

    private List<QuicTls13CipherSuite> enabledCipherSuites() {
        SSLEngine sslEngine = configuredServerEngine();
        List<QuicTls13CipherSuite> cipherSuites = new ArrayList<>();
        for (String enabledCipherSuite : sslEngine.getEnabledCipherSuites()) {
            try {
                cipherSuites.add(QuicTls13CipherSuite.forName(enabledCipherSuite));
            } catch (IllegalArgumentException e) {
                // Ignore suites outside the QUIC-compatible TLS 1.3 set Helidon currently implements.
            }
        }
        return List.copyOf(cipherSuites);
    }

    private ClientKeyShareSelection selectClientKeyShare(QuicTlsClientHelloMessage clientHello)
            throws QuicTransportException {
        List<QuicTlsNamedGroup> supportedGroups = supportedGroups();
        List<QuicTlsKeyShareEntry> clientKeyShares = clientHello.keyShares();
        for (QuicTlsNamedGroup supportedGroup : supportedGroups) {
            for (QuicTlsKeyShareEntry clientKeyShare : clientKeyShares) {
                if (clientKeyShare.namedGroup() == supportedGroup) {
                    return ClientKeyShareSelection.keyShare(clientKeyShare);
                }
            }
        }

        List<QuicTlsNamedGroup> clientSupportedGroups = clientHello.supportedGroups();
        for (QuicTlsNamedGroup supportedGroup : supportedGroups) {
            if (clientSupportedGroups.contains(supportedGroup)) {
                return ClientKeyShareSelection.helloRetryRequest(supportedGroup);
            }
        }
        throw QuicTlsHandshakeMessages.handshakeFailure("ClientHello did not offer a compatible key share");
    }

    private Result helloRetryRequest(byte[] clientHelloBytes,
                                     QuicTlsClientHelloMessage clientHello,
                                     QuicTls13CipherSuite selectedCipherSuite,
                                     QuicTlsNamedGroup selectedGroup,
                                     byte[] cookie)
            throws QuicTransportException {
        if (state != State.EXPECT_CLIENT_HELLO) {
            throw new IllegalStateException("Server handshake is not expecting to send HelloRetryRequest");
        }

        QuicTlsServerHelloMessage helloRetryRequest =
                helloRetryRequest(clientHello.legacySessionId(), selectedCipherSuite, selectedGroup, cookie);
        byte[] helloRetryRequestBytes = copy(helloRetryRequest.encode());

        transcript.reset();
        transcript.add(ByteBuffer.wrap(clientHelloBytes));
        transcript.add(ByteBuffer.wrap(helloRetryRequestBytes));

        this.helloRetryRequest = new HelloRetryRequestState(clientHello, selectedCipherSuite, selectedGroup, cookie);
        this.state = State.EXPECT_SECOND_CLIENT_HELLO;
        return new HelloRetryRequestResult(helloRetryRequestBytes);
    }

    private ServerFlight createServerFlight(byte[] clientHelloBytes,
                                            QuicTlsClientHelloMessage clientHello,
                                            QuicTls13CipherSuite selectedCipherSuite,
                                            QuicTlsKeyShareEntry clientKeyShare)
            throws QuicTransportException {
        Optional<ClientSignatureSchemes> offeredSignatureSchemes = clientSignatureSchemes(clientHello);
        List<SNIServerName> requestedServerNames = decodeRequestedServerNames(clientHello);
        String requestedServerName = requestedServerNames.stream()
                .filter(SNIHostName.class::isInstance)
                .map(SNIHostName.class::cast)
                .map(SNIHostName::getAsciiName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .findFirst()
                .orElse(null);
        this.serverName = requestedServerName;
        String selectedApplicationProtocol = selectApplicationProtocol(clientHello);
        byte[] clientTransportParameters = requiredClientTransportParameters(clientHello);
        Optional<ServerResumptionSelection> resumptionSelection = selectResumption(clientHello,
                                                                                   clientHelloBytes,
                                                                                   selectedCipherSuite,
                                                                                   selectedApplicationProtocol,
                                                                                   requestedServerName);
        if (resumptionSelection.isPresent()) {
            return createResumedServerFlight(clientHelloBytes,
                                             clientHello,
                                             selectedCipherSuite,
                                             clientKeyShare,
                                             selectedApplicationProtocol,
                                             clientTransportParameters,
                                             resumptionSelection.orElseThrow());
        }
        ClientSignatureSchemes clientSignatureSchemes = offeredSignatureSchemes.orElseThrow(
                () -> QuicTlsHandshakeMessages.missingExtension(
                        "ClientHello missing signature_algorithms extension"));
        this.clientCertificateSignatureSchemes =
                List.copyOf(clientSignatureSchemes.certificateSignatureSchemes());
        ServerCredentials credentials = chooseCredentials(clientSignatureSchemes.certificateVerifySchemes(),
                                                          clientSignatureSchemes.certificateSignatureSchemes(),
                                                          requestedServerNames);

        QuicTlsKeySharePossession serverKeyShare =
                QuicTlsKeySharePossession.create(clientKeyShare.namedGroup(), secureRandom);

        QuicTlsServerHelloMessage serverHello =
                serverHello(clientHello.legacySessionId(), selectedCipherSuite, serverKeyShare.keyShareEntry(), -1);
        byte[] serverHelloBytes = copy(serverHello.encode());

        if (helloRetryRequest == null) {
            transcript.reset();
        }
        transcript.add(ByteBuffer.wrap(clientHelloBytes));
        transcript.add(ByteBuffer.wrap(serverHelloBytes));

        QuicTls13ConnectionSecrets secrets;
        byte[] helloTranscriptHash;
        QuicLongHeaderTrafficKeys serverHandshakeKeys;
        byte[] encryptedExtensions;
        byte[] certificateRequest = null;
        byte[] certificate;
        byte[] certificateVerify;
        byte[] finished;
        QuicOneRttTrafficKeys serverOneRttKeys;
        secrets = QuicTls13ConnectionSecrets.create(version,
                                                    selectedCipherSuite,
                                                    serverKeyShare,
                                                    clientKeyShare,
                                                    false,
                                                    confidentialityLimits.aesGcm(),
                                                    confidentialityLimits.chacha20Poly1305());
        helloTranscriptHash = transcript.hash(selectedCipherSuite);
        serverHandshakeKeys = secrets.deriveHandshakeTrafficKeys(helloTranscriptHash);

        encryptedExtensions = encodeEncryptedExtensions(selectedApplicationProtocol,
                                                        localTransportParameters,
                                                        requestedServerName != null);
        transcript.add(ByteBuffer.wrap(encryptedExtensions));

        if (sslParameters.getNeedClientAuth() || sslParameters.getWantClientAuth()) {
            List<QuicTlsSignatureScheme> certificateVerifySchemes = localCertificateVerifySchemes();
            List<QuicTlsSignatureScheme> certificateSignatureSchemes = localCertificateSignatureSchemes();
            certificateRequest = encodeCertificateRequestMessage(certificateVerifySchemes,
                                                                 certificateSignatureSchemes,
                                                                 clientCertificateAuthorities());
            transcript.add(ByteBuffer.wrap(certificateRequest));
            requestedClientCertificateVerifySchemes = List.copyOf(certificateVerifySchemes);
            requestedClientCertificateSignatureSchemes = List.copyOf(certificateSignatureSchemes);
        } else {
            requestedClientCertificateVerifySchemes = List.of();
            requestedClientCertificateSignatureSchemes = List.of();
        }

        certificate = encodeCertificateMessage(credentials.certificateChain());
        transcript.add(ByteBuffer.wrap(certificate));

        certificateVerify = copy(QuicTlsCertificateVerifyMessage.sign(credentials.signatureScheme(),
                                                                      credentials.privateKey(),
                                                                      transcript.hash(selectedCipherSuite),
                                                                      false)
                                         .encode());
        transcript.add(ByteBuffer.wrap(certificateVerify));

        QuicTls13SecretSchedule secretSchedule = new QuicTls13SecretSchedule(selectedCipherSuite);
        SecretKey serverFinishedKey =
                secretSchedule.deriveFinishedKey(secrets.serverHandshakeTrafficSecret(helloTranscriptHash));
        finished = copy(QuicTlsFinishedMessage.create(secretSchedule.computeVerifyData(serverFinishedKey,
                                                                                       transcript.hash(
                                                                                               selectedCipherSuite)))
                                .encode());
        transcript.add(ByteBuffer.wrap(finished));
        serverOneRttKeys = secrets.deriveOneRttTrafficKeys(transcript.hash(selectedCipherSuite));

        this.cipherSuite = selectedCipherSuite;
        this.connectionSecrets = secrets;
        this.serverHelloTranscriptHash = helloTranscriptHash.clone();
        this.handshakeTrafficKeys = serverHandshakeKeys;
        this.oneRttTrafficKeys = serverOneRttKeys;
        this.applicationProtocol = selectedApplicationProtocol;
        this.serverName = requestedServerName;
        this.remoteTransportParameters = clientTransportParameters.clone();
        this.localCertificates = credentials.certificateChain().clone();
        this.peerCertificates = null;
        this.helloRetryRequest = null;
        this.state = certificateRequest == null ? State.EXPECT_CLIENT_FINISHED : State.EXPECT_CLIENT_CERTIFICATE;

        return new ServerFlight(serverHelloBytes,
                                encryptedExtensions,
                                certificateRequest,
                                certificate,
                                certificateVerify,
                                finished,
                                helloTranscriptHash,
                                selectedApplicationProtocol,
                                clientTransportParameters,
                                credentials.certificateChain());
    }

    private Optional<ServerResumptionSelection> selectResumption(QuicTlsClientHelloMessage clientHello,
                                                                 byte[] clientHelloBytes,
                                                                 QuicTls13CipherSuite selectedCipherSuite,
                                                                 String selectedApplicationProtocol,
                                                                 String requestedServerName)
            throws QuicTransportException {
        if (helloRetryRequest != null || sslParameters.getNeedClientAuth() || sslParameters.getWantClientAuth()) {
            return Optional.empty();
        }
        Optional<QuicTlsPreSharedKeys.OfferedPsks> offeredPsks = clientHello.offeredPreSharedKeys();
        if (offeredPsks.isEmpty()) {
            return Optional.empty();
        }
        QuicTlsExtension pskKeyExchangeModes =
                clientHello.extension(QuicTlsExtensions.PSK_KEY_EXCHANGE_MODES).orElse(null);
        if (pskKeyExchangeModes == null
                || !QuicTlsPskKeyExchangeModes.decodeClientHello(pskKeyExchangeModes.dataBuffer())
                        .contains(QuicTlsPskKeyExchangeModes.PSK_DHE_KE)) {
            return Optional.empty();
        }

        List<QuicTlsPreSharedKeys.PskIdentity> identities = offeredPsks.orElseThrow().identities();
        List<byte[]> binders = offeredPsks.orElseThrow().binders();
        for (int identityIndex = 0; identityIndex < identities.size(); identityIndex++) {
            QuicTlsPreSharedKeys.PskIdentity identity = identities.get(identityIndex);
            Optional<QuicTlsResumptionTicket> resumptionTicket = serverSessionCache.cached(identity.identity());
            if (resumptionTicket.isEmpty()) {
                continue;
            }
            QuicTlsResumptionTicket cachedTicket = resumptionTicket.orElseThrow();
            if (!cachedTicket.transportCompatible(version)) {
                continue;
            }
            if (!selectedCipherSuite.sameHash(cachedTicket.cipherSuite())) {
                continue;
            }
            if (!Objects.equals(Optional.ofNullable(selectedApplicationProtocol), cachedTicket.applicationProtocol())) {
                continue;
            }
            if (!Objects.equals(Optional.ofNullable(requestedServerName), cachedTicket.serverName())) {
                continue;
            }
            if (!validTicketAge(identity.obfuscatedTicketAge(), cachedTicket)) {
                continue;
            }

            byte[] expectedBinder = QuicTlsPreSharedKeys.computeBinder(cachedTicket, null, null, clientHelloBytes);
            if (!MessageDigest.isEqual(expectedBinder, binders.get(identityIndex))) {
                throw QuicTlsHandshakeMessages.decryptError(
                        "ClientHello pre_shared_key binder cannot be verified");
            }
            return Optional.of(new ServerResumptionSelection(identityIndex, cachedTicket));
        }
        return Optional.empty();
    }

    private ServerFlight createResumedServerFlight(byte[] clientHelloBytes,
                                                   QuicTlsClientHelloMessage clientHello,
                                                   QuicTls13CipherSuite selectedCipherSuite,
                                                   QuicTlsKeyShareEntry clientKeyShare,
                                                   String selectedApplicationProtocol,
                                                   byte[] clientTransportParameters,
                                                   ServerResumptionSelection resumptionSelection)
            throws QuicTransportException {
        QuicTlsKeySharePossession serverKeyShare =
                QuicTlsKeySharePossession.create(clientKeyShare.namedGroup(), secureRandom);

        QuicTlsServerHelloMessage serverHello =
                serverHello(clientHello.legacySessionId(),
                            selectedCipherSuite,
                            serverKeyShare.keyShareEntry(),
                            resumptionSelection.selectedIdentity());
        byte[] serverHelloBytes = copy(serverHello.encode());

        transcript.reset();
        transcript.add(ByteBuffer.wrap(clientHelloBytes));
        transcript.add(ByteBuffer.wrap(serverHelloBytes));

        QuicTls13ConnectionSecrets secrets;
        byte[] helloTranscriptHash;
        QuicLongHeaderTrafficKeys serverHandshakeKeys;
        byte[] encryptedExtensions;
        byte[] finished;
        QuicOneRttTrafficKeys serverOneRttKeys;
        secrets = QuicTls13ConnectionSecrets.create(version,
                                                    selectedCipherSuite,
                                                    resumptionSelection.resumptionTicket().resumptionPsk(),
                                                    serverKeyShare,
                                                    clientKeyShare,
                                                    false,
                                                    confidentialityLimits);
        helloTranscriptHash = transcript.hash(selectedCipherSuite);
        serverHandshakeKeys = secrets.deriveHandshakeTrafficKeys(helloTranscriptHash);

        encryptedExtensions = encodeEncryptedExtensions(selectedApplicationProtocol,
                                                        localTransportParameters,
                                                        serverName != null);
        transcript.add(ByteBuffer.wrap(encryptedExtensions));

        QuicTls13SecretSchedule secretSchedule = new QuicTls13SecretSchedule(selectedCipherSuite);
        SecretKey serverFinishedKey =
                secretSchedule.deriveFinishedKey(secrets.serverHandshakeTrafficSecret(helloTranscriptHash));
        finished = copy(QuicTlsFinishedMessage.create(secretSchedule.computeVerifyData(serverFinishedKey,
                                                                                       transcript.hash(
                                                                                               selectedCipherSuite)))
                                .encode());
        transcript.add(ByteBuffer.wrap(finished));
        serverOneRttKeys = secrets.deriveOneRttTrafficKeys(transcript.hash(selectedCipherSuite));

        this.cipherSuite = selectedCipherSuite;
        this.connectionSecrets = secrets;
        this.serverHelloTranscriptHash = helloTranscriptHash.clone();
        this.handshakeTrafficKeys = serverHandshakeKeys;
        this.oneRttTrafficKeys = serverOneRttKeys;
        this.applicationProtocol = selectedApplicationProtocol;
        this.remoteTransportParameters = clientTransportParameters.clone();
        this.localCertificates = null;
        this.peerCertificates = null;
        this.clientCertificateSignatureSchemes = List.of();
        this.requestedClientCertificateVerifySchemes = List.of();
        this.requestedClientCertificateSignatureSchemes = List.of();
        this.helloRetryRequest = null;
        this.state = State.EXPECT_CLIENT_FINISHED;

        return new ServerFlight(serverHelloBytes,
                                encryptedExtensions,
                                null,
                                null,
                                null,
                                finished,
                                helloTranscriptHash,
                                selectedApplicationProtocol,
                                clientTransportParameters,
                                null);
    }

    private HelloRetryRequestState requireHelloRetryRequestState() {
        HelloRetryRequestState retryState = helloRetryRequest;
        if (retryState == null) {
            throw new IllegalStateException("Server handshake is not expecting a retried ClientHello");
        }
        return retryState;
    }

    private List<QuicTlsNamedGroup> supportedGroups() {
        String[] namedGroups = sslParameters.getNamedGroups();
        if (namedGroups == null || namedGroups.length == 0) {
            return DEFAULT_SUPPORTED_GROUPS;
        }

        List<QuicTlsNamedGroup> groups = new ArrayList<>(namedGroups.length);
        for (String namedGroup : namedGroups) {
            for (QuicTlsNamedGroup candidate : QuicTlsNamedGroup.values()) {
                if (candidate.tlsName().equalsIgnoreCase(namedGroup)) {
                    groups.add(candidate);
                    break;
                }
            }
        }
        if (groups.isEmpty()) {
            throw new IllegalArgumentException("No supported TLS named groups are enabled");
        }
        return List.copyOf(groups);
    }

    private Optional<ClientSignatureSchemes> clientSignatureSchemes(QuicTlsClientHelloMessage clientHello)
            throws QuicTransportException {
        QuicTlsExtension signatureAlgorithms =
                clientHello.extension(QuicTlsExtensions.SIGNATURE_ALGORITHMS).orElse(null);
        QuicTlsExtension certificateSignatureAlgorithms =
                clientHello.extension(QuicTlsExtensions.SIGNATURE_ALGORITHMS_CERT).orElse(null);
        List<QuicTlsSignatureScheme> certificateSignatureSchemes =
                certificateSignatureAlgorithms == null
                        ? null
                        : QuicTlsSignatureScheme.decodeCertificateSignatureVector(
                                certificateSignatureAlgorithms.dataBuffer(),
                                "supported_certificate_signature_algorithms",
                                "ClientHello");
        if (signatureAlgorithms == null) {
            return Optional.empty();
        }
        List<QuicTlsSignatureScheme> certificateVerifySchemes =
                QuicTlsSignatureScheme.decodeCertificateVerifyVector(signatureAlgorithms.dataBuffer(),
                                                                      "supported_signature_algorithms",
                                                                      "ClientHello");
        if (certificateSignatureSchemes == null) {
            certificateSignatureSchemes = QuicTlsSignatureScheme.decodeCertificateSignatureVector(
                    signatureAlgorithms.dataBuffer(),
                    "supported_signature_algorithms",
                    "ClientHello");
        }
        return Optional.of(new ClientSignatureSchemes(certificateVerifySchemes, certificateSignatureSchemes));
    }

    private List<QuicTlsSignatureScheme> localCertificateVerifySchemes() {
        return QuicTlsSignatureScheme.certificateVerifySchemes(sslParameters.getSignatureSchemes());
    }

    private List<QuicTlsSignatureScheme> localCertificateSignatureSchemes() {
        return QuicTlsSignatureScheme.certificateSignatureSchemes(sslParameters.getSignatureSchemes());
    }

    private String selectApplicationProtocol(QuicTlsClientHelloMessage clientHello) throws QuicTransportException {
        QuicTlsExtension alpnExtension = clientHello.extension(QuicTlsExtensions.APPLICATION_LAYER_PROTOCOL_NEGOTIATION)
                .orElseThrow(() -> QuicTlsHandshakeMessages.noApplicationProtocol(
                        "Client did not offer application layer protocol"));
        return QuicTlsApplicationProtocols.selectServerProtocol(QuicTlsApplicationProtocols.decodeClientHello(
                                                                        alpnExtension.dataBuffer()),
                                                                sslParameters.getApplicationProtocols());
    }

    private byte[] requiredClientTransportParameters(QuicTlsClientHelloMessage clientHello) throws QuicTransportException {
        QuicTlsExtension transportParameters = clientHello.extension(QuicTlsExtensions.QUIC_TRANSPORT_PARAMETERS)
                .orElseThrow(() -> QuicTlsHandshakeMessages.missingExtension(
                        "ClientHello missing quic_transport_parameters extension"));
        return transportParameters.data();
    }

    private ServerCredentials chooseCredentials(List<QuicTlsSignatureScheme> clientCertificateVerifySchemes,
                                                List<QuicTlsSignatureScheme> clientCertificateSignatureSchemes,
                                                List<SNIServerName> requestedServerNames) throws QuicTransportException {
        List<QuicTlsSignatureScheme> localCertificateVerifySchemes = localCertificateVerifySchemes();
        List<QuicTlsSignatureScheme> localCertificateSignatureSchemes = localCertificateSignatureSchemes();
        Set<String> candidateKeyTypes = new LinkedHashSet<>();
        for (QuicTlsSignatureScheme clientCertificateVerifyScheme : clientCertificateVerifySchemes) {
            if (localCertificateVerifySchemes.contains(clientCertificateVerifyScheme)) {
                candidateKeyTypes.add(clientCertificateVerifyScheme.keyType());
            }
        }
        if (candidateKeyTypes.isEmpty()) {
            throw QuicTlsHandshakeMessages.handshakeFailure("No supported CertificateVerify signature algorithm");
        }

        List<QuicTlsSignatureScheme> effectiveCertificateSignatureSchemes =
                clientCertificateSignatureSchemes.stream()
                        .filter(localCertificateSignatureSchemes::contains)
                        .toList();

        // RFC 8446 section 4.4.2.2 recommends continuing with a certificate chain of the server's choice when
        // signature_algorithms_cert has no usable overlap. An empty peer list exposes that fallback state to the key
        // manager; CertificateVerify selection below remains restricted to the modern signature_algorithms overlap.
        String[] localSupportedSignatureSchemes = localCertificateSignatureSchemes.stream()
                .map(QuicTlsSignatureScheme::tlsName)
                .toArray(String[]::new);
        String[] peerSupportedSignatureSchemes = effectiveCertificateSignatureSchemes.stream()
                .map(QuicTlsSignatureScheme::tlsName)
                .toArray(String[]::new);
        SSLParameters callbackParameters = QuicTlsParameters.copy(sslParameters);
        if (!requestedServerNames.isEmpty()) {
            callbackParameters.setServerNames(requestedServerNames);
        }
        callbackParameters.setCipherSuites(configuredServerEngine().getEnabledCipherSuites());
        callbackParameters.setSignatureSchemes(localSupportedSignatureSchemes);
        QuicTlsCallbackEngine callbackEngine = QuicTlsManagerCallbacks.callbackEngine(false,
                                                                                       callbackParameters,
                                                                                       localSupportedSignatureSchemes,
                                                                                       peerSupportedSignatureSchemes,
                                                                                       peerHost,
                                                                                       peerPort);

        ServerCredentials fallbackCredentials = null;
        for (String keyType : candidateKeyTypes) {
            Set<String> candidateAliases = new LinkedHashSet<>();
            String selectedAlias = QuicTlsManagerCallbacks.chooseServerAlias(keyManager,
                                                                              keyType,
                                                                              null,
                                                                              callbackEngine);
            if (selectedAlias == null) {
                continue;
            }
            candidateAliases.add(selectedAlias);
            Set<String> listedCandidateAliases = new LinkedHashSet<>();
            String[] listedAliases;
            try {
                listedAliases = keyManager.getServerAliases(keyType, null);
            } catch (ProviderException e) {
                throw QuicTlsHandshakeMessages.internalError(
                        "Server key manager failed to list certificate aliases",
                        e);
            }
            if (listedAliases != null) {
                for (String listedAlias : listedAliases) {
                    if (listedAlias != null) {
                        listedCandidateAliases.add(listedAlias);
                    }
                }
            }
            candidateAliases.addAll(listedCandidateAliases);

            X509Certificate selectedCertificate = null;
            for (String alias : candidateAliases) {
                PrivateKey privateKey = QuicTlsManagerCallbacks.privateKey(keyManager, alias);
                X509Certificate[] certificateChain = QuicTlsManagerCallbacks.certificateChain(keyManager, alias);
                if (privateKey == null || certificateChain == null || certificateChain.length == 0) {
                    throw QuicTlsHandshakeMessages.internalError(
                            "The local server certificate alias does not provide complete credentials",
                            new IllegalStateException("Incomplete server credentials for alias " + alias));
                }
                for (X509Certificate certificate : certificateChain) {
                    if (certificate == null) {
                        throw QuicTlsHandshakeMessages.internalError(
                                "The local server certificate chain contains a null certificate",
                                new IllegalStateException("Invalid server certificate chain for alias " + alias));
                    }
                }
                if (alias.equals(selectedAlias)) {
                    selectedCertificate = certificateChain[0];
                } else if (selectedCertificate == null
                        || !QuicTlsManagerCallbacks.sameCertificateIdentity(selectedCertificate,
                                                                           certificateChain[0])) {
                    continue;
                }
                PublicKey publicKey;
                try {
                    publicKey = certificateChain[0].getPublicKey();
                } catch (ProviderException e) {
                    throw QuicTlsHandshakeMessages.internalError(
                            "Failed to read the local server certificate public key",
                            e);
                }
                if (publicKey == null) {
                    throw QuicTlsHandshakeMessages.internalError(
                            "The local server certificate does not provide a public key",
                            new IllegalStateException("Invalid server certificate for alias " + alias));
                }

                Optional<QuicTlsSignatureScheme> signatureScheme =
                        selectCertificateVerifyScheme(clientCertificateVerifySchemes,
                                                      localCertificateVerifySchemes,
                                                      privateKey,
                                                      publicKey);
                if (signatureScheme.isEmpty()) {
                    continue;
                }

                ServerCredentials credentials =
                        new ServerCredentials(privateKey, certificateChain, signatureScheme.orElseThrow());
                if (effectiveCertificateSignatureSchemes.isEmpty()
                        || QuicTlsSignatureScheme.supportsCertificateChain(certificateChain,
                                                                          effectiveCertificateSignatureSchemes)) {
                    return credentials;
                }
                if (fallbackCredentials == null) {
                    fallbackCredentials = credentials;
                }
            }
        }

        if (fallbackCredentials != null) {
            return fallbackCredentials;
        }
        throw QuicTlsHandshakeMessages.handshakeFailure("No supported server certificate available");
    }

    private Principal[] clientCertificateAuthorities() {
        X509TrustManager currentTrustManager = trustManager;
        if (currentTrustManager == null) {
            return new Principal[0];
        }

        X509Certificate[] acceptedIssuers;
        try {
            acceptedIssuers = currentTrustManager.getAcceptedIssuers();
        } catch (ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("Trust manager failed to provide accepted issuers", e);
        }
        if (acceptedIssuers == null || acceptedIssuers.length == 0) {
            return new Principal[0];
        }

        Principal[] authorities = new Principal[acceptedIssuers.length];
        for (int i = 0; i < acceptedIssuers.length; i++) {
            X509Certificate acceptedIssuer = acceptedIssuers[i];
            if (acceptedIssuer == null) {
                throw QuicTlsHandshakeMessages.internalError(
                        "Trust manager returned a null accepted issuer",
                        new IllegalStateException("Invalid accepted issuer at index " + i));
            }
            try {
                authorities[i] = acceptedIssuer.getSubjectX500Principal();
            } catch (ProviderException e) {
                throw QuicTlsHandshakeMessages.internalError("Failed to read an accepted issuer", e);
            }
        }
        return authorities;
    }

    private void validateClientCertificates(X509Certificate[] certificates) throws QuicTransportException {
        X509TrustManager currentTrustManager = trustManager;
        if (currentTrustManager == null) {
            throw QuicPacketProtection.internalError("Client authentication requested without an X509TrustManager");
        }

        String[] localSupportedSignatureSchemes = requestedClientCertificateSignatureSchemes.stream()
                .map(QuicTlsSignatureScheme::tlsName)
                .toArray(String[]::new);
        String[] peerSupportedSignatureSchemes = clientCertificateSignatureSchemes.stream()
                .map(QuicTlsSignatureScheme::tlsName)
                .toArray(String[]::new);
        SSLParameters callbackParameters = QuicTlsParameters.copy(sslParameters);
        callbackParameters.setSignatureSchemes(localSupportedSignatureSchemes);
        QuicTlsCallbackEngine callbackEngine = QuicTlsManagerCallbacks.callbackEngine(false,
                                                                                       callbackParameters,
                                                                                       localSupportedSignatureSchemes,
                                                                                       peerSupportedSignatureSchemes,
                                                                                       peerHost,
                                                                                       peerPort);
        callbackEngine.handshakeApplicationProtocol(applicationProtocol);
        callbackEngine.callbackSession().protocol("TLSv1.3");
        callbackEngine.callbackSession().cipherSuite(cipherSuite.name());
        callbackEngine.callbackSession().localCertificates(localCertificates);
        callbackEngine.callbackSession().peerCertificates(certificates);

        QuicTlsManagerCallbacks.checkClientTrusted(currentTrustManager,
                                                   certificates.clone(),
                                                   QuicTlsHandshakeMessages.peerCertificateAuthType(certificates[0]),
                                                   callbackEngine);
    }

    private SSLEngine configuredServerEngine() {
        try {
            SSLEngine sslEngine = peerHost == null
                    ? sslContext.createSSLEngine()
                    : sslContext.createSSLEngine(peerHost, peerPort);
            sslEngine.setUseClientMode(false);
            sslEngine.setSSLParameters(sslParameters);
            return sslEngine;
        } catch (ProviderException | UnsupportedOperationException | IllegalStateException | IllegalArgumentException e) {
            throw QuicTlsHandshakeMessages.internalError("Failed to configure the server TLS engine", e);
        }
    }

    private QuicTlsServerHelloMessage serverHello(byte[] legacySessionId,
                                                  QuicTls13CipherSuite cipherSuite,
                                                  QuicTlsKeyShareEntry keyShareEntry,
                                                  int selectedIdentity) {
        byte[] random = new byte[QuicTlsCodecSupport.RANDOM_LENGTH];
        try {
            secureRandom.nextBytes(random);
        } catch (ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("Failed to generate the ServerHello random", e);
        }
        List<QuicTlsExtension> extensions = new ArrayList<>(selectedIdentity >= 0 ? 3 : 2);
        extensions.add(QuicTlsExtension.create(QuicTlsExtensions.KEY_SHARE,
                                               QuicTlsKeyShares.encodeServerHello(keyShareEntry)));
        extensions.add(QuicTlsExtension.create(QuicTlsExtensions.SUPPORTED_VERSIONS,
                                               QuicTlsSupportedVersions.encodeServerHello(
                                                       QuicTlsSupportedVersions.TLS_1_3)));
        if (selectedIdentity >= 0) {
            extensions.add(QuicTlsExtension.create(QuicTlsExtensions.PRE_SHARED_KEY,
                                                   QuicTlsPreSharedKeys.encodeServerHello(selectedIdentity)));
        }
        return QuicTlsServerHelloMessage.create(
                LEGACY_TLS_VERSION,
                random,
                legacySessionId,
                cipherSuite.codePoint(),
                0,
                extensions);
    }

    private enum State {
        EXPECT_CLIENT_HELLO,
        EXPECT_SECOND_CLIENT_HELLO,
        EXPECT_CLIENT_CERTIFICATE,
        EXPECT_CLIENT_CERTIFICATE_VERIFY,
        EXPECT_CLIENT_FINISHED,
        COMPLETE
    }

    sealed interface Result permits HelloRetryRequestResult, ServerFlight {
    }

    record StartParameters(SSLContext sslContext,
                           SSLParameters sslParameters,
                           X509KeyManager keyManager,
                           X509TrustManager trustManager,
                           byte[] localTransportParameters,
                           SecureRandom secureRandom,
                           String peerHost,
                           int peerPort,
                           QuicTlsServerSessionCache serverSessionCache,
                           QuicAeadLimits.Confidentiality confidentialityLimits) {
    }

    record HelloRetryRequestResult(byte[] helloRetryRequest) implements Result {
        HelloRetryRequestResult {
            helloRetryRequest = helloRetryRequest.clone();
        }

        @Override
        public byte[] helloRetryRequest() {
            return helloRetryRequest.clone();
        }
    }

    record ServerFlight(byte[] serverHello,
                        byte[] encryptedExtensions,
                        byte[] certificateRequest,
                        byte[] certificate,
                        byte[] certificateVerify,
                        byte[] finished,
                        byte[] serverHelloTranscriptHash,
                        String applicationProtocol,
                        byte[] remoteTransportParameters,
                        X509Certificate[] localCertificates) implements Result {
        ServerFlight {
            serverHello = serverHello.clone();
            encryptedExtensions = encryptedExtensions.clone();
            certificateRequest = certificateRequest == null ? null : certificateRequest.clone();
            certificate = certificate == null ? null : certificate.clone();
            certificateVerify = certificateVerify == null ? null : certificateVerify.clone();
            finished = finished.clone();
            serverHelloTranscriptHash = serverHelloTranscriptHash.clone();
            applicationProtocol = Objects.requireNonNull(applicationProtocol, "applicationProtocol");
            remoteTransportParameters = remoteTransportParameters.clone();
            localCertificates = localCertificates == null ? null : localCertificates.clone();
        }

        @Override
        public byte[] serverHello() {
            return serverHello.clone();
        }

        @Override
        public byte[] encryptedExtensions() {
            return encryptedExtensions.clone();
        }

        @Override
        public byte[] certificateRequest() {
            return certificateRequest == null ? null : certificateRequest.clone();
        }

        @Override
        public byte[] certificate() {
            return certificate == null ? null : certificate.clone();
        }

        @Override
        public byte[] certificateVerify() {
            return certificateVerify == null ? null : certificateVerify.clone();
        }

        @Override
        public byte[] finished() {
            return finished.clone();
        }

        @Override
        public byte[] serverHelloTranscriptHash() {
            return serverHelloTranscriptHash.clone();
        }

        @Override
        public byte[] remoteTransportParameters() {
            return remoteTransportParameters.clone();
        }

        @Override
        public X509Certificate[] localCertificates() {
            return localCertificates == null ? null : localCertificates.clone();
        }
    }

    private record ServerResumptionSelection(int selectedIdentity, QuicTlsResumptionTicket resumptionTicket) {
        private ServerResumptionSelection {
            Objects.requireNonNull(resumptionTicket, "resumptionTicket");
        }
    }

    private record ServerCredentials(PrivateKey privateKey,
                                     X509Certificate[] certificateChain,
                                     QuicTlsSignatureScheme signatureScheme) {
        private ServerCredentials {
            Objects.requireNonNull(privateKey, "privateKey");
            certificateChain = Objects.requireNonNull(certificateChain, "certificateChain").clone();
            Objects.requireNonNull(signatureScheme, "signatureScheme");
        }

        @Override
        public X509Certificate[] certificateChain() {
            return certificateChain.clone();
        }
    }

    private record ClientSignatureSchemes(List<QuicTlsSignatureScheme> certificateVerifySchemes,
                                          List<QuicTlsSignatureScheme> certificateSignatureSchemes) {
        private ClientSignatureSchemes {
            certificateVerifySchemes = List.copyOf(certificateVerifySchemes);
            certificateSignatureSchemes = List.copyOf(certificateSignatureSchemes);
        }
    }

    private record ClientKeyShareSelection(QuicTlsKeyShareEntry keyShareEntry,
                                           QuicTlsNamedGroup helloRetryRequestGroup) {
        private ClientKeyShareSelection {
            if (keyShareEntry == null && helloRetryRequestGroup == null) {
                throw new IllegalArgumentException("Either a key share or a retry group is required");
            }
            if (keyShareEntry != null && helloRetryRequestGroup != null) {
                throw new IllegalArgumentException("A key share selection cannot also be a retry request");
            }
        }

        static ClientKeyShareSelection keyShare(QuicTlsKeyShareEntry keyShareEntry) {
            return new ClientKeyShareSelection(Objects.requireNonNull(keyShareEntry, "keyShareEntry"), null);
        }

        static ClientKeyShareSelection helloRetryRequest(QuicTlsNamedGroup selectedGroup) {
            return new ClientKeyShareSelection(null, Objects.requireNonNull(selectedGroup, "selectedGroup"));
        }

        boolean helloRetryRequest() {
            return helloRetryRequestGroup != null;
        }
    }

    private record HelloRetryRequestState(QuicTlsClientHelloMessage initialClientHello,
                                          QuicTls13CipherSuite cipherSuite,
                                          QuicTlsNamedGroup selectedGroup,
                                          byte[] cookie) {
        private HelloRetryRequestState {
            Objects.requireNonNull(initialClientHello, "initialClientHello");
            Objects.requireNonNull(cipherSuite, "cipherSuite");
            Objects.requireNonNull(selectedGroup, "selectedGroup");
            cookie = cookie == null ? null : cookie.clone();
        }

        @Override
        public byte[] cookie() {
            return cookie == null ? null : cookie.clone();
        }
    }
}
