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
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import io.helidon.common.buffers.BufferData;

final class QuicTls13ClientHandshake {
    // TLS 1.3 keeps the ClientHello legacy_version pinned to TLS 1.2 for wire compatibility, even though real
    // version negotiation happens in supported_versions.
    private static final int LEGACY_TLS_VERSION = 0x0303;
    // TLS 1.3 forbids negotiated compression but still carries the historical null-compression byte in ClientHello.
    private static final byte[] LEGACY_NULL_COMPRESSION = new byte[] {0};

    private final QuicVersion version;
    private final SecureRandom secureRandom;
    private final QuicTlsHandshakeTranscript transcript = new QuicTlsHandshakeTranscript();
    private final QuicTlsClientHelloMessage initialClientHello;
    private final List<QuicTlsNamedGroup> supportedGroups;
    private final List<Integer> supportedVersions;
    private final Set<Integer> offeredExtensionTypes;
    private final List<QuicTlsExtension> baseExtensions;
    private final QuicAeadLimits.Confidentiality confidentialityLimits;

    private QuicTlsClientHelloMessage currentClientHello;
    private byte[] currentClientHelloBytes;
    private QuicTlsLocalKeyShares currentKeyShares;
    private QuicTlsResumptionTicket currentResumptionTicket;
    private HelloRetryRequestState helloRetryRequest;
    private boolean complete;

    private QuicTls13ClientHandshake(QuicVersion version,
                                     QuicTlsClientHelloMessage initialClientHello,
                                     QuicTlsLocalKeyShares initialKeyShares,
                                     QuicTlsResumptionTicket resumptionTicket,
                                     SecureRandom secureRandom,
                                     QuicAeadLimits.Confidentiality confidentialityLimits) throws QuicTransportException {
        this.version = Objects.requireNonNull(version, "version");
        this.initialClientHello = Objects.requireNonNull(initialClientHello, "initialClientHello");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        this.supportedGroups = requireSupportedGroups(initialClientHello);
        this.supportedVersions = requireSupportedVersions(initialClientHello);
        this.offeredExtensionTypes = extensionTypes(initialClientHello.extensions());
        this.baseExtensions = baseExtensions(initialClientHello.extensions());
        this.currentClientHello = initialClientHello;
        this.currentClientHelloBytes = QuicTlsCodecSupport.copy(initialClientHello.encode());
        this.currentKeyShares = Objects.requireNonNull(initialKeyShares, "initialKeyShares");
        this.currentResumptionTicket = resumptionTicket;
        this.confidentialityLimits = Objects.requireNonNull(confidentialityLimits, "confidentialityLimits");

        if (initialClientHello.extension(QuicTlsExtensions.COOKIE).isPresent()) {
            throw new IllegalArgumentException("Initial ClientHello must not include a cookie extension");
        }
        validateInitialResumptionState(initialClientHello, resumptionTicket);
        validateInitialKeyShares(initialClientHello, supportedGroups, initialKeyShares);
        transcript.add(ByteBuffer.wrap(currentClientHelloBytes));
    }

    static QuicTls13ClientHandshake start(QuicVersion version,
                                          QuicTlsClientHelloMessage initialClientHello,
                                          QuicTlsLocalKeyShares initialKeyShares,
                                          SecureRandom secureRandom) throws QuicTransportException {
        return new QuicTls13ClientHandshake(version,
                                            initialClientHello,
                                            initialKeyShares,
                                            null,
                                            secureRandom,
                                            QuicAeadLimits.Confidentiality.defaults());
    }

    static QuicTls13ClientHandshake start(QuicVersion version,
                                          ClientHelloParameters parameters,
                                          SecureRandom secureRandom)
            throws QuicTransportException {
        return start(version,
                     parameters,
                     secureRandom,
                     QuicAeadLimits.Confidentiality.defaults());
    }

