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

import static io.helidon.webclient.api.TestAddresses.ipv4First;
import static io.helidon.webclient.api.TestAddresses.ipv6First;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;

/**
 * Base for tests of the default lookup in a JVM started with specific networking properties.
 * Each subclass runs in its own surefire execution (see pom.xml), with properties set on the command line.
 */
abstract class DefaultLookupJvmTestBase {

    private final String preferIPv4Stack;
    private final String preferIPv6Addresses;
    private final DnsAddressLookup expected;

    DefaultLookupJvmTestBase(String preferIPv4Stack, String preferIPv6Addresses, DnsAddressLookup expected) {
        this.preferIPv4Stack = preferIPv4Stack;
        this.preferIPv6Addresses = preferIPv6Addresses;
        this.expected = expected;
    }

    @Test
    void testJvmProperties() {
        assertThat(System.getProperty(DefaultAddressLookupFinder.PREFER_IPV4_STACK), is(preferIPv4Stack));
        assertThat(System.getProperty(DefaultAddressLookupFinder.PREFER_IPV6_ADDRESSES), is(preferIPv6Addresses));
    }

    @Test
    void testDefaultLookup() {
        assertThat(DnsAddressLookup.defaultLookup(), is(expected));
    }

    @Test
    void testDefaultLookupResolution() {
        assertResolved(ipv6First());
        assertResolved(ipv4First());
    }

    private void assertResolved(InetAddress[] addresses) {
        DefaultDnsResolver resolver = new DefaultDnsResolver(hostname -> addresses.clone());
        InetAddress resolved = resolver.resolveAddress("mixed", DnsAddressLookup.defaultLookup());
        assertThat(resolved, sameInstance(expected.filter(addresses)[0]));
        if (expected == DnsAddressLookup.SYSTEM) {
            assertThat(resolved, sameInstance(addresses[0]));
        }
    }
}
