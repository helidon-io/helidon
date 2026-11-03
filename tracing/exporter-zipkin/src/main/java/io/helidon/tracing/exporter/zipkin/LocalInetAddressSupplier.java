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

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.helidon.tracing.exporter.zipkin;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

class LocalInetAddressSupplier implements Supplier<InetAddress> {

    private static final Logger LOGGER = Logger.getLogger(LocalInetAddressSupplier.class.getName());
    private static final LocalInetAddressSupplier INSTANCE =
            new LocalInetAddressSupplier(findLocalIp());
    private final InetAddress inetAddress;

    private LocalInetAddressSupplier(InetAddress inetAddress) {
        this.inetAddress = inetAddress;
    }

    @Override
    public InetAddress get() {
        return inetAddress;
    }

    /** Logic borrowed from brave.internal.Platform.produceLocalEndpoint. */
    private static InetAddress findLocalIp() {
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                Enumeration<InetAddress> addresses = nic.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address.isSiteLocalAddress()) {
                        return address;
                    }
                }
            }
        } catch (Exception e) {
            // don't crash the caller if there was a problem reading nics.
            LOGGER.log(Level.FINE, "error reading nics", e);
        }
        return null;
    }

    static LocalInetAddressSupplier getInstance() {
        return INSTANCE;
    }
}
