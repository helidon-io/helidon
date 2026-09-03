/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.LruCache;
import io.helidon.webclient.api.DnsAddressLookup;
import io.helidon.webclient.api.RuntimeUnknownHostException;
import io.helidon.webclient.spi.DnsResolver;

/**
 * Round-robin DNS resolver implementation.
 */
public final class RoundRobinDnsResolver implements DnsResolver {
    private static final int CACHE_CAPACITY = 10_000;
    // Identity sentinel used to distinguish a cache miss from a stored value.
    private static final HostnameAddresses CACHE_MISS = new HostnameAddresses(new InetAddress[0]);

    private final LruCache<String, HostnameAddresses> hostnameAddresses = LruCache.create(CACHE_CAPACITY);
    private final Lock cacheUpdateLock = new ReentrantLock();

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
        HostnameAddresses addresses = hostnameAddresses.peek(hostname, CACHE_MISS);
        if (addresses != CACHE_MISS) {
            return addresses.next();
        }

        try {
            InetAddress[] processed = dnsAddressLookup.filter(InetAddress.getAllByName(hostname));
            if (processed.length == 0) {
                String message = "No IP version " + dnsAddressLookup.name() + " found for host " + hostname;
                throw new RuntimeUnknownHostException(message);
            }
            HostnameAddresses resolved = new HostnameAddresses(processed);
            cacheUpdateLock.lock();
            try {
                addresses = hostnameAddresses.peek(hostname, CACHE_MISS);
                if (addresses == CACHE_MISS) {
                    hostnameAddresses.put(hostname, resolved);
                    addresses = resolved;
                }
                return addresses.next();
            } finally {
                cacheUpdateLock.unlock();
            }
        } catch (UnknownHostException e) {
            throw new RuntimeUnknownHostException(e);
        }
    }

    private static final class HostnameAddresses {
        private final AtomicInteger count = new AtomicInteger();
        private final InetAddress[] addresses;

        private HostnameAddresses(InetAddress[] addresses) {
            this.addresses = addresses;
        }

        private InetAddress next() {
            int index = count.getAndUpdate(current -> current == addresses.length - 1 ? 0 : current + 1);
            return addresses[index];
        }
    }
}
