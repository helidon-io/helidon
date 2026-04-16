/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

import java.io.StringReader;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.json.JsonObject;
import io.helidon.service.registry.Service;

import jakarta.json.Json;

// the type must be fully qualified, as it is code generated
@Service.Provider
@Weight(Weighted.DEFAULT_WEIGHT - 100)
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

    private LazyValue<Optional<ImdsInstanceInfo>> instanceInfo;

    ImdsInstanceInfoProvider(Supplier<OciConfig> config) {
        this.instanceInfo = LazyValue.create(() -> Optional.ofNullable(loadInstanceMetadata(config.get())));
    }

    @Override
    public Optional<ImdsInstanceInfo> get() {
        return instanceInfo.get();
    }

    ImdsInstanceInfo loadInstanceMetadata(OciConfig ociConfig) {
        JsonObject metadataJson = HelidonOci.imdsContent(ociConfig, HelidonOci.imdsUri(ociConfig));
        if (metadataJson != null) {
            return ImdsInstanceInfo.builder()
                    .displayName(requiredString(metadataJson, DISPLAY_NAME))
                    .hostName(requiredString(metadataJson, HOST_NAME))
                    .canonicalRegionName(requiredString(metadataJson, CANONICAL_REGION_NAME))
                    .region(requiredString(metadataJson, REGION))
                    .ociAdName(requiredString(metadataJson, OCI_AD_NAME))
                    .faultDomain(requiredString(metadataJson, FAULT_DOMAIN))
                    .compartmentId(requiredString(metadataJson, COMPARTMENT_ID))
                    .tenantId(requiredString(metadataJson, TENANT_ID))
                    .jsonObject(toJakartaJsonObject(metadataJson))
                    .build();
        }
        return null;
    }

    private static String requiredString(JsonObject metadataJson, String key) {
        return metadataJson.stringValue(key)
                .orElseThrow(() -> new IllegalStateException("Missing IMDS metadata field: " + key));
    }

    private static jakarta.json.JsonObject toJakartaJsonObject(JsonObject metadataJson) {
        try (var reader = Json.createReader(new StringReader(metadataJson.toString()))) {
            return reader.readObject();
        }
    }
}
