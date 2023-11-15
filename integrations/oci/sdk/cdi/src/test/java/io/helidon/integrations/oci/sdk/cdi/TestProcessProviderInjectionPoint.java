/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;

import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.ailanguage.AIServiceLanguage;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@AddBean(TestProcessProviderInjectionPoint.ExampleBean.class)
@AddExtension(ConfigCdiExtension.class)
@AddExtension(OciExtension.class)
@DisableDiscovery
@HelidonTest
class TestProcessProviderInjectionPoint {


    /*
     * Instance fields.
     */


    @Inject
    private Provider<ExampleBean> exampleBeanProvider;


    /*
     * Test methods.
     */


    @Test
    void testProcessProviderInjectionPoint() throws IOException {
        // Don't run this test if there's NO ADP anywhere; it will show up as "skipped" in the test run.
        assumeTrue(imdsAvailable() || configFileExists());

        assertThat(this.exampleBeanProvider.get(), is(not(nullValue())));
    }


    /*
     * Static methods.
     */


    private static final boolean configFileExists() throws IOException {
        try {
            return
                ConfigFileReader.parse(System.getProperty("oci.config.file", "~/.oci/config"),
                                       System.getProperty("oci.auth.profile")) != null;
        } catch (final FileNotFoundException ignored) {
            return false;
        }
    }

    private static final boolean imdsAvailable() {
        try {
            return InetAddress.getByName(System.getProperty("oci.imds.hostname", "169.254.169.254"))
                .isReachable(Integer.getInteger("oci.imds.timeout", 100).intValue());
        } catch (final IOException ignored) {
            return false;
        }
    }


    /*
     * Inner and nested classes.
     */


    @Dependent
    static class ExampleBean {

        // Required by the CDI specification.
        @Deprecated // For CDI use only.
        ExampleBean() {
            super();
        }

        @Inject
        private ExampleBean(Provider<AIServiceLanguage> p) {
            super();
            assertThat(p, is(not(nullValue())));
            assertThat(p.get(), is(not(nullValue())));
        }

    }

}
