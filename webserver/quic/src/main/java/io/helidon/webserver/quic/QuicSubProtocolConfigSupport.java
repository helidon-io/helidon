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

package io.helidon.webserver.quic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import java.util.Set;

import io.helidon.webserver.TransportBindingContext;
import io.helidon.webserver.quic.spi.QuicSubProtocolConfig;
import io.helidon.webserver.quic.spi.QuicSubProtocolProvider;
import io.helidon.webserver.quic.spi.QuicSubProtocolRuntime;

final class QuicSubProtocolConfigSupport {
    private QuicSubProtocolConfigSupport() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static List<ResolvedRuntime> createRuntimes(TransportBindingContext context,
                                                List<QuicSubProtocolConfig> protocols,
                                                List<QuicSubProtocolProvider> providers) {
        if (protocols.isEmpty()) {
            throw new UnsupportedOperationException("Listener " + context.listenerContext().config().name()
                                                            + " configures a QUIC binding, but no QUIC protocols are enabled");
        }

        List<ResolvedRuntime> result = new ArrayList<>(protocols.size());
        try {
            for (QuicSubProtocolConfig protocol : protocols) {
                List<QuicSubProtocolProvider> matchingProviders = providers.stream()
                        .filter(it -> it.configKey().equals(protocol.type()))
                        .filter(it -> it.protocolConfigType().isInstance(protocol))
                        .toList();

                if (matchingProviders.isEmpty()) {
                    throw new UnsupportedOperationException("No QUIC protocol provider is available for configured protocol "
                                                                    + protocolId(protocol));
                }
                if (matchingProviders.size() > 1) {
                    throw new UnsupportedOperationException("Multiple QUIC protocol providers match configured protocol "
                                                                    + protocolId(protocol) + ": " + matchingProviders);
                }
                QuicSubProtocolProvider provider = matchingProviders.getFirst();
                result.add(new ResolvedRuntime(protocol,
                                               (QuicSubProtocolRuntime) provider.create(context, protocol)));
            }
        } catch (RuntimeException | Error e) {
            for (int i = result.size() - 1; i >= 0; i--) {
                try {
                    result.get(i).runtime().close();
                } catch (RuntimeException | Error cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
            }
            throw e;
        }
        return List.copyOf(result);
    }

    static SequencedMap<String, ResolvedRuntime> resolveAlpns(String listenerName,
                                                             List<ResolvedRuntime> runtimes,
                                                             List<String> alpnPreference) {
        List<RuntimeAlpns> runtimeAlpns = new ArrayList<>(runtimes.size());
        SequencedMap<String, ResolvedRuntime> registered = new LinkedHashMap<>();
        for (ResolvedRuntime runtime : runtimes) {
            List<String> alpnIds = List.copyOf(runtime.runtime().alpnIds());
            if (alpnIds.isEmpty()) {
                throw new UnsupportedOperationException("Listener " + listenerName
                                                                + " resolved QUIC protocol "
                                                                + protocolId(runtime.config())
                                                                + " without any ALPN identifiers");
            }
            runtimeAlpns.add(new RuntimeAlpns(runtime, alpnIds));
            for (String alpnId : alpnIds) {
                ResolvedRuntime previous = registered.putIfAbsent(alpnId, runtime);
                if (previous != null) {
                    throw new UnsupportedOperationException("Listener " + listenerName
                                                                    + " resolved duplicate QUIC ALPN \""
                                                                    + alpnId + "\" for "
                                                                    + protocolId(previous.config()) + " and "
                                                                    + protocolId(runtime.config()));
                }
            }
        }
        List<String> configuredPreference = List.copyOf(alpnPreference);
        if (configuredPreference.isEmpty()) {
            runtimeAlpns.sort(Comparator.comparing(it -> it.alpnIds().getFirst()));
            SequencedMap<String, ResolvedRuntime> fallback = new LinkedHashMap<>();
            for (RuntimeAlpns runtime : runtimeAlpns) {
                for (String alpnId : runtime.alpnIds()) {
                    fallback.put(alpnId, runtime.runtime());
                }
            }
            return Collections.unmodifiableSequencedMap(fallback);
        }

        SequencedMap<String, ResolvedRuntime> preferred = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (String alpnId : configuredPreference) {
            if (!seen.add(alpnId)) {
                throw new UnsupportedOperationException("Listener " + listenerName
                                                                + " configures duplicate QUIC ALPN preference \""
                                                                + alpnId + "\"");
            }
            ResolvedRuntime runtime = registered.get(alpnId);
            if (runtime == null) {
                throw new UnsupportedOperationException("Listener " + listenerName
                                                                + " configures unknown QUIC ALPN preference \""
                                                                + alpnId + "\"; registered identifiers are "
                                                                + registered.sequencedKeySet());
            }
            preferred.put(alpnId, runtime);
        }
        if (preferred.size() != registered.size()) {
            List<String> missing = registered.sequencedKeySet()
                    .stream()
                    .filter(it -> !preferred.containsKey(it))
                    .toList();
            throw new UnsupportedOperationException("Listener " + listenerName
                                                            + " QUIC ALPN preference omits registered identifiers "
                                                            + missing);
        }
        return Collections.unmodifiableSequencedMap(preferred);
    }

    private static String protocolId(QuicSubProtocolConfig protocol) {
        return protocol.type() + "(" + protocol.name() + ")";
    }

    record ResolvedRuntime(QuicSubProtocolConfig config, QuicSubProtocolRuntime runtime) {
    }

    private record RuntimeAlpns(ResolvedRuntime runtime, List<String> alpnIds) {
    }
}
