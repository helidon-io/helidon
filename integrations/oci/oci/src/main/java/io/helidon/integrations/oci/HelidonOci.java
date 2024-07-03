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
import java.lang.System.Logger.Level;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.webclient.api.WebClient;

import jakarta.json.JsonObject;

import static io.helidon.integrations.oci.OciConfigSupport.IMDS_HOSTNAME;
import static io.helidon.integrations.oci.OciConfigSupport.IMDS_URI;

/**
 * Helper methods for OCI integration.
 */
public final class HelidonOci {
    private static final System.Logger LOGGER = System.getLogger(HelidonOci.class.getName());
    private static final Header BEARER_HEADER = HeaderValues.create(HeaderNames.AUTHORIZATION, "Bearer Oracle");

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
            URI imdsUri = imdsUri(config);

            if (InetAddress.getByName(imdsUri.getHost())
                    .isReachable((int) timeout.toMillis())) {
                return imdsAvailable(config, imdsUri);
            }
            return false;
        } catch (IOException e) {
            LOGGER.log(Level.TRACE,
                       "IMDS service is not reachable, or timed out for address: "
                               + IMDS_HOSTNAME + ".",
                       e);
            return false;
        }
    }

    private static boolean imdsAvailable(OciConfig config, URI imdsUri) {
        // check if the endpoint is available (we have only checked the host/IP address)
        return imdsContent(config, imdsUri) != null;
    }

    static JsonObject imdsContent(OciConfig config, URI imdsUri) {
        int retries = config.imdsDetectRetries().orElse(0);

        Exception firstException = null;
        Status firstStatus = null;

        for (int retry = 0; retry <= retries; retry++) {
            try {
                var response = WebClient.builder()
                        .connectTimeout(config.imdsTimeout())
                        .readTimeout(config.imdsTimeout())
                        .baseUri(imdsUri)
                        .build()
                        .get("instance")
                        .accept(MediaTypes.APPLICATION_JSON)
                        .header(BEARER_HEADER)
                        .request();
                if (response.status() == Status.OK_200) {
                    return response.as(JsonObject.class);
                }
                firstStatus = firstStatus == null ? response.status() : firstStatus;
            } catch (Exception e) {
                firstException = firstException == null ? e : firstException;
            }
        }
        String message = "OCI IMDS not available on " + imdsUri;
        if (firstException == null) {
            LOGGER.log(Level.INFO, message + " Status received: " + firstStatus);
        } else {
            LOGGER.log(Level.INFO, message + " Exception logged only in TRACE");
            LOGGER.log(Level.TRACE, message, firstException);
        }

        return null;
    }

    static URI imdsUri(OciConfig config) {
        return config.imdsBaseUri()
                .orElse(IMDS_URI);
    }
}
