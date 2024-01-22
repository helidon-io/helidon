/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import io.helidon.common.LazyValue;

import static java.lang.System.Logger.Level;

/**
 * Heavily inspired by Netty.
 */
final class DefaultAddressLookupFinder {

    private static final System.Logger LOGGER = System.getLogger(DefaultAddressLookupFinder.class.getName());

    /**
     * {@code true} if IPv4 should be used even if the system supports both IPv4 and IPv6.
     */
    private static final LazyValue<Boolean> IPV4_PREFERRED = LazyValue.create(() ->
            Boolean.getBoolean("java.net.preferIPv4Stack"));

    /**
     * {@code true} if an IPv6 address should be preferred when a host has both an IPv4 address and an IPv6 address.
     */
    private static final LazyValue<Boolean> IPV6_PREFERRED = LazyValue.create(() ->
            Boolean.getBoolean("java.net.preferIPv6Addresses"));

    private static final LazyValue<DnsAddressLookup> DEFAULT_IP_VERSION = LazyValue.create(() -> {
        if (IPV4_PREFERRED.get() || !IPV6_PREFERRED.get()) {
            LOGGER.log(Level.DEBUG, "Preferring IPv4 over IPv6 address resolution");
            return DnsAddressLookup.IPV4_PREFERRED;
        }
        LOGGER.log(Level.DEBUG, "Preferring IPv6 over IPv4 address resolution");
        return DnsAddressLookup.IPV6_PREFERRED;
    });

    private DefaultAddressLookupFinder() {
        throw new IllegalStateException("This class should not be instantiated");
    }

    static DnsAddressLookup defaultDnsAddressLookup() {
        return DEFAULT_IP_VERSION.get();
    }
}
