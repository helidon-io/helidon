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

package io.helidon.webclient.api;

import io.helidon.common.LazyValue;

import static java.lang.System.Logger.Level;

/**
 * Heavily inspired by Netty.
 */
final class DefaultAddressLookupFinder {

    static final String PREFER_IPV4_STACK = "java.net.preferIPv4Stack";
    static final String PREFER_IPV6_ADDRESSES = "java.net.preferIPv6Addresses";

    private static final System.Logger LOGGER = System.getLogger(DefaultAddressLookupFinder.class.getName());

    private static final LazyValue<DnsAddressLookup> DEFAULT_IP_VERSION = LazyValue.create(() -> {
        DnsAddressLookup lookup = select(System.getProperty(PREFER_IPV4_STACK),
                                         System.getProperty(PREFER_IPV6_ADDRESSES));
        if (LOGGER.isLoggable(Level.DEBUG)) {
            switch (lookup) {
                case IPV6_PREFERRED -> LOGGER.log(Level.DEBUG, "Preferring IPv6 over IPv4 address resolution");
                case SYSTEM -> LOGGER.log(Level.DEBUG, "Using system resolver order for address resolution");
                default -> LOGGER.log(Level.DEBUG, "Preferring IPv4 over IPv6 address resolution");
            }
        }
        return lookup;
    });

    private DefaultAddressLookupFinder() {
        throw new IllegalStateException("This class should not be instantiated");
    }

    static DnsAddressLookup defaultDnsAddressLookup() {
        return DEFAULT_IP_VERSION.get();
    }

    /**
     * Select the lookup strategy for the given property values, following the JDK interpretation.
     * {@code java.net.preferIPv4Stack} takes precedence and must be exactly {@code true};
     * {@code java.net.preferIPv6Addresses} is compared ignoring case.
     *
     * @param preferIPv4Stack     value of {@code java.net.preferIPv4Stack}, may be {@code null}
     * @param preferIPv6Addresses value of {@code java.net.preferIPv6Addresses}, may be {@code null}
     * @return lookup strategy
     */
    static DnsAddressLookup select(String preferIPv4Stack, String preferIPv6Addresses) {
        if ("true".equals(preferIPv4Stack)) {
            return DnsAddressLookup.IPV4_PREFERRED;
        }
        if ("true".equalsIgnoreCase(preferIPv6Addresses)) {
            return DnsAddressLookup.IPV6_PREFERRED;
        }
        if ("system".equalsIgnoreCase(preferIPv6Addresses)) {
            return DnsAddressLookup.SYSTEM;
        }
        return DnsAddressLookup.IPV4_PREFERRED;
    }
}
