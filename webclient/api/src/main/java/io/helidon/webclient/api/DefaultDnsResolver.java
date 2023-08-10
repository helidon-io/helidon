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

import java.net.InetAddress;

import io.helidon.webclient.spi.DnsResolver;

/**
 * Default DNS resolver. Connection creation fallbacks to the standard Java approach.
 *
 * @see io.helidon.webclient.api.WebClientConfig.Builder#dnsResolver(io.helidon.webclient.spi.DnsResolver)
 */
public final class DefaultDnsResolver implements DnsResolver {

    private DefaultDnsResolver() {
    }

    /**
     * Create new instance.
     *
     * @return new instance
     */
    public static DefaultDnsResolver create() {
        return new DefaultDnsResolver();
    }

    @Override
    public boolean useDefaultJavaResolver() {
        return true;
    }

    @Override
    public InetAddress resolveAddress(String hostname, DnsAddressLookup dnsAddressLookup) {
        throw new IllegalStateException("This DNS resolver is not meant to be used.");
    }

}
