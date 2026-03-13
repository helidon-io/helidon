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
package io.helidon.integrations.oci.metrics.cdi;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

import io.helidon.integrations.oci.metrics.OciMetricsConfig;
import io.helidon.integrations.oci.metrics.OciMetricsConfigBase;
import io.helidon.integrations.oci.metrics.OciMetricsSupport;
import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

@HelidonTest
@DisableDiscovery
@AddBean(OverridingOciMetricsBean.class)
// Use OciMetricsCdiExtensionTest.MockOciMonitoringExtension to avoid Monitoring client from being instantiated
// with OCI authentication
@AddExtension(OciMetricsCdiExtensionTest.MockOciMonitoringExtension.class)
// Supporting Extensions for CDI
@AddExtension(ServerCdiExtension.class)
@AddExtension(JaxRsCdiExtension.class)
@AddExtension(ConfigCdiExtension.class)
@AddConfig(key = "oci.metrics.product", value = TestOverridingBean.PRODUCT)
@AddConfig(key = "oci.metrics.fleet", value = TestOverridingBean.FLEET)
class TestOverridingBean {

    static final String PRODUCT = "overriding-product-name";
    static final String FLEET = "overriding-fleet";

    // The injected bean should be the test one which overrides the default implementation.
    @Inject
    private OciMetricsBean bean;

    @Test
    void checkOverriding() throws NoSuchFieldException, IllegalAccessException {
        assertThat("Config key", bean.configKey(), is(equalTo("oci.metrics")));
        assertThat("Injected bean", bean, instanceOf(OverridingOciMetricsBean.class));
        OciMetricsSupport ociMetricsSupport = bean.ociMetricsSupport();

        String namespace = getStringField("namespace", ociMetricsSupport);
        String resourceGroup = getStringField("resourceGroup", ociMetricsSupport);

        assertThat("Effective namespace", namespace, is(equalTo(PRODUCT)));
        assertThat("Effective resourceGroup", resourceGroup, is(equalTo(FLEET)));
    }

    private String getStringField(String fieldName, OciMetricsSupport ociMetricsSupport) {
        // Except for testing, there is no need for OciMetricsSupport to expose methods returning the namespace and resource
        // group so just use reflection rather than add those methods. It's only a test so using reflection is not so bad.
        //
        // Now that OciMetricsSupport delegates to OciMetricsService which is a package-private class, we need to take a few
        // added steps to get the data we want.
        try {
            var delegateField = OciMetricsSupport.class.getDeclaredField("delegate");
            delegateField.setAccessible(true);

            var ociMetricsServiceClass = Class.forName("io.helidon.integrations.oci.metrics.OciMetricsService");
            var delegate = delegateField.get(ociMetricsSupport);

            var prototypeMethod = ociMetricsServiceClass.getDeclaredMethod("prototype");
            prototypeMethod.setAccessible(true);

            OciMetricsConfig prototype = (OciMetricsConfig) prototypeMethod.invoke(delegate);

            // Account for the fact that OciMetricsConfig inherits the base interface which is where the method is declared.
            var getterMethodForRequestedField = OciMetricsConfigBase.class.getDeclaredMethod(fieldName);
            getterMethodForRequestedField.setAccessible(true);

            // This test only attempts to fetch Optional<String> values, so just cast the return value that way.
            var value = (Optional<String>) getterMethodForRequestedField.invoke(prototype);
            return value.orElse(null);

        } catch (NoSuchFieldException | ClassNotFoundException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