    static QuicTls13ClientHandshake start(QuicVersion version,
                                          ClientHelloParameters parameters,
                                          SecureRandom secureRandom,
                                          QuicAeadLimits.Confidentiality confidentialityLimits)
            throws QuicTransportException {
        ClientHelloParameters params = Objects.requireNonNull(parameters, "parameters");
        requireUnmanagedExtensions(params.additionalExtensions());
        requireResumptionCompatibility(params.cipherSuites(), params.additionalExtensions(), params.resumptionTicket());
        QuicTlsLocalKeyShares initialKeyShares =
                QuicTlsLocalKeyShares.create(params.initialKeyShareGroups(),
                                             Objects.requireNonNull(secureRandom, "secureRandom"));
        QuicTlsClientHelloMessage initialClientHello = createClientHello(
                new ClientHelloBuild(LEGACY_TLS_VERSION,
                                     params.random(),
                                     params.legacySessionId(),
                                     cipherSuiteCodePoints(params.cipherSuites()),
                                     LEGACY_NULL_COMPRESSION,
                                     params.additionalExtensions(),
                                     params.supportedGroups(),
                                     List.of(QuicTlsSupportedVersions.TLS_1_3),
                                     initialKeyShares.keyShareEntries(),
                                     null,
                                     params.resumptionTicket(),
                                     false,
                                     null,
                                     null));
        return new QuicTls13ClientHandshake(version,
                                            initialClientHello,
                                            initialKeyShares,
                                            params.resumptionTicket(),
                                            secureRandom,
                                            confidentialityLimits);
    }

    QuicTlsClientHelloMessage clientHelloMessage() {
        return currentClientHello;
    }

    ByteBuffer clientHello() {
        return ByteBuffer.wrap(currentClientHelloBytes).asReadOnlyBuffer();
    }

    Result consumeServerHello(ByteBuffer message) throws QuicTransportException {
        if (complete) {
            throw new IllegalStateException("TLS hello exchange already completed");
        }

        byte[] encodedMessage = QuicTlsCodecSupport.copy(Objects.requireNonNull(message, "message"));
        QuicTlsServerHelloMessage serverHello = QuicTlsServerHelloMessage.decode(ByteBuffer.wrap(encodedMessage));
        return serverHello.helloRetryRequest()
                ? processHelloRetryRequest(serverHello, encodedMessage)
                : processServerHello(serverHello, encodedMessage);
    }

    private static int requireSupportedVersion(QuicTlsServerHelloMessage serverHello, String messageName)
            throws QuicTransportException {
        return serverHello.supportedVersion()
                .orElseThrow(() -> QuicTlsHandshakeMessages.decodeError(
                        "Malformed " + messageName + " message: missing supported_versions extension"));
    }

    private static void requireLegacyServerHelloFields(QuicTlsServerHelloMessage serverHello, String messageName)
            throws QuicTransportException {
        if (serverHello.legacyVersion() != LEGACY_TLS_VERSION) {
            throw QuicTlsHandshakeMessages.illegalParameter(messageName + " legacy_version must be 0x0303");
        }
        if (serverHello.legacyCompressionMethod() != 0) {
            throw QuicTlsHandshakeMessages.illegalParameter(messageName + " legacy_compression_method must be 0");
        }
    }

    private static List<QuicTlsNamedGroup> requireSupportedGroups(QuicTlsClientHelloMessage clientHello)
            throws QuicTransportException {
        List<QuicTlsNamedGroup> groups = clientHello.supportedGroups();
        if (groups.isEmpty()) {
            throw new IllegalArgumentException("ClientHello must include at least one supported group");
        }
        return List.copyOf(groups);
    }

    private static List<Integer> requireSupportedVersions(QuicTlsClientHelloMessage clientHello)
            throws QuicTransportException {
        List<Integer> versions = clientHello.supportedVersions();
        if (!versions.contains(QuicTlsSupportedVersions.TLS_1_3)) {
            throw new IllegalArgumentException("ClientHello must offer TLS 1.3");
        }
        return List.copyOf(versions);
    }

