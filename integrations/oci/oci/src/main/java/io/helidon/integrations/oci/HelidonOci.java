/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;

import static io.helidon.integrations.oci.OciConfigSupport.IMDS_HOSTNAME;

/**
 * Helper methods for OCI integration.
 */
public final class HelidonOci {
    private static final System.Logger LOGGER = System.getLogger(HelidonOci.class.getName());

    private HelidonOci() {
    }

    /**
     * Check whether IMDS (metadata service endpoint) is available or not.
     *
     * @param config OCI meta configuration, allowing a customized IMDS endpoint
     * @return whether the metadata service is available
     */
    public static boolean imdsAvailable(OciConfig config) {
        Duration timeout = config.imdsTimeout();

        try {
            if (InetAddress.getByName(config.imdsBaseUri().map(URI::getHost).orElse(IMDS_HOSTNAME))
                    .isReachable((int) timeout.toMillis())) {
                return RegionProviderSdk.regionFromImdsDirect(config) != null;
            }
            return false;
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.TRACE,
                       "IMDS service is not reachable, or timed out for address: "
                               + IMDS_HOSTNAME + ".",
                       e);
            return false;
        }
    }
}
