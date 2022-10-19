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

package io.helidon.nima.webclient;

import java.net.InetAddress;

import io.helidon.nima.webclient.spi.DnsResolver;

/**
 * No Nima specific DNS resolver to be used. Connection creation fallbacks to the standard Java approach.
 */
public final class NoDnsResolver implements DnsResolver {

    private NoDnsResolver() {
    }

    /**
     * Create new instance.
     *
     * @return new instance
     */
    public static NoDnsResolver create() {
        return new NoDnsResolver();
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
