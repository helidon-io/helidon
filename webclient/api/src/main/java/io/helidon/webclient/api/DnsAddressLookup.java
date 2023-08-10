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

package io.helidon.webclient.api;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Function;

/**
 * DNS address lookup strategy.
 */
public enum DnsAddressLookup {

    /**
     * Only IPv4 addresses will be used.
     */
    IPV4(new Ipv4Only()),

    /**
     * Only IPv6 addresses will be used.
     */
    IPV6(new Ipv6Only()),

    /**
     * Both IPv4 and IPv6 addresses will be used, but if there are any IPv4, they take precedence.
     */
    IPV4_PREFERRED(new Ipv4Preferred()),

    /**
     * Both IPv4 and IPv6 addresses will be used, but if there are any IPv6, they take precedence.
     */
    IPV6_PREFERRED(new Ipv6Preferred());

    /**
     * Default address lookup for this VM.
     *
     * @return default lookup
     */
    public static DnsAddressLookup defaultLookup() {
        return DefaultAddressLookupFinder.defaultDnsAddressLookup();
    }

    private static final InetAddressComparator COMPARATOR = new InetAddressComparator();
    private final Function<InetAddress[], InetAddress[]> function;

    DnsAddressLookup(Function<InetAddress[], InetAddress[]> function) {
        this.function = function;
    }

    /**
     * Process obtained {@link InetAddress} array according to the selected lookup strategy.
     *
     * @param addresses addresses to be processed
     * @return processed array
     */
    public InetAddress[] filter(InetAddress[] addresses) {
        return function.apply(addresses);
    }

    private static class Ipv4Only implements Function<InetAddress[], InetAddress[]> {

        @Override
        public InetAddress[] apply(InetAddress[] addresses) {
            return Arrays.stream(addresses)
                    .filter(DnsAddressLookup::isIPv4)
                    .toArray(InetAddress[]::new);
        }
    }

    private static class Ipv6Only implements Function<InetAddress[], InetAddress[]> {

        @Override
        public InetAddress[] apply(InetAddress[] addresses) {
            return Arrays.stream(addresses)
                    .filter(DnsAddressLookup::isIPv6)
                    .toArray(InetAddress[]::new);
        }
    }

    private static class Ipv4Preferred implements Function<InetAddress[], InetAddress[]> {

        @Override
        public InetAddress[] apply(InetAddress[] addresses) {
            InetAddress[] copy = Arrays.copyOfRange(addresses, 0, addresses.length);
            Arrays.sort(copy, COMPARATOR);
            return copy;
        }
    }

    private static class Ipv6Preferred implements Function<InetAddress[], InetAddress[]> {

        @Override
        public InetAddress[] apply(InetAddress[] addresses) {
            InetAddress[] copy = Arrays.copyOfRange(addresses, 0, addresses.length);
            Arrays.sort(copy, (o1, o2) -> COMPARATOR.compare(o1, o2) * -1);
            return copy;
        }
    }

    private static final class InetAddressComparator implements Comparator<InetAddress>, Serializable {
        @Override
        public int compare(InetAddress o1, InetAddress o2) {
            //sorts IPv4 to be the first by default
            if (isIPv4(o1)) {
                if (isIPv4(o2)) {
                    return 0;
                } else {
                    return -1;
                }
            } else {
                if (isIPv6(o2)) {
                    return 0;
                } else {
                    return 1;
                }
            }
        }
    }

    private static boolean isIPv4(InetAddress address) {
        return address.getAddress().length == 4;
    }

    private static boolean isIPv6(InetAddress address) {
        return address.getAddress().length == 16;
    }

}
