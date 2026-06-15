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

package io.helidon.webserver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsMaterial;
import io.helidon.common.uri.UriAuthority;
import io.helidon.common.uri.UriHost;

/**
 * Listener-local registry that maps normalized SNI host names to virtual-host TLS configuration.
 * <p>
 * The registry is deliberately about TLS selection only. It validates virtual-host TLS configuration at startup, chooses
 * the TLS material for a connection before request routing, and creates the SNI context later used to enforce HTTP
 * authority policy.
 */
final class VirtualHostRegistry implements ListenerTlsContext {
    private final String socketName;
    private final Tls defaultTls;
    private final SniConfig sniConfig;
    private final Map<String, HostEntry> exactHosts;
    private final Map<String, HostEntry> wildcardHosts;
    private final boolean enabled;

    private VirtualHostRegistry(String socketName,
                                Tls defaultTls,
                                SniConfig sniConfig,
                                Map<String, HostEntry> exactHosts,
                                Map<String, HostEntry> wildcardHosts) {
        this.socketName = socketName;
        this.defaultTls = defaultTls;
        this.sniConfig = sniConfig;
        this.exactHosts = Map.copyOf(exactHosts);
        this.wildcardHosts = Map.copyOf(wildcardHosts);
        this.enabled = !exactHosts.isEmpty() || !wildcardHosts.isEmpty();
    }

