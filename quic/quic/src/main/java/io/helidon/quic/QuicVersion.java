/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Api;

/**
 * Represents the QUIC versions defined in their corresponding RFCs.
 */
@Api.Incubating
public enum QuicVersion {
    // the version numbers are defined in their respective RFCs
    /**
     * QUIC version 1 as defined by RFC 9000.
     */
    QUIC_V1(1), // RFC-9000
    /**
     * QUIC version 2 as defined by RFC 9369.
     */
    QUIC_V2(0x6b3343cf); // RFC 9369

    // 32 bits unsigned integer representing the version as
    // defined in RFC. This is the version number as sent
    // in long headers packets (see RFC 9000).
    private final int versionNumber;

    QuicVersion(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    /**
     * Returns the {@code QuicVersion} corresponding to the {@code versionNumber}.
     *
     * @param versionNumber The version number
     * @return the {@code QuicVersion} corresponding to the {@code versionNumber}, or
     *         {@link Optional#empty() an empty Optional} if the {@code versionNumber}
     *         does not correspond to a QUIC version
     */
    public static Optional<QuicVersion> of(int versionNumber) {
        for (QuicVersion qv : QuicVersion.values()) {
            if (qv.versionNumber == versionNumber) {
                return Optional.of(qv);
            }
        }
        return Optional.empty();
    }

    /**
     * Selects the first {@code QuicVersion} in iteration order to be used in the first packet during
     * connection initiation.
     *
     * @param quicVersions the available QUIC versions
     * @return the QUIC version to use in the first packet
     * @throws NullPointerException     if {@code quicVersions} is null or any element
     *                                 in it is null
     * @throws IllegalArgumentException if {@code quicVersions} is empty
     */
    public static QuicVersion firstFlightVersion(Collection<QuicVersion> quicVersions) {
        Objects.requireNonNull(quicVersions);
        Iterator<QuicVersion> versions = quicVersions.iterator();
        if (!versions.hasNext()) {
            throw new IllegalArgumentException("Empty quic versions");
        }
        QuicVersion first = Objects.requireNonNull(versions.next(), "quicVersions contains null");
        while (versions.hasNext()) {
            Objects.requireNonNull(versions.next(), "quicVersions contains null");
        }
        return first;
    }

    /**
     * Returns the version number.
     *
     * @return the version number
     */
    public int versionNumber() {
        return this.versionNumber;
    }

    /**
     * Returns the canonical QUIC-version text.
     *
     * @return the canonical QUIC-version text
     */
    public String text() {
        return name();
    }
}
