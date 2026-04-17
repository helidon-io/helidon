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

import java.net.URI;
import java.util.Map;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.json.JsonObject;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class InstanceInfoProviderTest {

    private ServiceRegistryManager registryManager;
    private ServiceRegistry registry;

    private final int port;

    InstanceInfoProviderTest(URI uri) {
        this.port = uri.getPort();
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/opc/v2/instance", ImdsEmulator::emulateImds);
    }

    void setUp(Config config) {
        OciConfigProvider.config(OciConfig.create(config));
        registryManager = ServiceRegistryManager.create();
        registry = registryManager.registry();
    }

    @AfterEach
    void tearDown() {
        registry = null;
        if (registryManager != null) {
            registryManager.shutdown();
        }
    }

    @Test
    @SuppressWarnings("removal")
    void testInstanceInfoFromImds() {
        Config config = Config.just(ConfigSources.create(Map.of("imds-base-uri",
                                                                "http://localhost:%d/opc/v2/".formatted(port))));
        setUp(config);

        ImdsInstanceInfo instanceInfo = registry.get(ImdsInstanceInfo.class);
        assertInstanceInfoValues(
                instanceInfo.canonicalRegionName(),
                instanceInfo.displayName(),
                instanceInfo.hostName(),
                instanceInfo.region(),
                instanceInfo.ociAdName(),
                instanceInfo.faultDomain(),
                instanceInfo.compartmentId(),
                instanceInfo.tenantId());
        assertInstanceInfoValues(
                instanceInfo.json().stringValue(ImdsInstanceInfoProvider.CANONICAL_REGION_NAME).orElseThrow(),
                instanceInfo.json().stringValue(ImdsInstanceInfoProvider.DISPLAY_NAME).orElseThrow(),
                instanceInfo.json().stringValue(ImdsInstanceInfoProvider.HOST_NAME).orElseThrow(),
                instanceInfo.json().stringValue(ImdsInstanceInfoProvider.REGION).orElseThrow(),
                instanceInfo.json().stringValue(ImdsInstanceInfoProvider.OCI_AD_NAME).orElseThrow(),
                instanceInfo.json().stringValue(ImdsInstanceInfoProvider.FAULT_DOMAIN).orElseThrow(),
                instanceInfo.json().stringValue(ImdsInstanceInfoProvider.COMPARTMENT_ID).orElseThrow(),
                instanceInfo.json().stringValue(ImdsInstanceInfoProvider.TENANT_ID).orElseThrow());
        assertInstanceInfoValues(
                instanceInfo.jsonObject().getString(ImdsInstanceInfoProvider.CANONICAL_REGION_NAME),
                instanceInfo.jsonObject().getString(ImdsInstanceInfoProvider.DISPLAY_NAME),
                instanceInfo.jsonObject().getString(ImdsInstanceInfoProvider.HOST_NAME),
                instanceInfo.jsonObject().getString(ImdsInstanceInfoProvider.REGION),
                instanceInfo.jsonObject().getString(ImdsInstanceInfoProvider.OCI_AD_NAME),
                instanceInfo.jsonObject().getString(ImdsInstanceInfoProvider.FAULT_DOMAIN),
                instanceInfo.jsonObject().getString(ImdsInstanceInfoProvider.COMPARTMENT_ID),
                instanceInfo.jsonObject().getString(ImdsInstanceInfoProvider.TENANT_ID));
    }

    @Test
    @SuppressWarnings("removal")
    void testHelidonJsonBuilderPopulatesDeprecatedJsonObject() {
        JsonObject metadataJson = metadataJson("helidon-json");

        ImdsInstanceInfo instanceInfo = instanceInfoBuilder()
                .json(metadataJson)
                .build();

        assertThat(instanceInfo.json(), is(metadataJson));
        assertThat(instanceInfo.jsonObject().getString(ImdsInstanceInfoProvider.DISPLAY_NAME), is("helidon-json"));
    }

    @Test
    @SuppressWarnings("removal")
    void testDeprecatedJsonObjectBuilderPopulatesHelidonJson() {
        jakarta.json.JsonObject metadataJson = jakarta.json.Json.createObjectBuilder()
                .add(ImdsInstanceInfoProvider.CANONICAL_REGION_NAME, ImdsEmulator.CANONICAL_REGION_NAME)
                .add(ImdsInstanceInfoProvider.DISPLAY_NAME, "jsonp")
                .add(ImdsInstanceInfoProvider.HOST_NAME, ImdsEmulator.HOST_NAME)
                .add(ImdsInstanceInfoProvider.REGION, ImdsEmulator.REGION)
                .add(ImdsInstanceInfoProvider.OCI_AD_NAME, ImdsEmulator.OCI_AD_NAME)
                .add(ImdsInstanceInfoProvider.FAULT_DOMAIN, ImdsEmulator.FAULT_DOMAIN)
                .add(ImdsInstanceInfoProvider.COMPARTMENT_ID, ImdsEmulator.COMPARTMENT_ID)
                .add(ImdsInstanceInfoProvider.TENANT_ID, ImdsEmulator.TENANT_ID)
                .build();

        ImdsInstanceInfo instanceInfo = instanceInfoBuilder()
                .jsonObject(metadataJson)
                .build();

        assertThat(instanceInfo.json().stringValue(ImdsInstanceInfoProvider.DISPLAY_NAME).orElseThrow(), is("jsonp"));
        assertThat(instanceInfo.jsonObject(), is(metadataJson));
    }

    @Test
    @SuppressWarnings("removal")
    void testHelidonJsonWinsWhenBothJsonTypesConfigured() {
        JsonObject preferredJson = metadataJson("preferred");
        jakarta.json.JsonObject otherJson = jakarta.json.Json.createObjectBuilder()
                .add(ImdsInstanceInfoProvider.CANONICAL_REGION_NAME, ImdsEmulator.CANONICAL_REGION_NAME)
                .add(ImdsInstanceInfoProvider.DISPLAY_NAME, "legacy")
                .add(ImdsInstanceInfoProvider.HOST_NAME, ImdsEmulator.HOST_NAME)
                .add(ImdsInstanceInfoProvider.REGION, ImdsEmulator.REGION)
                .add(ImdsInstanceInfoProvider.OCI_AD_NAME, ImdsEmulator.OCI_AD_NAME)
                .add(ImdsInstanceInfoProvider.FAULT_DOMAIN, ImdsEmulator.FAULT_DOMAIN)
                .add(ImdsInstanceInfoProvider.COMPARTMENT_ID, ImdsEmulator.COMPARTMENT_ID)
                .add(ImdsInstanceInfoProvider.TENANT_ID, ImdsEmulator.TENANT_ID)
                .build();

        ImdsInstanceInfo instanceInfo = instanceInfoBuilder()
                .jsonObject(otherJson)
                .json(preferredJson)
                .build();

        assertThat(instanceInfo.json(), is(preferredJson));
        assertThat(instanceInfo.jsonObject().getString(ImdsInstanceInfoProvider.DISPLAY_NAME), is("preferred"));
    }

    private static ImdsInstanceInfo.Builder instanceInfoBuilder() {
        return ImdsInstanceInfo.builder()
                .canonicalRegionName(ImdsEmulator.CANONICAL_REGION_NAME)
                .displayName(ImdsEmulator.DISPLAY_NAME)
                .hostName(ImdsEmulator.HOST_NAME)
                .region(ImdsEmulator.REGION)
                .ociAdName(ImdsEmulator.OCI_AD_NAME)
                .faultDomain(ImdsEmulator.FAULT_DOMAIN)
                .compartmentId(ImdsEmulator.COMPARTMENT_ID)
                .tenantId(ImdsEmulator.TENANT_ID);
    }

    private static JsonObject metadataJson(String displayName) {
        return JsonObject.builder()
                .set(ImdsInstanceInfoProvider.CANONICAL_REGION_NAME, ImdsEmulator.CANONICAL_REGION_NAME)
                .set(ImdsInstanceInfoProvider.DISPLAY_NAME, displayName)
                .set(ImdsInstanceInfoProvider.HOST_NAME, ImdsEmulator.HOST_NAME)
                .set(ImdsInstanceInfoProvider.REGION, ImdsEmulator.REGION)
                .set(ImdsInstanceInfoProvider.OCI_AD_NAME, ImdsEmulator.OCI_AD_NAME)
                .set(ImdsInstanceInfoProvider.FAULT_DOMAIN, ImdsEmulator.FAULT_DOMAIN)
                .set(ImdsInstanceInfoProvider.COMPARTMENT_ID, ImdsEmulator.COMPARTMENT_ID)
                .set(ImdsInstanceInfoProvider.TENANT_ID, ImdsEmulator.TENANT_ID)
                .build();
    }

    private static void assertInstanceInfoValues(String canonicalRegionName,
                                                 String displayName,
                                                 String hostName, String region,
                                                 String ociAdName,
                                                 String faultDomain,
                                                 String compartmentId,
                                                 String tenantId) {
        assertThat(canonicalRegionName, is(ImdsEmulator.CANONICAL_REGION_NAME));
        assertThat(displayName, is(ImdsEmulator.DISPLAY_NAME));
        assertThat(hostName, is(ImdsEmulator.HOST_NAME));
        assertThat(region, is(ImdsEmulator.REGION));
        assertThat(ociAdName, is(ImdsEmulator.OCI_AD_NAME));
        assertThat(faultDomain, is(ImdsEmulator.FAULT_DOMAIN));
        assertThat(compartmentId, is(ImdsEmulator.COMPARTMENT_ID));
        assertThat(tenantId, is(ImdsEmulator.TENANT_ID));
    }

    public static class ImdsEmulator {
        static String CANONICAL_REGION_NAME = "us-helidon-1";
        static String DISPLAY_NAME = "helidon-server";
        static String HOST_NAME = DISPLAY_NAME;
        static String REGION = "hel";
        static String OCI_AD_NAME = "hel-ad-1";
        static String FAULT_DOMAIN = "FAULT-DOMAIN-3";
        static String COMPARTMENT_ID = "ocid1.compartment.oc1..dummyCompartment";
        static String TENANT_ID = "ocid1.tenancy.oc1..dummyTenancy";
        private static final String IMDS_RESPONSE = """
                {
                  "agentConfig": {
                    "allPluginsDisabled": false,
                    "managementDisabled": false,
                    "monitoringDisabled": false
                  },
                  "availabilityDomain": "RPOG:US-ASHBURN-AD-2",
                  "canonicalRegionName": "%s",
                  "compartmentId": "%s",
                  "definedTags": {
                    "Oracle-Tags": {
                      "CreatedBy": "ocid1.flock.oc1..aaaaaaaafiugcy22eekoer3usi7rlyjatcftll2gsakgi42bzsl5zpfbshbq",
                      "CreatedOn": "2024-02-07T23:31:16.518Z"
                    }
                  },
                  "displayName": "%s",
                  "faultDomain": "%s",
                  "hostname": "%s",
                  "id": "ocid1.instance.oc1.iad.dummy",
                  "image": "ocid1.image.oc1.iad.dummy",
                  "instancePoolId": "ocid1.instancepool.oc1.iad.dummy",
                  "metadata": {
                    "compute_management": {
                      "instance_configuration": {
                        "state": "SUCCEEDED"
                      }
                    },
                    "hostclass": "helidon-overlay"
                  },
                  "ociAdName": "%s",
                  "region": "%s",
                  "regionInfo": {
                    "realmDomainComponent": "oraclecloud.com",
                    "realmKey": "oc1",
                    "regionIdentifier": "us-ashburn-1",
                    "regionKey": "IAD"
                  },
                  "shape": "VM.Standard.A1.Flex",
                  "shapeConfig": {
                    "maxVnicAttachments": 2,
                    "memoryInGBs": 12.0,
                    "networkingBandwidthInGbps": 2.0,
                    "ocpus": 2.0
                  },
                  "state": "Running",
                  "tenantId": "%s",
                  "timeCreated": 1707348698696
                }
                """.formatted(CANONICAL_REGION_NAME,
                              COMPARTMENT_ID,
                              DISPLAY_NAME,
                              FAULT_DOMAIN,
                              HOST_NAME,
                              OCI_AD_NAME,
                              REGION,
                              TENANT_ID);

        private static void emulateImds(ServerRequest req, ServerResponse res) {
            res.headers().contentType(MediaTypes.APPLICATION_JSON);
            res.send(IMDS_RESPONSE);
        }
    }
}
