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

package io.helidon.webclient.dns.resolver.first;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.webclient.api.DnsAddressLookup;
import io.helidon.webclient.api.RuntimeUnknownHostException;
import io.helidon.webclient.spi.DnsResolver;

final class FirstDnsResolver implements DnsResolver {

    private final Map<String, InetAddress> hostnameAddresses = new ConcurrentHashMap<>();

    FirstDnsResolver() {
    }

    @Override
    public InetAddress resolveAddress(String hostname, DnsAddressLookup dnsAddressLookup) {
        Objects.requireNonNull(hostname);
        Objects.requireNonNull(dnsAddressLookup);

        return hostnameAddresses.computeIfAbsent(hostname, host -> {
            try {
                InetAddress[] processed = dnsAddressLookup.filter(InetAddress.getAllByName(hostname));
                if (processed.length == 0) {
                    throw new RuntimeUnknownHostException("No IP version " + dnsAddressLookup.name() + " found for host " + host);
                }
                return processed[0];
            } catch (UnknownHostException e) {
                throw new RuntimeUnknownHostException(e);
            }
        });
    }

}
