/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.discovery;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;

import io.helidon.common.Weight;
import io.helidon.service.registry.Service.Singleton;

import static java.util.Collections.unmodifiableSequencedSet;
import static java.util.Objects.requireNonNull;

@Singleton
@Weight(Double.MIN_VALUE)
final class NoopDiscovery implements Discovery {

    NoopDiscovery() {
        super();
    }

    @Override // Discovery
    public SequencedSet<DiscoveredUri> uris(String name, URI defaultValue) {
        requireNonNull(name, "name");
        return unmodifiableSequencedSet(new LinkedHashSet<>(List.of(new DefaultUri(defaultValue))));
    }

    private record DefaultUri(URI uri) implements DiscoveredUri {

        private DefaultUri {
            requireNonNull(uri, "uri");
        }

        @Override // DiscoveredUri
        public Map<String, String> metadata() {
            return Map.of();
        }

        @Override // Record
        public String toString() {
            return this.uri().toString();
        }

    }

}
