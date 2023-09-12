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

import java.util.Map;
import java.util.Set;

import io.helidon.config.Config;

import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;

import static io.helidon.config.mp.MpConfig.toHelidonConfig;
import static io.helidon.integrations.oci.sdk.runtime.OciExtension.configSupplier;
import static io.helidon.integrations.oci.sdk.runtime.OciExtension.configuredAuthenticationDetailsProvider;
import static io.helidon.integrations.oci.sdk.runtime.OciExtension.fallbackConfigSupplier;
import static io.helidon.integrations.oci.sdk.runtime.OciExtension.isSufficientlyConfigured;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class TestOciSdkRuntimeOciExtension {

    @Test
    void testOciSdkRuntimeOciExtensionUsesEmptyMicroProfileConfigProperly() {
        // Create a MicroProfile Config Config object that is almost entirely empty, save for a single property used to
        // test that this creation worked.
        org.eclipse.microprofile.config.Config emptyMicroProfileConfig = ConfigProviderResolver.instance().getBuilder()
            .withSources(new MinimalConfigSource(Map.of("valid", "true")))
            .build();

        // (Sanity check.)
        assertThat(emptyMicroProfileConfig.getValue("valid", String.class), is(equalTo("true")));

        // Convert the MicroProfile Config Config into a Helidon Config.
        Config hc = toHelidonConfig(emptyMicroProfileConfig);
        assertThat(hc, not(equalTo(nullValue())));

        // Pass it to the io.helidon.integrations.oci.sdk.runtime.OciExtension class as its "fallback config supplier".
        fallbackConfigSupplier(() -> hc);

        // Assert that this method call worked.
        assertThat(configSupplier().get(), is(equalTo(hc)));

        // Assert that the Helidon Config is not "sufficiently configured", for the purposes of the
        // io.helidon.integrations.oci.sdk.runtime.OciExtension class, since it does not specify an explicit
        // authentication strategy.  This effectively tests whether "auto" is
        // io.helidon.integrations.oci.sdk.runtime.OciExtension's effective authentication strategy.
        assertThat(isSufficientlyConfigured(hc), is(equalTo(false)));
    }

    @Test
    void testOciSdkRuntimeOciExtensionUsesNonEmptyMicroProfileConfigProperly() {
        // Create a MicroProfile Config Config object that contains "oci.auth-strategies" set to "config" only. Note
        // that, deliberately, although its value is "config", there are deliberately no other properties. The
        // io.helidon.integrations.oci.sdk.runtime.OciExtension class will indicate that this is a valid strategy, but
        // (correctly) that it is not available since we didn't set any other properties.
        org.eclipse.microprofile.config.Config c = ConfigProviderResolver.instance().getBuilder()
            .withSources(new MinimalConfigSource(Map.of("oci.auth-strategies", "config")))
            .build();

        // (Sanity check.)
        assertThat(c.getValue("oci.auth-strategies", String.class), is(equalTo("config")));

        // Convert the MicroProfile Config Config into a Helidon Config.
        Config hc = toHelidonConfig(c);
        assertThat(hc, not(equalTo(nullValue())));

        // Pass it to the io.helidon.integrations.oci.sdk.runtime.OciExtension class as its "fallback config supplier".
        fallbackConfigSupplier(() -> hc);

        // Assert that this method call worked.
        assertThat(configSupplier().get(), is(equalTo(hc)));

        // Assert that the selected strategy is not, in fact, available, since we didn't supply enough information for
        // it to be so. We have to pass "false" for "verifyIsAvailable" because otherwise, despite the fact we have
        // specified an unavailable but explicit strategy, it will try to fall back to a different one.
        assertThat(configuredAuthenticationDetailsProvider(false), is(equalTo(SimpleAuthenticationDetailsProvider.class)));
    }

    private static class MinimalConfigSource implements ConfigSource {

        private final Map<String, String> properties;

        private MinimalConfigSource(Map<String, String> properties) {
            super();
            this.properties = Map.copyOf(properties);
        }

        @Override
        public String getName() {
            return this.getClass().getName();
        }

        @Override
        public Map<String, String> getProperties() {
            return this.properties;
        }

        @Override
        public Set<String> getPropertyNames() {
            return this.getProperties().keySet();
        }

        @Override
        public String getValue(String propertyName) {
            return propertyName == null ? null : this.getProperties().get(propertyName);
        }

    }

}