    private static void validateInitialKeyShares(QuicTlsClientHelloMessage clientHello,
                                                 List<QuicTlsNamedGroup> supportedGroups,
                                                 QuicTlsLocalKeyShares initialKeyShares) throws QuicTransportException {
        List<QuicTlsKeyShareEntry> keyShares = clientHello.keyShares();
        if (keyShares.isEmpty()) {
            throw new IllegalArgumentException("ClientHello must include at least one key share");
        }

        List<QuicTlsNamedGroup> keyShareGroups = keyShares.stream()
                .map(QuicTlsKeyShareEntry::namedGroup)
                .toList();
        if (!keyShareGroups.equals(initialKeyShares.namedGroups())) {
            throw new IllegalArgumentException("ClientHello key shares do not match the local possessions");
        }
        if (!supportedGroups.containsAll(keyShareGroups)) {
            throw new IllegalArgumentException("ClientHello key shares must be a subset of supported_groups");
        }
    }

    private static Set<Integer> extensionTypes(List<QuicTlsExtension> extensions) {
        Set<Integer> types = new HashSet<>();
        for (QuicTlsExtension extension : extensions) {
            types.add(extension.type());
        }
        return Set.copyOf(types);
    }

    private static List<QuicTlsExtension> baseExtensions(List<QuicTlsExtension> extensions) {
        List<QuicTlsExtension> baseExtensions = new ArrayList<>(extensions.size());
        for (QuicTlsExtension extension : extensions) {
            switch (extension.type()) {
            case QuicTlsExtensions.SUPPORTED_GROUPS,
                 QuicTlsExtensions.KEY_SHARE,
                 QuicTlsExtensions.SUPPORTED_VERSIONS,
                 QuicTlsExtensions.COOKIE,
                 QuicTlsExtensions.EARLY_DATA,
                 QuicTlsExtensions.PRE_SHARED_KEY -> {
            }
            default -> baseExtensions.add(extension);
            }
        }
        return List.copyOf(baseExtensions);
    }

    private static void requireUnmanagedExtensions(List<QuicTlsExtension> additionalExtensions) {
        for (QuicTlsExtension extension : QuicTlsExtensions.copyOf(additionalExtensions)) {
            switch (extension.type()) {
            case QuicTlsExtensions.SUPPORTED_GROUPS,
                 QuicTlsExtensions.KEY_SHARE,
                 QuicTlsExtensions.SUPPORTED_VERSIONS,
                 QuicTlsExtensions.COOKIE,
                 QuicTlsExtensions.EARLY_DATA,
                 QuicTlsExtensions.PRE_SHARED_KEY -> throw new IllegalArgumentException(
                    String.format("Managed ClientHello extension must not be passed via additionalExtensions: 0x%04x",
                                  extension.type()));
            default -> {
            }
            }
        }
    }

    private static void requireResumptionCompatibility(List<QuicTls13CipherSuite> cipherSuites,
                                                       List<QuicTlsExtension> additionalExtensions,
                                                       QuicTlsResumptionTicket resumptionTicket) {
        if (resumptionTicket == null) {
            return;
        }
        if (QuicTlsExtensions.find(additionalExtensions, QuicTlsExtensions.PSK_KEY_EXCHANGE_MODES).isEmpty()) {
            throw new IllegalArgumentException("Resumption requires a psk_key_exchange_modes extension");
        }
        for (QuicTls13CipherSuite cipherSuite : cipherSuites) {
            if (resumptionTicket.cipherSuite().sameHash(cipherSuite)) {
                return;
            }
        }
        throw new IllegalArgumentException("Resumption ticket is incompatible with the offered cipher suites");
    }

    private static void validateInitialResumptionState(QuicTlsClientHelloMessage clientHello,
                                                       QuicTlsResumptionTicket resumptionTicket)
            throws QuicTransportException {
        boolean preSharedKeyPresent = clientHello.extension(QuicTlsExtensions.PRE_SHARED_KEY).isPresent();
        if (preSharedKeyPresent && clientHello.extensions().getLast().type() != QuicTlsExtensions.PRE_SHARED_KEY) {
            throw new IllegalArgumentException("ClientHello pre_shared_key must be the last extension");
        }
        if (clientHello.extension(QuicTlsExtensions.EARLY_DATA).isPresent() && !preSharedKeyPresent) {
            throw new IllegalArgumentException("ClientHello early_data requires pre_shared_key");
        }
        if (resumptionTicket == null && preSharedKeyPresent) {
            throw new IllegalArgumentException("ClientHello pre_shared_key requires resumption state");
        }
        if (resumptionTicket != null && !preSharedKeyPresent) {
            throw new IllegalArgumentException("Resumption state requires a ClientHello pre_shared_key extension");
        }
    }