    static VirtualHostRegistry create(String socketName, ListenerConfig listenerConfig, Tls defaultTls) {
        SniConfig sniConfig = listenerConfig.sni();
        List<VirtualHostConfig> virtualHosts = listenerConfig.virtualHosts();

        if (virtualHosts.isEmpty()) {
            if (sniConfig.missing() != SniSelectionPolicy.FALLBACK
                    || sniConfig.unmatched() != SniSelectionPolicy.FALLBACK) {
                throw new IllegalArgumentException("Listener " + socketName
                                                           + " cannot reject missing or unmatched SNI without virtual hosts");
            }
            return new VirtualHostRegistry(socketName, defaultTls, sniConfig, Map.of(), Map.of());
        }

        if (!defaultTls.enabled()) {
            throw new IllegalArgumentException("Listener " + socketName + " virtual hosts require listener TLS");
        }
        if (!listenerConfig.useNio()) {
            throw new IllegalArgumentException("Listener " + socketName
                                                       + " virtual hosts require NIO TLS; use-nio must be true");
        }

        Map<String, HostEntry> exact = new HashMap<>();
        Map<String, HostEntry> wildcard = new HashMap<>();
        for (VirtualHostConfig virtualHost : virtualHosts) {
            Tls tls = virtualHost.tls();
            if (!tls.enabled()) {
                throw new IllegalArgumentException("Virtual host " + virtualHost.host()
                                                           + " on listener " + socketName + " requires enabled TLS");
            }
            HostEntry entry = HostEntry.create(virtualHost.host(), tls);
            Map<String, HostEntry> target = entry.wildcard() ? wildcard : exact;
            HostEntry previous = target.putIfAbsent(entry.indexKey(), entry);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate virtual host " + entry.configuredHost()
                                                           + " on listener " + socketName);
            }
        }
        return new VirtualHostRegistry(socketName, defaultTls, sniConfig, exact, wildcard);
    }

    @Override
    public Tls tls() {
        return defaultTls;
    }

    @Override
    public boolean virtualHostsEnabled() {
        return enabled;
    }

    @Override
    public void validateVirtualHosts() {
        for (HostEntry entry : exactHosts.values()) {
            entry.tls().newEngine();
        }
        for (HostEntry entry : wildcardHosts.values()) {
            entry.tls().newEngine();
        }
    }

    void validateConfiguredHost(String configuredHost) {
        hostEntry(configuredHost);
    }

    void reloadTls(TlsMaterial material, String configuredHost) {
        Objects.requireNonNull(material, "material");
        hostEntry(configuredHost).tls().reload(material);
    }

    private HostEntry hostEntry(String configuredHost) {
        HostEntry.HostKey key = HostEntry.key(configuredHost);
        Map<String, HostEntry> target = key.wildcard() ? wildcardHosts : exactHosts;
        HostEntry entry = target.get(key.indexKey());
        if (entry == null) {
            throw new IllegalArgumentException("Virtual host " + key.configuredHost()
                                                       + " is not configured on listener " + socketName);
        }
        return entry;
    }

    @Override
    public ListenerTlsContext.Selection select(String presentedHost) {
        String host = Objects.requireNonNull(presentedHost);
        HostEntry entry = match(host);
        if (entry != null) {
            SniMatchType matchType = entry.wildcard() ? SniMatchType.WILDCARD : SniMatchType.EXACT;
            return ListenerTlsContext.Selection.create(entry.tls(),
                                                       new Context(Optional.of(host), Optional.of(entry.configuredHost()),
                                                                   matchType));
        }

        if (sniConfig.unmatched() == SniSelectionPolicy.REJECT) {
            throw new ListenerTlsContext.RejectedSniException("Unmatched SNI " + host + " rejected by listener "
                                                                      + socketName, true);
        }
        return fallback(Optional.of(host), SniMatchType.FALLBACK_UNMATCHED);
    }

    @Override
    public ListenerTlsContext.Selection selectWithoutSni() {
        if (sniConfig.missing() == SniSelectionPolicy.REJECT) {
            throw new ListenerTlsContext.RejectedSniException("Missing SNI rejected by listener " + socketName, false);
        }
        return fallback(Optional.empty(), SniMatchType.FALLBACK_MISSING);
    }

    private ListenerTlsContext.Selection fallback(Optional<String> presentedHost, SniMatchType matchType) {
        return ListenerTlsContext.Selection.create(defaultTls, new Context(presentedHost, Optional.empty(), matchType));
    }

    private HostEntry match(String host) {
        HostEntry exact = exactHosts.get(host);
        if (exact != null) {
            return exact;
        }

        int dot = host.indexOf('.');
        if (dot == -1 || dot == host.length() - 1) {
            return null;
        }
        String suffix = host.substring(dot + 1);
        return wildcardHosts.get(suffix);
    }

    private boolean matchesConfiguredHost(String host) {
        return match(host) != null;
    }

    private record HostEntry(String configuredHost, String indexKey, boolean wildcard, Tls tls) {
        private static HostEntry create(String configuredHost, Tls tls) {
            HostKey key = key(configuredHost);
            return new HostEntry(key.configuredHost(), key.indexKey(), key.wildcard(), tls);
        }

        private static HostKey key(String configuredHost) {
            Objects.requireNonNull(configuredHost, "configuredHost");
            if (configuredHost.startsWith("*.")) {
                return wildcard(configuredHost);
            }
            if (configuredHost.indexOf('*') >= 0) {
                throw new IllegalArgumentException("Virtual host wildcard must be the complete left-most label: "
                                                           + configuredHost);
            }

            UriHost host = UriHost.create(configuredHost);
            if (host.kind() != UriHost.Kind.DNS) {
                throw new IllegalArgumentException("Virtual host must be a DNS name: " + configuredHost);
            }
            return new HostKey(host.value(), host.value(), false);
        }

        private static HostKey wildcard(String configuredHost) {
            if (configuredHost.indexOf('*', 1) != -1) {
                throw new IllegalArgumentException("Virtual host wildcard must be the complete left-most label: "
                                                           + configuredHost);
            }
            String suffix = configuredHost.substring(2);
            UriHost host = UriHost.create(suffix);
            if (host.kind() != UriHost.Kind.DNS) {
                throw new IllegalArgumentException("Virtual host wildcard suffix must be a DNS name: " + configuredHost);
            }
            return new HostKey("*." + host.value(), host.value(), true);
        }

        private record HostKey(String configuredHost, String indexKey, boolean wildcard) {
        }
    }

    private final class Context implements SniContext {
        private final Optional<String> presentedHost;
        private final Optional<String> matchedHost;
        private final SniMatchType matchType;

        private Context(Optional<String> presentedHost, Optional<String> matchedHost, SniMatchType matchType) {
            this.presentedHost = Objects.requireNonNull(presentedHost);
            this.matchedHost = Objects.requireNonNull(matchedHost);
            this.matchType = Objects.requireNonNull(matchType);
        }

        @Override
        public Optional<String> presentedHost() {
            return presentedHost;
        }

        @Override
        public Optional<String> matchedHost() {
            return matchedHost;
        }

        @Override
        public SniMatchType matchType() {
            return matchType;
        }

        @Override
        public AuthorityCheck checkAuthority(String authority) {
            UriHost authorityHost = UriAuthority.create(authority).host();
            String normalizedAuthority = authorityHost.value();

            if (sniConfig.authorityMismatch() == SniAuthorityPolicy.REJECT
                    && presentedHost.isPresent()
                    && !normalizedAuthority.equals(presentedHost.get())) {
                return AuthorityCheck.AUTHORITY_MISMATCH;
            }

            if (matchedHost.isPresent()) {
                return AuthorityCheck.ALLOWED;
            }

            if (sniConfig.fallbackAuthority() == SniAuthorityPolicy.REJECT
                    && authorityHost.kind() == UriHost.Kind.DNS
                    && matchesConfiguredHost(normalizedAuthority)) {
                return AuthorityCheck.FALLBACK_AUTHORITY;
            }
            return AuthorityCheck.ALLOWED;
        }
    }
}
