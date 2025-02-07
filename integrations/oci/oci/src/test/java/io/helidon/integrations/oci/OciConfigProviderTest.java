/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class OciConfigProviderTest {
    @Test
    void testConfig() {
        final String FEDERATION_ENDPOINT = "https://auth.us-myregion-1.oraclecloud.com";
        final String TENANT_ID = "ocid1.tenancy.oc1..mytenancyid";
        final String REGION = "us-phoenix-1";
        final String CONFIG_FILE_PATH = "/path1/path2/.oci/config";

        Properties p = System.getProperties();
        p.put("helidon.oci.authentication-method", AuthenticationMethodConfigFile.METHOD);
        p.put("helidon.oci.federation-endpoint", FEDERATION_ENDPOINT);
        p.put("helidon.oci.tenant-id", TENANT_ID);
        p.put("HELIDON_OCI_REGION", REGION);
        p.put("helidon.oci.authentication.config-file.path", CONFIG_FILE_PATH);
        System.setProperties(p);

        // clean up ociConfig from OciConfigProvider
        OciConfigProvider.config(null);
        var ociConfig = new OciConfigProvider().get();
        assertThat(ociConfig.authenticationMethod(), is(AuthenticationMethodConfigFile.METHOD));
        assertThat(ociConfig.federationEndpoint().get().toString(), is(FEDERATION_ENDPOINT));
        assertThat(ociConfig.tenantId().get().toString(), is(TENANT_ID));
        assertThat(ociConfig.region().get().getRegionId(), is(REGION));
        assertThat(ociConfig.configFileMethodConfig().get().path().get(), is(CONFIG_FILE_PATH));
    }
}
