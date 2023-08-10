/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webclient.dns.resolver.roundrobin;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.webclient.api.DnsAddressLookup;
import io.helidon.webclient.api.RuntimeUnknownHostException;
import io.helidon.webclient.spi.DnsResolver;

/**
 * Round-robin DNS resolver implementation.
 */
public final class RoundRobinDnsResolver implements DnsResolver {

    private final Map<String, HostnameAddresses> hostnameAddresses = new HashMap<>();

    private RoundRobinDnsResolver() {
    }

    /**
     * Create new instance.
     *
     * @return new instance
     */
    public static RoundRobinDnsResolver create() {
        return new RoundRobinDnsResolver();
    }

    @Override
    public InetAddress resolveAddress(String hostname, DnsAddressLookup dnsAddressLookup) {
        HostnameAddresses hostnameAddress = hostnameAddresses.computeIfAbsent(hostname, host -> {
            try {
                InetAddress[] processed = dnsAddressLookup.filter(InetAddress.getAllByName(host));
                if (processed.length == 0) {
                    throw new RuntimeUnknownHostException("No IP version " + dnsAddressLookup.name() + " found for host " + host);
                }
                return new HostnameAddresses(new AtomicInteger(), processed);
            } catch (UnknownHostException e) {
                throw new RuntimeUnknownHostException(e);
            }
        });
        return hostnameAddress.addresses[hostnameAddress.count.getAndIncrement() % hostnameAddress.addresses.length];
    }

    private record HostnameAddresses(AtomicInteger count, InetAddress[] addresses) {
    }

}
