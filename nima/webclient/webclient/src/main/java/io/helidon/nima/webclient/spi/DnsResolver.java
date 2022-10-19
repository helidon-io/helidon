/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.webclient.spi;

import java.net.InetAddress;

import io.helidon.nima.webclient.DnsAddressLookup;

/**
 * DNS resolving interface.
 */
public interface DnsResolver {

    /**
     * Whether to use standard Java DNS resolver.
     * If this method returns true, {@link #resolveAddress(String, DnsAddressLookup)} method is not invoked and
     * no {@link DnsAddressLookup} preferences will be applied.
     *
     * @return use standard Java resolver
     */
    default boolean useDefaultJavaResolver() {
        return false;
    }

    /**
     * Resolve hostname to {@link InetAddress}.
     *
     * @param hostname          hostname to resolve
     * @param dnsAddressLookup  allowed version of the IP
     * @return resolved InetAddress instance
     */
    InetAddress resolveAddress(String hostname, DnsAddressLookup dnsAddressLookup);

}
