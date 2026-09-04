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

import java.util.Objects;
import java.util.Optional;

final class QuicTlsResumptionTicket {
    private static final long MAX_TICKET_LIFETIME_SECONDS = 604_800L;

    private final QuicVersion quicVersion;
    private final QuicTls13CipherSuite cipherSuite;
    private final long ticketLifetimeSeconds;
    private final long ticketAgeAdd;
    private final byte[] ticketNonce;
    private final byte[] ticket;
    private final byte[] resumptionPsk;
    private final String applicationProtocol;
    private final String serverName;
    private final byte[] remoteTransportParameters;
    private final long issuedAtMillis;

    QuicTlsResumptionTicket(QuicVersion quicVersion,
                            QuicTls13CipherSuite cipherSuite,
                            long ticketLifetimeSeconds,
                            long ticketAgeAdd,
                            byte[] ticketNonce,
                            byte[] ticket,
                            byte[] resumptionPsk,
                            String applicationProtocol,
                            byte[] remoteTransportParameters,
                            long issuedAtMillis) {
        this(quicVersion,
             cipherSuite,
             ticketLifetimeSeconds,
             ticketAgeAdd,
             ticketNonce,
             ticket,
             resumptionPsk,
             applicationProtocol,
             null,
             remoteTransportParameters,
             issuedAtMillis);
    }

    QuicTlsResumptionTicket(QuicVersion quicVersion,
                            QuicTls13CipherSuite cipherSuite,
                            long ticketLifetimeSeconds,
                            long ticketAgeAdd,
                            byte[] ticketNonce,
                            byte[] ticket,
                            byte[] resumptionPsk,
                            String applicationProtocol,
                            String serverName,
                            byte[] remoteTransportParameters,
                            long issuedAtMillis) {
        this.quicVersion = Objects.requireNonNull(quicVersion, "quicVersion");
        this.cipherSuite = Objects.requireNonNull(cipherSuite, "cipherSuite");
        this.ticketLifetimeSeconds = ticketLifetimeSeconds;
        this.ticketAgeAdd = ticketAgeAdd;
        this.ticketNonce = Objects.requireNonNull(ticketNonce, "ticketNonce").clone();
        this.ticket = Objects.requireNonNull(ticket, "ticket").clone();
        this.resumptionPsk = Objects.requireNonNull(resumptionPsk, "resumptionPsk").clone();
        this.applicationProtocol = applicationProtocol;
        this.serverName = serverName;
        this.remoteTransportParameters = remoteTransportParameters == null ? null : remoteTransportParameters.clone();
        this.issuedAtMillis = issuedAtMillis;
    }

    QuicVersion quicVersion() {
        return quicVersion;
    }

    QuicTls13CipherSuite cipherSuite() {
        return cipherSuite;
    }

    long ticketLifetimeSeconds() {
        return ticketLifetimeSeconds;
    }

    long ticketAgeAdd() {
        return ticketAgeAdd;
    }

    byte[] ticketNonce() {
        return ticketNonce.clone();
    }

    byte[] ticket() {
        return ticket.clone();
    }

    byte[] resumptionPsk() {
        return resumptionPsk.clone();
    }

    Optional<String> applicationProtocol() {
        return Optional.ofNullable(applicationProtocol);
    }

    Optional<String> serverName() {
        return Optional.ofNullable(serverName);
    }

    Optional<byte[]> remoteTransportParameters() {
        return Optional.ofNullable(remoteTransportParameters == null ? null : remoteTransportParameters.clone());
    }

    long issuedAtMillis() {
        return issuedAtMillis;
    }

    boolean transportCompatible(QuicVersion version) {
        return quicVersion == version;
    }

    boolean expired(long nowMillis) {
        return expired(nowMillis, Long.MAX_VALUE);
    }

    boolean expired(long nowMillis, long cacheTimeoutMillis) {
        long cappedLifetimeSeconds = Math.min(ticketLifetimeSeconds, MAX_TICKET_LIFETIME_SECONDS);
        if (cappedLifetimeSeconds <= 0) {
            return true;
        }
        long lifetimeMillis = Math.min(cappedLifetimeSeconds * 1000L, cacheTimeoutMillis);
        return nowMillis - issuedAtMillis >= lifetimeMillis;
    }
}
