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
package io.helidon.webclient.api;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;

import static io.helidon.webclient.api.TestAddresses.V4_A;
import static io.helidon.webclient.api.TestAddresses.V4_B;
import static io.helidon.webclient.api.TestAddresses.V6_A;
import static io.helidon.webclient.api.TestAddresses.V6_B;
import static io.helidon.webclient.api.TestAddresses.ipv4First;
import static io.helidon.webclient.api.TestAddresses.ipv6First;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

class DnsAddressLookupTest {

    @Test
    void testIpv4() {
        assertThat(DnsAddressLookup.IPV4.filter(ipv6First()), arrayContaining(V4_A, V4_B));
        assertThat(DnsAddressLookup.IPV4.filter(ipv4First()), arrayContaining(V4_A, V4_B));
    }

    @Test
    void testIpv6() {
        assertThat(DnsAddressLookup.IPV6.filter(ipv6First()), arrayContaining(V6_A, V6_B));
        assertThat(DnsAddressLookup.IPV6.filter(ipv4First()), arrayContaining(V6_A, V6_B));
    }

    @Test
    void testIpv4Preferred() {
        assertThat(DnsAddressLookup.IPV4_PREFERRED.filter(ipv6First()), arrayContaining(V4_A, V4_B, V6_A, V6_B));
        assertThat(DnsAddressLookup.IPV4_PREFERRED.filter(ipv4First()), arrayContaining(V4_A, V4_B, V6_A, V6_B));
    }

    @Test
    void testIpv6Preferred() {
        assertThat(DnsAddressLookup.IPV6_PREFERRED.filter(ipv6First()), arrayContaining(V6_A, V6_B, V4_A, V4_B));
        assertThat(DnsAddressLookup.IPV6_PREFERRED.filter(ipv4First()), arrayContaining(V6_A, V6_B, V4_A, V4_B));
    }

    @Test
    void testSystemPreservesOrder() {
        assertThat(DnsAddressLookup.SYSTEM.filter(ipv6First()), arrayContaining(V6_A, V4_A, V6_B, V4_B));
        assertThat(DnsAddressLookup.SYSTEM.filter(ipv4First()), arrayContaining(V4_A, V6_A, V4_B, V6_B));
    }

    @Test
    void testInputNotModified() {
        for (DnsAddressLookup lookup : DnsAddressLookup.values()) {
            InetAddress[] input = ipv6First();
            InetAddress[] result = lookup.filter(input);
            assertThat(lookup.name(), input, arrayContaining(ipv6First()));
            assertThat(lookup.name(), result, not(sameInstance(input)));
        }
    }

    @Test
    void testEmptyAndSingleFamily() {
        for (DnsAddressLookup lookup : DnsAddressLookup.values()) {
            assertThat(lookup.name(), lookup.filter(new InetAddress[0]), emptyArray());
        }
        InetAddress[] v4Only = {V4_B, V4_A};
        assertThat(DnsAddressLookup.SYSTEM.filter(v4Only), arrayContaining(V4_B, V4_A));
        assertThat(DnsAddressLookup.IPV6_PREFERRED.filter(v4Only), arrayContaining(V4_B, V4_A));
        assertThat(DnsAddressLookup.IPV6.filter(v4Only), emptyArray());
    }
}