    private static List<Integer> cipherSuiteCodePoints(List<QuicTls13CipherSuite> cipherSuites) {
        Objects.requireNonNull(cipherSuites, "cipherSuites");
        if (cipherSuites.isEmpty()) {
            throw new IllegalArgumentException("At least one cipher suite is required");
        }

        List<Integer> codePoints = new ArrayList<>(cipherSuites.size());
        for (QuicTls13CipherSuite cipherSuite : cipherSuites) {
            codePoints.add(Objects.requireNonNull(cipherSuite, "cipherSuite").codePoint());
        }
        return List.copyOf(codePoints);
    }

    private static QuicTlsClientHelloMessage createClientHello(ClientHelloBuild build) throws QuicTransportException {
        int legacyVersion = build.legacyVersion();
        byte[] random = build.random();
        byte[] legacySessionId = build.legacySessionId();
        List<Integer> cipherSuites = build.cipherSuites();
        byte[] legacyCompressionMethods = build.legacyCompressionMethods();
        List<QuicTlsExtension> additionalExtensions = build.additionalExtensions();
        List<QuicTlsNamedGroup> supportedGroups = build.supportedGroups();
        List<Integer> supportedVersions = build.supportedVersions();
        List<QuicTlsKeyShareEntry> keyShares = build.keyShares();
        byte[] cookie = build.cookie();
        QuicTlsResumptionTicket resumptionTicket = build.resumptionTicket();
        boolean includeEarlyData = build.includeEarlyData();
        byte[] previousClientHello = build.previousClientHello();
        byte[] helloRetryRequest = build.helloRetryRequest();
        QuicTlsPreSharedKeys.OfferedPsks placeholderPsks = null;
        QuicTlsPreSharedKeys.PskIdentity pskIdentity = null;
        if (resumptionTicket != null) {
            pskIdentity = new QuicTlsPreSharedKeys.PskIdentity(resumptionTicket.ticket(),
                                                               obfuscatedTicketAge(resumptionTicket,
                                                                                   System.currentTimeMillis()));
            placeholderPsks = new QuicTlsPreSharedKeys.OfferedPsks(List.of(pskIdentity),
                                                                   List.of(new byte[resumptionTicket.cipherSuite()
                                                                           .hashLength()]));
        }

        List<QuicTlsExtension> extensions = createClientHelloExtensions(additionalExtensions,
                                                                        supportedGroups,
                                                                        supportedVersions,
                                                                        keyShares,
                                                                        cookie,
                                                                        includeEarlyData,
                                                                        placeholderPsks);
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.create(legacyVersion,
                                                                                 random,
                                                                                 legacySessionId,
                                                                                 cipherSuites,
                                                                                 legacyCompressionMethods,
                                                                                 extensions);
        if (resumptionTicket == null) {
            return clientHello;
        }

        byte[] encodedClientHello = QuicTlsCodecSupport.copy(clientHello.encode());
        byte[] binder = QuicTlsPreSharedKeys.computeBinder(resumptionTicket,
                                                           previousClientHello,
                                                           helloRetryRequest,
                                                           encodedClientHello);
        return QuicTlsClientHelloMessage.create(legacyVersion,
                                                random,
                                                legacySessionId,
                                                cipherSuites,
                                                legacyCompressionMethods,
                                                createClientHelloExtensions(additionalExtensions,
                                                                            supportedGroups,
                                                                            supportedVersions,
                                                                            keyShares,
                                                                            cookie,
                                                                            includeEarlyData,
                                                                            new QuicTlsPreSharedKeys.OfferedPsks(
                                                                                    List.of(pskIdentity),
                                                                                    List.of(binder))));
    }

