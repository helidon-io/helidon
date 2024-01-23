/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

class DefaultDnsResolverTest {

    @Test
    void testIpv4Resolution() {
        DefaultDnsResolver resolver = DefaultDnsResolver.create();
        InetAddress inetAddress = resolver.resolveAddress("localhost", DnsAddressLookup.IPV4_PREFERRED);
        assertThat(inetAddress.getHostAddress(), is("127.0.0.1"));
    }

    @Test
    void testIpv6Resolution() {
        DefaultDnsResolver resolver = DefaultDnsResolver.create();
        InetAddress inetAddress = resolver.resolveAddress("localhost", DnsAddressLookup.IPV6_PREFERRED);
        assertThat(inetAddress.getHostAddress(), is("0:0:0:0:0:0:0:1"));
    }
}
