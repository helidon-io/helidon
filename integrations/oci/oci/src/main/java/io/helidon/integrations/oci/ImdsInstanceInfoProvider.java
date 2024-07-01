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

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.integrations.oci.spi.OciImdsInstanceInfo;
import io.helidon.service.registry.Service;

import jakarta.json.JsonObject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Service.Provider
@Weight(Weighted.DEFAULT_WEIGHT - 100)
class ImdsInstanceInfoProvider implements OciImdsInstanceInfo {
    private static final System.Logger LOGGER = System.getLogger(ImdsInstanceInfoProvider.class.getName());

    // Commonly used key names for instance data that will be used for the getter methods
    static final String DISPLAY_NAME = "displayName";
    static final String HOST_NAME = "hostname";
    static final String CANONICAL_REGION_NAME = "canonicalRegionName";
    static final String OCI_AD_NAME = "ociAdName";
    static final String FAULT_DOMAIN = "faultDomain";
    static final String REGION = "region";
    static final String COMPARTMENT_ID = "compartmentId";
    static final String TENANT_ID = "tenantId";

    private LazyValue<Optional<ImdsInstanceInfo>> instanceInfo;

    ImdsInstanceInfoProvider(Supplier<OciConfig> config) {
        this.instanceInfo = LazyValue.create(() -> Optional.ofNullable(loadInstanceMetadata(config.get())));
    }

    @Override
    public Optional<ImdsInstanceInfo> instanceInfo() {
        return instanceInfo.get();
    }

    ImdsInstanceInfo loadInstanceMetadata(OciConfig ociConfig) {
        Optional<URI> uri = ociConfig.imdsBaseUri();
        String instanceMetadataUri = uri.isPresent() ? uri.get().toString() : null;
        Client client = ClientBuilder.newBuilder()
                .connectTimeout(ociConfig.imdsTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(ociConfig.imdsTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .build();
        try {
            Response response = client.target(instanceMetadataUri)
                    .path("instance")
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer Oracle")
                    .get();
            System.out.println("Instance Metadata response: " + response);
            if (response.getStatus() >= 300) {
                LOGGER.log(System.Logger.Level.TRACE,
                           String.format("Cannot obtain instance metadata: status = %d, entity = '%s'",
                                         response.getStatus(),
                                         response.readEntity(String.class)));
            }
            JsonObject instanceInfo = response.readEntity(JsonObject.class);
            return ImdsInstanceInfo.builder()
                    .displayName(instanceInfo.getString(DISPLAY_NAME))
                    .hostName(instanceInfo.getString(HOST_NAME))
                    .canonicalRegionName(instanceInfo.getString(CANONICAL_REGION_NAME))
                    .region(instanceInfo.getString(REGION))
                    .ociAdName(instanceInfo.getString(OCI_AD_NAME))
                    .faultDomain(instanceInfo.getString(FAULT_DOMAIN))
                    .compartmentId(instanceInfo.getString(COMPARTMENT_ID))
                    .tenantId(instanceInfo.getString(TENANT_ID))
                    .jsonObject(instanceInfo)
                    .build();
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.TRACE,
                       "Cannot obtain instance metadata",
                       e);
        } finally {
            client.close();
        }

        return null;
    }
}
