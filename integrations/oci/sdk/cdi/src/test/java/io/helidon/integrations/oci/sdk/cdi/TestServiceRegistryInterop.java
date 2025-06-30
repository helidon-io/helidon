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
package io.helidon.integrations.oci.sdk.cdi;

import java.io.IOException;

import io.helidon.microprofile.cdi.ServiceRegistryExtension;
import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.testing.AddBean;
import io.helidon.microprofile.testing.AddExtension;
import io.helidon.microprofile.testing.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.junit.jupiter.api.Test;

import static io.helidon.integrations.oci.sdk.cdi.Utils.configFileExists;
import static io.helidon.integrations.oci.sdk.cdi.Utils.imdsAvailable;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@AddExtension(ConfigCdiExtension.class)
@AddExtension(OciExtension.class)
@AddExtension(ServiceRegistryExtension.class)
@DisableDiscovery
@HelidonTest
class TestServiceRegistryInterop {

    @Inject
    private Provider<BasicAuthenticationDetailsProvider> adp;

    @Inject
    private BeanContainer bc;

    private TestServiceRegistryInterop() {
        super();
    }

    @Test
    void test() throws IOException {
        // Don't run this test if there's no possibility of ADP discovery/resolution; it will show up as "skipped" in
        // the test run.
        assumeTrue(imdsAvailable() || configFileExists());

        // Prove that both ServiceRegistryExtension and OciExtension have contributed beans.
        assertThat(this.bc.getBeans(BasicAuthenticationDetailsProvider.class).size(), is(2));

        // Prove that contextual reference acquisition nevertheless works properly (likely because the
        // ServiceRegistryExtension-contributed Bean is an enabled alternative).
        assertThat(this.adp.get(), not(nullValue()));

    }

}
