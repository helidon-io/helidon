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

import java.util.Optional;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.integrations.oci.spi.OciImdsInstanceInfo;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddBean(InstanceInfoProviderTest.IMDSSimulationResource.class)
class InstanceInfoProviderTest {

    private ServiceRegistryManager registryManager;
    private ServiceRegistry registry;

    static int serverPort = CDI.current().getBeanManager().getExtension(ServerCdiExtension.class).port();

    void setUp(Config config) {
        OciConfigProvider.config(OciConfig.create(config.get("helidon.oci")));
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
    void testInstanceInfoFromImds() {
        String yamlConfig = """
                helidon.oci:
                  imds-base-uri: http://localhost:%d/opc/v2/
                """.formatted(serverPort);
        Config config = Config.just(ConfigSources.create(yamlConfig, MediaTypes.APPLICATION_YAML));
        OciConfigProvider.config(OciConfig.create(config.get("helidon.oci")));
        registryManager = ServiceRegistryManager.create();
        setUp(config);

        OciImdsInstanceInfo instanceInfo = registry.get(OciImdsInstanceInfo.class);
        assertInstanceInfoValues(
                instanceInfo.instanceInfo().get().canonicalRegionName(),
                instanceInfo.instanceInfo().get().displayName(),
                instanceInfo.instanceInfo().get().hostName(),
                instanceInfo.instanceInfo().get().region(),
                instanceInfo.instanceInfo().get().ociAdName(),
                instanceInfo.instanceInfo().get().faultDomain(),
                instanceInfo.instanceInfo().get().compartmentId(),
                instanceInfo.instanceInfo().get().tenantId());
        assertInstanceInfoValues(
                instanceInfo.instanceInfo().get().jsonObject().getString(ImdsInstanceInfoProvider.CANONICAL_REGION_NAME),
                instanceInfo.instanceInfo().get().jsonObject().getString(ImdsInstanceInfoProvider.DISPLAY_NAME),
                instanceInfo.instanceInfo().get().jsonObject().getString(ImdsInstanceInfoProvider.HOST_NAME),
                instanceInfo.instanceInfo().get().jsonObject().getString(ImdsInstanceInfoProvider.REGION),
                instanceInfo.instanceInfo().get().jsonObject().getString(ImdsInstanceInfoProvider.OCI_AD_NAME),
                instanceInfo.instanceInfo().get().jsonObject().getString(ImdsInstanceInfoProvider.FAULT_DOMAIN),
                instanceInfo.instanceInfo().get().jsonObject().getString(ImdsInstanceInfoProvider.COMPARTMENT_ID),
                instanceInfo.instanceInfo().get().jsonObject().getString(ImdsInstanceInfoProvider.TENANT_ID));
    }

    private static void assertInstanceInfoValues(String canonicalRegionName,
                                                 String displayName,
                                                 String hostName, String region,
                                                 String ociAdName,
                                                 String faultDomain,
                                                 String compartmentId,
                                                 String tenantId) {
        assertThat(canonicalRegionName, is(IMDSSimulationResource.CANONICAL_REGION_NAME));
        assertThat(displayName, is(IMDSSimulationResource.DISPLAY_NAME));
        assertThat(hostName, is(IMDSSimulationResource.HOST_NAME));
        assertThat(region, is(IMDSSimulationResource.REGION));
        assertThat(ociAdName, is(IMDSSimulationResource.OCI_AD_NAME));
        assertThat(faultDomain, is(IMDSSimulationResource.FAULT_DOMAIN));
        assertThat(compartmentId, is(IMDSSimulationResource.COMPARTMENT_ID));
        assertThat(tenantId, is(IMDSSimulationResource.TENANT_ID));
    }

    // This bean will simulate an Instance Metadata Service response
    @Path("/")
    public static class IMDSSimulationResource {
        static String CANONICAL_REGION_NAME = "us-helidon-1";
        static String DISPLAY_NAME = "helidon-server";
        static String HOST_NAME = DISPLAY_NAME;
        static String REGION = "iad";
        static String OCI_AD_NAME = "iad-ad-1";
        static String FAULT_DOMAIN = "FAULT-DOMAIN-3";
        static String COMPARTMENT_ID = "ocid1.compartment.oc1..dummyCompartment";
        static String TENANT_ID = "ocid1.tenancy.oc1..dummyTenancy";

        @GET
        @Path("/opc/v2/instance")
        public String instanceInfo() {
            return """
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
        }
    }
}
