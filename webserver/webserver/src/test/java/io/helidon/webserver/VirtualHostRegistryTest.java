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

import java.util.Optional;

import io.helidon.common.tls.Tls;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VirtualHostRegistryTest {
    private static final String SOCKET_NAME = "test";
    private static final Tls TLS = Tls.builder()
            .trustAll(true)
            .build();
    private static final Tls DISABLED_TLS = Tls.builder()
            .enabled(false)
            .build();

    @Test
    void rejectsNonDefaultSelectionPolicyWithoutVirtualHosts() {
        ListenerConfig listener = ListenerConfig.builder()
                .sni(it -> it.missing(SniSelectionPolicy.REJECT))
                .build();

        assertThrows(IllegalArgumentException.class, () -> VirtualHostRegistry.create(SOCKET_NAME, listener, DISABLED_TLS));
    }

    @Test
    void virtualHostsRequireListenerTls() {
        ListenerConfig listener = ListenerConfig.builder()
                .addVirtualHost(virtualHost("api.example.com"))
                .build();

        assertThrows(IllegalArgumentException.class, () -> VirtualHostRegistry.create(SOCKET_NAME, listener, DISABLED_TLS));
    }

    @Test
    void virtualHostsRequireNioTls() {
        ListenerConfig listener = ListenerConfig.builder()
                .tls(TLS)
                .useNio(false)
                .addVirtualHost(virtualHost("api.example.com"))
                .build();

        assertThrows(IllegalArgumentException.class, () -> VirtualHostRegistry.create(SOCKET_NAME, listener, TLS));
    }

    @Test
    void duplicateNormalizedHostsFail() {
        ListenerConfig listener = ListenerConfig.builder()
                .tls(TLS)
                .addVirtualHost(virtualHost("Api.Example.COM."))
                .addVirtualHost(virtualHost("api.example.com"))
                .build();

        assertThrows(IllegalArgumentException.class, () -> VirtualHostRegistry.create(SOCKET_NAME, listener, TLS));
    }

    @Test
    void exactHostWinsOverWildcardAndAuthorityMustMatchPresentedSni() {
        VirtualHostRegistry registry = registry("api.example.com", "*.example.com");

        VirtualHostRegistry.Selection selection = registry.select("api.example.com");

        assertThat(selection.tls(), is(TLS));
        assertThat(selection.sniContext().matchType(), is(SniMatchType.EXACT));
        assertThat(selection.sniContext().matchedHost(), is(Optional.of("api.example.com")));
        assertThat(selection.sniContext().checkAuthority("api.example.com"), is(SniContext.AuthorityCheck.ALLOWED));
        assertThat(selection.sniContext().checkAuthority("admin.example.com"),
                   is(SniContext.AuthorityCheck.AUTHORITY_MISMATCH));
    }

    @Test
    void wildcardMatchesOneLabelOnly() {
        VirtualHostRegistry registry = registry("*.example.com");

        SniContext context = registry.select("api.example.com").sniContext();

        assertThat(context.presentedHost(), is(Optional.of("api.example.com")));
        assertThat(context.matchedHost(), is(Optional.of("*.example.com")));
        assertThat(context.matchType(), is(SniMatchType.WILDCARD));
        assertThat(registry.select("a.b.example.com").sniContext().matchType(),
                   is(SniMatchType.FALLBACK_UNMATCHED));
    }

    @Test
    void fallbackAuthorityRejectsConfiguredHostNames() {
        VirtualHostRegistry registry = registry("api.example.com", "*.example.org");

        SniContext context = registry.selectWithoutSni().sniContext();

        assertThat(context.matchType(), is(SniMatchType.FALLBACK_MISSING));
        assertThat(context.checkAuthority("api.example.com"), is(SniContext.AuthorityCheck.FALLBACK_AUTHORITY));
        assertThat(context.checkAuthority("admin.example.org"), is(SniContext.AuthorityCheck.FALLBACK_AUTHORITY));
        assertThat(context.checkAuthority("other.example.net"), is(SniContext.AuthorityCheck.ALLOWED));
    }

    @Test
    void unmatchedPresentedSniRejectsDifferentAuthority() {
        VirtualHostRegistry registry = registry("api.example.com");
        SniContext context = registry.select("unmatched.example.com").sniContext();

        assertThat(context.checkAuthority("unmatched.example.com"), is(SniContext.AuthorityCheck.ALLOWED));
        assertThat(context.checkAuthority("other.example.com"), is(SniContext.AuthorityCheck.AUTHORITY_MISMATCH));
    }

    @Test
    void checkAuthorityUsesRealAuthorityParser() {
        VirtualHostRegistry registry = registry("api.example.com");
        SniContext context = registry.select("api.example.com").sniContext();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                          () -> SniRequestSupport.checkAuthority(context, "bad authority"));

        assertThat(exception.getMessage(), containsString("Invalid HTTP authority"));
    }

    @Test
    void missingAndUnmatchedRejectPoliciesRejectTlsSelection() {
        ListenerConfig listener = ListenerConfig.builder()
                .tls(TLS)
                .addVirtualHost(virtualHost("api.example.com"))
                .sni(it -> it.missing(SniSelectionPolicy.REJECT)
                        .unmatched(SniSelectionPolicy.REJECT))
                .build();
        VirtualHostRegistry registry = VirtualHostRegistry.create(SOCKET_NAME, listener, TLS);

        VirtualHostRegistry.RejectedSniException missing =
                assertThrows(VirtualHostRegistry.RejectedSniException.class, registry::selectWithoutSni);
        VirtualHostRegistry.RejectedSniException unmatched =
                assertThrows(VirtualHostRegistry.RejectedSniException.class,
                             () -> registry.select("other.example.com"));

        assertThat(missing.sendUnrecognizedNameAlert(), is(false));
        assertThat(unmatched.sendUnrecognizedNameAlert(), is(true));
    }

    private static VirtualHostRegistry registry(String... hosts) {
        ListenerConfig.Builder builder = ListenerConfig.builder()
                .tls(TLS);
        for (String host : hosts) {
            builder.addVirtualHost(virtualHost(host));
        }
        return VirtualHostRegistry.create(SOCKET_NAME, builder.build(), TLS);
    }

    private static VirtualHostConfig virtualHost(String host) {
        return VirtualHostConfig.builder()
                .host(host)
                .tls(TLS)
                .build();
    }
}
