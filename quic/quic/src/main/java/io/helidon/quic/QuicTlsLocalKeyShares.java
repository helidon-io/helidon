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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class QuicTlsLocalKeyShares {
    private final Map<QuicTlsNamedGroup, QuicTlsKeySharePossession> possessions;
    private final List<QuicTlsKeyShareEntry> keyShareEntries;

    private QuicTlsLocalKeyShares(Map<QuicTlsNamedGroup, QuicTlsKeySharePossession> possessions) {
        this.possessions = Collections.unmodifiableMap(new LinkedHashMap<>(possessions));
        List<QuicTlsKeyShareEntry> entries = new ArrayList<>(possessions.size());
        for (QuicTlsKeySharePossession possession : possessions.values()) {
            entries.add(possession.keyShareEntry());
        }
        this.keyShareEntries = List.copyOf(entries);
    }

    static QuicTlsLocalKeyShares create(List<QuicTlsNamedGroup> namedGroups, SecureRandom secureRandom) {
        Objects.requireNonNull(namedGroups, "namedGroups");
        Objects.requireNonNull(secureRandom, "secureRandom");
        if (namedGroups.isEmpty()) {
            throw new IllegalArgumentException("At least one TLS named group is required");
        }

        Map<QuicTlsNamedGroup, QuicTlsKeySharePossession> possessions = new LinkedHashMap<>();
        for (QuicTlsNamedGroup namedGroup : namedGroups) {
            QuicTlsNamedGroup group = Objects.requireNonNull(namedGroup, "namedGroup");
            if (possessions.putIfAbsent(group, QuicTlsKeySharePossession.create(group, secureRandom)) != null) {
                throw new IllegalArgumentException("Duplicate TLS named group: " + group.tlsName());
            }
        }
        return new QuicTlsLocalKeyShares(possessions);
    }

    List<QuicTlsNamedGroup> namedGroups() {
        return List.copyOf(possessions.keySet());
    }

    List<QuicTlsKeyShareEntry> keyShareEntries() {
        return keyShareEntries;
    }

    QuicTlsKeyShareEntry keyShareEntry(QuicTlsNamedGroup namedGroup) {
        return possession(namedGroup).keyShareEntry();
    }

    byte[] sharedSecret(QuicTlsKeyShareEntry peerKeyShare) {
        QuicTlsKeyShareEntry normalized = Objects.requireNonNull(peerKeyShare, "peerKeyShare");
        QuicTlsKeySharePossession possession = possessions.get(normalized.namedGroup());
        if (possession == null) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "Peer selected an unoffered TLS named group: " + normalized.namedGroup().tlsName());
        }
        return possession.sharedSecret(normalized);
    }

    private QuicTlsKeySharePossession possession(QuicTlsNamedGroup namedGroup) {
        QuicTlsKeySharePossession possession = possessions.get(Objects.requireNonNull(namedGroup, "namedGroup"));
        if (possession == null) {
            throw new IllegalArgumentException("No local TLS key share for named group: " + namedGroup.tlsName());
        }
        return possession;
    }
}