    private static List<QuicTlsExtension> createClientHelloExtensions(List<QuicTlsExtension> additionalExtensions,
                                                                      List<QuicTlsNamedGroup> supportedGroups,
                                                                      List<Integer> supportedVersions,
                                                                      List<QuicTlsKeyShareEntry> keyShares,
                                                                      byte[] cookie,
                                                                      boolean includeEarlyData,
                                                                      QuicTlsPreSharedKeys.OfferedPsks offeredPsks) {
        List<QuicTlsExtension> extensions = new ArrayList<>(additionalExtensions.size() + 3
                                                                    + (cookie == null ? 0 : 1)
                                                                    + (includeEarlyData ? 1 : 0)
                                                                    + (offeredPsks == null ? 0 : 1));
        extensions.addAll(QuicTlsExtensions.copyOf(additionalExtensions));
        extensions.add(QuicTlsExtension.create(QuicTlsExtensions.SUPPORTED_GROUPS,
                                               QuicTlsSupportedGroups.encode(supportedGroups)));
        extensions.add(QuicTlsExtension.create(QuicTlsExtensions.KEY_SHARE,
                                               QuicTlsKeyShares.encodeClientHello(keyShares)));
        extensions.add(QuicTlsExtension.create(QuicTlsExtensions.SUPPORTED_VERSIONS,
                                               QuicTlsSupportedVersions.encodeClientHello(supportedVersions)));
        if (cookie != null) {
            extensions.add(QuicTlsExtension.create(QuicTlsExtensions.COOKIE, QuicTlsCookie.encode(cookie)));
        }
        if (includeEarlyData) {
            extensions.add(QuicTlsExtension.create(QuicTlsExtensions.EARLY_DATA, BufferData.EMPTY_BYTES));
        }
        if (offeredPsks != null) {
            extensions.add(QuicTlsExtension.create(QuicTlsExtensions.PRE_SHARED_KEY,
                                                   QuicTlsPreSharedKeys.encodeClientHello(offeredPsks)));
        }
        return List.copyOf(extensions);
    }

    private static long obfuscatedTicketAge(QuicTlsResumptionTicket resumptionTicket, long nowMillis) {
        long ageMillis = Math.max(0L, nowMillis - resumptionTicket.issuedAtMillis());
        return (ageMillis + resumptionTicket.ticketAgeAdd()) & 0xFFFF_FFFFL;
    }

    private HelloRetryRequestResult processHelloRetryRequest(QuicTlsServerHelloMessage helloRetryRequest,
                                                             byte[] encodedHelloRetryRequest)
            throws QuicTransportException {
        HelloRetryRequestState retryState = validateHelloRetryRequest(helloRetryRequest);
        QuicTlsLocalKeyShares retryKeyShares = QuicTlsLocalKeyShares.create(List.of(retryState.selectedGroup()),
                                                                            secureRandom);
        QuicTlsResumptionTicket retryResumptionTicket = retryResumptionTicket(retryState.cipherSuite());
        QuicTlsClientHelloMessage retryClientHello =
                createRetryClientHello(retryKeyShares, retryState.cookie(), retryResumptionTicket, encodedHelloRetryRequest);
        byte[] encodedRetryClientHello = QuicTlsCodecSupport.copy(retryClientHello.encode());
        if (Arrays.equals(encodedRetryClientHello, currentClientHelloBytes)) {
            throw QuicTlsHandshakeMessages.illegalParameter("HelloRetryRequest did not change the ClientHello");
        }

        transcript.add(ByteBuffer.wrap(encodedHelloRetryRequest));
        transcript.add(ByteBuffer.wrap(encodedRetryClientHello));
        this.currentClientHello = retryClientHello;
        this.currentClientHelloBytes = encodedRetryClientHello;
        this.currentKeyShares = retryKeyShares;
        this.currentResumptionTicket = retryResumptionTicket;
        this.helloRetryRequest = retryState;
        return new HelloRetryRequestResult(helloRetryRequest, retryClientHello);
    }

