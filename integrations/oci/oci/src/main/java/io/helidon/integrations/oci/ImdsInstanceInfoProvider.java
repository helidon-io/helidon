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

import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.service.registry.Service;
import io.helidon.webclient.api.WebClient;

import jakarta.json.JsonObject;

@Service.Provider
@Weight(Weighted.DEFAULT_WEIGHT - 100)
        // the type must be fully qualified, as it is code generated
class ImdsInstanceInfoProvider implements Supplier<Optional<io.helidon.integrations.oci.ImdsInstanceInfo>> {

    // Commonly used key names for instance data that will be used for the getter methods
    static final String DISPLAY_NAME = "displayName";
    static final String HOST_NAME = "hostname";
    static final String CANONICAL_REGION_NAME = "canonicalRegionName";
    static final String OCI_AD_NAME = "ociAdName";
    static final String FAULT_DOMAIN = "faultDomain";
    static final String REGION = "region";
    static final String COMPARTMENT_ID = "compartmentId";
    static final String TENANT_ID = "tenantId";

    private static final System.Logger LOGGER = System.getLogger(ImdsInstanceInfoProvider.class.getName());
    private static final Header BEARER_HEADER = HeaderValues.create(HeaderNames.AUTHORIZATION, "Bearer Oracle");
    private LazyValue<Optional<ImdsInstanceInfo>> instanceInfo;

    ImdsInstanceInfoProvider(Supplier<OciConfig> config) {
        this.instanceInfo = LazyValue.create(() -> Optional.ofNullable(loadInstanceMetadata(config.get())));
        new Exception().printStackTrace();
    }

    @Override
    public Optional<ImdsInstanceInfo> get() {
        new Exception().printStackTrace();
        return instanceInfo.get();
    }

    ImdsInstanceInfo loadInstanceMetadata(OciConfig ociConfig) {
        URI uri = ociConfig.imdsBaseUri().orElse(OciConfigSupport.IMDS_URI);

        // a one-off client instance, as this happens exactly once
        WebClient client = WebClient.builder()
                .baseUri(uri)
                .connectTimeout(ociConfig.imdsTimeout())
                .readTimeout(ociConfig.imdsTimeout())
                .build();

        JsonObject metadataJson;

        try (var response = client.get("/instance")
                .accept(MediaTypes.APPLICATION_JSON)
                .header(BEARER_HEADER)
                .request()) {

            if (response.status().family() != Status.Family.SUCCESSFUL) {
                LOGGER.log(Level.TRACE,
                           String.format("Cannot obtain instance metadata: status = %s, entity = '%s'",
                                         response.status(),
                                         response.as(String.class)));
                return null;
            }
            metadataJson = response.as(JsonObject.class);
        } catch (Exception e) {
            LOGGER.log(Level.TRACE,
                       "Cannot obtain instance metadata from: " + uri,
                       e);
            return null;
        }

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "IMDS response: " + metadataJson);
        }

        return ImdsInstanceInfo.builder()
                .displayName(metadataJson.getString(DISPLAY_NAME))
                .hostName(metadataJson.getString(HOST_NAME))
                .canonicalRegionName(metadataJson.getString(CANONICAL_REGION_NAME))
                .region(metadataJson.getString(REGION))
                .ociAdName(metadataJson.getString(OCI_AD_NAME))
                .faultDomain(metadataJson.getString(FAULT_DOMAIN))
                .compartmentId(metadataJson.getString(COMPARTMENT_ID))
                .tenantId(metadataJson.getString(TENANT_ID))
                .jsonObject(metadataJson)
                .build();
    }
}
