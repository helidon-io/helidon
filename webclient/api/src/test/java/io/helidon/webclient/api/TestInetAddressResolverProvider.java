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
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Child-JVM-only DNS fixtures. Mixed address streams deliberately ignore the JDK lookup policy to isolate
 * Helidon's own filtering. These streams do not model the JDK system resolver's property-dependent behavior.
 * The service descriptor is created only in the child classpath by {@link DefaultLookupJvmTest}.
 */
public final class TestInetAddressResolverProvider extends InetAddressResolverProvider {

    static final String IPV4_FIRST = "ipv4-first.fixture.invalid";
    static final String IPV6_FIRST = "ipv6-first.fixture.invalid";
    static final String IPV4_ONLY = "ipv4-only.fixture.invalid";

    private static boolean installed;

    public TestInetAddressResolverProvider() {
    }

    static boolean installed() {
        return installed;
    }

    @Override
    public InetAddressResolver get(Configuration configuration) {
        installed = true;
        return new InetAddressResolver() {
            @Override
            public Stream<InetAddress> lookupByName(String hostname, LookupPolicy lookupPolicy) throws UnknownHostException {
                return switch (hostname) {
                case IPV4_FIRST -> Arrays.stream(TestAddresses.ipv4First());
                case IPV6_FIRST -> Arrays.stream(TestAddresses.ipv6First());
                case IPV4_ONLY -> Stream.of(TestAddresses.V4_A);
                default -> throw new UnknownHostException(hostname);
                };
            }

            @Override
            public String lookupByAddress(byte[] address) throws UnknownHostException {
                throw new UnknownHostException("Reverse lookups are outside this fixture");
            }
        };
    }

    @Override
    public String name() {
        return "helidon-test-addresses";
    }
}
