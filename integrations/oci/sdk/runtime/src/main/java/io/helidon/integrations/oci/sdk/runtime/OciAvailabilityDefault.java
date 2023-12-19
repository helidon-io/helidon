/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.sdk.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import io.helidon.common.Weight;
import io.helidon.inject.Services;
import io.helidon.inject.service.Injection;

import com.oracle.bmc.Region;

import static com.oracle.bmc.auth.AbstractFederationClientAuthenticationDetailsProviderBuilder.METADATA_SERVICE_BASE_URL;

/**
 * This (overridable) implementation will check the {@link OciConfig} for {@code IMDS} availability. And if it is found to be
 * available, will also perform a secondary check on {@link Region#getRegionFromImds()} to ensure it returns a non-null value.
 */
@Injection.Singleton
@Weight(Services.INJECT_WEIGHT)
class OciAvailabilityDefault implements OciAvailability {
    private static final String OPC_PATH = getOpcPath(METADATA_SERVICE_BASE_URL);

    @Override
    public boolean isRunningOnOci(OciConfig ociConfig) {
        return runningOnOci(ociConfig);
    }

    static boolean runningOnOci(OciConfig ociConfig) {
        if (!canReach(ociConfig.imdsHostName(), (int) ociConfig.imdsTimeout().toMillis())) {
            return false;
        }

        return (Region.getRegionFromImds("http://" + ociConfig.imdsHostName() + OPC_PATH) != null);
    }

    static boolean canReach(String address,
                            int timeoutInMills) {
        if (address == null) {
            return false;
        }

        InetAddress imds;
        try {
            imds = InetAddress.getByName(address);
        } catch (UnknownHostException unknownHostException) {
            throw new UncheckedIOException(unknownHostException.getMessage(), unknownHostException);
        }

        try {
            return imds.isReachable(timeoutInMills);
        } catch (ConnectException connectException) {
            return false;
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException.getMessage(), ioException);
        }
    }

    static String getOpcPath(String metadataServiceBaseURL) {
        String input = metadataServiceBaseURL;
        int index = -1;
        for (int nth = 3; nth > 0; nth--) {
            index = input.indexOf("/", index + 1);
            if (index == -1) {
                throw new IllegalStateException("Unable to find opc path from '" + metadataServiceBaseURL + "'");
            }
        }
        return metadataServiceBaseURL.substring(index);
    }
}
