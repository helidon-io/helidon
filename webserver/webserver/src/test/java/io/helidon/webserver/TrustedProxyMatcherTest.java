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

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.configurable.AllowList;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TrustedProxyMatcherTest {
    @Test
    void exactDenyMatchesEquivalentIpv6Address() throws Exception {
        AllowList trustedProxies = AllowList.builder()
                .allowAll(true)
                .addDenied("2001:db8::1")
                .build();
        var remoteAddress = new InetSocketAddress(InetAddress.getByName("2001:db8:0:0:0:0:0:1"), 8080);

        assertThat(new TrustedProxyMatcher(trustedProxies).test(remoteAddress), is(false));
    }

    @Test
    void exactRulesMatchEquivalentIpv4Address() throws Exception {
        var remoteAddress = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 8080);

        for (String configuredAddress : List.of("2130706433", "127.1", "127.0.1", "127.0.0.1")) {
            AllowList allowedTrustedProxies = AllowList.builder()
                    .addAllowed(configuredAddress)
                    .build();
            AllowList deniedTrustedProxies = AllowList.builder()
                    .allowAll(true)
                    .addDenied(configuredAddress)
                    .build();

            assertAll(configuredAddress,
                      () -> assertThat("exact IPv4 allow",
                                       new TrustedProxyMatcher(allowedTrustedProxies).test(remoteAddress),
                                       is(true)),
                      () -> assertThat("exact IPv4 deny",
                                       new TrustedProxyMatcher(deniedTrustedProxies).test(remoteAddress),
                                       is(false)));
        }
    }

    @Test
    void outOfRangeIpv4DoesNotCreateAddressAlias() throws Exception {
        AllowList trustedProxies = AllowList.builder()
                .addAllowed("4294967296")
                .build();
        var remoteAddress = new InetSocketAddress(InetAddress.getByAddress(new byte[4]), 8080);

        assertThat(new TrustedProxyMatcher(trustedProxies).test(remoteAddress), is(false));
    }

    @Test
    void overlongIpv4DoesNotCreateAddressAlias() throws Exception {
        AllowList trustedProxies = AllowList.builder()
                .addAllowed("0000000000001.0.0.1")
                .build();
        var remoteAddress = new InetSocketAddress(InetAddress.getByAddress(new byte[] {1, 0, 0, 1}), 8080);

        assertThat(new TrustedProxyMatcher(trustedProxies).test(remoteAddress), is(false));
    }

    @Test
    void overlongEmbeddedIpv4DoesNotCreateAddressAlias() throws Exception {
        AllowList trustedProxies = AllowList.builder()
                .addAllowed("::ffff:0000000000001.0.0.1")
                .build();
        var remoteAddress = new InetSocketAddress(InetAddress.getByAddress(new byte[] {1, 0, 0, 1}), 8080);

        assertThat(new TrustedProxyMatcher(trustedProxies).test(remoteAddress), is(false));
    }

    @Test
    void exactRulesMatchLocallyParsedIpv6Addresses() throws Exception {
        Map<String, byte[]> configuredAddresses = Map.of(
                "::", new byte[16],
                "2001:db8::",
                new byte[] {0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                "::0E05C",
                new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xe0, 0x5c},
                "::ffff:192.0.2.1",
                new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff,
                        (byte) 192, 0, 2, 1},
                "2001:db8::192.0.2.1",
                new byte[] {0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0,
                        (byte) 192, 0, 2, 1});

        for (Map.Entry<String, byte[]> entry : configuredAddresses.entrySet()) {
            AllowList allowedTrustedProxies = AllowList.builder()
                    .addAllowed(entry.getKey())
                    .build();
            AllowList deniedTrustedProxies = AllowList.builder()
                    .allowAll(true)
                    .addDenied(entry.getKey())
                    .build();
            var remoteAddress = new InetSocketAddress(InetAddress.getByAddress(entry.getValue()), 8080);

            assertAll(entry.getKey(),
                      () -> assertThat("exact IPv6 allow",
                                       new TrustedProxyMatcher(allowedTrustedProxies).test(remoteAddress),
                                       is(true)),
                      () -> assertThat("exact IPv6 deny",
                                       new TrustedProxyMatcher(deniedTrustedProxies).test(remoteAddress),
                                       is(false)));
        }
    }

    @Test
    void outOfRangeIpv6DoesNotCreateAddressAlias() throws Exception {
        AllowList trustedProxies = AllowList.builder()
                .addAllowed("::10000")
                .build();
        var remoteAddress = new InetSocketAddress(InetAddress.getByAddress(new byte[16]), 8080);

        assertThat(new TrustedProxyMatcher(trustedProxies).test(remoteAddress), is(false));
    }

    @Test
    void rulesMatchEquivalentScopedIpv6Address() throws Exception {
        var scopedAddress = NetworkInterface.networkInterfaces()
                .flatMap(NetworkInterface::inetAddresses)
                .filter(Inet6Address.class::isInstance)
                .map(Inet6Address.class::cast)
                .filter(address -> address.getScopedInterface() != null
                        && address.getScopeId() > 0)
                .findFirst();
        assumeTrue(scopedAddress.isPresent(), "No scoped IPv6 address is available");

        var interfaceAddress = scopedAddress.orElseThrow();
        var namedScopeAddress = Inet6Address.getByAddress(null,
                                                         interfaceAddress.getAddress(),
                                                         interfaceAddress.getScopedInterface());
        var numericScopeAddress = Inet6Address.getByAddress(null,
                                                           interfaceAddress.getAddress(),
                                                           interfaceAddress.getScopeId());
        assertThat(namedScopeAddress, is(numericScopeAddress));
        assertThat(namedScopeAddress.getHostAddress(), not(is(numericScopeAddress.getHostAddress())));

        String namedScope = namedScopeAddress.getHostAddress();
        AllowList deniedTrustedProxies = AllowList.builder()
                .allowAll(true)
                .addDenied(namedScope)
                .build();
        var remoteAddress = new InetSocketAddress(numericScopeAddress, 8080);

        assertThat("exact named-scope deny",
                   new TrustedProxyMatcher(deniedTrustedProxies).test(remoteAddress),
                   is(false));

        AllowList allowedTrustedProxies = AllowList.builder()
                .addAllowed(namedScope)
                .build();

        assertThat("exact named-scope allow",
                   new TrustedProxyMatcher(allowedTrustedProxies).test(remoteAddress),
                   is(true));

        AllowList predicateDeniedTrustedProxies = AllowList.builder()
                .allowAll(true)
                .addDenied(namedScope::equals)
                .build();

        assertThat("request-time named-scope deny",
                   new TrustedProxyMatcher(predicateDeniedTrustedProxies).test(remoteAddress),
                   is(false));

        AllowList predicateAllowedTrustedProxies = AllowList.builder()
                .addAllowed(namedScope::equals)
                .build();

        assertThat("request-time named-scope allow",
                   new TrustedProxyMatcher(predicateAllowedTrustedProxies).test(remoteAddress),
                   is(true));
    }

    @Test
    void numericScopedRuleDoesNotRequireInterfaceLookup() throws Exception {
        var numericScopeAddress = Inet6Address.getByAddress(null,
                                                           InetAddress.getByName("fe80::1").getAddress(),
                                                           Integer.MAX_VALUE);
        AllowList trustedProxies = AllowList.builder()
                .addAllowed(numericScopeAddress.getHostAddress())
                .build();
        var remoteAddress = new InetSocketAddress(numericScopeAddress, 8080);

        assertThat(new TrustedProxyMatcher(trustedProxies).test(remoteAddress), is(true));
    }

    @Test
    void exactNumericScopeIgnoresLeadingZeroes() throws Exception {
        var numericScopeAddress = Inet6Address.getByAddress(null,
                                                           InetAddress.getByName("fe80::1").getAddress(),
                                                           3);
        var remoteAddress = new InetSocketAddress(numericScopeAddress, 8080);
        String configuredAddress = "fe80::1%003";

        AllowList allowedTrustedProxies = AllowList.builder()
                .addAllowed(configuredAddress)
                .build();
        AllowList deniedTrustedProxies = AllowList.builder()
                .allowAll(true)
                .addDenied(configuredAddress)
                .build();

        assertAll(() -> assertThat("exact numeric-scope allow",
                                   new TrustedProxyMatcher(allowedTrustedProxies).test(remoteAddress),
                                   is(true)),
                  () -> assertThat("exact numeric-scope deny",
                                   new TrustedProxyMatcher(deniedTrustedProxies).test(remoteAddress),
                                   is(false)));
    }

    @Test
    void exactZeroScopeMatchesUnscopedIpv6Address() throws Exception {
        var remoteAddress = new InetSocketAddress(InetAddress.getByAddress(
                new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}), 8080);

        for (String configuredAddress : List.of("::1%0", "::1%000")) {
            AllowList allowedTrustedProxies = AllowList.builder()
                    .addAllowed(configuredAddress)
                    .build();
            AllowList deniedTrustedProxies = AllowList.builder()
                    .allowAll(true)
                    .addDenied(configuredAddress)
                    .build();

            assertAll(configuredAddress,
                      () -> assertThat("exact zero-scope allow",
                                       new TrustedProxyMatcher(allowedTrustedProxies).test(remoteAddress),
                                       is(true)),
                      () -> assertThat("exact zero-scope deny",
                                       new TrustedProxyMatcher(deniedTrustedProxies).test(remoteAddress),
                                       is(false)));
        }
    }

    @Test
    void scopedIpv4MappedRuleDoesNotMatchIpv4Address() throws Exception {
        AllowList trustedProxies = AllowList.builder()
                .addAllowed("::ffff:192.0.2.1%0")
                .build();
        var remoteAddress = new InetSocketAddress(InetAddress.getByAddress(
                new byte[] {(byte) 192, 0, 2, 1}), 8080);

        assertThat(new TrustedProxyMatcher(trustedProxies).test(remoteAddress), is(false));
    }

    @Test
    void allDigitNamedScopeRetainsLeadingZeroes() {
        String configuredAddress = "fe80::1%003";
        var remoteAddress = InetSocketAddress.createUnresolved("fe80:0:0:0:0:0:0:1%003", 8080);

        AllowList allowedTrustedProxies = AllowList.builder()
                .addAllowed(configuredAddress)
                .build();
        AllowList deniedTrustedProxies = AllowList.builder()
                .allowAll(true)
                .addDenied(configuredAddress)
                .build();

        assertAll(() -> assertThat("exact all-digit named-scope allow",
                                   new TrustedProxyMatcher(allowedTrustedProxies).test(remoteAddress),
                                   is(true)),
                  () -> assertThat("exact all-digit named-scope deny",
                                   new TrustedProxyMatcher(deniedTrustedProxies).test(remoteAddress),
                                   is(false)));
    }

    @Test
    void bracketedScopedRulesMatchEquivalentAddress() throws Exception {
        var scopedInterface = NetworkInterface.networkInterfaces()
                .filter(networkInterface -> networkInterface.getIndex() > 0)
                .findFirst();
        assumeTrue(scopedInterface.isPresent(), "No indexed network interface is available");

        var networkInterface = scopedInterface.orElseThrow();
        var scopedAddress = Inet6Address.getByAddress(null,
                                                     InetAddress.getByName("fe80::1").getAddress(),
                                                     networkInterface.getIndex());
        var remoteAddress = new InetSocketAddress(scopedAddress, 8080);
        String numericScopeAddress = scopedAddress.getHostAddress();
        int scopeIndex = numericScopeAddress.lastIndexOf('%');
        String address = numericScopeAddress.substring(0, scopeIndex);

        for (String configuredAddress : List.of("[" + numericScopeAddress + "]",
                                                "[" + address + "%" + networkInterface.getName() + "]")) {
            AllowList allowedTrustedProxies = AllowList.builder()
                    .addAllowed(configuredAddress)
                    .build();
            AllowList deniedTrustedProxies = AllowList.builder()
                    .allowAll(true)
                    .addDenied(configuredAddress)
                    .build();

            assertAll(configuredAddress,
                      () -> assertThat("exact bracketed-scope allow",
                                       new TrustedProxyMatcher(allowedTrustedProxies).test(remoteAddress),
                                       is(true)),
                      () -> assertThat("exact bracketed-scope deny",
                                       new TrustedProxyMatcher(deniedTrustedProxies).test(remoteAddress),
                                       is(false)));
        }
    }

    @Test
    void namedScopeAliasAcceptsPlatformCharacters() {
        String configuredAddress = "fe80::1%proxy+edge";
        var remoteAddress = InetSocketAddress.createUnresolved("fe80:0:0:0:0:0:0:1%proxy+edge", 8080);

        AllowList allowedTrustedProxies = AllowList.builder()
                .addAllowed(configuredAddress)
                .build();
        AllowList deniedTrustedProxies = AllowList.builder()
                .allowAll(true)
                .addDenied(configuredAddress)
                .build();

        assertAll(() -> assertThat("exact named-scope allow",
                                   new TrustedProxyMatcher(allowedTrustedProxies).test(remoteAddress),
                                   is(true)),
                  () -> assertThat("exact named-scope deny",
                                   new TrustedProxyMatcher(deniedTrustedProxies).test(remoteAddress),
                                   is(false)));
    }

    @Test
    void potentialNamedScopePrefixFailsClosedWithoutInterface() throws Exception {
        var numericScopeAddress = Inet6Address.getByAddress(null,
                                                           InetAddress.getByName("fe80::1").getAddress(),
                                                           Integer.MAX_VALUE);
        String numericScope = numericScopeAddress.getHostAddress();
        String address = numericScope.substring(0, numericScope.lastIndexOf('%'));
        AllowList trustedProxies = AllowList.builder()
                .addAllowed(numericScope)
                .addDeniedPrefix(address + "%9")
                .build();
        var remoteAddress = new InetSocketAddress(numericScopeAddress, 8080);

        assertThat(new TrustedProxyMatcher(trustedProxies).test(remoteAddress), is(false));
    }

    @Test
    void potentialNumericNamedScopeDenyFailsClosedWithoutInterface() throws Exception {
        var numericScopeAddress = Inet6Address.getByAddress(null,
                                                           InetAddress.getByName("fe80::1").getAddress(),
                                                           Integer.MAX_VALUE);
        String numericScope = numericScopeAddress.getHostAddress();
        String address = numericScope.substring(0, numericScope.lastIndexOf('%'));
        AllowList trustedProxies = AllowList.builder()
                .addAllowed(numericScope)
                .addDenied(address + "%123")
                .build();
        var remoteAddress = new InetSocketAddress(numericScopeAddress, 8080);

        assertThat(new TrustedProxyMatcher(trustedProxies).test(remoteAddress), is(false));
    }

    @Test
    void exactAllowMatchesEquivalentIpv6Address() throws Exception {
        AllowList trustedProxies = AllowList.builder()
                .addAllowed("2001:db8::1")
                .build();
        var remoteAddress = new InetSocketAddress(InetAddress.getByName("2001:db8:0:0:0:0:0:1"), 8080);

        assertThat(new TrustedProxyMatcher(trustedProxies).test(remoteAddress), is(true));
    }

    @Test
    void denyOnAddressOverridesAllowOnHostString() {
        AllowList trustedProxies = AllowList.builder()
                .addAllowed("2001:db8::1")
                .addDenied("2001:db8:0:0:0:0:0:1")
                .build();
        var remoteAddress = new InetSocketAddress("2001:db8::1", 8080);

        assertThat(new TrustedProxyMatcher(trustedProxies).test(remoteAddress), is(false));
    }

    @Test
    void textualDenyOnAddressOverridesTextualAllowOnHostString() {
        AllowList trustedProxies = AllowList.builder()
                .addAllowedPrefix("2001:db8::")
                .addDeniedSuffix(":0:0:0:0:0:1")
                .build();
        var remoteAddress = new InetSocketAddress("2001:db8::1", 8080);

        assertThat(new TrustedProxyMatcher(trustedProxies).test(remoteAddress), is(false));
    }

    @Test
    void identicalRepresentationsAreTestedOnce() throws Exception {
        var invocations = new AtomicInteger();
        AllowList trustedProxies = AllowList.builder()
                .addAllowed(value -> {
                    invocations.incrementAndGet();
                    return true;
                })
                .build();
        var remoteAddress = new InetSocketAddress(InetAddress.getByName("192.0.2.1"), 8080);

        assertThat(new TrustedProxyMatcher(trustedProxies).test(remoteAddress), is(true));
        assertThat(invocations.get(), is(1));
    }

    @Test
    void unresolvedHostNameRemainsTextual() {
        AllowList trustedProxies = AllowList.builder()
                .addAllowed("proxy.example")
                .build();
        var remoteAddress = InetSocketAddress.createUnresolved("proxy.example", 8080);

        assertThat(new TrustedProxyMatcher(trustedProxies).test(remoteAddress), is(true));
    }

    @Test
    void invalidNumericAddressRemainsTextual() {
        AllowList trustedProxies = AllowList.builder()
                .addAllowed("2001:db8:::1")
                .build();
        var remoteAddress = InetSocketAddress.createUnresolved("2001:db8:::1", 8080);

        assertThat(new TrustedProxyMatcher(trustedProxies).test(remoteAddress), is(true));
    }
}