    private CompleteResult processServerHello(QuicTlsServerHelloMessage serverHello, byte[] encodedServerHello)
            throws QuicTransportException {
        validateServerHello(serverHello);
        transcript.add(ByteBuffer.wrap(encodedServerHello));

        QuicTls13ConnectionSecrets connectionSecrets = connectionSecrets(serverHello);
        byte[] serverHelloTranscriptHash = transcript.hash(connectionSecrets.cipherSuite());
        boolean preSharedKeySelected = serverHello.selectedIdentity().isPresent();
        complete = true;
        return new CompleteResult(serverHello, connectionSecrets, preSharedKeySelected, serverHelloTranscriptHash);
    }

    private HelloRetryRequestState validateHelloRetryRequest(QuicTlsServerHelloMessage helloRetryRequest)
            throws QuicTransportException {
        if (this.helloRetryRequest != null) {
            throw QuicTlsHandshakeMessages.illegalParameter("Received second HelloRetryRequest");
        }

        requireLegacyServerHelloFields(helloRetryRequest, "HelloRetryRequest");
        int selectedVersion = requireSupportedVersion(helloRetryRequest, "HelloRetryRequest");
        if (selectedVersion != QuicTlsSupportedVersions.TLS_1_3 || !supportedVersions.contains(selectedVersion)) {
            throw QuicTlsHandshakeMessages.illegalParameter("HelloRetryRequest selected an unsupported TLS version");
        }
        if (!initialClientHello.cipherSuites().contains(helloRetryRequest.cipherSuite())) {
            throw QuicTlsHandshakeMessages.illegalParameter("HelloRetryRequest selected an unsupported cipher suite");
        }
        if (!Arrays.equals(helloRetryRequest.legacySessionIdEcho(), initialClientHello.legacySessionId())) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "HelloRetryRequest legacy_session_id_echo does not match the ClientHello");
        }

        validateHelloRetryRequestExtensions(helloRetryRequest);
        QuicTlsNamedGroup selectedGroup = helloRetryRequest.helloRetryRequestSelectedGroup()
                .orElseThrow(() -> QuicTlsHandshakeMessages.decodeError(
                        "Malformed HelloRetryRequest message: missing key_share extension"));
        if (!supportedGroups.contains(selectedGroup)) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "HelloRetryRequest selected a group outside the ClientHello supported_groups extension");
        }
        if (currentKeyShares.namedGroups().contains(selectedGroup)) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "HelloRetryRequest selected a group already present in the ClientHello key_share extension");
        }

        Optional<QuicTlsExtension> cookieExtension = helloRetryRequest.extension(QuicTlsExtensions.COOKIE);
        byte[] cookie = cookieExtension.isPresent() ? QuicTlsCookie.decode(cookieExtension.get().dataBuffer()) : null;
        return new HelloRetryRequestState(helloRetryRequest.cipherSuite(), selectedVersion, selectedGroup, cookie);
    }

    private void validateServerHello(QuicTlsServerHelloMessage serverHello) throws QuicTransportException {
        requireLegacyServerHelloFields(serverHello, "ServerHello");
        int selectedVersion = requireSupportedVersion(serverHello, "ServerHello");
        if (helloRetryRequest != null && selectedVersion != helloRetryRequest.supportedVersion()) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "ServerHello supported version does not match the HelloRetryRequest");
        }
        if (selectedVersion != QuicTlsSupportedVersions.TLS_1_3 || !supportedVersions.contains(selectedVersion)) {
            throw QuicTlsHandshakeMessages.illegalParameter("ServerHello selected an unsupported TLS version");
        }
        QuicTlsExtensions.validateServerResponse(serverHello.extensions(),
                                                currentClientHello,
                                                QuicTlsHandshakeMessages.SERVER_HELLO);
        if (helloRetryRequest != null && serverHello.cipherSuite() != helloRetryRequest.cipherSuite()) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "ServerHello cipher suite does not match the HelloRetryRequest");
        }
        if (!initialClientHello.cipherSuites().contains(serverHello.cipherSuite())) {
            throw QuicTlsHandshakeMessages.illegalParameter("ServerHello selected an unsupported cipher suite");
        }
        if (!Arrays.equals(serverHello.legacySessionIdEcho(), initialClientHello.legacySessionId())) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "ServerHello legacy_session_id_echo does not match the ClientHello");
        }

        QuicTlsKeyShareEntry peerKeyShare = serverHello.keyShare()
                .orElseThrow(() -> QuicTlsHandshakeMessages.decodeError(
                        "Malformed ServerHello message: missing key_share extension"));
        if (helloRetryRequest != null && peerKeyShare.namedGroup() != helloRetryRequest.selectedGroup()) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "ServerHello key_share group does not match the HelloRetryRequest selected group");
        }
        if (!currentKeyShares.namedGroups().contains(peerKeyShare.namedGroup())) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "ServerHello key_share group was not offered in the current ClientHello");
        }
    }

    private void validateHelloRetryRequestExtensions(QuicTlsServerHelloMessage helloRetryRequest)
            throws QuicTransportException {
        for (QuicTlsExtension extension : helloRetryRequest.extensions()) {
            int type = extension.type();
            switch (type) {
            case QuicTlsExtensions.SUPPORTED_VERSIONS, QuicTlsExtensions.KEY_SHARE -> {
                if (!offeredExtensionTypes.contains(type)) {
                    throw QuicTlsHandshakeMessages.illegalParameter(
                            String.format("HelloRetryRequest extension 0x%04x was not offered by the client", type));
                }
            }
            case QuicTlsExtensions.COOKIE -> {
                // cookie is the only extension that may be added by HelloRetryRequest even if it was absent initially.
            }
            default -> throw QuicTlsHandshakeMessages.illegalParameter(
                    String.format("HelloRetryRequest included unsupported extension 0x%04x", type));
            }
        }
    }

    private QuicTlsClientHelloMessage createRetryClientHello(QuicTlsLocalKeyShares retryKeyShares,
                                                             byte[] cookie,
                                                             QuicTlsResumptionTicket resumptionTicket,
                                                             byte[] encodedHelloRetryRequest)
            throws QuicTransportException {
        return createClientHello(new ClientHelloBuild(initialClientHello.legacyVersion(),
                                                      initialClientHello.random(),
                                                      initialClientHello.legacySessionId(),
                                                      initialClientHello.cipherSuites(),
                                                      initialClientHello.legacyCompressionMethods(),
                                                      baseExtensions,
                                                      supportedGroups,
                                                      supportedVersions,
                                                      retryKeyShares.keyShareEntries(),
                                                      cookie,
                                                      resumptionTicket,
                                                      false,
                                                      currentClientHelloBytes,
                                                      encodedHelloRetryRequest));
    }

    private QuicTls13ConnectionSecrets connectionSecrets(QuicTlsServerHelloMessage serverHello)
            throws QuicTransportException {
        OptionalInt selectedIdentity = serverHello.selectedIdentity();
        if (selectedIdentity.isEmpty()) {
            return QuicTls13ConnectionSecrets.forServerHello(version,
                                                             currentKeyShares,
                                                             serverHello,
                                                             true,
                                                             confidentialityLimits.aesGcm(),
                                                             confidentialityLimits.chacha20Poly1305());
        }

        QuicTlsResumptionTicket resumptionTicket = currentResumptionTicket;
        if (resumptionTicket == null) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "ServerHello selected pre_shared_key without a matching ClientHello offer");
        }
        if (selectedIdentity.getAsInt() != 0) {
            throw QuicTlsHandshakeMessages.illegalParameter("ServerHello selected an out-of-range pre_shared_key identity");
        }

        QuicTls13CipherSuite selectedCipherSuite;
        try {
            selectedCipherSuite = QuicTls13CipherSuite.forCodePoint(serverHello.cipherSuite());
        } catch (IllegalArgumentException e) {
            throw QuicTlsHandshakeMessages.decodeError(
                    String.format("Unsupported TLS 1.3 cipher suite: 0x%04x", serverHello.cipherSuite()));
        }
        if (!resumptionTicket.cipherSuite().sameHash(selectedCipherSuite)) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "ServerHello selected a cipher suite incompatible with the offered pre_shared_key");
        }
        return QuicTls13ConnectionSecrets.forServerHello(version,
                                                         currentKeyShares,
                                                         serverHello,
                                                         resumptionTicket.resumptionPsk(),
                                                         true,
                                                         confidentialityLimits);
    }

    private QuicTlsResumptionTicket retryResumptionTicket(int selectedCipherSuiteCodePoint) {
        QuicTlsResumptionTicket resumptionTicket = currentResumptionTicket;
        if (resumptionTicket == null) {
            return null;
        }
        try {
            return resumptionTicket.cipherSuite().sameHash(QuicTls13CipherSuite.forCodePoint(selectedCipherSuiteCodePoint))
                    ? resumptionTicket
                    : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    sealed interface Result permits HelloRetryRequestResult, CompleteResult {
    }

    record ClientHelloParameters(byte[] random,
                                 byte[] legacySessionId,
                                 List<QuicTls13CipherSuite> cipherSuites,
                                 List<QuicTlsNamedGroup> supportedGroups,
                                 List<QuicTlsNamedGroup> initialKeyShareGroups,
                                 List<QuicTlsExtension> additionalExtensions,
                                 QuicTlsResumptionTicket resumptionTicket) {
    }

    record HelloRetryRequestResult(QuicTlsServerHelloMessage helloRetryRequest,
                                   QuicTlsClientHelloMessage clientHelloMessage) implements Result {
        HelloRetryRequestResult {
            Objects.requireNonNull(helloRetryRequest, "helloRetryRequest");
            Objects.requireNonNull(clientHelloMessage, "clientHelloMessage");
        }

        ByteBuffer clientHello() {
            return clientHelloMessage.encode();
        }
    }

    record CompleteResult(QuicTlsServerHelloMessage serverHello,
                          QuicTls13ConnectionSecrets connectionSecrets,
                          boolean preSharedKeySelected,
                          byte[] serverHelloTranscriptHash) implements Result {
        CompleteResult {
            Objects.requireNonNull(serverHello, "serverHello");
            Objects.requireNonNull(connectionSecrets, "connectionSecrets");
            serverHelloTranscriptHash = Objects.requireNonNull(serverHelloTranscriptHash, "serverHelloTranscriptHash")
                    .clone();
        }

        @Override
        public byte[] serverHelloTranscriptHash() {
            return serverHelloTranscriptHash.clone();
        }

        QuicLongHeaderTrafficKeys handshakeTrafficKeys() {
            return connectionSecrets.deriveHandshakeTrafficKeys(serverHelloTranscriptHash);
        }
    }

    private record ClientHelloBuild(int legacyVersion,
                                    byte[] random,
                                    byte[] legacySessionId,
                                    List<Integer> cipherSuites,
                                    byte[] legacyCompressionMethods,
                                    List<QuicTlsExtension> additionalExtensions,
                                    List<QuicTlsNamedGroup> supportedGroups,
                                    List<Integer> supportedVersions,
                                    List<QuicTlsKeyShareEntry> keyShares,
                                    byte[] cookie,
                                    QuicTlsResumptionTicket resumptionTicket,
                                    boolean includeEarlyData,
                                    byte[] previousClientHello,
                                    byte[] helloRetryRequest) {
    }

    private record HelloRetryRequestState(int cipherSuite,
                                          int supportedVersion,
                                          QuicTlsNamedGroup selectedGroup,
                                          byte[] cookie) {
        private HelloRetryRequestState {
            Objects.requireNonNull(selectedGroup, "selectedGroup");
            cookie = cookie == null ? null : cookie.clone();
        }

        @Override
        public byte[] cookie() {
            return cookie == null ? null : cookie.clone();
        }
    }
}
