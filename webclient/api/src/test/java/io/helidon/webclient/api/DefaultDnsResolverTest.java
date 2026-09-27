/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
package io.helidon.webclient.api;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static io.helidon.webclient.api.TestAddresses.V4_A;
import static io.helidon.webclient.api.TestAddresses.V6_A;
import static io.helidon.webclient.api.TestAddresses.ipv4First;
import static io.helidon.webclient.api.TestAddresses.ipv6First;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultDnsResolverTest {

    @Test
    void testIpv4Resolution() {
        DefaultDnsResolver resolver = DefaultDnsResolver.create();
        InetAddress inetAddress = resolver.resolveAddress("localhost", DnsAddressLookup.IPV4_PREFERRED);
        assertThat(inetAddress.getHostAddress(), is("127.0.0.1"));
    }

    @Test
    @EnabledIf("isIPv6Configured")
    void testIpv6Resolution() {
        DefaultDnsResolver resolver = DefaultDnsResolver.create();
        InetAddress inetAddress = resolver.resolveAddress("localhost", DnsAddressLookup.IPV6_PREFERRED);
        assertThat(inetAddress.getHostAddress(), is("0:0:0:0:0:0:0:1"));
    }

    @Test
    void testMixedIpv6FirstResolution() {
        DefaultDnsResolver resolver = new DefaultDnsResolver(hostname -> ipv6First());
        assertThat(resolver.resolveAddress("mixed", DnsAddressLookup.IPV4_PREFERRED), sameInstance(V4_A));
        assertThat(resolver.resolveAddress("mixed", DnsAddressLookup.IPV6_PREFERRED), sameInstance(V6_A));
        assertThat(resolver.resolveAddress("mixed", DnsAddressLookup.IPV4), sameInstance(V4_A));
        assertThat(resolver.resolveAddress("mixed", DnsAddressLookup.IPV6), sameInstance(V6_A));
        assertThat(resolver.resolveAddress("mixed", DnsAddressLookup.SYSTEM), sameInstance(V6_A));
    }

    @Test
    void testMixedIpv4FirstResolution() {
        DefaultDnsResolver resolver = new DefaultDnsResolver(hostname -> ipv4First());
        assertThat(resolver.resolveAddress("mixed", DnsAddressLookup.IPV4_PREFERRED), sameInstance(V4_A));
        assertThat(resolver.resolveAddress("mixed", DnsAddressLookup.IPV6_PREFERRED), sameInstance(V6_A));
        assertThat(resolver.resolveAddress("mixed", DnsAddressLookup.IPV4), sameInstance(V4_A));
        assertThat(resolver.resolveAddress("mixed", DnsAddressLookup.IPV6), sameInstance(V6_A));
        assertThat(resolver.resolveAddress("mixed", DnsAddressLookup.SYSTEM), sameInstance(V4_A));
    }

    @Test
    void testNoMatchingAddress() {
        DefaultDnsResolver resolver = new DefaultDnsResolver(hostname -> new InetAddress[] {V4_A});
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveAddress("v4only", DnsAddressLookup.IPV6));
    }

    @Test
    void testUnknownHost() {
        DefaultDnsResolver resolver = new DefaultDnsResolver(hostname -> {
            throw new UnknownHostException(hostname);
        });
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveAddress("unknown", DnsAddressLookup.SYSTEM));
    }

    private boolean isIPv6Configured() {
        try {
            InetAddress[] address = InetAddress.getAllByName("localhost");
            return Stream.of(address).anyMatch(a -> a instanceof Inet6Address);
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
